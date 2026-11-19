package com.example.analytics.statistics

import com.example.ai.provider.api.ProxyApiService
import com.example.ai.provider.dto.ProxyAnalyticsRequest
import com.example.analytics.cleaner.DataCleaner
import com.example.core.network.RetryPolicy
import com.example.core.security.SecurityValidator
import com.example.domain.model.AppSettings
import com.example.domain.model.ColumnStatistics
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DataAnalyticsResult
import com.example.domain.model.ParsedTable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

@Singleton
class BackendAnalyticsClient @Inject constructor(
  private val proxyApiService: ProxyApiService,
  private val securityValidator: SecurityValidator,
) {
  suspend fun executeSandboxed(
    table: ParsedTable,
    operations: List<String>,
    settings: AppSettings,
  ): Result<DataAnalyticsResult> = runCatching {
    require(settings.connectionMode == ConnectionMode.BACKEND_PROXY && settings.analyticsSandboxEnabled) {
      "Backend analytics sandbox is not enabled."
    }
    require(table.rows.size <= 10_000) {
      "Dataset exceeds 10,000 row sandbox limit."
    }
    val baseUrl = securityValidator.validateHttpsUrl(settings.proxyBaseUrl).getOrThrow()
    val resp =
      RetryPolicy.withExponentialBackoff {
        proxyApiService.executeAnalyticsViaProxy(
          fullHttpsUrl = "${baseUrl}v1/analytics/execute",
          request =
            ProxyAnalyticsRequest(
              datasetName = table.name,
              table = table,
              operations = operations,
              timeoutSeconds = 15,
              maxMemoryMb = 256,
            ),
        )
      }
    if (!resp.isSuccessful) {
      error("Backend sandbox analytics failed (HTTP ${resp.code()}).")
    }
    val body = resp.body() ?: error("Empty analytics sandbox response.")
    if (!body.error.isNullOrBlank()) error(body.error)
    body.result ?: error("Backend analytics returned null result.")
  }
}

