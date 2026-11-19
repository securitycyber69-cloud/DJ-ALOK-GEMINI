package com.example.ai.provider.dto

import com.example.domain.model.DataAnalyticsResult
import com.example.domain.model.ParsedTable
import com.example.domain.model.SearchResultItem
import kotlinx.serialization.Serializable

@Serializable
data class ProxyChatRequest(
  val modelId: String,
  val provider: String,
  val systemInstruction: String,
  val temperature: Double = 0.7,
  val stream: Boolean = true,
  val messages: List<ProxyTurnDto>,
)

@Serializable
data class ProxyTurnDto(
  val role: String,
  val text: String,
  val images: List<ProxyImageDto> = emptyList(),
)

@Serializable
data class ProxyImageDto(
  val mimeType: String,
  val base64Data: String,
)

@Serializable
data class ProxyStreamEventDto(
  val delta: String? = null,
  val usage: ProxyUsageDto? = null,
  val error: String? = null,
)

@Serializable
data class ProxyUsageDto(
  val promptTokens: Int = 0,
  val completionTokens: Int = 0,
  val totalTokens: Int = 0,
)

@Serializable
data class ProxySearchRequest(
  val query: String,
  val topic: String = "general",
  val page: Int = 1,
  val pageSize: Int = 8,
  val includePageContent: Boolean = false,
)

@Serializable
data class ProxySearchResponse(
  val results: List<SearchResultItem> = emptyList(),
  val hasMorePages: Boolean = false,
  val providerUsed: String = "backend-proxy",
)

@Serializable
data class ExternalSearchRequest(
  val q: String,
  val num: Int = 8,
  val page: Int = 1,
)

@Serializable
data class ExternalSearchResponse(
  val organic: List<ExternalSearchOrganicItem> = emptyList(),
  val news: List<ExternalSearchNewsItem> = emptyList(),
)

@Serializable
data class ExternalSearchOrganicItem(
  val title: String = "",
  val link: String = "",
  val snippet: String = "",
  val date: String? = null,
)

@Serializable
data class ExternalSearchNewsItem(
  val title: String = "",
  val link: String = "",
  val snippet: String = "",
  val source: String? = null,
  val date: String? = null,
)

@Serializable
data class ProxyAudioRequest(
  val mimeType: String,
  val base64Audio: String,
  val languageHint: String? = null,
  val fileName: String,
)

@Serializable
data class ProxyAudioResponse(
  val text: String = "",
  val detectedLanguage: String = "unknown",
  val summary: String = "",
  val keyPoints: List<String> = emptyList(),
)

@Serializable
data class ProxyAnalyticsRequest(
  val datasetName: String,
  val table: ParsedTable,
  val operations: List<String>,
  val timeoutSeconds: Int = 15,
  val maxMemoryMb: Int = 256,
)

@Serializable
data class ProxyAnalyticsResponse(
  val result: DataAnalyticsResult? = null,
  val error: String? = null,
)
