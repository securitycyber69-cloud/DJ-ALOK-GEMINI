package com.example.multimodal.image

import com.example.core.image.ImageProcessor
import com.example.domain.model.ProcessedImagePayload
import javax.inject.Inject
import javax.inject.Singleton

enum class PhotoAnalysisMode {
  GENERAL_UNDERSTANDING,
  MULTI_IMAGE_COMPARISON,
  DOCUMENT_OCR,
  CHART_OR_INFOGRAPHIC,
  SCREENSHOT_UI,
}

@Singleton
class PhotoIntelligenceEngine @Inject constructor(
  private val imageProcessor: ImageProcessor,
) {
  suspend fun processSingleImage(uriString: String): Result<ProcessedImagePayload> =
    imageProcessor.processUri(uriString)

  suspend fun processMultipleImages(uriStrings: List<String>): List<ProcessedImagePayload> {
    return uriStrings.take(4).mapNotNull { uri ->
      imageProcessor.processUri(uri).getOrNull()
    }
  }

  fun detectAnalysisMode(prompt: String, imageCount: Int): PhotoAnalysisMode {
    val q = prompt.lowercase()
    return when {
      imageCount > 1 || "compare" in q || "difference" in q -> PhotoAnalysisMode.MULTI_IMAGE_COMPARISON
      "chart" in q || "graph" in q || "infographic" in q || "plot" in q -> PhotoAnalysisMode.CHART_OR_INFOGRAPHIC
      "screenshot" in q || "screen" in q || "ui" in q || "error" in q -> PhotoAnalysisMode.SCREENSHOT_UI
      "text" in q || "ocr" in q || "receipt" in q || "table" in q || "read" in q || "document" in q ->
        PhotoAnalysisMode.DOCUMENT_OCR
      else -> PhotoAnalysisMode.GENERAL_UNDERSTANDING
    }
  }

  fun buildVisionInstruction(prompt: String, images: List<ProcessedImagePayload>): String {
    val mode = detectAnalysisMode(prompt, images.size)
    return buildString {
      appendLine("PHOTO INTELLIGENCE DIRECTIVE (${mode.name}):")
      appendLine("- Examine all ${images.size} attached image(s) carefully: identify visible objects, scene context, spatial/visual relationships, and any visible text (OCR).")
      when (mode) {
        PhotoAnalysisMode.MULTI_IMAGE_COMPARISON ->
          appendLine("- Compare the attached images side-by-side: highlight similarities, visual differences, and include a concise Markdown comparison table.")
        PhotoAnalysisMode.DOCUMENT_OCR ->
          appendLine("- Extract all clearly legible text verbatim, format structured data into a Markdown table if tabular, and state confidence qualifications for blurry/occluded regions.")
        PhotoAnalysisMode.CHART_OR_INFOGRAPHIC ->
          appendLine("- Interpret axes, units, legends, exact visible values (without guessing unreadable values), and summarize trends.")
        PhotoAnalysisMode.SCREENSHOT_UI ->
          appendLine("- Analyze UI hierarchy, text labels, error banners, and provide actionable troubleshooting or explanation.")
        PhotoAnalysisMode.GENERAL_UNDERSTANDING ->
          appendLine("- Answer the user's question directly with structured observations, detected text (if any), and confidence qualifications where ambiguous.")
      }
      val metaNotes = images.mapIndexedNotNull { idx, img ->
        img.exifSummary?.let { "Image ${idx + 1}: ${img.width}x${img.height} (${img.mimeType}, $it)" }
          ?: "Image ${idx + 1}: ${img.width}x${img.height} (${img.mimeType})"
      }
      if (metaNotes.isNotEmpty()) {
        appendLine("Processed image payloads: ${metaNotes.joinToString("; ")}")
      }
    }
  }
}
