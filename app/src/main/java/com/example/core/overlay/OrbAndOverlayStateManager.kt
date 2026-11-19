package com.example.core.overlay

import androidx.compose.ui.geometry.Offset
import com.example.domain.model.FloatingOrbConfig

object OrbPositionHelper {

  fun clampFraction(fraction: Float, minVal: Float = 0.04f, maxVal: Float = 0.96f): Float =
    fraction.coerceIn(minVal, maxVal)

  fun computeSnappedXFraction(
    rawXFraction: Float,
    snapToEdge: Boolean,
  ): Float {
    val clamped = clampFraction(rawXFraction)
    if (!snapToEdge) return clamped
    return if (clamped < 0.5f) 0.06f else 0.94f
  }

  fun pixelOffsetForConfig(
    config: FloatingOrbConfig,
    containerWidthPx: Float,
    containerHeightPx: Float,
    orbSizePx: Float,
  ): Offset {
    val usableW = (containerWidthPx - orbSizePx).coerceAtLeast(1f)
    val usableH = (containerHeightPx - orbSizePx).coerceAtLeast(1f)
    val x = (config.positionXFraction.coerceIn(0f, 1f) * usableW)
    val y = (config.positionYFraction.coerceIn(0.08f, 0.88f) * usableH)
    return Offset(x, y)
  }
}
