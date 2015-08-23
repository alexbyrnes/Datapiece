package com.datapiece

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.{ InputStreamReader, InputStream }
import org.apache.commons.io.IOUtils
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.io.Source

import scala.collection.mutable.ListBuffer

import java.io.File

object Datapiece extends App {

  val parser = new scopt.OptionParser[Config]("datapiece") {
    override def showUsageOnError = true
    head("Datapiece", "0.1")
    opt[Double]('p', "percentOfWindow") action { (x, c) =>
      c.copy(percentOfWindow = x)
    } text ("Approximate percent of the total image area taken up by the target box. Use for processing one box per image.")
    opt[Double]('r', "ratio") action { (x, c) =>
      c.copy(ratio = x)
    } text ("Approximate ratio, width divided by height, of the target box. Use for processing one box per image.")
    opt[String]('b', "boxes") action { (x, c) =>
      c.copy(json = x)
    } text ("JSON bounding boxes file. Use for multiple boxes per image. Format: [{\"x1\":10, \"y1\":10, \"x2\": 20, \"y2\": 30},... ]")
    opt[String]('i', "infile") action { (x, c) =>
      c.copy(infile = x)
    } optional () text ("Image input file.")
    opt[String]('o', "outfile") action { (x, c) =>
      c.copy(outfile = x)
    } text ("Image output file.")
    opt[Int]('R', "buffer") action { (x, c) =>
      c.copy(buf = x)
    } text ("Buffer around the target boxes. Default: 10 pts (or pixels if bounding boxes are in pixels. See dpi and sourcedpi.) Should be large enough to account for shift, skew, enlargement of target boxes but small enough not to include other boxes on the page. Overlap with bounding boxes in the JSON is OK.")
    opt[Int]('m', "minblobsize") action { (x, c) =>
      c.copy(minBlobSize = x)
    } text ("Minimum area in pixels of connected components. Default: 2000. Eliminates very small enclosed areas from search space.")
    opt[Int]('B', "border") action { (x, c) =>
      c.copy(border = x)
    } text ("Border to crop from target boxes in pixels. Eliminates any remaining lines around the box.")
    opt[Int]('d', "dpi") action { (x, c) =>
      c.copy(dpi = x)
    } text ("DPI of image.")
    opt[Int]('s', "sourcedpi") action { (x, c) =>
      c.copy(sourceDpi = x)
    } text ("DPI of JSON. Default is 72. If your bounding box JSON is in points (pts) from a standard PDF, leave this blank.")
    opt[Int]('t', "threshold") action { (x, c) =>
      c.copy(threshold = x)
    } text ("Threshold for binarization. Default is 128 (Gray halfway between white and black).")
    opt[Unit]('S', "split") action { (x, c) =>
      c.copy(split = true)
    } text ("Split images into separate files. Uses prefix specified by the outfile option. Example: -S -o /tmp/out will produce /tmp/out0.png, /tmp/out1.png etc...")
    opt[String]('j', "jsonout") action { (x, c) =>
      c.copy(jsonOut = x)
    } text ("Filename for boxes found (JSON).  If used with --findonly flag, no images will be produced.")
    opt[Unit]('f', "findonly") action { (x, c) =>
      c.copy(findOnly = true)
    } text ("Find boxes only. Filename for JSON output required.")

  }

  parser.parse(args, Config()) match {
    case Some(config) => run(config)
    case None => println("Error: Missing config")
  }

  def run(config: Config) {
    //Thread.sleep(15000)

    val buf = config.buf // Brevity

    val json = Source.fromFile(config.json).mkString
    val boxes = mapJSON(json)

    val scale = config.dpi / config.sourceDpi.toDouble

    val minX = boxes.map(_.x1).min - buf
    val minY = boxes.map(_.y1).min - buf
    val maxX = boxes.map(_.x2).max + buf
    val maxY = boxes.map(_.y2).max + buf

    val minXscale = (minX * scale).toInt
    val minYscale = (minY * scale).toInt
    val maxXscale = (maxX * scale).toInt
    val maxYscale = (maxY * scale).toInt

    val boxesSubimage = boxes.map(b => Box(b.x1 - minX, b.y1 - minY, b.x2 - minX, b.y2 - minY, b.name, b.exact))

    val bufferedImage = ImageIO.read(new File(config.infile))
    val subImage = bufferedImage.getSubimage(minXscale, minYscale, maxXscale - minXscale, maxYscale - minYscale)

    val image = toIntArray(subImage, -2)

    val cropped: List[Box] = boxesSubimage.map(b => processBox(image, b, buf, scale, config))

    // translate back to full image
    val croppedFullImage = cropped.map(b => Box(b.x1 + minXscale, b.y1 + minYscale, b.x2 + minXscale, b.y2 + minYscale, b.name, b.exact))

    if (config.jsonOut != "")
      saveFoundAsText(croppedFullImage, config.jsonOut)

    if (config.findOnly)
      return

    if (config.split) {
      // Save each box to separate image
      var i = 0
      var fname = ""

      for (c <- croppedFullImage) {
        // File names for named boxes,
        // integer for unnamed
        if (c.name != "")
          fname = config.outfile + c.name
        else
          fname = config.outfile + i

        writeSubImage(c, bufferedImage, fname)
        i += 1
      }

    } else {
      /*
      // Put boxes in one image
      val totalWidth = cropped.map((b:Box) => b.x2-b.x1).sum
      val maxHeight = cropped.map((b:Box) => b.y2-b.y1).reduce(Math.max)

      var matConcat = new Mat(maxHeight, totalWidth, image.`type`(), new Scalar(255,255,255))

      var cursor = 0
      for (c <- cropped) {
        val areaOfInterest = matConcat.submat(0, c.y2-c.y1, cursor, cursor+(c.x2-c.x1))
        val submatrix = image.submat(c.y1, c.y2, c.x1, c.x2)
        submatrix.copyTo(areaOfInterest)
        cursor += c.x2-c.x1
      }

      // If there's an output file name,
      // used it, otherwise print to stdout
      if (config.outfile != "") {
        Imgcodecs.imwrite(config.outfile, matConcat)
      } else {
        val matBufout = new MatOfByte()
        Imgcodecs.imencode(".png", matConcat, matBufout)
        val bufout: Array[Byte] = matBufout.toArray()
        IOUtils.write(bufout, System.out)
      }
*/
    }

  }

