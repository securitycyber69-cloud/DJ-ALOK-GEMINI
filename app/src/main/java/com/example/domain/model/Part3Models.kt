package com.example.domain.model

import kotlinx.serialization.Serializable

enum class IntelligenceIntent(val label: String) {
  DIRECT_CHAT("Direct Chat"),
  NORMAL_AI("AI Response"),
  LIVE_SEARCH("Live Search"),
  DEEP_RESEARCH("Deep Research"),
  PHOTO_INTELLIGENCE("Photo Intelligence"),
  VIDEO_INTELLIGENCE("Video Intelligence"),
  AUDIO_INTELLIGENCE("Audio Intelligence"),
  DOCUMENT_INTELLIGENCE("Document Intelligence"),
  DATA_ANALYSIS("Data Analysis"),
  CHART_VISUALIZATION("Interactive Chart"),
}

enum class MediaAttachmentKind {
  PHOTO,
  VIDEO,
  AUDIO,
  DOCUMENT,
}

@Serializable
data class PendingMediaItem(
  val id: String,
  val uriString: String,
  val displayName: String,
  val mimeType: String,
  val byteSize: Long,
  val kind: MediaAttachmentKind,
  val isSampleArtwork: Boolean = false,
)

@Serializable
enum class SourceVerificationStatus(val label: String) {
  VERIFIED_HTTPS("Verified HTTPS"),
  GROUNDED_REDIRECT("Google Grounded"),
  UNVERIFIED_FORMAT("Unverified Format"),
  FETCH_FAILED("Fetch Failed"),
}

@Serializable
data class SearchResultItem(
  val id: String,
  val title: String,
  val url: String,
  val publisher: String,
  val snippet: String,
  val publishedTimestamp: String? = null,
  val retrievedTimestampMs: Long,
  val verificationStatus: SourceVerificationStatus,
  val sourceDomain: String,
  val relevanceScore: Double = 1.0,
  val pageExcerpt: String? = null,
)

@Serializable
data class SearchResponse(
  val query: String,
  val results: List<SearchResultItem>,
  val page: Int,
  val hasMorePages: Boolean,
  val providerUsed: String,
  val retrievedAtMs: Long,
)

@Serializable
data class ConflictingClaim(
  val topic: String,
  val claimA: String,
  val sourceA: String,
  val claimB: String,
  val sourceB: String,
)

@Serializable
data class ResearchReport(
  val query: String,
  val complexity: String,
  val executiveSummary: String,
  val researchObjectives: List<String>,
  val methodology: String,
  val keyFindings: List<String>,
  val detailedAnalysis: String,
  val sourceComparison: List<String>,
  val conflictingInformation: List<ConflictingClaim>,
  val limitations: List<String>,
  val conclusion: String,
  val references: List<SearchResultItem>,
  val generatedAtMs: Long,
) {
  fun toMarkdownExport(): String = buildString {
    appendLine("# Deep Research Report: $query")
    appendLine()
    appendLine("## Executive Summary")
    appendLine(executiveSummary)
    appendLine()
    appendLine("## Research Objectives")
    researchObjectives.forEach { appendLine("- $it") }
    appendLine()
    appendLine("## Methodology")
    appendLine(methodology)
    appendLine()
    appendLine("## Key Findings")
    keyFindings.forEach { appendLine("- $it") }
    appendLine()
    appendLine("## Detailed Analysis")
    appendLine(detailedAnalysis)
    appendLine()
    appendLine("## Source Comparison")
    sourceComparison.forEach { appendLine("- $it") }
    appendLine()
    appendLine("## Conflicting Information")
    if (conflictingInformation.isEmpty()) {
      appendLine("No direct factual conflicts identified across verified sources.")
    } else {
      conflictingInformation.forEach { c ->
        appendLine("- **${c.topic}**: ${c.sourceA} states \"${c.claimA}\" vs ${c.sourceB} states \"${c.claimB}\"")
      }
    }
    appendLine()
    appendLine("## Limitations")
    limitations.forEach { appendLine("- $it") }
    appendLine()
    appendLine("## Conclusion")
    appendLine(conclusion)
    appendLine()
    appendLine("## References")
    references.forEachIndexed { idx, ref ->
      appendLine("${idx + 1}. [${ref.title}](${ref.url}) — ${ref.publisher} (${ref.verificationStatus.label})")
    }
  }
}

sealed interface DeepResearchProgress {
  data object UnderstandingQuestion : DeepResearchProgress
  data class DetectingComplexity(val complexityLabel: String) : DeepResearchProgress
  data class PlanningQueries(val queries: List<String>) : DeepResearchProgress
  data class SearchingWeb(val currentQuery: String, val queryIndex: Int, val totalQueries: Int, val sourcesCollected: Int) : DeepResearchProgress
  data class DeduplicatingAndVerifying(val rawCount: Int, val verifiedCount: Int) : DeepResearchProgress
  data class SynthesizingReport(val verifiedSourcesCount: Int) : DeepResearchProgress
  data class Completed(val report: ResearchReport) : DeepResearchProgress
  data object Cancelled : DeepResearchProgress
  data class Failed(val reason: String) : DeepResearchProgress
}

