package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalMousomiColors
import com.example.ui.theme.SparkleCyan
import com.example.ui.theme.SparkleGreen
import com.example.ui.theme.SparkleYellow

/**
 * Draws the pure-black upper canvas smoothly blooming into a deep navy and royal-blue glow
 * toward the bottom, matching the reference Gemini mobile screen.
 */
@Composable
fun Modifier.geminiBackgroundGlow(): Modifier {
  val colors = LocalMousomiColors.current
  return this.drawBehind {
    drawRect(color = colors.topBackground)
    // Vertical atmospheric transition from upper negative space to bottom glow
    drawRect(
      brush =
        Brush.verticalGradient(
          0.00f to colors.topBackground,
          0.50f to colors.topBackground,
          0.74f to colors.midGlow.copy(alpha = 0.85f),
          0.90f to colors.bottomGlow.copy(alpha = 0.92f),
          1.00f to colors.midGlow,
        )
    )
    // Radial royal-blue focal bloom centered behind the Chat/Task & input dock
    drawRect(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              colors.bottomGlow.copy(alpha = if (colors.isDark) 0.55f else 0.75f),
              Color.Transparent,
            ),
          center = Offset(size.width * 0.5f, size.height * 0.88f),
          radius = size.width * 0.95f,
        )
    )
  }
}

/**
 * Signature Gemini four-point concave sparkle with smooth rounded tips and multi-color gradient.
 */
@Composable
fun GeminiFourPointSparkle(
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
  animated: Boolean = false,
) {
  val transition = rememberInfiniteTransition(label = "sparkle_pulse")
  val rotation by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = if (animated) 360f else 0f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 8000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "sparkle_rotation",
    )

  Canvas(modifier = modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val cx = w / 2f
    val cy = h / 2f
    val r = minOf(w, h) * 0.46f
    val waist = r * 0.24f

    val starPath =
      Path().apply {
        moveTo(cx, cy - r)
        cubicTo(
          cx + waist * 0.45f,
          cy - waist,
          cx + waist,
          cy - waist * 0.45f,
          cx + r,
          cy,
        )
        cubicTo(
          cx + waist,
          cy + waist * 0.45f,
          cx + waist * 0.45f,
          cy + waist,
          cx,
          cy + r,
        )
        cubicTo(
          cx - waist * 0.45f,
          cy + waist,
          cx - waist,
          cy + waist * 0.45f,
          cx - r,
          cy,
        )
        cubicTo(
          cx - waist,
          cy - waist * 0.45f,
          cx - waist * 0.45f,
          cy - waist,
          cx,
          cy - r,
        )
        close()
      }

    rotate(degrees = if (animated) rotation else 0f, pivot = Offset(cx, cy)) {
      // Primary vivid royal-blue to cyan base
      drawPath(
        path = starPath,
        brush =
          Brush.linearGradient(
            colors = listOf(Color(0xFF2563EB), Color(0xFF3B82F6), SparkleCyan),
            start = Offset(cx, cy - r),
            end = Offset(cx + r, cy + r),
          ),
      )
      // Golden yellow left-lobe blend
      drawPath(
        path = starPath,
        brush =
          Brush.radialGradient(
            colors = listOf(SparkleYellow, SparkleYellow.copy(alpha = 0.0f)),
            center = Offset(cx - r * 0.72f, cy - r * 0.08f),
            radius = r * 0.85f,
          ),
      )
      // Emerald green lower-left transition
      drawPath(
        path = starPath,
        brush =
          Brush.radialGradient(
            colors = listOf(SparkleGreen.copy(alpha = 0.92f), Color.Transparent),
            center = Offset(cx - r * 0.28f, cy + r * 0.62f),
            radius = r * 0.75f,
          ),
      )
    }
  }
}

/**
 * Top-bar diagonal stylus/pencil with sparkle dots matching the Gemini New Chat icon.
 */