  def saveFoundAsText(boxes: List[Box], fname: String) {

    mapper.registerModule(DefaultScalaModule)

    val out = new java.io.FileWriter(fname)
    val prettyPrinter = mapper.writerWithDefaultPrettyPrinter()

    out.write(prettyPrinter.writeValueAsString(boxes))
    out.close

  }

  def toIntArray(img: BufferedImage, threshold: Int): Array[Array[Int]] = {
    val w = img.getWidth
    val h = img.getHeight

    val ba = Array.ofDim[Int](w, h)

    for (x <- 0 until w; y <- 0 until h) {
      if (img.getRGB(x, y) > threshold)
        ba(x)(y) = 1
      else
        ba(x)(y) = 0
    }

    // ---------
    /*val wp = ba.length
    val hp = ba(0).length
    //val label = Array.ofDim[Int](ncol, nrow)

    for (xp <- 0 until wp) {
      for (yp <- 0 until hp) {
        print(ba(xp)(yp))
      }
      println
    }*/

    ba
  }

  def writeSubImage(b: Box, image: BufferedImage, fname: String) {
    if (b.y2 - b.y1 <= 0 || b.x2 - b.x1 <= 0) {
      println("Image zero or negative size: " + fname)
      return
    }

    try {
      //val submatrix = image.submat(c.y1, c.y2, c.x1, c.x2)
      println(b)
      val submatrix = image.getSubimage(b.x1, b.y1, b.x2 - b.x1, b.y2 - b.y1)
      //Imgcodecs.imwrite(fname + ".png", submatrix)
      ImageIO.write(submatrix, "png", new File(fname + ".png"))
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

  def processBox(image: Array[Array[Int]], b: Box, buffer: Int, scale: Double, config: Config): Box = {

    if (b.exact) {
      return Box((scale * b.x1).toInt, (scale * b.y1).toInt, (scale * b.x2).toInt, (scale * b.y2).toInt, b.name, b.exact)
    }

    val x1L = (scale * (b.x1 - buffer)).toInt
    val y1L = (scale * (b.y1 - buffer)).toInt
    val x2L = (scale * (b.x2 + buffer)).toInt
    val y2L = (scale * (b.y2 + buffer)).toInt

    val (ratio, percent) = boxToRP(b, buffer)

    println(Box(x1L, y1L, x2L, y2L, b.name))
    val cb = find(image, Box(x1L, y1L, x2L, y2L), ratio, percent, config.minBlobSize, config.border)
    println(cb)

    Box(x1L + cb.x1, y1L + cb.y1, x1L + cb.x2, y1L + cb.y2, b.name, b.exact)
  }

  def find(image: Array[Array[Int]], area: Box, ratio: Double, percentOfWindow: Double, minBlobSize: Int = 2000, border: Int = 2): Box = {

    var blobs = FeatureDetection.labelImage(image, area)

    //blobs.foreach(println)

    val imsize: Float = image.length * image(0).length

    val sizeOfZeroBased = (x: Box) => ((x.x2 - x.x1) + 1) * ((x.y2 - x.y1) + 1)
    val sizeOf = (x: Box) => (x.x2 - x.x1) * (x.y2 - x.y1)

    blobs = blobs.filter(b => sizeOf(b) > minBlobSize).filter(b => sizeOfZeroBased(b) < sizeOf(area))

    blobs.foreach(println)

    if (blobs.length == 0)
      return Box(0, 0, 0, 0, "none")

    val sizes = blobs.map(b => sizeOf(b))

    val percents = sizes.map(_ / imsize)

    val percent_diffs = percents.map(p => Math.abs(percentOfWindow - p))

    val aratios = blobs.map(b => (b.x2 - b.x1) / (b.y2 - b.y1))

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

}
