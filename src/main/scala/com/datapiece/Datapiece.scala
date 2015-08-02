package com.datapiece

import org.opencv.core.Core
import org.opencv.core.MatOfRect
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import reflect._
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.{InputStreamReader, InputStream}
import org.opencv.core.MatOfByte
import org.apache.commons.io.IOUtils
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.io.Source

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer


object Datapiece extends App {


  val parser = new scopt.OptionParser[Config]("datapiece") {
    override def showUsageOnError = true
    head("Datapiece", "0.1")
    opt[Double]('p', "percentOfWindow") action { (x, c) =>
      c.copy(percentOfWindow = x) } text("Approximate percent of the total image area taken up by the target box. Use for processing one box per image.")
    opt[Double]('r', "ratio") action { (x, c) =>
      c.copy(ratio = x) } text("Approximate ratio, width divided by height, of the target box. Use for processing one box per image.")
    opt[String]('b', "boxes") action { (x, c) =>
      c.copy(json = x) } text("JSON bounding boxes file. Use for multiple boxes per image. Format: [{\"x1\":10, \"y1\":10, \"x2\": 20, \"y2\": 30},... ]")
    opt[String]('i', "infile") action { (x, c) =>
      c.copy(infile = x) } optional() text("Image input file.")
    opt[String]('o', "outfile") action { (x, c) =>
      c.copy(outfile = x) } text("Image output file.")
    opt[Int]('R', "buffer") action { (x, c) =>
      c.copy(buf = x) } text("Buffer around the target boxes. Default: 10 pts (or pixels if bounding boxes are in pixels. See dpi and sourcedpi.) Should be large enough to account for shift, skew, enlargement of target boxes but small enough not to include other boxes on the page. Overlap with bounding boxes in the JSON is OK.")
    opt[Int]('m', "minblobsize") action { (x, c) =>
      c.copy(minBlobSize = x) } text("Minimum area in pixels of connected components. Default: 2000. Eliminates very small enclosed areas from search space.")
    opt[Int]('B', "border") action { (x, c) =>
      c.copy(border = x) } text("Border to crop from target boxes in pixels. Eliminates any remaining lines around the box.")      
    opt[Int]('d', "dpi") action { (x, c) =>
      c.copy(dpi = x) } text("DPI of image.")
    opt[Int]('s', "sourcedpi") action { (x, c) =>
      c.copy(sourceDpi = x) } text("DPI of JSON. Default is 72. If your bounding box JSON is in points (pts) from a standard PDF, leave this blank.")
    opt[Int]('t', "threshold") action { (x, c) =>
      c.copy(threshold = x) } text("Threshold for binarization. Default is 128 (Gray halfway between white and black).")
    opt[Unit]('S', "split") action { (x, c) =>
      c.copy(split = true) } text("Split images into separate files. Uses prefix specified by the outfile option. Example: -S -o /tmp/out will produce /tmp/out0.png, /tmp/out1.png etc...")
    opt[String]('j', "jsonout") action { (x, c) =>
      c.copy(jsonOut = x) } text("Filename for boxes found (JSON).  If used with --findonly flag, no images will be produced.")
    opt[Unit]('f', "findonly") action { (x, c) =>
      c.copy(findOnly = true) } text("Find boxes only. Filename for JSON output required.")

}

  parser.parse(args, Config()) match {
    case Some(config) => run(config) 
    case None => println("Error: Missing config")
  }

  def run(config: Config) {

    System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

    val buf = config.buf // Brevity

    val json = Source.fromFile(config.json).mkString 
    val boxes = mapJSON(json)

    val image: Mat = threshold(readImage(config.infile), config.threshold)
    val scale = (config.dpi/config.sourceDpi.toDouble).toInt

    val cropped: java.util.List[Box] = boxes.map(b => processBox(image, b, buf, scale, config))

    if (config.jsonOut != "") {
      mapper.registerModule(DefaultScalaModule)

      val out = new java.io.FileWriter(config.jsonOut)
      val prettyPrinter = mapper.writerWithDefaultPrettyPrinter()

      out.write(prettyPrinter.writeValueAsString(cropped))
      out.close

      if (config.findOnly) 
        return
    } 
    

    if (config.split) {
      // Save each box to separate image
      var i = 0
      var fname = ""

      for (c <- cropped) {

          // File names for named boxes,
          // integer for unnamed
          if (c.name != "")
            fname = config.outfile + c.name
          else
            fname = config.outfile + i

          val submatrix = image.submat(c.y1, c.y2, c.x1, c.x2)
          Imgcodecs.imwrite(fname + ".png", submatrix)
          i += 1
      }

    } else {

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
    }

  }

