package com.datapiece

/**
 * A rectangle with coordinates (x1, y1) for the
 * upper left corner and (x2, y2) for the lower
 * right corner.
 */

case class Box(
  var x1: Int,
  var y1: Int,
  var x2: Int,
  var y2: Int,
  name: String = "",
  exact: Boolean = false)
