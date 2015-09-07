package com.datapiece

class ArrayImage(val data: Array[Byte], val w: Int, val pixelLength: Int) {

  var transparencyOffset = 0
  if (pixelLength == 4) transparencyOffset = 1

  def apply(x: Int, y: Int): Byte = {

    data(((y * (w * pixelLength)) + (x * pixelLength)) + transparencyOffset)
  }
}
