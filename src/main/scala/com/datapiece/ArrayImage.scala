package com.datapiece

class ArrayImage(val data: Array[Byte], val w: Int, val pixelLength: Int) {

  def apply(x: Int, y: Int): Byte = {
    data((y * (w * pixelLength)) + (x * pixelLength))
  }
}
