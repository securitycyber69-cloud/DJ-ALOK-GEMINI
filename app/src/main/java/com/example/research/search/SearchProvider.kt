package com.example.research.search

import com.example.domain.model.AppSettings
import com.example.domain.model.SearchResponse
import com.example.domain.model.SearchResultItem

enum class SearchTopic(val label: String) {
  GENERAL("general"),
  NEWS("news"),
  TECHNOLOGY("technology"),
  RESEARCH("research"),
}

data class SearchQueryRequest(
  val query: String,
  val topic: SearchTopic = SearchTopic.GENERAL,
  val page: Int = 1,
  val pageSize: Int = 8,
  val includePageContent: Boolean = false,
)

class NoSearchProviderConfiguredException(message: String) : IllegalStateException(message)

interface SearchProvider {
  val providerName: String

  fun isAvailable(settings: AppSettings): Boolean

  suspend fun search(
    request: SearchQueryRequest,
    settings: AppSettings,
  ): Result<SearchResponse>
}

interface SearchService {
  suspend fun search(
    request: SearchQueryRequest,
    settings: AppSettings,
  ): Result<SearchResponse>

  suspend fun searchMultipleQueries(
    queries: List<String>,
    topic: SearchTopic,
    settings: AppSettings,
  ): Result<List<SearchResultItem>>

  fun clearCache()
}
