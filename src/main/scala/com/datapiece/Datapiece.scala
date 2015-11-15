package com.datapiece

import java.awt.image.BufferedImage
import java.awt.Color
import java.awt.image.DataBufferByte
import javax.imageio.ImageIO
import java.io.{ File => JFile }

import scala.io.Source

import better.files._

import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.tototoshi.csv._

import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.JsonMethods.mapper
import org.json4s._

import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * The core functions: opening images and Boxes file, detecting
 * exact crop area for each Box and saving the result.
 */
object Datapiece {

  /**
   * Run the process.
   */
  def run(config: Config) {

    var boxes = getBoxes(config.boxesFile)
    val scale = getScale(config.dpi, config.sourceDpi)
    val input = File(config.infile)
    var masked = false

    val outdir = File(config.outfile)

    val files = input.glob(".*.png", syntax = "regex")

    val futureList = Future.traverse(files)(infile ⇒
      Future {

        if (!config.quiet) {
          println("Processing: " + infile.path)
        }

        val infilePath = config.maskfile match {
          case "" => infile.path.toString
          case mask => File(mask).path.toString
        }

        var bufferedImage = getImage(infilePath)
        val bytes = getImageData(bufferedImage)
        var pixelLength = getPixelLength(bufferedImage)
        val image = new ArrayImage(bytes, bufferedImage.getWidth, pixelLength)

        val cropped = boxes.map(b => processBox(image, b, scale, config))

        if (config.jsonOut != "") {
          saveFoundAsText(cropped, config.jsonOut)
        }

        var outfile = outdir.path.toString

        if (!config.findOnly) {

          var infileBase = infile.name

          if (config.split) {
            infileBase = infile.name.toString.split('.')(0) + "_"
          } else if (outdir.isDirectory) {
            outfile = outdir.path + "/" + infileBase
          }

          if (masked) {
            bufferedImage = getImage(infile.path.toString)
          }

          writeImage(bufferedImage, cropped, outfile, config.split, config.horizontal)
        }

        outfile
      }
    )

    val done = Await.result(futureList, 800 hour)

  }

  /**
   * Apply per-Box settings like "exact," use derived settings (scale, ratio, percent),
   * and pass specific global parameters to find method.
   */
  def processBox(image: ArrayImage, b: Box, scale: Double, config: Config): Box = {

    val buffer = config.buf

    if (b.exact) {
      return Box((scale * b.x1).toInt, (scale * b.y1).toInt, (scale * b.x2).toInt, (scale * b.y2).toInt, b.name, b.exact)
    }

    val x1L = (scale * (b.x1 - buffer)).toInt
    val y1L = (scale * (b.y1 - buffer)).toInt
    val x2L = (scale * (b.x2 + buffer)).toInt
    val y2L = (scale * (b.y2 + buffer)).toInt

    val (ratio, percent) = boxToRP(b, buffer)
    val largeBox = Box(x1L, y1L, x2L, y2L)

    val cb = find(image, largeBox, ratio, percent, scale, config.minBlobSize, config.border, config.stretch)

    Box(x1L + cb.x1, y1L + cb.y1, x1L + cb.x2, y1L + cb.y2, b.name, b.exact)
  }

  /**
   * Size of Box when coordinates are zero-based.
   */

  val sizeOfZeroBased = (x: Box) => ((x.x2 - x.x1) + 1) * ((x.y2 - x.y1) + 1)

  /**
   * Algorithm for guessing correct connected component bounding
   * box based on the size and aspect ratio.
   */

  def find(image: ArrayImage, area: Box, ratio: Double, percentOfWindow: Double, scale: Double, minBlobSize: Int = 2000, border: Int = 2, stretch: Int = 0): Box = {

    // We stretch by a few pixes in the search area so
    // the total area doesn't become a candidate.
    val areaLong = Box(area.x1 + stretch, area.y1, area.x2, area.y2)

    var blobs = FeatureDetection.labelImage(image, areaLong)

    val imsize: Float = image.data.length

    blobs = blobs.filter(b => sizeOfZeroBased(b) > minBlobSize)

    if (blobs.length == 0)
      return Box(0, 0, 0, 0, "none")

    val sizes = blobs.map(b => sizeOfZeroBased(b))

    val percents = sizes.map(_ / imsize)

    val percent_diffs = percents.map(p => Math.abs(percentOfWindow - p))

    val aratios = blobs.map(b => ((b.x2 - b.x1) + 1) / ((b.y2 - b.y1) + 1))

    val aratio_diffs = aratios.map(r => Math.abs(r - ratio))

    val min_ratio_diff = aratio_diffs.min
    val min_percent_diff = percent_diffs.min

    val close_ratio = aratio_diffs.indexOf(min_ratio_diff)
    val close_percent = percent_diffs.indexOf(min_percent_diff)

    var b_index: Int = -1

    if (close_ratio != close_percent) {
      val min_ratio_score = min_ratio_diff / ratio
      val min_ratio_percent_score = percent_diffs(close_ratio) / percentOfWindow

      val min_percent_score = min_percent_diff / percentOfWindow
      val min_percent_ratio_score = aratio_diffs(close_percent)
      if (min_ratio_score * min_ratio_percent_score < min_percent_score * min_percent_ratio_score)
        b_index = close_ratio
      else
        b_index = close_percent
    } else {
      b_index = close_ratio // They're the same
    }

    val b = blobs(b_index)

    val x1 = b.x1.toInt + border + stretch
    val y1 = b.y1.toInt + border
    val x2 = b.x2.toInt - border
    val y2 = b.y2.toInt - border

    Box(x1, y1, x2, y2)

  }

  /**
   * Save image or images.
   */