@Composable
fun GeminiNewChatPencilIcon(
  tint: Color,
  modifier: Modifier = Modifier,
  size: Dp = 22.dp,
) {
  Canvas(modifier = modifier.size(size)) {
    val s = this.size.minDimension
    val stroke = Stroke(width = s * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Diagonal pencil body
    val pencilPath =
      Path().apply {
        moveTo(s * 0.22f, s * 0.78f)
        lineTo(s * 0.30f, s * 0.54f)
        lineTo(s * 0.72f, s * 0.12f)
        lineTo(s * 0.88f, s * 0.28f)
        lineTo(s * 0.46f, s * 0.70f)
        close()
      }
    drawPath(path = pencilPath, color = tint, style = stroke)

    // Pencil tip line
    drawLine(
      color = tint,
      start = Offset(s * 0.63f, s * 0.21f),
      end = Offset(s * 0.79f, s * 0.37f),
      strokeWidth = s * 0.075f,
      cap = StrokeCap.Round,
    )

    // Subtle sparkle dots around the bottom/top like the screenshot
    val dotOffsets =
      listOf(
        Offset(s * 0.18f, s * 0.38f),
        Offset(s * 0.28f, s * 0.22f),
        Offset(s * 0.64f, s * 0.80f),
        Offset(s * 0.82f, s * 0.66f),
      )
    dotOffsets.forEach { center ->
      drawCircle(color = tint, radius = s * 0.036f, center = center)
    }
  }
}

/**
 * Suggestion Row 2 outline icon (swirling motion lines matching "Create engaging video captions").
 */
@Composable
fun GeminiCaptionsSparkleIcon(
  tint: Color,
  modifier: Modifier = Modifier,
  size: Dp = 20.dp,
) {
  Canvas(modifier = modifier.size(size)) {
    val s = this.size.minDimension
    val stroke = Stroke(width = s * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawCircle(color = tint, radius = s * 0.36f, center = Offset(s * 0.5f, s * 0.5f), style = stroke)
    drawLine(
      color = tint,
      start = Offset(s * 0.22f, s * 0.78f),
      end = Offset(s * 0.78f, s * 0.22f),
      strokeWidth = s * 0.08f,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = tint,
      start = Offset(s * 0.16f, s * 0.54f),
      end = Offset(s * 0.54f, s * 0.16f),
      strokeWidth = s * 0.07f,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = tint,
      start = Offset(s * 0.46f, s * 0.84f),
      end = Offset(s * 0.84f, s * 0.46f),
      strokeWidth = s * 0.07f,
      cap = StrokeCap.Round,
    )
  }
}

/**
 * Suggestion Row 3 outline icon (cluster of 3 four-point outlined sparkles).
 */
@Composable
fun GeminiMultiSparkleIcon(
  tint: Color,
  modifier: Modifier = Modifier,
  size: Dp = 20.dp,
) {
  Canvas(modifier = modifier.size(size)) {
    val s = this.size.minDimension
    val stroke = Stroke(width = s * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun drawMiniStar(cx: Float, cy: Float, r: Float) {
      val path =
        Path().apply {
          moveTo(cx, cy - r)
          quadraticTo(cx, cy, cx + r, cy)
          quadraticTo(cx, cy, cx, cy + r)
          quadraticTo(cx, cy, cx - r, cy)
          quadraticTo(cx, cy, cx, cy - r)
          close()
        }
      drawPath(path = path, color = tint, style = stroke)
    }

    drawMiniStar(s * 0.38f, s * 0.56f, s * 0.26f)
    drawMiniStar(s * 0.72f, s * 0.28f, s * 0.16f)
    drawMiniStar(s * 0.74f, s * 0.76f, s * 0.13f)
  }
}

/**
 * Three-bar vertical audio-wave icon inside the blue Gemini Live circle button.
 */
@Composable
fun GeminiLiveWaveformIcon(
  tint: Color,
  modifier: Modifier = Modifier,
  size: Dp = 22.dp,
) {
  Canvas(modifier = modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val barWidth = w * 0.13f
    val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

    // Left short bar
    val sideHeight = h * 0.42f
    val sideTop = (h - sideHeight) / 2f
    drawRoundRect(
      color = tint,
      topLeft = Offset(w * 0.18f, sideTop),
      size = Size(barWidth, sideHeight),
      cornerRadius = corner,
    )

    // Center tall bar
    val centerHeight = h * 0.74f
    val centerTop = (h - centerHeight) / 2f
    drawRoundRect(
      color = tint,
      topLeft = Offset((w - barWidth) / 2f, centerTop),
      size = Size(barWidth, centerHeight),
      cornerRadius = corner,
    )

    // Right short bar
    drawRoundRect(
      color = tint,
      topLeft = Offset(w * 0.82f - barWidth, sideTop),
      size = Size(barWidth, sideHeight),
      cornerRadius = corner,
    )
  }
}
