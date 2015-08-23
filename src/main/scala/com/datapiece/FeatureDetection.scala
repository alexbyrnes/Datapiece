package com.datapiece

import scala.collection.mutable.Stack
import scala.collection.mutable.{ Map => MutableMap }

object FeatureDetection { /* extends App {

  run()

  def run() {
    val test = Array.ofDim[Int](6, 6)

    test(0) = Array(0, 0, 0, 0, 0, 0)
    test(1) = Array(0, 0, 1, 1, 0, 0)
    test(2) = Array(0, 0, 0, 1, 0, 0)
    test(3) = Array(0, 0, 0, 0, 0, 0)
    test(4) = Array(0, 1, 0, 1, 0, 0)
    test(5) = Array(0, 0, 0, 0, 0, 0)

    var labeled = labelImage(test)
    labeled.foreach(println)

  }*/

  def labelImage(img: Array[Array[Int]], m: Box): List[Box] = {

    /*
        var a = Array.ofDim[Int](w, h)
        a.length == w
        a(0).length == h

        x <- 0 until w; y <- 0 until h

        ba(x)(y)
    */

    //val ox = m.x1
    //val oy = m.y1

    val w = m.x2 - m.x1
    val h = m.y2 - m.y1
    var lab = 1
    var pos = Array[Int]()
    val stack = Stack[Array[Int]]()
    val label = Array.ofDim[Int](w, h)

    for (xc <- m.x1 until m.x2; yc <- m.y1 until m.y2) {
      //for (r <- 1 until nrow - 1; c <- 1 until ncol - 1) {

      var xcl = xc - m.x1
      var ycl = yc - m.y1

      if (img(xc)(yc) != 0 && label(xcl)(ycl) <= 0) {

        //TODO: USE TUPLE

        stack.push(Array(xc, yc))
        label(xcl)(ycl) = lab

        while (!stack.isEmpty) {

          pos = stack.pop()

          /// TODO DON'T RECREATE THESE
          var i = pos(0)
          var j = pos(1)

          var x = m.x1
          var y = m.y1

          for (lr <- -1 to 1; ud <- -1 to 1 if !(lr == 0 && ud == 0)) {
            x = i + lr
            y = j + ud

            if (x < m.x2 && y < m.y2 && x >= m.x1 && y >= m.y1) {
              if (img(x)(y) == 1 && label(x - m.x1)(y - m.y1) == 0) {
                stack.push(Array(x, y))
                label(x - m.x1)(y - m.y1) = lab
              }
            }
          }

        }
        lab += 1
      }
    }

    /*var a = getBoundingBoxes(label)
    for ((k, v) <- a) {
      println(k)
      println(v)
      println
    }*/
    getBoundingBoxes(label)

  }

  def getBoundingBoxes(img: Array[Array[Int]]): List[Box] = {

    val w = img.length
    val h = img(0).length

    var boxes = MutableMap[Int, Box]()

    var l = 0

    for (x <- 0 until w; y <- 0 until h) {
      //for (r <- 0 until nrow; c <- 0 until ncol) {
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
    }

    boxes.values.toList
  }

}