  def writeImage(bufferedImage: BufferedImage, cropped: List[Box], outfile: String, split: Boolean, horizontal: Boolean = false) {

    if (split) {
      writeSplitImages(bufferedImage, cropped, outfile)
    } else if (horizontal) {
      writeWholeImageHorizontal(bufferedImage, cropped, outfile)
    } else {
      writeWholeImageVertical(bufferedImage, cropped, outfile)
    }
  }

  def writeSplitImages(bufferedImage: BufferedImage, cropped: List[Box], outfile: String) {
    var i = 0
    var fname = ""

    for (c <- cropped) {
      // File names for named boxes,
      // integer for unnamed
      if (c.name != "")
        fname = outfile + c.name
      else
        fname = outfile + i

      writeSubImage(c, bufferedImage, fname)
      i += 1
    }
  }

  def writeWholeImageHorizontal(bufferedImage: BufferedImage, cropped: List[Box], outfile: String) {

    val totalWidth = cropped.map((b: Box) => b.x2 - b.x1).sum
    val maxHeight = cropped.map((b: Box) => b.y2 - b.y1).reduce(Math.max)

    val combined = new BufferedImage(totalWidth, maxHeight, BufferedImage.TYPE_INT_RGB)

    val g = combined.getGraphics

    var cursor = 0
    for (c <- cropped) {

      var si = bufferedImage.getSubimage(c.x1, c.y1, c.x2 - c.x1, c.y2 - c.y1)
      g.drawImage(si, cursor, 0, null)

      cursor += c.x2 - c.x1
    }

    ImageIO.write(combined, "PNG", new JFile(outfile))

  }

  def writeWholeImageVertical(bufferedImage: BufferedImage, cropped: List[Box], outfile: String) {

    val totalHeight = cropped.map((b: Box) => b.y2 - b.y1).sum
    val maxWidth = cropped.map((b: Box) => b.x2 - b.x1).reduce(Math.max)

    val combined = new BufferedImage(maxWidth, totalHeight, BufferedImage.TYPE_INT_RGB)

    val g = combined.getGraphics

    g.setColor(Color.WHITE)
    g.fillRect(0, 0, combined.getWidth, combined.getHeight)

    var cursor = 0
    for (c <- cropped) {

      var si = bufferedImage.getSubimage(c.x1, c.y1, c.x2 - c.x1, c.y2 - c.y1)
      g.drawImage(si, 0, cursor, null)

      cursor += c.y2 - c.y1
    }

    ImageIO.write(combined, "PNG", new JFile(outfile))

  }

  def getPixelLength(bufferedImage: BufferedImage): Int = {
    var pixelLength = 0
    if (bufferedImage.getAlphaRaster() != null)
      pixelLength = 4
    else
      pixelLength = 3

    pixelLength
  }

  def getImageData(bufferedImage: BufferedImage): Array[Byte] = {
    bufferedImage.getRaster().getDataBuffer().asInstanceOf[DataBufferByte].getData()
  }

  def getImage(infile: String): BufferedImage = {
    ImageIO.read(new JFile(infile))
  }

  def getScale(dpi: Int, sourceDpi: Int): Double = {
    dpi / sourceDpi.toDouble
  }

  /**
   * Read the Boxes file.
   */

  def getBoxes(boxesFile: String) = {
    val extension = getExtension(boxesFile)

    var boxes: List[Box] = List(Box(0, 0, 0, 0))

    if (extension == "csv") {

      val reader = CSVReader.open(new JFile(boxesFile))
      val csv = reader.all()
      reader.close()

      boxes = csv.map { x =>
        var tm = x.map(_.trim)
        Box(
          tm(1).toInt,
          tm(2).toInt,
          tm(3).toInt,
          tm(4).toInt,
          tm(0),
          tm(5).toBoolean)
      }

    } else if (extension == "json") {

      val json = Source.fromFile(boxesFile).mkString

      boxes = mapJSON(json)

    } else {

      throw new Exception("Error: Unrecognized boxes file extension. Must be json or csv.")
    }
    boxes
  }

  def getExtension(boxesFile: String): String = {

    var extension = ""
    try {
      extension = boxesFile.split('.').last.toLowerCase
    } catch {
      case e: Exception => throw new Exception("Error: Invalid boxes file.")
    }

    extension
  }

  def saveFoundAsText(boxes: List[Box], fname: String) {

    mapper.registerModule(DefaultScalaModule)

    val out = new java.io.FileWriter(fname)
    val prettyPrinter = mapper.writerWithDefaultPrettyPrinter()

    out.write(prettyPrinter.writeValueAsString(boxes))
    out.close

  }

  def writeSubImage(b: Box, image: BufferedImage, fname: String) {
    if (b.y2 - b.y1 < 0 || b.x2 - b.x1 < 0) {
      println("Image zero or negative size: " + fname)
      return
    }

    try {
      val subimage = image.getSubimage(b.x1, b.y1, (b.x2 - b.x1) + 1, (b.y2 - b.y1) + 1)
      ImageIO.write(subimage, "png", new JFile(fname + ".png"))
    } catch {
      case e: Exception => println(e)
    }
  }

  def boxToRP(b: Box, buf: Int): (Double, Double) = {
    val width = b.x2 - b.x1
    val height = b.y2 - b.y1
    val area = height * width
    val buffer_area = (height + 2 * buf) * (width + 2 * buf)
    val ratio = width / height.toDouble

    val percent = area / buffer_area.toDouble

    (ratio, percent)

  }

  def mapJSON(json: String): List[Box] = {
    implicit val formats = DefaultFormats
    parse(json).extract[List[Box]]
  }

}
