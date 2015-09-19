Datapiece is page segmentation for documents with tables, fill-in-the-blanks, or other small areas of interest across many files.  Output is each area cropped exactly, or using computer vision to detect the location of a bounding box based on an estimate from the user.

Documents with data in them tend to make Optical Character Recognition on whole pages difficult: small strings of alphanumeric characters surrounded by lines and boxes.  These pieces of data are also generally sensitive to OCR errors.  There's little context to use for correction, values tend to be codes, dates, and numbers, or non-dictionary words such as first and last names, and the markup used to identify the data to the human eye tends to make OCR programs batty.  The aim of Datapiece is to break documents into precisely-cropped images for further processing or publication.


### Page Segmentation

Page segmentation is part of the preprocessing done by Optical Character Recognition (OCR) programs to divide a printed page into paragraphs, headers, sidebars or other blocks of text.


### Requirements

     Java 1.7+
     SBT

### Installation

### Usage

Datapiece can be run using Nailgun to avoid the JVM startup cost or on its own.  Examples are provided in run-examples.sh and run-examples-nailgun.sh (`./start-nailgun-server.sh` first).

The input bounding boxes is a JSON list of objects:

```json

[{"name": "contract_dates", "x1": 305, "y1": 105, "x2": 403, "y2": 129},
 {"name": "station", "x1": 403, "y1": 180, "x2": 454, "y2": 204},
 {"name": "billing_calendar", "x1": 455, "y1": 154, "x2": 534, "y2": 179}
]


```

### Notes on coordinates

*See here first if you have trouble.*

Coordinates are given as x1/y1 and x2/y2.  These are the absolute coordinates of the upper left and lower right corners of the bounding box, not the upper left coordinate and the height/width.  Some graphics programs will give coordinates with height/width.  Also note the coordinates are in points for compatibility with Tabula and other PDF applications.  To get an input image for Datapiece you need to convert a PDF to PNG format *at a particular resolution*.  For OCR this generally needs to be pretty high like 300 dots per inch.  If you convert your PDF using 300 DPI, put 300 as the dpi parameter to Datapiece and everything should work out fine.  The numbers you get from Tabula or another PDF program from the original PDF will be translated to pixels in the .png file.  If you got your bounding boxes from the input PNG, just leave --dpi out.

See boxes_contract.json and "Integration with Tabula" below for more information on generating bounding boxes.


### Examples

Output single horizontally-arranged image and the JSON bounding boxes found.

    datapiece -i pngs/contract2.png -b boxes_contract.json -o out/contract2_one_line.png --dpi 300 --jsonout out/contract2.json


Output series of images named out/contract2_<field>.png with --split.

    datapiece -i pngs/contract2.png -b boxes_contract.json -o out/contract2_ --dpi 300 --split


Output JSON without images with --findonly.

See all command line options with `datapiece --help`



### Integration with Tabula

Tabula's JSON bounding box output can be used as an unofficial front-end.  Go to Advanced Options, JSON output, and save your fields


### Command Line Parameters

```

Datapiece 0.1

Usage: datapiece [options]

  -b <value> | --boxes <value>
        JSON or CSV bounding boxes file. Use for multiple boxes per image. Format: [{"name": "field_name", "x1":10, "y1":10, "x2": 20, "y2": 30},... ] 
 or field_name,10,10,20,30...
  -i <value> | --infile <value>
        Image input file.
  -o <value> | --outfile <value>
        Image output file.
  -R <value> | --buffer <value>
        Buffer around the target boxes. Default: 10 pts (or pixels if bounding boxes are in pixels. See dpi and sourcedpi.) Should be large enough to account for shift, skew, enlargement of target boxes but small enough not to include other boxes on the page. Overlap with bounding boxes in the JSON is OK.
  -m <value> | --minblobsize <value>
        Minimum area in pixels of connected components. Default: 2000. Eliminates very small enclosed areas from search space.
  -B <value> | --border <value>
        Border to crop from target boxes in pixels. Eliminates any remaining lines around the box.
  -d <value> | --dpi <value>
        DPI of image.
  -s <value> | --sourcedpi <value>
        DPI of JSON. Default is 72. If your bounding box JSON is in points (pts) from a standard PDF, leave this blank.
  -S | --split
        Split images into separate files. Uses prefix specified by the outfile option. Example: -S -o /tmp/out will produce /tmp/out0.png, /tmp/out1.png etc...
  -j <value> | --jsonout <value>
        Filename for boxes found (JSON).  If used with --findonly flag, no images will be produced.
  -f | --findonly
        Find boxes only. Filename for JSON output required.


```


### Contributing

See Datapiece.scala for most of the code.  Recompile with:

    sbt -Djava.library.path=lib/linux -java-home /usr/lib/jvm/java-7-openjdk-amd64/ assembly

Pull requests welcome.


### Bugs, feature requests etc

Please submit bugs for feature requests to "Issues" above, or send me an email through my profile.  Updates on twitter @alexbyrnes.

### License


