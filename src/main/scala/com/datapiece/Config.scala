package com.datapiece

/** The input parameters. Documented in the help text. */

case class Config(
  percentOfWindow: Double = 0.0,
  ratio: Double = 1.0,
  boxesFile: String = "",
  infile: String = "",
  outfile: String = "",
  maskfile: String = "",
  dpi: Int = 72,
  sourceDpi: Int = 72,
  buf: Int = 10,
  minBlobSize: Int = 2000,
  border: Int = 0,
  threshold: Int = 128,
  split: Boolean = false,
  jsonOut: String = "",
  findOnly: Boolean = false,
  quiet: Boolean = false,
  horizontal: Boolean = false,
  stretch: Int = 5)