  def readImage(filename: String): Mat = {

    val source = if (filename != "") {
      Imgcodecs.imread(filename,  Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE )
    } else {
      val inputStream : InputStream = System.in
      val buf : Array[Byte] = IOUtils.toByteArray(inputStream) 

      val matBuf = new MatOfByte() 
      matBuf.fromArray(buf: _*) 
      Imgcodecs.imdecode(matBuf, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE)
    }

    source
  }

  def threshold(source: Mat, threshold: Int = 128): Mat = { 
      
    var thresholded = new Mat(source.rows(),source.cols(),source.`type`())
    Imgproc.threshold(source, thresholded, threshold, 255, Imgproc.THRESH_BINARY)
 
    thresholded    
  }


  def boxToRP(b: Box, buf: Int): (Double, Double) = {
    val width = b.x2 - b.x1
    val height = b.y2 - b.y1
    val area = height * width
    val buffer_area = (height + 2*buf) * (width + 2*buf)
    val ratio = width/height.toDouble

    val percent = area / buffer_area.toDouble
    
    (ratio, percent)

  }

  def mapJSON(json:String): List[Box] = {

    implicit val formats = DefaultFormats
    parse(json).extract[List[Box]]

  }

  def processBox(image: Mat, b: Box, buffer: Int, scale: Int, config: Config): Box = { 

    if (b.exact) {
      return Box(scale*b.x1, scale*b.y1, scale*b.x2, scale*b.y2, b.name, b.exact)
    }

    val x1L = scale*(b.x1-buffer)
    val y1L = scale*(b.y1-buffer)
    val x2L = scale*(b.x2+buffer)
    val y2L = scale*(b.y2+buffer)
    val areaOfInterest = image.submat(y1L, y2L, x1L, x2L)
    val (ratio, percent) = boxToRP(b, buffer)

    val cb = find(areaOfInterest, ratio, percent, config.minBlobSize, config.border)
    Box(x1L + cb.x1, y1L + cb.y1, x1L + cb.x2, y1L + cb.y2, b.name, b.exact)
  }



  def find(image: Mat, ratio: Double, percentOfWindow: Double, minBlobSize: Int = 2000, border: Int = 2): Box = { 

    // Array index constants
    val minXi = 0
    val minYi = 1
    val width = 2
    val height = 3

    def minX(b: Mat) = b.get(0, minXi)(0)
    def minY(b: Mat) = b.get(0, minYi)(0)
    def maxX(b: Mat) = minX(b) + b.get(0, width)(0)
    def maxY(b: Mat) = minY(b) + b.get(0, height)(0)
    
    val o = new MatOfRect
    val o2 = new MatOfRect

    var connOut = new Mat(image.rows(), image.cols(), image.`type`())
    val out = Imgproc.connectedComponentsWithStats(image, connOut, o, o2)
    val imsize: Float = image.rows * image.cols

    val sizeOf = (x:Mat) => (maxX(x) - minX(x)) * (maxY(x) - minY(x))

    var blobs = for (i <- 0 until o.rows) yield o.row(i)
    blobs = blobs.filter(b => sizeOf(b) > minBlobSize)

    val sizes = blobs.map(b => sizeOf(b))
    val percents = sizes.map(_ / imsize)
    val percent_diffs = percents.map(p => Math.abs(percentOfWindow - p))
    val aratios = blobs.map(b => (maxX(b) - minX(b)) / (maxY(b) - minY(b))) 
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
      b_index = close_ratio  // They're the same
    }

    val b = blobs(b_index)

    val x1 = minX(b).toInt + border
    val y1 = minY(b).toInt + border
    val x2 = maxX(b).toInt - border
    val y2 = maxY(b).toInt - border
    
    Box(x1, y1, x2, y2) 
  }  
}

 
