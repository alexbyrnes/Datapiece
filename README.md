## Datapiece

Datapiece does high performance page segmentation for documents with tables, fill-in-the-blanks, or other small areas of interest across many files.  The output is the interest areas cropped exactly based on an estimated location.  This is primarily useful for large transcription efforts with Optical Character Recognition (OCR) but should help document processing for publication, storage, or to make reading the documents easier.

Documents with data in them make OCR on whole pages difficult. Data tends to come in small strings of characters surrounded by lines and boxes.

Differences between standard paragraph text and "data documents":

Data documents:

* Have little context to use for correcting errors. Values tend to be codes, dates, and numbers, or non-dictionary words such as first and last names.
* Have markup used to identify the data to the human eye that is difficult for OCR applications to distinguish from characters, and symbols.
* Come in large numbers: disclosure forms, tax documents, election results, and other institutional forms.


*Page segmentation is part of the preprocessing done by OCR programs to divide a printed page into paragraphs, headers, sidebars or other blocks of text.

![explainer](https://raw.githubusercontent.com/alexbyrnes/Datapiece/master/documentation/explainer.png)


## Installation

### Requirements

     Java 1.7+

### Usage

Prepare a JSON or CSV file with a bounding box for each field you're interested in extracting.  Each "box" should have a name, coordinates of the upper left and lower right corners of the box and optionally "exact" equal to true or false.  `"exact": true` tells datapiece to skip any search to find the exact area to crop and just use the exact coordinates given.  This is used with very predictable forms, or areas that don't have lines around them (see the [advertiser_address](https://github.com/alexbyrnes/Datapiece/blob/master/boxes_contract.json) field from the examples.  JSON can be produced from [Tabula](integration-with-tabula).  Both formats can be written from GIMP, Photoshop, Adobe Reader etc.  

See [Notes on coordinates](#notes-on-coordinates) for details on how to write coordinates in points at a particular resolution, or pixels. 

See [Extracting from PDFs](#extracting-from-pdfs) for instructions on extracting PNGs from PDFs, thresholding, and [creating masks](#using-mask-files).


###### JSON
```json

[{"name": "contract_dates", "x1": 305, "y1": 105, "x2": 403, "y2": 129},
 {"name": "station", "x1": 403, "y1": 180, "x2": 454, "y2": 204},
 {"name": "billing_calendar", "x1": 455, "y1": 154, "x2": 534, "y2": 179}
]
```
###### CSV

```text
contract_dates, 305, 105, 403, 129, false
station, 403, 180, 454, 204, false
billing_calendar, 455, 154, 534, 179, false
```

Each JSON object or CSV row corresponds to the outline of a field taken from a representative document with with point (x1, y1) at the top left corner, and (x2, y2) at the bottom right.  This is an approximate guess at the size, and location of the same field in other documents.  Datapiece will take this information for many fields and many PNG images and output the fields as separate images or one horizontally or vertically aligned image.


![example output](https://raw.githubusercontent.com/alexbyrnes/Datapiece/master/documentation/contract2_in.png)

After processing:

![example output](https://raw.githubusercontent.com/alexbyrnes/Datapiece/master/documentation/contract2.png)


### Examples using [FCC Political Files](https://stations.fcc.gov/)

(`datapiece` refers to the one-line script in the root directory.  `java -jar datapiece.jar` is equivalent.)

Output single vertically-arranged image.

    datapiece -i pngs/contract2.png -b boxes_contract.csv --dpi 300 -o out/contract2_out.png

Include a JSON file with the coordinates of the fields found.

    datapiece -i pngs/contract2.png -b boxes_contract.csv --dpi 300 --jsonout out/contract2.json -o out/contract2_out.png

Output series of images named out/contract2_<field>.png with --split.

    datapiece -i pngs/contract2.png -b boxes_contract.json -o out/contract2_ --dpi 300 --split

Process whole directory.

    datapiece -i pngs/ -b boxes_contract.json -o out/ --dpi 300
    
[Example script](https://github.com/alexbyrnes/Datapiece/blob/master/run-examples.sh)

#### Extracting from PDFs

One easy way to extract PNGs from a large number of PDFs is Ghostscript and ImageMagick:

Extract a PNG from a PDF.

    gs -sDEVICE=png16m -r300 in.pdf -o image.png 

(Ghostscript options like `-dFirstPage=1 -dLastPage=1` are also useful.)

Threshold the PNG using ImageMagick.

    convert image.png -threshold 50% png32:final.png

Threshold and create a mask file (see below) with small gaps between lines filled in.

    convert image.png -threshold 50% -morphology Open Square:1 png32:final_mask.png

The result final.png and final_mask.png are appropriate for input to datapiece. Note `-r300` is the resolution.  A higher resolution will produce a larger and more detailed image.  *The 300 from this command should go in the --dpi parameter in datapiece.*

##### Using mask files

Mask files are useful if the preprocessing to sharpen the borders around fields or otherwise enhance your image degrades the text quality.  In this case you can use one image with clean text to extract from, and one mask file with cleaner bounding boxes.  

For example, on Unix/Linux, ImageMagick can fill in small gaps between lines in the bounding boxes, but the process will make the text less readible.  Example script for filling in small gaps.

The `-M` or `--mask` parameter should be the path to an arbitrarily-named mask file, or a directory of mask files with one mask image for each input image (for multiple masks, the file name of the mask and the input file should be the same).

    datapiece -i pngs/contract_4.png -b boxes_contract.csv -o out/ -M masks/contract_4.png


### Integration with [Tabula](https://github.com/tabulapdf/tabula)

Tabula's JSON bounding box output can be used as an unofficial front-end.  Go to Advanced Options, JSON output, and save your fields


### Command Line Parameters

```

Datapiece 0.1
Usage: datapiece [options]

  -b <value> | --boxes <value>
        JSON or CSV bounding boxes file. Use for multiple boxes per image. Format: [{"name": "field_name", "x1":10, "y1":10, "x2": 20, "y2": 30, "exact": false},... ] 
 or field_name,10,10,20,30,false...
  -i <value> | --infile <value>
        Image input file or directory.
  -o <value> | --outfile <value>
        Image output file or directory.  If input is a directory, output must be a directory.
  -M <value> | --maskfile <value>
        Mask to use to identify target areas. Output will still come from input file.
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
  -q | --quiet
        Quiet mode.
  -h | --horizontal
        Output with images laid out horizontally. Only applies when split (-S) is not used. Default is vertical.
  -c <value> | --stretch <value>
        Pixels of stretch to add to search area. Useful for eliminating large candidate areas caused by whitespace at the edges of search area.

```

### Notes on coordinates

*See here first if you have trouble.*

Coordinates are given as x1/y1 and x2/y2.  These are the absolute coordinates of the upper left and lower right corners of the bounding box, *not the upper left coordinate and the height/width*.  Also note the coordinates are in [points](https://en.wikipedia.org/wiki/Point_(typography)) for compatibility with Tabula and other PDF applications.  To get an input image for Datapiece you need to [convert a PDF to PNG format](#extracting-from-pdfs) *at a particular resolution*.  For OCR this generally needs to be very high like 300 dots-per-inch and above.  If you convert your PDF using 300 DPI, put 300 as the dpi parameter to Datapiece and everything should work out fine.  The numbers you get from Tabula or another PDF program from the original PDF will be translated to pixels in the .png file.  If you got your bounding boxes from the input PNG, just leave --dpi out.

In some circumstances, the DPI of the source PDF will be something other than 72.  In these (rare) instances, use both the --dpi parameter for the resolution you extracted at, and --sourcedpi for the DPI of the original PDF.  Most of the time --dpi is the only parameter used.

See [boxes_contract.json](https://github.com/alexbyrnes/Datapiece/blob/master/boxes_contract.json), [boxes_contract.csv](https://github.com/alexbyrnes/Datapiece/blob/master/boxes_contract.csv) and [Integration with Tabula](#integrating-with-tabula) for more information on generating bounding boxes.


### Contributing

See Datapiece.scala to get started. Recompile with:

    sbt assembly

Pull requests welcome.


### Bugs, feature requests, updates

Please [submit bugs for feature requests](https://github.com/alexbyrnes/Datapiece/issues), or send me an email through my profile.  Updates on twitter [@alexbyrnes](https://twitter.com/alexbyrnes).

### License

Datapiece is released under the [MIT License](http://www.opensource.org/licenses/MIT).
