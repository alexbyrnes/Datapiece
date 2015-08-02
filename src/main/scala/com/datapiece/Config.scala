package com.datapiece

case class Config(percentOfWindow: Double = 0.0, ratio: Double = 1.0, json: String = "", infile: String = "", outfile: String = "", dpi: Int = 72, sourceDpi: Int = 72, buf: Int = 10, minBlobSize: Int = 2000, border: Int = 2, threshold: Int = 128, split: Boolean = false, jsonOut: String = "", findOnly: Boolean = false, kwargs: Map[String,String] = Map())

