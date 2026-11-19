package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ChartSpec
import com.example.domain.model.ChartType
import com.example.ui.theme.LocalMousomiColors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveChartCard(
  chart: ChartSpec,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val onBg = MaterialTheme.colorScheme.onBackground
  var zoomed by remember { mutableStateOf(false) }
  var selectedPointInfo by remember { mutableStateOf<String?>(null) }

  val palette =
    if (colors.isDark) {
      listOf(
        Color(0xFF60A5FA),
        Color(0xFFA855F7),
        Color(0xFF34D399),
        Color(0xFFF472B6),
        Color(0xFFFBBF24),
      )
    } else {
      listOf(
        Color(0xFF2563EB),
        Color(0xFF7C3AED),
        Color(0xFF059669),
        Color(0xFFDB2777),
        Color(0xFFD97706),
      )
    }

  val allPoints = chart.series.flatMap { it.points }
  val minY = (allPoints.minOfOrNull { it.yValue } ?: 0.0).let { minOf(0.0, it) }
  val maxY = (allPoints.maxOfOrNull { it.yValue } ?: 1.0).let { if (it <= minY) minY + 1.0 else it }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("interactive_chart_card"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = chart.title,
            color = onBg,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = chart.subtitle,
            color = colors.mutedText,
            style = MaterialTheme.typography.labelSmall,
          )
        }
        FilterChip(
          selected = zoomed,
          onClick = { zoomed = !zoomed },
          label = { Text(if (zoomed) "1.6x" else "1x", fontSize = 11.sp) },
        )
      }

      selectedPointInfo?.let { tooltip ->
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = colors.activeChipContainer,
        ) {
          Text(
            text = tooltip,
            color = onBg,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }

      val canvasWidthModifier =
        if (zoomed) Modifier.width(560.dp) else Modifier.fillMaxWidth()

      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .height(210.dp),
      ) {
        val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        val axisColor = colors.mutedText.copy(alpha = 0.7f)

        Canvas(
          modifier =
            canvasWidthModifier
              .height(200.dp)
              .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
              .pointerInput(chart, zoomed) {
                detectTapGestures { tapOffset ->
                  val pts = chart.series.firstOrNull()?.points.orEmpty()
                  if (pts.isEmpty()) return@detectTapGestures
                  val stepX = size.width / (pts.size.coerceAtLeast(1)).toFloat()
                  val idx = ((tapOffset.x / stepX).toInt()).coerceIn(0, pts.lastIndex)
                  val pt = pts[idx]
                  val formattedY =
                    if (abs(pt.yValue - pt.yValue.toLong()) < 1e-6) pt.yValue.toLong().toString()
                    else "%.2f".format(pt.yValue)
                  selectedPointInfo =
                    "${chart.xAxisLabel}: ${pt.label} • ${chart.yAxisLabel}: $formattedY${chart.unit}"
                }
              },
        ) {
          val w = size.width
          val h = size.height
          val ySpan = (maxY - minY).coerceAtLeast(1e-6)

          // Horizontal reference grid lines
          for (g in 0..4) {
            val gy = h - (g / 4f) * h
            drawLine(
              color = gridLineColor,
              start = Offset(0f, gy),
              end = Offset(w, gy),
              strokeWidth = 1f,
            )
          }
          drawLine(color = axisColor, start = Offset(0f, h), end = Offset(w, h), strokeWidth = 2f)

          when (chart.chartType) {
            ChartType.PIE -> {
              val pts = chart.series.firstOrNull()?.points.orEmpty()
              val total = pts.sumOf { abs(it.yValue) }.coerceAtLeast(1e-6)
              val diameter = min(w, h) * 0.85f
              val topLeft = Offset((w - diameter) / 2f, (h - diameter) / 2f)
              var startAngle = -90f
              pts.forEachIndexed { idx, pt ->
                val sweep = ((abs(pt.yValue) / total) * 360.0).toFloat()
                drawArc(
                  color = palette[idx % palette.size],
                  startAngle = startAngle,
                  sweepAngle = sweep,
                  useCenter = true,
                  topLeft = topLeft,
                  size = Size(diameter, diameter),
                )
                startAngle += sweep
              }
            }

            ChartType.BAR, ChartType.COMPARISON -> {
              val seriesCount = chart.series.size.coerceAtLeast(1)
              val pointCount = chart.series.maxOfOrNull { it.points.size } ?: 1
              val groupWidth = w / pointCount.toFloat()
              val barWidth = (groupWidth * 0.72f / seriesCount).coerceAtLeast(6f)

              chart.series.forEachIndexed { sIdx, s ->
                val sColor = palette[sIdx % palette.size]
                s.points.forEachIndexed { pIdx, pt ->
                  val normH = (((pt.yValue - minY) / ySpan).toFloat() * h).coerceIn(2f, h)
                  val left = pIdx * groupWidth + (groupWidth * 0.14f) + (sIdx * barWidth)
                  drawRect(
                    color = sColor,
                    topLeft = Offset(left, h - normH),
                    size = Size(barWidth * 0.9f, normH),
                  )
                }
              }
            }

            ChartType.SCATTER -> {
              val pts = chart.series.firstOrNull()?.points.orEmpty()
              val minX = pts.minOfOrNull { it.xValue } ?: 0.0
              val maxX = (pts.maxOfOrNull { it.xValue } ?: 1.0).let { if (it <= minX) minX + 1.0 else it }
              val xSpan = (maxX - minX).coerceAtLeast(1e-6)
              val c = palette.first()
              pts.forEach { pt ->
                val px = (((pt.xValue - minX) / xSpan).toFloat() * (w - 16f)) + 8f
                val py = h - (((pt.yValue - minY) / ySpan).toFloat() * (h - 16f)) - 8f
                drawCircle(color = c, radius = 7f, center = Offset(px, py))
              }
            }

            ChartType.LINE, ChartType.AREA, ChartType.TIME_SERIES -> {
              chart.series.forEachIndexed { sIdx, s ->
                val pts = s.points
                if (pts.isEmpty()) return@forEachIndexed
                val sColor = palette[sIdx % palette.size]
                val linePath = Path()
                val fillPath = Path()
                val step = if (pts.size <= 1) w else w / (pts.size - 1).toFloat()

                val coords =
                  pts.mapIndexed { idx, pt ->
                    val px = if (pts.size == 1) w / 2f else idx * step
                    val py = h - (((pt.yValue - minY) / ySpan).toFloat() * (h - 12f)) - 6f
                    Offset(px, py)
                  }

                coords.forEachIndexed { idx, offset ->
                  if (idx == 0) {
                    linePath.moveTo(offset.x, offset.y)
                    fillPath.moveTo(offset.x, h)
                    fillPath.lineTo(offset.x, offset.y)
                  } else {
                    linePath.lineTo(offset.x, offset.y)
                    fillPath.lineTo(offset.x, offset.y)
                  }
                }
                if (chart.chartType == ChartType.AREA || sIdx == 0) {
                  fillPath.lineTo(coords.last().x, h)
                  fillPath.close()
                  drawPath(path = fillPath, color = sColor.copy(alpha = 0.16f))
                }
                drawPath(
                  path = linePath,
                  color = sColor,
                  style = Stroke(width = 4f, cap = StrokeCap.Round),
                )
                coords.forEach { pt ->
                  drawCircle(color = sColor, radius = 5.5f, center = pt)
                }
              }
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "X: ${chart.xAxisLabel} • Y: ${chart.yAxisLabel}${if (chart.unit.isNotBlank()) " (${chart.unit})" else ""}",
          color = colors.mutedText,
          style = MaterialTheme.typography.labelSmall,
        )
        Text(
          text = "Min ${"%.1f".format(minY)} / Max ${"%.1f".format(maxY)}",
          color = colors.mutedText,
          style = MaterialTheme.typography.labelSmall,
        )
      }

      // Legend
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (chart.chartType == ChartType.PIE) {
          chart.series.firstOrNull()?.points.orEmpty().take(6).forEachIndexed { idx, pt ->
            LegendBadge(color = palette[idx % palette.size], label = "${pt.label} (${"%.1f".format(pt.yValue)})")
          }
        } else {
          chart.series.forEachIndexed { idx, s ->
            LegendBadge(color = palette[idx % palette.size], label = s.name)
          }
        }
      }

      Text(
        text = "Verified data: ${chart.sourceAttribution} • Tap chart to inspect values",
        color = colors.mutedText,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.5.sp,
      )
    }
  }
}

@Composable
private fun LegendBadge(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
    Text(text = label, style = MaterialTheme.typography.labelSmall)
  }
}
