package com.example.analytics.parser

import com.example.domain.model.DocumentExtractionResult
import com.example.domain.model.ParsedTable
import com.example.multimodal.document.DocumentProcessor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Singleton
class TabularDataParser @Inject constructor(
  private val json: Json,
) {
  fun extractTablesFromDocuments(documents: List<DocumentExtractionResult>): List<ParsedTable> {
    val all = mutableListOf<ParsedTable>()
    documents.forEach { doc ->
      all += doc.tables
      if (doc.tables.isEmpty() && doc.formatLabel == "JSON") {
        parseJsonArrayToTable(doc.displayName, doc.extractedText)?.let { all += it }
      }
      if (doc.tables.isEmpty() && doc.formatLabel == "TXT") {
        parseInlineTabularText(doc.displayName, doc.extractedText)?.let { all += it }
      }
    }
    return all
  }

  fun parseInlineTabularText(sourceName: String, text: String): ParsedTable? {
    val trimmed = text.trim()
    // Try JSON array of objects
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      parseJsonArrayToTable(sourceName, trimmed)?.let { return it }
    }

    val candidateLines =
      trimmed.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("```") }

    // Try Markdown pipe table
    val pipeLines = candidateLines.filter { it.contains("|") && it.count { c -> c == '|' } >= 2 }
    if (pipeLines.size >= 2) {
      val cleanedPipeLines =
        pipeLines.filterNot { line ->
          line.replace("|", "").replace("-", "").replace(":", "").isBlank()
        }
      if (cleanedPipeLines.size >= 2) {
        val parsed =
          cleanedPipeLines.map { line ->
            line.trim('|').split('|').map { it.trim() }
          }
        val headers = parsed.first()
        val rows = parsed.drop(1).map { r -> normalizeWidth(r, headers.size) }
        if (headers.isNotEmpty() && rows.isNotEmpty()) {
          return ParsedTable(
            name = sourceName,
            headers = headers,
            rows = rows,
            pageOrSheetRef = "Markdown Table",
          )
        }
      }
    }

    // Try inline CSV lines (at least 2 lines with consistent commas)
    val csvLines = candidateLines.filter { it.contains(",") }
    if (csvLines.size >= 2) {
      val parsed = DocumentProcessor.parseCsvTextToTable(sourceName, csvLines.joinToString("\n"))
      if (parsed.headers.size >= 2 && parsed.rows.isNotEmpty()) {
        return parsed.copy(pageOrSheetRef = "Inline CSV")
      }
    }
    return null
  }

  private fun parseJsonArrayToTable(name: String, rawJson: String): ParsedTable? {
    val start = rawJson.indexOf('[')
    val end = rawJson.lastIndexOf(']')
    if (start < 0 || end <= start) return null
    val slice = rawJson.substring(start, end + 1)

    return runCatching {
      val array = json.parseToJsonElement(slice) as? JsonArray ?: return null
      val objects = array.mapNotNull { it as? JsonObject }
      if (objects.isEmpty()) return null
      val headers = objects.flatMap { it.keys }.distinct()
      val rows =
        objects.map { obj ->
          headers.map { key ->
            when (val el = obj[key]) {
              is JsonPrimitive -> el.contentOrNull.orEmpty()
              null -> ""
              else -> el.toString()
            }
          }
        }
      ParsedTable(name = name, headers = headers, rows = rows, pageOrSheetRef = "JSON Dataset")
    }.getOrNull()
  }

  private fun normalizeWidth(row: List<String>, targetSize: Int): List<String> {
    return if (row.size >= targetSize) row.take(targetSize)
    else row + List(targetSize - row.size) { "" }
  }
}
