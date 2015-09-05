package com.datapiece

class ArrayImage(val data: Array[Byte], val w: Int, val pixelLength: Int) {

  def apply(x: Int, y: Int): Byte = {
    data((y * (w * pixelLength)) + (x * pixelLength))
    //data((y * w) + (x * pixelLength))
  }

  /*def set(x: Int, y: Int, v: Byte) {
    data((y * w) + (x * pixelLength)) = v
  }*/
}

//000, -1-1-1, 000, 000, -1-1-1, 000
/*0, -1, 0
0, -1, 0

0,  1, 2, 3,  4, 5
0, -1, 0, 0, -1, 0

w = 3

x  y
0, 0 = 0
1, 0 = 1
2, 0 = 2
0, 1 = 3
1, 1 = 4
2, 1 = 5

data((y * w) + x)


0, 0, 0, -1, -1, -1, 0, 0, 0
0, 0, 0, -1, -1, -1, 0, 0, 0


0,  1, 2, 3,  4, 5
0, -1, 0, 0, -1, 0

0, 1, 2,  3,  4,  5, 6, 7, 8, 9,10,11, 12, 13, 14,15,16,17
0, 0, 0, -1, -1, -1, 0, 0, 0, 0, 0, 0, -1, -1, -1, 0, 0, 0

w = 9
pl = 3

x  y
0, 0 = 0
1, 0 = 3
2, 0 = 6
0, 1 = 9
1, 1 = 4
2, 1 = 5

(y * (w)) + (x * pixelLength)
*/
