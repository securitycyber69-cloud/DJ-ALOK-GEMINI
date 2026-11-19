package com.example.research.search

import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.api.ProxyApiService
import com.example.ai.provider.dto.ExternalSearchRequest
import com.example.ai.provider.dto.GeminiContent
import com.example.ai.provider.dto.GeminiGenerateRequest
import com.example.ai.provider.dto.GeminiGenerationConfig
import com.example.ai.provider.dto.GeminiPart
import com.example.ai.provider.dto.GeminiTool
import com.example.ai.provider.dto.ProxySearchRequest
import com.example.core.network.RetryPolicy
import com.example.core.security.SecurityValidator
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.SearchResponse
import com.example.domain.model.SearchResultItem
import com.example.domain.model.SourceVerificationStatus
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class HttpPageRetriever @Inject constructor(
  private val okHttpClient: OkHttpClient,
  private val securityValidator: SecurityValidator,
) {
  suspend fun fetchPageExcerpt(url: String, maxChars: Int = 1800): String? =
    withContext(Dispatchers.IO) {
      if (!securityValidator.validateHttpsResourceUrl(url)) return@withContext null
      runCatching {
        val req =
          Request.Builder()
            .url(url)
            .header("User-Agent", "MousomiAI-ResearchBot/1.0 (+Android)")
            .get()
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) return@use null
          val bodyText = resp.body?.string()?.take(60_000).orEmpty()
          stripHtml(bodyText).take(maxChars).ifBlank { null }
        }
      }.getOrNull()
    }

  private fun stripHtml(html: String): String {
    return html
      .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
      .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
      .replace(Regex("<[^>]+>"), " ")
      .replace(Regex("&nbsp;|&amp;|&quot;|&#39;|&lt;|&gt;"), " ")
      .replace(Regex("\\s+"), " ")
      .trim()
  }
}

@Singleton
class BackendProxySearchProvider @Inject constructor(
  private val proxyApiService: ProxyApiService,
  private val securityValidator: SecurityValidator,
) : SearchProvider {
  override val providerName: String = "Backend Search Gateway"

  override fun isAvailable(settings: AppSettings): Boolean =
    settings.connectionMode == ConnectionMode.BACKEND_PROXY && settings.proxyBaseUrl.isNotBlank()

  override suspend fun search(request: SearchQueryRequest, settings: AppSettings): Result<SearchResponse> =
    runCatching {
      val baseUrl = securityValidator.validateHttpsUrl(settings.proxyBaseUrl).getOrThrow()
      val endpoint = "${baseUrl}v1/search"
      val response =
        RetryPolicy.withExponentialBackoff {
          proxyApiService.searchViaProxy(
            fullHttpsUrl = endpoint,
            request =
              ProxySearchRequest(
                query = request.query,
                topic = request.topic.label,
                page = request.page,
                pageSize = request.pageSize,
                includePageContent = request.includePageContent,
              ),
          )
        }
      if (!response.isSuccessful) {
        error("Proxy search failed (HTTP ${response.code()}).")
      }
      val body = response.body() ?: error("Empty search response from backend proxy.")
      SearchResponse(
        query = request.query,
        results = body.results,
        page = request.page,
        hasMorePages = body.hasMorePages,
        providerUsed = body.providerUsed,
        retrievedAtMs = System.currentTimeMillis(),
      )
    }
}