@Singleton
class StatisticalAnalyzer @Inject constructor(
  private val dataCleaner: DataCleaner,
  private val backendAnalyticsClient: BackendAnalyticsClient,
) {

  suspend fun analyze(
    rawTable: ParsedTable,
    settings: AppSettings,
  ): DataAnalyticsResult {
    val cleanedResult = dataCleaner.clean(rawTable)
    val table = cleanedResult.cleanedTable

    // If backend Python sandbox analytics is explicitly configured and enabled, prefer isolated server execution
    if (settings.connectionMode == ConnectionMode.BACKEND_PROXY && settings.analyticsSandboxEnabled) {
      val remote =
        backendAnalyticsClient.executeSandboxed(
          table = table,
          operations = listOf("clean", "summary_stats", "correlation", "trend", "groupby"),
          settings = settings,
        )
      if (remote.isSuccess) {
        return remote.getOrThrow().copy(sandboxExecutedOnBackend = true)
      }
    }

    val numericColumns = LinkedHashMap<String, List<Double>>()
    val rowValuesByCol = LinkedHashMap<String, List<Double?>>()

    table.headers.forEachIndexed { colIdx, header ->
      val parsedList = table.rows.map { row -> dataCleaner.parseNumberOrNull(row.getOrNull(colIdx).orEmpty()) }
      rowValuesByCol[header] = parsedList
      val nonNull = parsedList.filterNotNull()
      if (nonNull.size >= (table.rows.size * 0.6).coerceAtLeast(1.0) && nonNull.isNotEmpty()) {
        numericColumns[header] = nonNull
      }
    }

    val statsList =
      numericColumns.map { (col, values) -> computeColumnStats(col, values) }

    val trends =
      numericColumns.mapNotNull { (col, values) ->
        if (values.size < 3) return@mapNotNull null
        val slope = linearSlope(values)
        val pctChange =
          if (abs(values.first()) > 1e-9) ((values.last() - values.first()) / abs(values.first())) * 100.0
          else 0.0
        val dir =
          when {
            slope > 1e-6 -> "Upward trend"
            slope < -1e-6 -> "Downward trend"
            else -> "Flat / stable"
          }
        "$col: $dir (start=${fmt(values.first())} -> end=${fmt(values.last())}, delta=${"%.1f".format(pctChange)}%)"
      }

    val correlations = computePairwiseCorrelations(table, rowValuesByCol, numericColumns.keys.toList())
    val groupHighlights = computeGroupHighlights(table, numericColumns.keys.firstOrNull())

    val previewTable =
      table.copy(
        rows = table.rows.take(12),
      )

    return DataAnalyticsResult(
      datasetName = table.name,
      cleaningSummary = cleanedResult.summary,
      numericStatistics = statsList,
      detectedTrends = trends,
      correlationNotes = correlations,
      groupedHighlights = groupHighlights,
      sandboxExecutedOnBackend = false,
      previewTable = previewTable,
    )
  }

  fun computeColumnStats(columnName: String, values: List<Double>): ColumnStatistics {
    val sorted = values.sorted()
    val count = values.size
    val sum = values.sum()
    val mean = sum / count
    val median =
      if (count % 2 == 1) sorted[count / 2]
      else (sorted[count / 2 - 1] + sorted[count / 2]) / 2.0
    val variance = values.sumOf { v -> (v - mean) * (v - mean) } / count
    val stdDev = sqrt(variance)
    return ColumnStatistics(
      columnName = columnName,
      count = count,
      sum = sum,
      mean = mean,
      median = median,
      min = sorted.first(),
      max = sorted.last(),
      stdDev = stdDev,
    )
  }

  fun pearsonCorrelation(xs: List<Double>, ys: List<Double>): Double? {
    if (xs.size != ys.size || xs.size < 3) return null
    val n = xs.size
    val meanX = xs.sum() / n
    val meanY = ys.sum() / n
    var num = 0.0
    var denX = 0.0
    var denY = 0.0
    for (i in 0 until n) {
      val dx = xs[i] - meanX
      val dy = ys[i] - meanY
      num += dx * dy
      denX += dx * dx
      denY += dy * dy
    }
    val denom = sqrt(denX * denY)
    return if (denom > 1e-12) (num / denom).coerceIn(-1.0, 1.0) else null
  }

  private fun linearSlope(values: List<Double>): Double {
    val n = values.size
    val meanX = (n - 1) / 2.0
    val meanY = values.sum() / n
    var num = 0.0
    var den = 0.0
    for (i in values.indices) {
      val dx = i - meanX
      num += dx * (values[i] - meanY)
      den += dx * dx
    }
    return if (den > 1e-12) num / den else 0.0
  }

  private fun computePairwiseCorrelations(
    table: ParsedTable,
    rowValuesByCol: Map<String, List<Double?>>,
    numericColNames: List<String>,
  ): List<String> {
    if (numericColNames.size < 2) return emptyList()
    val notes = mutableListOf<String>()
    for (i in 0 until minOf(numericColNames.size, 4)) {
      for (j in i + 1 until minOf(numericColNames.size, 4)) {
        val c1 = numericColNames[i]
        val c2 = numericColNames[j]
        val colA = rowValuesByCol[c1] ?: continue
        val colB = rowValuesByCol[c2] ?: continue
        val pairedX = mutableListOf<Double>()
        val pairedY = mutableListOf<Double>()
        for (r in table.rows.indices) {
          val a = colA.getOrNull(r)
          val b = colB.getOrNull(r)
          if (a != null && b != null) {
            pairedX += a
            pairedY += b
          }
        }
        val rVal = pearsonCorrelation(pairedX, pairedY)
        if (rVal != null) {
          val strength =
            when {
              abs(rVal) >= 0.75 -> "Strong"
              abs(rVal) >= 0.4 -> "Moderate"
              else -> "Weak"
            }
          notes += "$c1 vs $c2: Pearson r = ${"%.3f".format(rVal)} ($strength)"
        }
      }
    }
    return notes
  }

  private fun computeGroupHighlights(table: ParsedTable, primaryNumericCol: String?): List<String> {
    if (primaryNumericCol == null || table.headers.size < 2) return emptyList()
    val numIdx = table.headers.indexOf(primaryNumericCol)
    val catIdx = table.headers.indices.firstOrNull { it != numIdx } ?: return emptyList()
    val catHeader = table.headers[catIdx]

    val grouped = LinkedHashMap<String, MutableList<Double>>()
    for (row in table.rows) {
      val cat = row.getOrNull(catIdx)?.trim().orEmpty()
      val valNum = dataCleaner.parseNumberOrNull(row.getOrNull(numIdx).orEmpty())
      if (cat.isNotEmpty() && valNum != null) {
        grouped.getOrPut(cat) { mutableListOf() }.add(valNum)
      }
    }
    if (grouped.size !in 2..15) return emptyList()

    return grouped.entries
      .map { (k, vals) -> Triple(k, vals.sum(), vals.average()) }
      .sortedByDescending { it.second }
      .take(5)
      .map { (k, sum, avg) -> "$catHeader='$k': total=${fmt(sum)}, avg=${fmt(avg)}" }
  }

  private fun fmt(v: Double): String =
    if (abs(v - v.toLong()) < 1e-6) v.toLong().toString() else "%.2f".format(v)
}
