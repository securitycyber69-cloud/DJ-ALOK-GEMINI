package com.example.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.edge.EdgeLightRenderParams

/**
 * Full four-side soft-diffused Gemini-style perimeter edge lighting overlay.
 * Uses centered matrix-rotated SweepGradient shaders and multi-layer BlurMaskFilter
 * ambient bloom so colors flow seamlessly around every side and rounded corner
 * without sharp wireframe lines or uneven/patchy spots.
 * Completely non-touch-blocking and lifecycle-aware.
 */
@Composable
fun EdgeLightPerimeterOverlay(
  params: EdgeLightRenderParams,
  modifier: Modifier = Modifier,
) {
  if (!params.isVisible) return

  val lifecycleOwner = LocalLifecycleOwner.current
  var isAppForeground by remember { mutableStateOf(true) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> isAppForeground = true
        Lifecycle.Event.ON_PAUSE -> isAppForeground = false
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val shouldAnimate = isAppForeground && params.speedMultiplier > 0f
  val transition = rememberInfiniteTransition(label = "edge_light_transition")
  val durationMs = (3600f / params.speedMultiplier.coerceAtLeast(0.2f)).toInt().coerceIn(1100, 10000)

  val phase by
    if (shouldAnimate) {
      transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
          ),
        label = "edge_phase",
      )
    } else {
      remember { mutableStateOf(32f) }
    }

  val pulse by
    if (shouldAnimate) {
      transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.0f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = (durationMs / 2).coerceIn(750, 3400), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
          ),
        label = "edge_pulse",
      )
    } else {
      remember { mutableStateOf(0.92f) }
    }

  val intColors =
    remember(params.colorArgs) {
      val list = params.colorArgs.map { Color(it).toArgb() }
      when {
        list.size >= 2 && list.first() == list.last() -> list.toIntArray()
        list.size >= 2 -> (list + list.first()).toIntArray()
        else -> intArrayOf(0xFF3B82F6.toInt(), 0xFFA855F7.toInt(), 0xFFEC4899.toInt(), 0xFFF59E0B.toInt(), 0xFF3B82F6.toInt())
      }
    }

  val paint =
    remember {
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        isDither = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
      }
    }
  val shaderMatrix = remember { Matrix() }
  val rectF = remember { RectF() }

  Canvas(modifier = modifier.fillMaxSize().clipToBounds().testTag("edge_light_perimeter_canvas")) {
    val w = size.width
    val h = size.height
    if (w <= 2f || h <= 2f) return@Canvas

    val cx = w / 2f
    val cy = h / 2f
    val baseStrokePx = (params.thicknessDp * density).coerceIn(3f, 28f)
    val cornerPx = (params.cornerRadiusDp * density).coerceIn(8f, minOf(w, h) * 0.25f)
    val effectiveAlpha =
      (params.brightness * pulse * (1f + params.voiceReactiveBoost * 0.22f)).coerceIn(0.12f, 1.0f)

    val shader: Shader =
      if (params.breathingOnly) {
        LinearGradient(0f, 0f, w, h, intColors, null, Shader.TileMode.CLAMP)
      } else {
        SweepGradient(cx, cy, intColors, null).apply {
          shaderMatrix.reset()
          shaderMatrix.setRotate(phase, cx, cy)
          setLocalMatrix(shaderMatrix)
        }
      }

    paint.shader = shader

    drawIntoCanvas { canvas ->
      val nc = canvas.nativeCanvas

      fun drawBlurredRim(
        strokeWidthPx: Float,
        blurRadiusPx: Float,
        alphaScale: Float,
        insetPx: Float,
      ) {
        paint.strokeWidth = strokeWidthPx
        paint.alpha = (effectiveAlpha * alphaScale * 255f).toInt().coerceIn(4, 255)
        paint.maskFilter =
          if (blurRadiusPx > 0.5f) BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
          else null

        rectF.set(insetPx, insetPx, (w - insetPx).coerceAtLeast(insetPx + 2f), (h - insetPx).coerceAtLeast(insetPx + 2f))
        val cr = (cornerPx - insetPx).coerceAtLeast(0f)
        nc.drawRoundRect(rectF, cr, cr, paint)
      }

      // Layer 1: Wide, out-of-focus inward ambient light bleed along all 4 sides & corners
      if (params.glowLayerCount >= 3) {
        drawBlurredRim(
          strokeWidthPx = baseStrokePx * 4.4f,
          blurRadiusPx = baseStrokePx * 3.8f,
          alphaScale = 0.26f,
          insetPx = baseStrokePx * 0.35f,
        )
      }

      // Layer 2: Soft diffused mid-glow wrapping the display corners smoothly
      if (params.glowLayerCount >= 2) {
        drawBlurredRim(
          strokeWidthPx = baseStrokePx * 2.4f,
          blurRadiusPx = baseStrokePx * 1.9f,
          alphaScale = 0.48f,
          insetPx = baseStrokePx * 0.15f,
        )
      }

      // Layer 3: Softly feathered luminous edge core anchored flush at screen perimeter (no harsh sharp wireframe)
      drawBlurredRim(
        strokeWidthPx = baseStrokePx * 1.05f,
        blurRadiusPx = baseStrokePx * 0.72f,
        alphaScale = 0.85f,
        insetPx = 0.5f,
      )
    }
  }
}
