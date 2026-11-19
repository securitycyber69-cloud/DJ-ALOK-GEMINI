package com.example.domain.router

import com.example.analytics.parser.TabularDataParser
import com.example.domain.model.IntelligenceIntent
import com.example.domain.model.MediaAttachmentKind
import com.example.domain.model.PendingMediaItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeIntelligenceRouter @Inject constructor(
  private val tabularDataParser: TabularDataParser,
) {
  fun classifyIntent(
    prompt: String,
    hasImages: Boolean,
    attachments: List<PendingMediaItem>,
    deepResearchRequested: Boolean = false,
  ): IntelligenceIntent {
    val q = prompt.lowercase().trim()

    if (attachments.any { it.kind == MediaAttachmentKind.VIDEO }) {
      return IntelligenceIntent.VIDEO_INTELLIGENCE
    }
    if (attachments.any { it.kind == MediaAttachmentKind.AUDIO }) {
      return IntelligenceIntent.AUDIO_INTELLIGENCE
    }

    val docs = attachments.filter { it.kind == MediaAttachmentKind.DOCUMENT }
    if (docs.isNotEmpty()) {
      val hasSpreadsheet =
        docs.any {
          it.displayName.endsWith(".csv", ignoreCase = true) ||
            it.displayName.endsWith(".xlsx", ignoreCase = true)
        }
      return when {
        chartKeywords.any { it in q } -> IntelligenceIntent.CHART_VISUALIZATION
        hasSpreadsheet || dataAnalyticsKeywords.any { it in q } -> IntelligenceIntent.DATA_ANALYSIS
        else -> IntelligenceIntent.DOCUMENT_INTELLIGENCE
      }
    }

    if (hasImages || attachments.any { it.kind == MediaAttachmentKind.PHOTO }) {
      return IntelligenceIntent.PHOTO_INTELLIGENCE
    }

    if (deepResearchRequested || deepResearchPhrases.any { it in q }) {
      return IntelligenceIntent.DEEP_RESEARCH
    }

    val hasInlineTable = tabularDataParser.parseInlineTabularText("Prompt Dataset", prompt) != null
    if (hasInlineTable && chartKeywords.any { it in q }) {
      return IntelligenceIntent.CHART_VISUALIZATION
    }
    if (hasInlineTable || (dataAnalyticsKeywords.any { it in q } && ("dataset" in q || "csv" in q || "excel" in q || "data" in q))) {
      return IntelligenceIntent.DATA_ANALYSIS
    }
    if (chartKeywords.any { it in q } && ("chart" in q || "plot" in q || "graph" in q)) {
      return IntelligenceIntent.CHART_VISUALIZATION
    }

    if (liveSearchKeywords.any { it in q }) {
      return IntelligenceIntent.LIVE_SEARCH
    }

    if (isCasualGreeting(q)) {
      return IntelligenceIntent.DIRECT_CHAT
    }

    return IntelligenceIntent.NORMAL_AI
  }

  private fun isCasualGreeting(q: String): Boolean {
    if (q.length > 48) return false
    val cleaned = q.replace(Regex("[^a-zA-Z0-9\\s\\u0980-\\u09FF\\u0900-\\u097F]"), "").trim()
    return greetingPhrases.any { cleaned == it || cleaned.startsWith("$it ") }
  }

  companion object {
    private val greetingPhrases =
      listOf(
        "hi", "hello", "hey", "good morning", "good evening", "good night",
        "how are you", "thanks", "thank you", "হ্যালো", "হাই", "ধন্যবাদ", "नमस्ते",
      )

    private val deepResearchPhrases =
      listOf(
        "deep research", "research in detail", "comprehensive report", "in-depth research",
        "in detail research", "detailed research", "literature review", "deep dive",
        "research report", "বিস্তারিত গবেষণা", "গভীর গবেষণা",
      )

    private val liveSearchKeywords =
      listOf(
        "today's", "today ", "latest ", "current ", "news", "breaking", "recent ",
        "this week", "this month", "search ", "look up", "find today", "up to date",
        "live price", "weather", "release date", "2026", "আজকের", "সর্বশেষ", "খবর", "ताज़ा", "समाचार",
      )

    private val chartKeywords =
      listOf(
        "chart", "plot", "graph", "bar chart", "line chart", "pie chart", "area chart",
        "scatter plot", "time series", "visualize",
      )

    private val dataAnalyticsKeywords =
      listOf(
        "analyze my excel", "analyze this csv", "analyze data", "data analysis",
        "statistics", "mean", "median", "correlation", "trend", "duplicate", "missing value",
      )
  }
}