@Serializable
data class VideoMetadataInfo(
  val displayName: String,
  val mimeType: String,
  val byteSize: Long,
  val durationMs: Long,
  val width: Int,
  val height: Int,
  val bitrateBps: Long,
  val hasAudioTrack: Boolean,
  val audioMimeType: String? = null,
  val audioSampleRateHz: Int? = null,
)

@Serializable
data class VideoTimelineSegment(
  val startTimeFormatted: String,
  val endTimeFormatted: String,
  val sceneDescription: String,
  val visualEvents: String,
  val transcriptExcerpt: String? = null,
)

@Serializable
data class VideoAnalysisResult(
  val metadata: VideoMetadataInfo,
  val analysisMethodNote: String,
  val sampledFrameCount: Int,
  val sampledTimestamps: List<String>,
  val summary: String,
  val topic: String,
  val timeline: List<VideoTimelineSegment>,
  val transcriptSummary: String,
  val keyObservations: List<String>,
)

@Serializable
data class AudioMetadataInfo(
  val displayName: String,
  val mimeType: String,
  val byteSize: Long,
  val durationMs: Long,
  val bitrateBps: Long,
)

@Serializable
data class AudioTranscriptSegment(
  val startTimeFormatted: String,
  val endTimeFormatted: String,
  val speaker: String? = null,
  val text: String,
)

@Serializable
data class AudioAnalysisResult(
  val metadata: AudioMetadataInfo,
  val detectedLanguage: String,
  val transcriptionEngine: String,
  val summary: String,
  val fullTranscript: String,
  val segments: List<AudioTranscriptSegment>,
  val keyPoints: List<String>,
  val translatedText: String? = null,
)

@Serializable
data class ParsedTable(
  val name: String,
  val headers: List<String>,
  val rows: List<List<String>>,
  val pageOrSheetRef: String? = null,
)

@Serializable
data class DocumentExtractionResult(
  val displayName: String,
  val formatLabel: String,
  val mimeType: String,
  val byteSize: Long,
  val pageCount: Int,
  val extractedText: String,
  val tables: List<ParsedTable>,
  val warnings: List<String> = emptyList(),
  val scannedPageImages: List<ProcessedImagePayload> = emptyList(),
)

@Serializable
data class DataCleaningSummary(
  val originalRowCount: Int,
  val cleanedRowCount: Int,
  val duplicateRowsRemoved: Int,
  val missingValuesByColumn: Map<String, Int>,
  val normalizedColumns: List<String>,
)

@Serializable
data class ColumnStatistics(
  val columnName: String,
  val count: Int,
  val sum: Double,
  val mean: Double,
  val median: Double,
  val min: Double,
  val max: Double,
  val stdDev: Double,
)

@Serializable
data class DataAnalyticsResult(
  val datasetName: String,
  val cleaningSummary: DataCleaningSummary,
  val numericStatistics: List<ColumnStatistics>,
  val detectedTrends: List<String>,
  val correlationNotes: List<String>,
  val groupedHighlights: List<String>,
  val sandboxExecutedOnBackend: Boolean,
  val previewTable: ParsedTable? = null,
)

@Serializable
enum class ChartType(val label: String) {
  BAR("Bar Chart"),
  LINE("Line Chart"),
  PIE("Pie Chart"),
  AREA("Area Chart"),
  SCATTER("Scatter Plot"),
  COMPARISON("Comparison Chart"),
  TIME_SERIES("Time Series"),
}

@Serializable
data class ChartDataPoint(
  val label: String,
  val xValue: Double,
  val yValue: Double,
)

@Serializable
data class ChartSeries(
  val name: String,
  val points: List<ChartDataPoint>,
)

@Serializable
data class ChartSpec(
  val title: String,
  val subtitle: String,
  val chartType: ChartType,
  val xAxisLabel: String,
  val yAxisLabel: String,
  val unit: String = "",
  val series: List<ChartSeries>,
  val sourceAttribution: String,
)

@Serializable
sealed class ResponseBlock {
  @Serializable
  data class MarkdownText(val content: String) : ResponseBlock()

  @Serializable
  data class SourceCitations(val sources: List<SearchResultItem>) : ResponseBlock()

  @Serializable
  data class ResearchReportBlock(val report: ResearchReport) : ResponseBlock()

  @Serializable
  data class VideoAnalysisBlock(val analysis: VideoAnalysisResult) : ResponseBlock()

  @Serializable
  data class AudioTranscriptBlock(val analysis: AudioAnalysisResult) : ResponseBlock()

  @Serializable
  data class DocumentSummaryBlock(
    val documentName: String,
    val formatLabel: String,
    val pageCount: Int,
    val extractedPreview: String,
    val tables: List<ParsedTable>,
    val warnings: List<String>,
  ) : ResponseBlock()

  @Serializable
  data class DataAnalyticsBlock(val analytics: DataAnalyticsResult) : ResponseBlock()

  @Serializable
  data class ChartBlock(val chart: ChartSpec) : ResponseBlock()

  @Serializable
  data class ProgressNote(val label: String) : ResponseBlock()
}

// Clean Part 4 boundary contracts (unimplemented in Part 3; reserved for Part 4)
interface FutureOverlayExtensionPoint {
  val isOverlaySupportedInCurrentBuild: Boolean get() = false
}

interface FutureSystemAutomationExtensionPoint {
  val isAutomationEnabledInCurrentBuild: Boolean get() = false
}
