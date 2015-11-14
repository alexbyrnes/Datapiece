package com.datapiece

/** An image with pixels in a one-dimensional array. */

class ArrayImage(val data: Array[Byte], val w: Int, val pixelLength: Int) {

  private var transparencyOffset = 0
  if (pixelLength == 4) transparencyOffset = 1

  def apply(x: Int, y: Int): Byte = {
    data(((y * (w * pixelLength)) + (x * pixelLength)) + transparencyOffset)
  }
}
