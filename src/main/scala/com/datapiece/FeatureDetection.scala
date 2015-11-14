package com.datapiece

import scala.collection.mutable.Stack
import scala.collection.mutable.Map

/**
 * The blob (connected component) detection and minimum
 * bounding box algorithms.
 */

object FeatureDetection {

  def labelImage(img: ArrayImage, m: Box): List[Box] = {

    val mx1 = m.x1
    val my1 = m.y1
    val mx2 = m.x2
    val my2 = m.y2

    val w = (mx2 - mx1) + 1
    val h = (my2 - my1) + 1
    var lab = 1
    var pos = Array[Int]()
    val stack = Stack[Array[Int]]()
    val label = Array.ofDim[Int](w, h)

    var (i, j, x, y) = (-1, -1, -1, -1)

    var xc = mx1
    var yc = -1
    var lr = -1

    var xcl = -1
    var ycl = -1

    while (xc <= mx2) {

      yc = my1

      while (yc <= my2) {

        xcl = xc - mx1
        ycl = yc - my1

        if (label(xcl)(ycl) <= 0) {

          stack.push(Array(xc, yc))
          label(xcl)(ycl) = lab

          while (!stack.isEmpty) {

            pos = stack.pop()

            i = pos(0)
            j = pos(1)

            x = mx1
            y = my1

            lr = -1
            while (lr <= 1) {
              var ud = -1
              while (ud <= 1) {
                if (!(lr == 0 && ud == 0)) {
                  x = i + lr
                  y = j + ud

                  if (x <= mx2 && y <= my2 && x >= mx1 && y >= my1) {
                    if (img(x, y) == -1 && label(x - mx1)(y - my1) == 0) {
                      stack.push(Array(x, y))
                      label(x - mx1)(y - my1) = lab
                    }
                  }

                }
                ud += 1
              }
              lr += 1
            }

          }
          lab += 1
        }
        yc += 1
      }
      xc += 1
    }
    getBoundingBoxes(label)
  }

  def getBoundingBoxes(img: Array[Array[Int]]): List[Box] = {

    val w = img.length
    val h = img(0).length

    var boxes = Map[Int, Box]()

    var l = 0

    var x = 0
    var y = -1

    while (x < w) {
      y = 0
      while (y < h) {
        l = img(x)(y)

        if (boxes.contains(l)) {

          var b = boxes(l)

          if (x < b.x1)
            b.x1 = x
          if (y < b.y1)
            b.y1 = y
          if (x > b.x2)
            b.x2 = x
          if (y > b.y2)
            b.y2 = y

        } else {

          boxes(l) = Box(x, y, x, y)

        }
        y += 1
      }
      x += 1
    }

    boxes.values.toList
  }

}
