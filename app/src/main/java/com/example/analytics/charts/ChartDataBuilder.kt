package com.example.analytics.charts

import com.example.analytics.cleaner.DataCleaner
import com.example.domain.model.ChartDataPoint
import com.example.domain.model.ChartSeries
import com.example.domain.model.ChartSpec
import com.example.domain.model.ChartType
import com.example.domain.model.ParsedTable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChartDataBuilder @Inject constructor(
  private val dataCleaner: DataCleaner,
) {

  fun detectRequestedChartType(prompt: String, table: ParsedTable): ChartType {
    val q = prompt.lowercase()
    return when {
      "pie" in q || "share" in q || "proportion" in q -> ChartType.PIE
      "scatter" in q || "correlation" in q -> ChartType.SCATTER
      "area" in q -> ChartType.AREA
      "time series" in q || "over time" in q || "timeline" in q -> ChartType.TIME_SERIES
      "compare" in q || "comparison" in q || "vs" in q -> ChartType.COMPARISON
      "line" in q || "trend" in q -> ChartType.LINE
      "bar" in q || "histogram" in q -> ChartType.BAR
      else -> inferBestChartTypeFromTable(table)
    }
  }

  fun buildChartFromVerifiedTable(
    table: ParsedTable,
    prompt: String,
    sourceAttribution: String,
  ): Result<ChartSpec> {
    if (table.headers.isEmpty() || table.rows.isEmpty()) {
      return Result.failure(
        IllegalArgumentException("Cannot build chart: no verified tabular rows available.")
      )
    }

    val chartType = detectRequestedChartType(prompt, table)
    val numericColIndices =
      table.headers.indices.filter { colIdx ->
        val nonEmpty =
          table.rows.count { row -> dataCleaner.parseNumberOrNull(row.getOrNull(colIdx).orEmpty()) != null }
        nonEmpty >= (table.rows.size * 0.5).coerceAtLeast(1.0)
      }

    if (numericColIndices.isEmpty()) {
      return Result.failure(
        IllegalArgumentException("Cannot build chart: no numeric columns found in '${table.name}'.")
      )
    }

    val labelColIdx =
      table.headers.indices.firstOrNull { it !in numericColIndices } ?: 0
    val targetValueCols =
      if (chartType == ChartType.COMPARISON || chartType == ChartType.LINE || chartType == ChartType.AREA) {
        numericColIndices.filter { it != labelColIdx || numericColIndices.size == 1 }.take(3)
      } else if (chartType == ChartType.SCATTER && numericColIndices.size >= 2) {
        listOf(numericColIndices[0], numericColIndices[1])
      } else {
        listOf(numericColIndices.first { it != labelColIdx || numericColIndices.size == 1 })
      }

    if (chartType == ChartType.SCATTER && numericColIndices.size >= 2) {
      val xCol = numericColIndices[0]
      val yCol = numericColIndices[1]
      val pts =
        table.rows.mapIndexedNotNull { rIdx, row ->
          val x = dataCleaner.parseNumberOrNull(row.getOrNull(xCol).orEmpty()) ?: return@mapIndexedNotNull null
          val y = dataCleaner.parseNumberOrNull(row.getOrNull(yCol).orEmpty()) ?: return@mapIndexedNotNull null
          ChartDataPoint(
            label = row.getOrNull(labelColIdx)?.ifBlank { "Row ${rIdx + 1}" } ?: "Row ${rIdx + 1}",
            xValue = x,
            yValue = y,
          )
        }
      if (pts.isEmpty()) {
        return Result.failure(IllegalArgumentException("No valid paired numeric coordinates for scatter plot."))
      }
      return Result.success(
        ChartSpec(
          title = "${table.headers[xCol]} vs ${table.headers[yCol]}",
          subtitle = "Scatter Plot from ${table.name} (${pts.size} points)",
          chartType = ChartType.SCATTER,
          xAxisLabel = table.headers[xCol],
          yAxisLabel = table.headers[yCol],
          unit = "",
          series = listOf(ChartSeries(name = table.headers[yCol], points = pts)),
          sourceAttribution = sourceAttribution,
        )
      )
    }

    val seriesList =
      targetValueCols.map { valCol ->
        val points =
          table.rows.take(24).mapIndexedNotNull { idx, row ->
            val y = dataCleaner.parseNumberOrNull(row.getOrNull(valCol).orEmpty()) ?: return@mapIndexedNotNull null
            val rawLabel = row.getOrNull(labelColIdx)?.trim().orEmpty().ifBlank { "#${idx + 1}" }
            ChartDataPoint(
              label = rawLabel.take(18),
              xValue = idx.toDouble(),
              yValue = y,
            )
          }
        ChartSeries(name = table.headers[valCol], points = points)
      }.filter { it.points.isNotEmpty() }

    if (seriesList.isEmpty()) {
      return Result.failure(IllegalArgumentException("No numeric points available to plot."))
    }

    val firstSeriesName = seriesList.first().name
    val inferredUnit =
      when {
        table.rows.any { row -> row.getOrNull(targetValueCols.first()).orEmpty().contains("%") } -> "%"
        table.rows.any { row -> row.getOrNull(targetValueCols.first()).orEmpty().contains("$") } -> "$"
        else -> ""
      }

    return Result.success(
      ChartSpec(
        title = "${table.name} — $firstSeriesName",
        subtitle = "${chartType.label} • ${seriesList.first().points.size} verified rows",
        chartType = chartType,
        xAxisLabel = table.headers[labelColIdx],
        yAxisLabel = seriesList.joinToString(" / ") { it.name },
        unit = inferredUnit,
        series = seriesList,
        sourceAttribution = sourceAttribution,
      )
    )
  }

  private fun inferBestChartTypeFromTable(table: ParsedTable): ChartType {
    val firstHeader = table.headers.firstOrNull()?.lowercase().orEmpty()
    val looksLikeTime =
      listOf("date", "time", "month", "year", "day", "quarter", "week").any { it in firstHeader }
    return when {
      looksLikeTime -> ChartType.TIME_SERIES
      table.rows.size <= 6 -> ChartType.BAR
      else -> ChartType.LINE
    }
  }
}
