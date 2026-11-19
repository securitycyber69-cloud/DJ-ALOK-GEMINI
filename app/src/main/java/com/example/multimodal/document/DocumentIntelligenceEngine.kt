package com.example.multimodal.document

import com.example.domain.model.DocumentExtractionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentIntelligenceEngine @Inject constructor(
  private val documentProcessor: DocumentProcessor,
) {
  suspend fun extractDocuments(uriStrings: List<String>): Result<List<DocumentExtractionResult>> {
    if (uriStrings.isEmpty()) {
      return Result.failure(IllegalArgumentException("No document URI provided."))
    }
    val extracted = mutableListOf<DocumentExtractionResult>()
    for (uri in uriStrings.take(4)) {
      val res = documentProcessor.processDocumentUri(uri)
      if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
      extracted += res.getOrThrow()
    }
    return Result.success(extracted)
  }

  fun searchInDocuments(
    documents: List<DocumentExtractionResult>,
    queryTerms: List<String>,
  ): List<String> {
    if (queryTerms.isEmpty()) return emptyList()
    val cleanTerms = queryTerms.map { it.trim().lowercase() }.filter { it.length >= 3 }
    if (cleanTerms.isEmpty()) return emptyList()

    val matches = mutableListOf<String>()
    documents.forEach { doc ->
      doc.extractedText.lines().forEachIndexed { idx, line ->
        val lower = line.lowercase()
        if (cleanTerms.any { it in lower }) {
          matches += "[${doc.displayName}, line ${idx + 1}] ${line.trim().take(200)}"
        }
      }
    }
    return matches.distinct().take(12)
  }

  fun buildDocumentPromptContext(
    documents: List<DocumentExtractionResult>,
    userPrompt: String,
  ): String {
    val terms =
      userPrompt
        .split(Regex("\\s+"))
        .filter { it.length >= 4 }
        .take(6)
    val docMatches = searchInDocuments(documents, terms)

    return buildString {
      appendLine("DOCUMENT INTELLIGENCE CONTEXT (Strictly separate document-derived facts from external knowledge):")
      if (documents.size > 1) {
        appendLine("Multi-document comparison requested across ${documents.size} documents.")
      }
      documents.forEachIndexed { idx, doc ->
        appendLine("=== DOCUMENT ${idx + 1}: ${doc.displayName} (${doc.formatLabel}, ${doc.pageCount} page/sheet(s)) ===")
        if (doc.warnings.isNotEmpty()) {
          appendLine("Extraction notes: ${doc.warnings.joinToString("; ")}")
        }
        if (doc.tables.isNotEmpty()) {
          appendLine("Extracted tables (${doc.tables.size}):")
          doc.tables.take(3).forEach { tbl ->
            appendLine("Table '${tbl.name}' (${tbl.headers.joinToString(" | ")}), rows=${tbl.rows.size}")
            tbl.rows.take(15).forEach { r -> appendLine("  - ${r.joinToString(" | ")}") }
          }
        }
        if (doc.extractedText.isNotBlank()) {
          appendLine("Document text excerpt (with page/sheet references where present):")
          appendLine(doc.extractedText.take(10_000))
        }
      }
      if (docMatches.isNotEmpty()) {
        appendLine("Matched passages:")
        docMatches.forEach { appendLine("- $it") }
      }
      appendLine("Label every claim derived from the uploaded documents clearly (e.g. 'According to [Document Name, Page/Sheet]').")
    }
  }
}
