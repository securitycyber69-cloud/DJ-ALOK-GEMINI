package com.example.core.screen

import android.graphics.Bitmap
import com.example.domain.model.AnnotationStroke
import com.example.domain.model.DrawingTool
import com.example.domain.model.NormBoundingBox
import kotlin.math.ceil
import kotlin.math.floor

object RegionCropper {

  fun computeBoundingBox(
    strokes: List<AnnotationStroke>,
    paddingFraction: Float = 0.04f,
  ): NormBoundingBox? {
    val activePoints =
      strokes
        .filter { it.tool != DrawingTool.ERASER && it.points.isNotEmpty() }
        .flatMap { it.points }
    if (activePoints.isEmpty()) return null

    var minX = 1f
    var minY = 1f
    var maxX = 0f
    var maxY = 0f
    activePoints.forEach { p ->
      if (p.x < minX) minX = p.x
      if (p.y < minY) minY = p.y
      if (p.x > maxX) maxX = p.x
      if (p.y > maxY) maxY = p.y
    }

    // Expand pointer / single-tap points into a readable box
    val pad = paddingFraction.coerceIn(0.01f, 0.15f)
    val left = (minX - pad).coerceIn(0f, 0.94f)
    val top = (minY - pad).coerceIn(0f, 0.94f)
    val right = (maxX + pad).coerceIn(left + 0.06f, 1f)
    val bottom = (maxY + pad).coerceIn(top + 0.06f, 1f)

    return NormBoundingBox(left = left, top = top, right = right, bottom = bottom)
  }

  fun cropBitmapToNormBox(
    source: Bitmap,
    box: NormBoundingBox,
  ): Bitmap? {
    if (!box.isValid() || source.width <= 1 || source.height <= 1) return null
    val x = floor(box.left.coerceIn(0f, 0.98f) * source.width).toInt().coerceIn(0, source.width - 2)
    val y = floor(box.top.coerceIn(0f, 0.98f) * source.height).toInt().coerceIn(0, source.height - 2)
    val rightPx = ceil(box.right.coerceIn(0f, 1f) * source.width).toInt().coerceIn(x + 1, source.width)
    val bottomPx = ceil(box.bottom.coerceIn(0f, 1f) * source.height).toInt().coerceIn(y + 1, source.height)
    val cropW = (rightPx - x).coerceAtLeast(1)
    val cropH = (bottomPx - y).coerceAtLeast(1)
    return Bitmap.createBitmap(source, x, y, cropW, cropH)
  }
}
