package com.datapiece

import scala.collection.mutable.Stack
import scala.collection.mutable.{ Map => MutableMap }

import ammonite.repl.Repl._

object FeatureDetection {

  var s = System.currentTimeMillis

  def time(m: String) {
    print(m + ": ")
    println(System.currentTimeMillis - s)
    s = System.currentTimeMillis
  }

  def labelImage(img: ArrayImage, m: Box): List[Box] = {
    time("fd start")

    val w = (m.x2 - m.x1) + 1
    val h = (m.y2 - m.y1) + 1
    var lab = 1
    var pos = Array[Int]()
    val stack = Stack[Array[Int]]()
    val label = Array.ofDim[Int](w, h)

    var (i, j, x, y) = (-1, -1, -1, -1)

    var xc = m.x1
    var yc = -1
    var lr = -1

    var xcl = -1
    var ycl = -1
    time("fd before while")

    while (xc <= m.x2) {

      yc = m.y1

      while (yc <= m.y2) {

        xcl = xc - m.x1
        ycl = yc - m.y1

        if (img(xc, yc) != 0 && label(xcl)(ycl) <= 0) {

          //debug("label" -> label, "img" -> img)

          stack.push(Array(xc, yc))
          label(xcl)(ycl) = lab

          while (!stack.isEmpty) {

            pos = stack.pop()

            i = pos(0)
            j = pos(1)

            x = m.x1
            y = m.y1

            lr = -1
            while (lr <= 1) {
              var ud = -1
              while (ud <= 1) {
                if (!(lr == 0 && ud == 0)) {
                  x = i + lr
                  y = j + ud

                  if (x <= m.x2 && y <= m.y2 && x >= m.x1 && y >= m.y1) {
                    if (img(x, y) == -1 && label(x - m.x1)(y - m.y1) == 0) {
                      stack.push(Array(x, y))
                      label(x - m.x1)(y - m.y1) = lab
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
    time("while")
    val r = getBoundingBoxes(label)
    time("get bb")
    r
  }

  def getBoundingBoxes(img: Array[Array[Int]]): List[Box] = {

    val w = img.length
    val h = img(0).length

    var boxes = MutableMap[Int, Box]()

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
