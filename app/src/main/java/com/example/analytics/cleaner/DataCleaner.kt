package com.example.analytics.cleaner

import com.example.domain.model.DataCleaningSummary
import com.example.domain.model.ParsedTable
import javax.inject.Inject
import javax.inject.Singleton

data class CleanedTableResult(
  val cleanedTable: ParsedTable,
  val summary: DataCleaningSummary,
)

@Singleton
class DataCleaner @Inject constructor() {

  fun clean(table: ParsedTable): CleanedTableResult {
    val cleanHeaders =
      table.headers.mapIndexed { idx, h -> h.trim().ifBlank { "Column_${idx + 1}" } }
    val missingCounts = LinkedHashMap<String, Int>()
    cleanHeaders.forEach { missingCounts[it] = 0 }

    val seenRowKeys = HashSet<String>()
    val keptRows = mutableListOf<List<String>>()
    var duplicatesRemoved = 0
    val normalizedColumns = LinkedHashSet<String>()

    for (rawRow in table.rows) {
      val normalizedRow =
        cleanHeaders.indices.map { colIdx ->
          val rawVal = rawRow.getOrNull(colIdx)?.trim().orEmpty()
          if (rawVal.isEmpty() || rawVal.equals("null", ignoreCase = true) || rawVal.equals("n/a", ignoreCase = true) || rawVal == "-") {
            val h = cleanHeaders[colIdx]
            missingCounts[h] = (missingCounts[h] ?: 0) + 1
            ""
          } else {
            val cleanedNum = stripNumericFormattingIfNumeric(rawVal)
            if (cleanedNum != rawVal) {
              normalizedColumns.add(cleanHeaders[colIdx])
            }
            cleanedNum
          }
        }

      if (normalizedRow.all { it.isEmpty() }) continue

      val rowKey = normalizedRow.joinToString("\u0001") { it.lowercase() }
      if (!seenRowKeys.add(rowKey)) {
        duplicatesRemoved++
      } else {
        keptRows += normalizedRow
      }
    }

    val cleanedTable =
      table.copy(
        headers = cleanHeaders,
        rows = keptRows,
      )

    return CleanedTableResult(
      cleanedTable = cleanedTable,
      summary =
        DataCleaningSummary(
          originalRowCount = table.rows.size,
          cleanedRowCount = keptRows.size,
          duplicateRowsRemoved = duplicatesRemoved,
          missingValuesByColumn = missingCounts.filterValues { it > 0 },
          normalizedColumns = normalizedColumns.toList(),
        ),
    )
  }

  fun parseNumberOrNull(cell: String): Double? {
    val cleaned =
      cell.trim()
        .removePrefix("$")
        .removePrefix("€")
        .removePrefix("£")
        .removePrefix("৳")
        .removePrefix("₹")
        .removeSuffix("%")
        .replace(",", "")
        .trim()
    return cleaned.toDoubleOrNull()
  }

  private fun stripNumericFormattingIfNumeric(raw: String): String {
    val candidate = raw.trim()
    val parsed = parseNumberOrNull(candidate) ?: return candidate
    return if (candidate.endsWith("%")) "$parsed%" else candidate
  }
}
