package com.datapiece

import better.files._

object Main extends App {

  /** Configuration parameter parser */

  val parser = new scopt.OptionParser[Config]("datapiece") {
    override def showUsageOnError = true
    head("Datapiece", "0.1")
    opt[String]('b', "boxes") action { (x, c) =>
      c.copy(boxesFile = x)
    } text "JSON or CSV bounding boxes file. Use for multiple boxes per image. Format: [{\"name\": \"field_name\", \"x1\":10, \"y1\":10, \"x2\": 20, \"y2\": 30, \"exact\": false},... ] \n or field_name,10,10,20,30,false..."
    opt[String]('i', "infile") action { (x, c) =>
      c.copy(infile = x)
    } optional () text "Image input file or directory."
    opt[String]('o', "outfile") action { (x, c) =>
      c.copy(outfile = x)
    } text "Image output file or directory.  If input is a directory, output must be a directory."
    opt[String]('M', "maskfile") action { (x, c) =>
      c.copy(maskfile = x)
    } text "Mask to use to identify target areas. Output will still come from input file."
    opt[Int]('R', "buffer") action { (x, c) =>
      c.copy(buf = x)
    } text "Buffer around the target boxes. Default: 10 pts (or pixels if bounding boxes are in pixels. See dpi and sourcedpi.) Should be large enough to account for shift, skew, enlargement of target boxes but small enough not to include other boxes on the page. Overlap with bounding boxes in the JSON is OK."
    opt[Int]('m', "minblobsize") action { (x, c) =>
      c.copy(minBlobSize = x)
    } text "Minimum area in pixels of connected components. Default: 2000. Eliminates very small enclosed areas from search space."
    opt[Int]('B', "border") action { (x, c) =>
      c.copy(border = x)
    } text "Border to crop from target boxes in pixels. Eliminates any remaining lines around the box."
    opt[Int]('d', "dpi") action { (x, c) =>
      c.copy(dpi = x)
    } text "DPI of image."
    opt[Int]('s', "sourcedpi") action { (x, c) =>
      c.copy(sourceDpi = x)
    } text "DPI of JSON. Default is 72. If your bounding box JSON is in points (pts) from a standard PDF, leave this blank."
    opt[Unit]('S', "split") action { (x, c) =>
      c.copy(split = true)
    } text "Split images into separate files. Uses prefix specified by the outfile option. Example: -S -o /tmp/out will produce /tmp/out0.png, /tmp/out1.png etc..."
    opt[String]('j', "jsonout") action { (x, c) =>
      c.copy(jsonOut = x)
    } text "Filename for boxes found (JSON).  If used with --findonly flag, no images will be produced."
    opt[Unit]('f', "findonly") action { (x, c) =>
      c.copy(findOnly = true)
    } text "Find boxes only. Filename for JSON output required."
    opt[Unit]('q', "quiet") action { (x, c) =>
      c.copy(quiet = true)
    } text "Quiet mode."
    opt[Unit]('h', "horizontal") action { (x, c) =>
      c.copy(horizontal = true)
    } text "Output with images laid out horizontally. Only applies when split (-S) is not used. Default is vertical."
    opt[Int]('c', "stretch") action { (x, c) =>
      c.copy(stretch = x)
    } text "Pixels of stretch to add to search area. Useful for eliminating large candidate areas caused by whitespace at the edges of search area."

  }

  parser.parse(args, Config()) match {
    case Some(config) => {
      validate(config)
      Datapiece.run(config)
    }
    case None => throw new Exception("Missing input parameters.")
  }

  def validate(config: Config): Unit = {

    val dir = File(config.infile)
    val outdir = File(config.outfile)
    val boxes = File(config.boxesFile)

    if (dir.isDirectory && !outdir.isDirectory)
      throw new Exception("If input is a directory, output must be a directory.")

    if (config.findOnly && config.jsonOut == "")
      throw new Exception("findonly requires output file in jsonout parameter.")

    if (dir.isDirectory && config.findOnly)
      throw new Exception("findonly is only for single file input.")

    if (!dir.exists)
      throw new Exception(s"Input file or directory ${config.infile} does not exist.")

    if (!dir.isDirectory && dir.extension.get.toLowerCase != ".png")
      throw new Exception("Input image must be a PNG.")

    boxes.extension match {
      case Some(ex) =>
      case None => throw new Exception(s"Boxes file ${config.boxesFile} does not exist.")

    }
    if (!Seq(".json", ".csv").contains(boxes.extension.get.toLowerCase))
      throw new Exception("Boxes file must be CSV or JSON.")

    if (config.horizontal && config.split)
      println("Warning: Horizontal orientation only applies when split parameter is false.")

  }

}