@Singleton
class ExternalSearchApiProvider @Inject constructor(
  private val proxyApiService: ProxyApiService,
  private val securityValidator: SecurityValidator,
) : SearchProvider {
  override val providerName: String = "External Search API"

  override fun isAvailable(settings: AppSettings): Boolean =
    settings.externalSearchUrl.isNotBlank() && settings.externalSearchApiKey.isNotBlank()

  override suspend fun search(request: SearchQueryRequest, settings: AppSettings): Result<SearchResponse> =
    runCatching {
      val validatedUrl =
        securityValidator.validateHttpsUrl(settings.externalSearchUrl).getOrThrow().trimEnd('/')
      val resp =
        RetryPolicy.withExponentialBackoff {
          proxyApiService.searchExternalApi(
            fullHttpsUrl = validatedUrl,
            apiKey = settings.externalSearchApiKey.trim(),
            request =
              ExternalSearchRequest(
                q = request.query,
                num = request.pageSize,
                page = request.page,
              ),
          )
        }
      if (!resp.isSuccessful) {
        error("External search API error (HTTP ${resp.code()}).")
      }
      val body = resp.body() ?: error("External search returned empty body.")
      val now = System.currentTimeMillis()
      val organicItems =
        body.organic.mapIndexed { index, item ->
          val domain = extractDomain(item.link)
          SearchResultItem(
            id = "ext_org_${UUID.randomUUID()}",
            title = item.title.ifBlank { domain },
            url = item.link,
            publisher = domain,
            snippet = item.snippet,
            publishedTimestamp = item.date,
            retrievedTimestampMs = now,
            verificationStatus =
              if (securityValidator.validateHttpsResourceUrl(item.link)) SourceVerificationStatus.VERIFIED_HTTPS
              else SourceVerificationStatus.UNVERIFIED_FORMAT,
            sourceDomain = domain,
            relevanceScore = (1.0 - index * 0.05).coerceAtLeast(0.2),
          )
        }
      val newsItems =
        body.news.mapIndexed { index, item ->
          val domain = extractDomain(item.link)
          SearchResultItem(
            id = "ext_news_${UUID.randomUUID()}",
            title = item.title.ifBlank { domain },
            url = item.link,
            publisher = item.source ?: domain,
            snippet = item.snippet,
            publishedTimestamp = item.date,
            retrievedTimestampMs = now,
            verificationStatus =
              if (securityValidator.validateHttpsResourceUrl(item.link)) SourceVerificationStatus.VERIFIED_HTTPS
              else SourceVerificationStatus.UNVERIFIED_FORMAT,
            sourceDomain = domain,
            relevanceScore = (0.98 - index * 0.05).coerceAtLeast(0.2),
          )
        }
      val merged = (newsItems + organicItems).filter { it.url.isNotBlank() }
      SearchResponse(
        query = request.query,
        results = merged,
        page = request.page,
        hasMorePages = merged.size >= request.pageSize,
        providerUsed = providerName,
        retrievedAtMs = now,
      )
    }

  private fun extractDomain(url: String): String =
    runCatching { URI(url).host?.removePrefix("www.").orEmpty() }.getOrDefault("").ifBlank { "web" }
}

@Singleton
class GeminiGroundingSearchProvider @Inject constructor(
  private val geminiApiService: GeminiApiService,
  private val securityValidator: SecurityValidator,
) : SearchProvider {
  override val providerName: String = "Gemini Google Search Grounding"

  override fun isAvailable(settings: AppSettings): Boolean =
    settings.geminiApiKey.isNotBlank()

  override suspend fun search(request: SearchQueryRequest, settings: AppSettings): Result<SearchResponse> =
    runCatching {
      val apiKey = settings.geminiApiKey.trim()
      require(apiKey.isNotEmpty()) { "Gemini API key is required for Google Search grounding." }

      val groundingPrompt =
        buildString {
          append("Search the web for the latest, verified information about: \"${request.query}\". ")
          append("Topic focus: ${request.topic.label}. ")
          append("Return concise factual points grounded strictly in retrieved web sources with dates where available.")
        }

      val modelId = settings.hydrogenBackingModelId.ifBlank { "gemini-2.5-flash" }
      val resp =
        RetryPolicy.withExponentialBackoff {
          geminiApiService.generateContent(
            model = modelId,
            apiKey = apiKey,
            request =
              GeminiGenerateRequest(
                contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = groundingPrompt)))),
                generationConfig = GeminiGenerationConfig(temperature = 0.2),
                tools = listOf(GeminiTool(google_search = JsonObject(emptyMap()))),
              ),
          )
        }

      if (!resp.isSuccessful) {
        error("Gemini Search Grounding failed (HTTP ${resp.code()}).")
      }

      val body = resp.body() ?: error("Empty response from Gemini Search Grounding.")
      val candidate = body.candidates.firstOrNull()
      val grounding = candidate?.groundingMetadata
      val chunks = grounding?.groundingChunks.orEmpty()
      val supports = grounding?.groundingSupports.orEmpty()
      val now = System.currentTimeMillis()

      val items =
        chunks.mapIndexedNotNull { idx, chunk ->
          val web = chunk.web ?: return@mapIndexedNotNull null
          val uri = web.uri?.trim().orEmpty()
          if (uri.isBlank()) return@mapIndexedNotNull null
          val domain =
            runCatching { URI(uri).host?.removePrefix("www.") }.getOrNull().orEmpty().ifBlank {
              web.title ?: "Google Search"
            }
          val matchingSnippets =
            supports
              .filter { idx in it.groundingChunkIndices }
              .mapNotNull { it.segment?.text?.trim() }
              .distinct()
          val snippetText =
            matchingSnippets.joinToString(" ").ifBlank {
              candidate?.content?.parts?.mapNotNull { it.text }?.joinToString(" ")?.take(240).orEmpty()
            }
          SearchResultItem(
            id = "gem_grd_$idx",
            title = web.title?.ifBlank { domain } ?: domain,
            url = uri,
            publisher = web.title ?: domain,
            snippet = snippetText,
            publishedTimestamp = null,
            retrievedTimestampMs = now,
            verificationStatus =
              if (uri.contains("vertexaisearch.cloud.google.com/grounding-api-redirect")) {
                SourceVerificationStatus.GROUNDED_REDIRECT
              } else if (securityValidator.validateHttpsResourceUrl(uri)) {
                SourceVerificationStatus.VERIFIED_HTTPS
              } else {
                SourceVerificationStatus.UNVERIFIED_FORMAT
              },
            sourceDomain = domain,
            relevanceScore = (1.0 - idx * 0.04).coerceAtLeast(0.3),
          )
        }

      SearchResponse(
        query = request.query,
        results = items,
        page = request.page,
        hasMorePages = false,
        providerUsed = providerName,
        retrievedAtMs = now,
      )
    }
}

