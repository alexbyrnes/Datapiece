package com.datapiece

import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import javax.imageio.ImageIO
import java.io.{ InputStreamReader, InputStream }
import org.apache.commons.io.IOUtils
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.JsonMethods.mapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.io.Source

import scala.collection.mutable.ListBuffer

import java.io.File

import com.github.tototoshi.csv._

//import ammonite.repl.Repl._

object Datapiece {

  var s = System.currentTimeMillis

  def run(config: Config) {
    //Thread.sleep(15000)
    val total = System.currentTimeMillis

    val buf = config.buf // Brevity

    var extension = getExtension(config)
    var boxes = getBoxes(config, extension)
    val scale = getScale(config)
    val bufferedImage = getImage(config)
    val bytes = getImageData(bufferedImage)
    var pixelLength = getPixelLength(bufferedImage)

    val image = new ArrayImage(bytes, bufferedImage.getWidth, pixelLength)

    val cropped = boxes.map(b => processBox(image, b, buf, scale, config))

    if (config.jsonOut != "")
      saveFoundAsText(cropped, config.jsonOut)

    if (config.findOnly)
      return

    writeImage(bufferedImage, cropped, config)
    print("total: ")
    println(System.currentTimeMillis - total)

  }

  def processBox(image: ArrayImage, b: Box, buffer: Int, scale: Double, config: Config): Box = {

    if (b.exact) {
      return Box((scale * b.x1).toInt, (scale * b.y1).toInt, (scale * b.x2).toInt, (scale * b.y2).toInt, b.name, b.exact)
    }

    val x1L = (scale * (b.x1 - buffer)).toInt
    val y1L = (scale * (b.y1 - buffer)).toInt
    val x2L = (scale * (b.x2 + buffer)).toInt
    val y2L = (scale * (b.y2 + buffer)).toInt

    val (ratio, percent) = boxToRP(b, buffer)
    val largeBox = Box(x1L, y1L, x2L, y2L)

    val cb = find(image, largeBox, ratio, percent, scale, config.minBlobSize, config.border)

    Box(x1L + cb.x1, y1L + cb.y1, x1L + cb.x2, y1L + cb.y2, b.name, b.exact)
  }

  val sizeOfZeroBased = (x: Box) => ((x.x2 - x.x1) + 1) * ((x.y2 - x.y1) + 1)

  def find(image: ArrayImage, area: Box, ratio: Double, percentOfWindow: Double, scale: Double, minBlobSize: Int = 2000, border: Int = 2): Box = {

    var blobs = FeatureDetection.labelImage(image, area)

    val imsize: Float = image.data.length

    blobs = blobs.filter(b => sizeOfZeroBased(b) > minBlobSize).filter(b => sizeOfZeroBased(b) < sizeOfZeroBased(area) * scale)

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

    val x1 = b.x1.toInt + border
    val y1 = b.y1.toInt + border
    val x2 = b.x2.toInt - border
    val y2 = b.y2.toInt - border

    Box(x1, y1, x2, y2)

  }

  def writeImage(bufferedImage: BufferedImage, cropped: List[Box], config: Config) {

    if (config.split) {
      writeSplitImages(bufferedImage, cropped, config)

    } else {

      writeWholeImage(bufferedImage, cropped, config)

    }

  }

  def writeSplitImages(bufferedImage: BufferedImage, cropped: List[Box], config: Config) {
    var i = 0
    var fname = ""

    for (c <- cropped) {
      // File names for named boxes,
      // integer for unnamed
      if (c.name != "")
        fname = config.outfile + c.name
      else
        fname = config.outfile + i

      writeSubImage(c, bufferedImage, fname)
      i += 1
    }
  }

  def writeWholeImage(bufferedImage: BufferedImage, cropped: List[Box], config: Config) {

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

    if (config.outfile != "") {
      ImageIO.write(combined, "PNG", new File(config.outfile))
    } else {

      throw new Exception("Error: No outfile.")

    }
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

  def getImage(config: Config): BufferedImage = {
    ImageIO.read(new File(config.infile))
  }

  def getScale(config: Config): Double = {
    config.dpi / config.sourceDpi.toDouble
  }

  def getBoxes(config: Config, extension: String) = {
    var boxes: List[Box] = List(Box(0, 0, 0, 0))

    if (extension == "csv") {

      // TODO DOES NOT TRIM
      val reader = CSVReader.open(new File(config.boxesFile))
      val csv = reader.all()
      reader.close()

      boxes = csv.map(x => Box(x(1).toInt, x(2).toInt, x(3).toInt, x(4).toInt, x(0), x(5).toBoolean))

    } else if (extension == "json") {

      val json = Source.fromFile(config.boxesFile).mkString

      boxes = mapJSON(json)

    } else {

      throw new Exception("Error: Unrecognized boxes file extension. Must be json or csv.")
    }
    boxes
  }

  def getExtension(config: Config): String = {

    var extension = ""
    try {
      extension = config.boxesFile.split('.').last.toLowerCase
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
      ImageIO.write(subimage, "png", new File(fname + ".png"))
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

  def time(m: String) {
    print(m + ": ")
    println(System.currentTimeMillis - s)
    s = System.currentTimeMillis
  }

}
