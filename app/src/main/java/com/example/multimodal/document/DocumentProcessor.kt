package com.example.multimodal.document

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import com.example.core.image.ImageProcessor
import com.example.core.security.SecurityValidator
import com.example.core.storage.TempMediaCacheManager
import com.example.domain.model.DocumentExtractionResult
import com.example.domain.model.ParsedTable
import com.example.domain.model.ProcessedImagePayload
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser

@Singleton
class DocumentProcessor @Inject constructor(
  private val tempMediaCacheManager: TempMediaCacheManager,
  private val imageProcessor: ImageProcessor,
  private val securityValidator: SecurityValidator,
  private val json: Json,
) {
  companion object {
    val ALLOWED_EXTENSIONS = setOf("pdf", "docx", "xlsx", "csv", "txt", "json")
    const val MAX_DOC_BYTES = 30L * 1024L * 1024L

    fun parseCsvTextToTable(tableName: String, rawCsv: String): ParsedTable {
      val lines = parseRfc4180Rows(rawCsv)
      if (lines.isEmpty()) {
        return ParsedTable(name = tableName, headers = emptyList(), rows = emptyList(), pageOrSheetRef = "Sheet 1")
      }
      val headers = lines.first().mapIndexed { i, h -> h.trim().ifBlank { "Column_${i + 1}" } }
      val rows =
        lines.drop(1).map { row ->
          if (row.size >= headers.size) row.take(headers.size)
          else row + List(headers.size - row.size) { "" }
        }
      return ParsedTable(
        name = tableName,
        headers = headers,
        rows = rows,
        pageOrSheetRef = "CSV Table",
      )
    }

    fun parseRfc4180Rows(text: String): List<List<String>> {
      val rows = mutableListOf<List<String>>()
      var currentRow = mutableListOf<String>()
      val cell = StringBuilder()
      var inQuotes = false
      var i = 0
      while (i < text.length) {
        val ch = text[i]
        when {
          ch == '"' -> {
            if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
              cell.append('"')
              i++
            } else {
              inQuotes = !inQuotes
            }
          }
          ch == ',' && !inQuotes -> {
            currentRow.add(cell.toString().trim())
            cell.clear()
          }
          (ch == '\n' || ch == '\r') && !inQuotes -> {
            if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
            currentRow.add(cell.toString().trim())
            cell.clear()
            if (currentRow.any { it.isNotEmpty() }) {
              rows.add(currentRow)
            }
            currentRow = mutableListOf()
          }
          else -> cell.append(ch)
        }
        i++
      }
      if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
        currentRow.add(cell.toString().trim())
        if (currentRow.any { it.isNotEmpty() }) {
          rows.add(currentRow)
        }
      }
      return rows
    }

    fun extractTextFromPdfBytes(pdfBytes: ByteArray): String {
      val latin = String(pdfBytes, Charsets.ISO_8859_1)
      val pageTextBlocks = mutableListOf<String>()
      val streamRegex = Regex("stream\\r?\\n([\\s\\S]*?)\\r?\\n?endstream")
      var pageCounter = 1

      for (match in streamRegex.findAll(latin)) {
        val streamContent = match.groupValues[1]
        val rawBytes = streamContent.toByteArray(Charsets.ISO_8859_1)
        val decodedStream =
          tryInflate(rawBytes)?.let { String(it, Charsets.ISO_8859_1) } ?: streamContent

        if ("BT" in decodedStream && ("Tj" in decodedStream || "TJ" in decodedStream)) {
          val textPieces = extractPdfTextOperators(decodedStream)
          if (textPieces.isNotBlank()) {
            pageTextBlocks += "[Page $pageCounter]\n$textPieces"
            pageCounter++
          }
        }
      }
      return pageTextBlocks.joinToString("\n\n").trim()
    }

    private fun tryInflate(compressed: ByteArray): ByteArray? {
      return runCatching {
        val inflater = Inflater()
        inflater.setInput(compressed)
        val buffer = ByteArray(4096)
        val out = java.io.ByteArrayOutputStream()
        while (!inflater.finished()) {
          val count = inflater.inflate(buffer)
          if (count == 0) {
            if (inflater.needsInput()) break
            if (inflater.needsDictionary()) break
          } else {
            out.write(buffer, 0, count)
          }
        }
        inflater.end()
        val bytes = out.toByteArray()
        if (bytes.isNotEmpty()) bytes else null
      }.getOrNull()
    }

    private fun extractPdfTextOperators(content: String): String {
      val sb = StringBuilder()
      val parenRegex = Regex("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)")
      val btRegex = Regex("BT([\\s\\S]*?)ET")
      for (btMatch in btRegex.findAll(content)) {
        val block = btMatch.groupValues[1]
        for (pMatch in parenRegex.findAll(block)) {
          val unescaped =
            pMatch.groupValues[1]
              .replace("\\(", "(")
              .replace("\\)", ")")
              .replace("\\n", "\n")
              .replace("\\r", "")
              .replace("\\t", " ")
              .replace("\\\\", "\\")
          sb.append(unescaped)
        }
        sb.appendLine()
      }
      return sb.toString().replace(Regex("[ \\t]+"), " ").trim()
    }
  }

  suspend fun processDocumentUri(uriString: String): Result<DocumentExtractionResult> =
    withContext(Dispatchers.IO) {
      var tempFile: File? = null
      try {
        val copied =
          tempMediaCacheManager
            .copyUriToTempFile(
              uriString = uriString,
              extensionHint = "txt",
              maxAllowedBytes = MAX_DOC_BYTES,
            )
            .getOrThrow()
        tempFile = copied.file

        securityValidator
          .validateAttachmentSize(copied.byteSize, MAX_DOC_BYTES, copied.displayName)
          .getOrThrow()

        val format = detectFormat(copied.displayName, copied.mimeType)
        require(format != null) {
          "Unsupported document '${copied.displayName}'. Supported: PDF, DOCX, XLSX, CSV, TXT, JSON."
        }

        val result =
          when (format) {
            DocFormat.TXT -> extractTxt(copied.file, copied.displayName, copied.mimeType, copied.byteSize)
            DocFormat.JSON -> extractJson(copied.file, copied.displayName, copied.mimeType, copied.byteSize)
            DocFormat.CSV -> extractCsv(copied.file, copied.displayName, copied.mimeType, copied.byteSize)
            DocFormat.DOCX -> extractDocx(copied.file, copied.displayName, copied.mimeType, copied.byteSize)
            DocFormat.XLSX -> extractXlsx(copied.file, copied.displayName, copied.mimeType, copied.byteSize)
            DocFormat.PDF -> extractPdf(copied.file, copied.displayName, copied.mimeType, copied.byteSize, uriString)
          }

        Result.success(result)
      } catch (e: Throwable) {
        Result.failure(e)
      } finally {
        tempMediaCacheManager.cleanupFile(tempFile)
      }
    }

  private enum class DocFormat(val label: String) {
    PDF("PDF"),
    DOCX("DOCX"),
    XLSX("XLSX"),
    CSV("CSV"),
    TXT("TXT"),
    JSON("JSON"),
  }

  private fun detectFormat(name: String, mime: String): DocFormat? {
    val ext = name.substringAfterLast('.', "").lowercase()
    val m = mime.lowercase()
    return when {
      ext == "pdf" || m == "application/pdf" -> DocFormat.PDF
      ext == "docx" || m.contains("wordprocessingml") -> DocFormat.DOCX
      ext == "xlsx" || m.contains("spreadsheetml") -> DocFormat.XLSX
      ext == "csv" || m == "text/csv" || m == "text/comma-separated-values" -> DocFormat.CSV
      ext == "json" || m == "application/json" -> DocFormat.JSON
      ext == "txt" || m == "text/plain" -> DocFormat.TXT
      else -> null
    }
  }

  private fun extractTxt(file: File, name: String, mime: String, size: Long): DocumentExtractionResult {
    val text = file.readText(Charsets.UTF_8)
    return DocumentExtractionResult(
      displayName = name,
      formatLabel = "TXT",
      mimeType = mime,
      byteSize = size,
      pageCount = 1,
      extractedText = text.take(40_000),
      tables = emptyList(),
    )
  }

  private fun extractJson(file: File, name: String, mime: String, size: Long): DocumentExtractionResult {
    val raw = file.readText(Charsets.UTF_8)
    val warnings = mutableListOf<String>()
    runCatching { json.parseToJsonElement(raw) }.onFailure {
      warnings += "JSON syntax warning: ${it.localizedMessage}"
    }
    return DocumentExtractionResult(
      displayName = name,
      formatLabel = "JSON",
      mimeType = mime,
      byteSize = size,
      pageCount = 1,
      extractedText = raw.take(40_000),
      tables = emptyList(),
      warnings = warnings,
    )
  }

  private fun extractCsv(file: File, name: String, mime: String, size: Long): DocumentExtractionResult {
    val raw = file.readText(Charsets.UTF_8)
    val table = parseCsvTextToTable(name, raw)
    return DocumentExtractionResult(
      displayName = name,
      formatLabel = "CSV",
      mimeType = mime,
      byteSize = size,
      pageCount = 1,
      extractedText = raw.take(40_000),
      tables = listOf(table),
    )
  }

  fun parseCsvTextToTable(tableName: String, rawCsv: String): ParsedTable =
    Companion.parseCsvTextToTable(tableName, rawCsv)

  private fun parseRfc4180Rows(text: String): List<List<String>> = Companion.parseRfc4180Rows(text)

  fun extractDocxFromStream(
    inputStream: InputStream,
    name: String,
    mime: String = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    size: Long = 0L,
  ): DocumentExtractionResult {
    var docXmlBytes: ByteArray? = null
    ZipInputStream(inputStream).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        if (entry.name == "word/document.xml") {
          docXmlBytes = zip.readBytes()
          break
        }
      }
    }
    val bytes = docXmlBytes ?: error("Invalid DOCX file: word/document.xml missing.")
    val paragraphs = mutableListOf<String>()
    val tables = mutableListOf<ParsedTable>()

    val parser = Xml.newPullParser()
    parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

    var inParagraph = false
    var inTable = false
    var inRow = false
    var inCell = false
    val pBuilder = StringBuilder()
    val cellBuilder = StringBuilder()
    var currentRow = mutableListOf<String>()
    var currentTableRows = mutableListOf<List<String>>()
    var tableIndex = 1

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          when (localTagName(parser.name)) {
            "tbl" -> {
              inTable = true
              currentTableRows = mutableListOf()
            }
            "tr" -> if (inTable) {
              inRow = true
              currentRow = mutableListOf()
            }
            "tc" -> if (inRow) {
              inCell = true
              cellBuilder.clear()
            }
            "p" -> if (!inTable) {
              inParagraph = true
              pBuilder.clear()
            }
          }
        }
        XmlPullParser.TEXT -> {
          val txt = parser.text.orEmpty()
          if (inCell) cellBuilder.append(txt)
          else if (inParagraph) pBuilder.append(txt)
        }
        XmlPullParser.END_TAG -> {
          when (localTagName(parser.name)) {
            "p" -> {
              if (inParagraph) {
                val pText = pBuilder.toString().trim()
                if (pText.isNotEmpty()) paragraphs += pText
                inParagraph = false
              } else if (inCell) {
                cellBuilder.append(" ")
              }
            }
            "tc" -> if (inCell) {
              currentRow += cellBuilder.toString().trim()
              inCell = false
            }
            "tr" -> if (inRow) {
              if (currentRow.any { it.isNotEmpty() }) currentTableRows += currentRow
              inRow = false
            }
            "tbl" -> if (inTable) {
              if (currentTableRows.isNotEmpty()) {
                val headers = currentTableRows.first()
                tables +=
                  ParsedTable(
                    name = "DOCX Table $tableIndex",
                    headers = headers,
                    rows = currentTableRows.drop(1),
                    pageOrSheetRef = "Table $tableIndex",
                  )
                tableIndex++
              }
              inTable = false
            }
          }
        }
      }
      event = parser.next()
    }

    return DocumentExtractionResult(
      displayName = name,
      formatLabel = "DOCX",
      mimeType = mime,
      byteSize = size,
      pageCount = (paragraphs.size / 28).coerceAtLeast(1),
      extractedText = paragraphs.joinToString("\n\n").take(40_000),
      tables = tables,
    )
  }

  private fun extractDocx(file: File, name: String, mime: String, size: Long): DocumentExtractionResult =
    file.inputStream().use { extractDocxFromStream(it, name, mime, size) }

  fun extractXlsxFromStream(
    inputStream: InputStream,
    name: String,
    mime: String = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    size: Long = 0L,
  ): DocumentExtractionResult {
    var sharedStringsXml: ByteArray? = null
    val sheets = sortedMapOf<String, ByteArray>()

    ZipInputStream(inputStream).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        when {
          entry.name == "xl/sharedStrings.xml" -> sharedStringsXml = zip.readBytes()
          entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") ->
            sheets[entry.name] = zip.readBytes()
        }
      }
    }

    val sharedStrings = sharedStringsXml?.let(::parseSharedStrings) ?: emptyList()
    val tables = mutableListOf<ParsedTable>()
    val textPreview = StringBuilder()

    sheets.entries.forEachIndexed { idx, (entryName, xmlBytes) ->
      val sheetTitle = "Sheet ${idx + 1} (${entryName.substringAfterLast('/')})"
      val rows = parseWorksheetRows(xmlBytes, sharedStrings)
      if (rows.isNotEmpty()) {
        val maxCols = rows.maxOf { it.size }.coerceAtLeast(1)
        val normalizedRows =
          rows.map { r -> if (r.size >= maxCols) r else r + List(maxCols - r.size) { "" } }
        val headers = normalizedRows.first().mapIndexed { cIdx, h -> h.ifBlank { "Col_${cIdx + 1}" } }
        val bodyRows = normalizedRows.drop(1)
        tables +=
          ParsedTable(
            name = "$name - Sheet ${idx + 1}",
            headers = headers,
            rows = bodyRows,
            pageOrSheetRef = sheetTitle,
          )
        textPreview.appendLine("[$sheetTitle]")
        textPreview.appendLine(headers.joinToString(" | "))
        bodyRows.take(50).forEach { r -> textPreview.appendLine(r.joinToString(" | ")) }
        textPreview.appendLine()
      }
    }

    return DocumentExtractionResult(
      displayName = name,
      formatLabel = "XLSX",
      mimeType = mime,
      byteSize = size,
      pageCount = tables.size.coerceAtLeast(1),
      extractedText = textPreview.toString().take(40_000),
      tables = tables,
    )
  }

  private fun extractXlsx(file: File, name: String, mime: String, size: Long): DocumentExtractionResult =
    file.inputStream().use { extractXlsxFromStream(it, name, mime, size) }

  private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
    val result = mutableListOf<String>()
    val parser = Xml.newPullParser()
    parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
    var inSi = false
    var inT = false
    val sb = StringBuilder()
    var ev = parser.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
      when (ev) {
        XmlPullParser.START_TAG -> {
          val tag = localTagName(parser.name)
          if (tag == "si") {
            inSi = true
            sb.clear()
          } else if (inSi && tag == "t") {
            inT = true
          }
        }
        XmlPullParser.TEXT -> if (inT) sb.append(parser.text.orEmpty())
        XmlPullParser.END_TAG -> {
          val tag = localTagName(parser.name)
          if (tag == "t") inT = false
          else if (tag == "si") {
            result += sb.toString()
            inSi = false
          }
        }
      }
      ev = parser.next()
    }
    return result
  }

  private fun parseWorksheetRows(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val parser = Xml.newPullParser()
    parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
    var currentRow = mutableListOf<String>()
    var cellType: String? = null
    var cellRef: String? = null
    var inValue = false
    var inInlineText = false
    val valueBuilder = StringBuilder()

    var ev = parser.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
      when (ev) {
        XmlPullParser.START_TAG -> {
          when (localTagName(parser.name)) {
            "row" -> currentRow = mutableListOf()
            "c" -> {
              cellType = parser.getAttributeValue(null, "t")
              cellRef = parser.getAttributeValue(null, "r")
              valueBuilder.clear()
            }
            "v" -> inValue = true
            "t" -> if (cellType == "inlineStr") inInlineText = true
          }
        }
        XmlPullParser.TEXT -> {
          if (inValue || inInlineText) valueBuilder.append(parser.text.orEmpty())
        }
        XmlPullParser.END_TAG -> {
          when (localTagName(parser.name)) {
            "v" -> inValue = false
            "t" -> inInlineText = false
            "c" -> {
              val rawVal = valueBuilder.toString().trim()
              val resolved =
                if (cellType == "s") {
                  val idx = rawVal.toIntOrNull()
                  if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else rawVal
                } else rawVal
              val colIdx = columnIndexFromCellRef(cellRef) ?: currentRow.size
              while (currentRow.size < colIdx) currentRow += ""
              currentRow += resolved
            }
            "row" -> if (currentRow.any { it.isNotEmpty() }) rows += currentRow
          }
        }
      }
      ev = parser.next()
    }
    return rows
  }

  private fun columnIndexFromCellRef(ref: String?): Int? {
    if (ref.isNullOrBlank()) return null
    val letters = ref.takeWhile { it.isLetter() }.uppercase()
    if (letters.isEmpty()) return null
    var idx = 0
    for (ch in letters) {
      idx = idx * 26 + (ch - 'A' + 1)
    }
    return (idx - 1).coerceAtLeast(0)
  }

  private fun extractPdf(
    file: File,
    name: String,
    mime: String,
    size: Long,
    uriString: String,
  ): DocumentExtractionResult {
    val pdfBytes = file.readBytes()
    val extractedText = extractTextFromPdfBytes(pdfBytes)
    val warnings = mutableListOf<String>()
    val renderedPages = mutableListOf<ProcessedImagePayload>()
    var detectedPages = 1

    runCatching {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          detectedPages = renderer.pageCount.coerceAtLeast(1)
          if (extractedText.isBlank()) {
            val pagesToRender = minOf(renderer.pageCount, 3)
            for (p in 0 until pagesToRender) {
              renderer.openPage(p).use { page ->
                val bmp = Bitmap.createBitmap(
                  (page.width * 2).coerceIn(400, 1400),
                  (page.height * 2).coerceIn(500, 1800),
                  Bitmap.Config.ARGB_8888,
                )
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                renderedPages +=
                  imageProcessor.compressBitmapPublic(
                    uriString = "$uriString#page=${p + 1}",
                    bitmap = bmp,
                    maxDimensionPx = 1200,
                    maxBytes = 420_000,
                    note = "PDF Page ${p + 1}",
                  )
              }
            }
            if (renderedPages.isNotEmpty()) {
              warnings +=
                "No embedded digital text stream found; rendered ${renderedPages.size} page(s) for vision OCR inspection."
            }
          }
        }
      }
    }

    if (extractedText.isBlank() && renderedPages.isEmpty()) {
      warnings += "PDF appears encrypted or image-only without extractable text streams."
    }

    return DocumentExtractionResult(
      displayName = name,
      formatLabel = "PDF",
      mimeType = mime,
      byteSize = size,
      pageCount = maxOf(detectedPages, Regex("\\[Page \\d+]").findAll(extractedText).count().coerceAtLeast(1)),
      extractedText = extractedText.take(40_000),
      tables = emptyList(),
      warnings = warnings,
      scannedPageImages = renderedPages,
    )
  }

  /**
   * Pure Kotlin/JVM PDF text-object extractor supporting both uncompressed and FlateDecode streams
   * with page markers so PDF text works seamlessly in JVM tests as well as Android devices.
   */
  fun extractTextFromPdfBytes(pdfBytes: ByteArray): String =
    Companion.extractTextFromPdfBytes(pdfBytes)

  private fun localTagName(fullName: String?): String {
    val raw = fullName.orEmpty()
    return raw.substringAfter(':', raw)
  }
}