@Singleton
class CompositeSearchService @Inject constructor(
  private val proxySearchProvider: BackendProxySearchProvider,
  private val externalSearchApiProvider: ExternalSearchApiProvider,
  private val geminiGroundingSearchProvider: GeminiGroundingSearchProvider,
  private val pageRetriever: HttpPageRetriever,
) : SearchService {

  private data class CacheEntry(val response: SearchResponse, val cachedAtMs: Long)
  private val cache = ConcurrentHashMap<String, CacheEntry>()
  private val cacheTtlMs = 5 * 60 * 1000L

  override suspend fun search(request: SearchQueryRequest, settings: AppSettings): Result<SearchResponse> {
    val cleanQuery = request.query.trim()
    if (cleanQuery.isEmpty()) {
      return Result.failure(IllegalArgumentException("Search query cannot be empty."))
    }

    val cacheKey = "${cleanQuery.lowercase()}|${request.topic.name}|p${request.page}|c${request.includePageContent}"
    val now = System.currentTimeMillis()
    cache[cacheKey]?.let { entry ->
      if (now - entry.cachedAtMs <= cacheTtlMs) {
        return Result.success(entry.response)
      }
    }

    val orderedProviders =
      listOf(proxySearchProvider, externalSearchApiProvider, geminiGroundingSearchProvider)
        .filter { it.isAvailable(settings) }

    if (orderedProviders.isEmpty()) {
      return Result.failure(
        NoSearchProviderConfiguredException(
          "No live search provider configured. Add a Gemini API key (for Google Search Grounding), an External Search endpoint, or an HTTPS Backend Proxy in Settings."
        )
      )
    }

    var lastError: Throwable? = null
    for (provider in orderedProviders) {
      val result = provider.search(request.copy(query = cleanQuery), settings)
      if (result.isSuccess) {
        val raw = result.getOrThrow()
        val deduped = deduplicateAndRank(raw.results)
        val enriched =
          if (request.includePageContent) {
            deduped.take(4).map { item ->
              val excerpt = pageRetriever.fetchPageExcerpt(item.url)
              if (excerpt != null) item.copy(pageExcerpt = excerpt) else item
            } + deduped.drop(4)
          } else {
            deduped
          }
        val finalResp = raw.copy(results = enriched)
        cache[cacheKey] = CacheEntry(finalResp, System.currentTimeMillis())
        return Result.success(finalResp)
      } else {
        lastError = result.exceptionOrNull()
      }
    }

    return Result.failure(
      lastError ?: IllegalStateException("Live search failed across all configured providers.")
    )
  }

  override suspend fun searchMultipleQueries(
    queries: List<String>,
    topic: SearchTopic,
    settings: AppSettings,
  ): Result<List<SearchResultItem>> {
    val aggregated = mutableListOf<SearchResultItem>()
    var firstFailure: Throwable? = null
    for (q in queries.distinct()) {
      val r = search(SearchQueryRequest(query = q, topic = topic, pageSize = 6), settings)
      if (r.isSuccess) {
        aggregated += r.getOrThrow().results
      } else if (firstFailure == null) {
        firstFailure = r.exceptionOrNull()
      }
    }
    val deduped = deduplicateAndRank(aggregated)
    return if (deduped.isEmpty() && firstFailure != null) {
      Result.failure(firstFailure)
    } else {
      Result.success(deduped)
    }
  }

  override fun clearCache() {
    cache.clear()
  }

  private fun deduplicateAndRank(items: List<SearchResultItem>): List<SearchResultItem> {
    val seenUrls = HashSet<String>()
    val seenTitles = HashSet<String>()
    return items
      .filter { item ->
        val normalizedUrl = item.url.trim().trimEnd('/').lowercase()
        val normalizedTitle = item.title.trim().lowercase()
        val uniqueUrl = normalizedUrl.isNotBlank() && seenUrls.add(normalizedUrl)
        val uniqueTitle = normalizedTitle.isBlank() || seenTitles.add(normalizedTitle)
        uniqueUrl && uniqueTitle
      }
      .sortedByDescending { it.relevanceScore }
  }
}
