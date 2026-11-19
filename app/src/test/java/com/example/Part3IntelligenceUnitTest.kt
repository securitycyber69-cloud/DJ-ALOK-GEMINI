package com.example

import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiProvider
import com.example.ai.provider.AiStreamEvent
import com.example.analytics.charts.ChartDataBuilder
import com.example.analytics.cleaner.DataCleaner
import com.example.analytics.parser.TabularDataParser
import com.example.analytics.statistics.BackendAnalyticsClient
import com.example.analytics.statistics.StatisticalAnalyzer
import com.example.core.security.SecurityValidator
import com.example.domain.model.AppSettings
import com.example.domain.model.ChartType
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DeepResearchProgress
import com.example.domain.model.IntelligenceIntent
import com.example.domain.model.MediaAttachmentKind
import com.example.domain.model.ParsedTable
import com.example.domain.model.PendingMediaItem
import com.example.domain.model.ProviderType
import com.example.domain.model.SearchResponse
import com.example.domain.model.SearchResultItem
import com.example.domain.model.SourceVerificationStatus
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.router.DefaultModelRouter
import com.example.domain.router.RealtimeIntelligenceRouter
import com.example.multimodal.audio.AudioProcessor
import com.example.research.deepresearch.DeepResearchEngine
import com.example.research.search.BackendProxySearchProvider
import com.example.research.search.CompositeSearchService
import com.example.research.search.ExternalSearchApiProvider
import com.example.research.search.GeminiGroundingSearchProvider
import com.example.research.search.HttpPageRetriever
import com.example.research.search.NoSearchProviderConfiguredException
import com.example.research.search.SearchQueryRequest
import com.example.research.search.SearchService
import com.example.research.search.SearchTopic
import com.example.research.verification.SourceVerifier
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

class Part3IntelligenceUnitTest {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }
  private val securityValidator = SecurityValidator()
  private val dataCleaner = DataCleaner()
  private val chartDataBuilder = ChartDataBuilder(dataCleaner)
  private val sourceVerifier = SourceVerifier(securityValidator)

  @Test
  fun `model catalog exposes exactly four verified selectable models without image generation`() {
    val models = VerifiedModelCatalog.fourSelectableModels
    assertEquals(4, models.size)
    assertEquals(
      listOf("Gemini 4 Hydrogen", "Gemini Pro", "Gemini Flash", "Groq Reasoning"),
      models.map { it.displayName },
    )
    assertFalse(models.any { it.displayName.contains("Image", ignoreCase = true) })
  }

  @Test
  fun `model router routes Gemini 4 Hydrogen default and custom backing IDs with failover`() {
    val router = DefaultModelRouter()
    val settings =
      AppSettings(
        geminiApiKey = "AIzaTestKey1234567890",
        groqApiKey = "gsk_TestKey1234567890",
        hydrogenBackingModelId = "gemini-2.5-flash-preview-05-20",
        groqReasoningModelId = "qwen/qwen3.8-27b",
      )

    val hydrogenRoute =
      router.route(
        prompt = "Explain distributed consensus algorithms",
        hasImages = false,
        imageCount = 0,
        requestedModelId = VerifiedModelCatalog.HYDROGEN_SLOT_ID,
        settings = settings,
      ).getOrThrow()
    assertEquals("Gemini 4 Hydrogen", hydrogenRoute.selectedModel.displayName)
    assertEquals("gemini-2.5-flash-preview-05-20", hydrogenRoute.selectedModel.id)
    assertEquals(ProviderType.GEMINI, hydrogenRoute.selectedModel.provider)

    val groqRoute =
      router.route(
        prompt = "Solve this dynamic programming recurrence",
        hasImages = false,
        imageCount = 0,
        requestedModelId = VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID,
        settings = settings,
      ).getOrThrow()
    assertEquals("Groq Reasoning", groqRoute.selectedModel.displayName)
    assertEquals(ProviderType.GROQ, groqRoute.selectedModel.provider)

    // Graceful failover when Groq key is missing but user picked Groq Reasoning
    val geminiOnlySettings = AppSettings(geminiApiKey = "AIzaTestKey1234567890", groqApiKey = "")
    val failover =
      router.route(
        prompt = "Write a unit test",
        hasImages = false,
        imageCount = 0,
        requestedModelId = VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID,
        settings = geminiOnlySettings,
      ).getOrThrow()
    assertEquals(ProviderType.GEMINI, failover.selectedModel.provider)
  }

  @Test
  fun `realtime intelligence router classifies all 10 intents accurately`() {
    val stubParser = createStubTabularDataParser()
    val router = RealtimeIntelligenceRouter(stubParser)

    assertEquals(
      IntelligenceIntent.DIRECT_CHAT,
      router.classifyIntent("Hello!", hasImages = false, attachments = emptyList()),
    )
    assertEquals(
      IntelligenceIntent.NORMAL_AI,
      router.classifyIntent("Explain how Kotlin coroutines dispatch continuations under the hood.", hasImages = false, attachments = emptyList()),
    )
    assertEquals(
      IntelligenceIntent.LIVE_SEARCH,
      router.classifyIntent("What is today's latest news on Android 16 release date?", hasImages = false, attachments = emptyList()),
    )
    assertEquals(
      IntelligenceIntent.DEEP_RESEARCH,
      router.classifyIntent("Do a deep research report on solid state battery commercialization", hasImages = false, attachments = emptyList()),
    )
    assertEquals(
      IntelligenceIntent.PHOTO_INTELLIGENCE,
      router.classifyIntent("Extract text from this receipt", hasImages = true, attachments = emptyList()),
    )
    assertEquals(
      IntelligenceIntent.VIDEO_INTELLIGENCE,
      router.classifyIntent(
        "Summarize what happens at 00:35",
        hasImages = false,
        attachments = listOf(PendingMediaItem("1", "content://vid", "clip.mp4", "video/mp4", 1024, MediaAttachmentKind.VIDEO)),
      ),
    )
    assertEquals(
      IntelligenceIntent.AUDIO_INTELLIGENCE,
      router.classifyIntent(
        "Transcribe this meeting",
        hasImages = false,
        attachments = listOf(PendingMediaItem("2", "content://aud", "note.m4a", "audio/mp4", 1024, MediaAttachmentKind.AUDIO)),
      ),
    )
    assertEquals(
      IntelligenceIntent.DOCUMENT_INTELLIGENCE,
      router.classifyIntent(
        "Summarize key clauses",
        hasImages = false,
        attachments = listOf(PendingMediaItem("3", "content://doc", "contract.pdf", "application/pdf", 1024, MediaAttachmentKind.DOCUMENT)),
      ),
    )
    assertEquals(
      IntelligenceIntent.DATA_ANALYSIS,
      router.classifyIntent(
        "Analyze trends and duplicates",
        hasImages = false,
        attachments = listOf(PendingMediaItem("4", "content://csv", "sales.csv", "text/csv", 1024, MediaAttachmentKind.DOCUMENT)),
      ),
    )
    assertEquals(
      IntelligenceIntent.CHART_VISUALIZATION,
      router.classifyIntent(
        "Plot a bar chart of revenue:\nMonth,Revenue\nJan,100\nFeb,150",
        hasImages = false,
        attachments = emptyList(),
      ),
    )
  }

  @Test
  fun `composite search fails honestly without fabricating URLs when unconfigured`() = runTest {
    val retrofit =
      Retrofit.Builder()
        .baseUrl("https://localhost/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    val geminiApi = retrofit.create(com.example.ai.provider.api.GeminiApiService::class.java)
    val proxyApi = retrofit.create(com.example.ai.provider.api.ProxyApiService::class.java)

    val service =
      CompositeSearchService(
        proxySearchProvider = BackendProxySearchProvider(proxyApi, securityValidator),
        externalSearchApiProvider = ExternalSearchApiProvider(proxyApi, securityValidator),
        geminiGroundingSearchProvider = GeminiGroundingSearchProvider(geminiApi, securityValidator),
        pageRetriever = HttpPageRetriever(OkHttpClient(), securityValidator),
      )

    val result =
      service.search(
        SearchQueryRequest(query = "latest Kotlin release", topic = SearchTopic.TECHNOLOGY),
        AppSettings(geminiApiKey = "", externalSearchUrl = "", proxyBaseUrl = ""),
      )

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is NoSearchProviderConfiguredException)
  }

  @Test
  fun `source verifier enforces HTTPS, deduplicates canonical URLs, and flags numeric conflicts`() {
    val now = 1_750_000_000_000L
    val raw =
      listOf(
        SearchResultItem(
          id = "1",
          title = "Report A",
          url = "https://www.example.com/report/",
          publisher = "Publisher A",
          snippet = "Market adoption reached 42% in 2025 according to industry audit.",
          retrievedTimestampMs = now,
          verificationStatus = SourceVerificationStatus.VERIFIED_HTTPS,
          sourceDomain = "example.com",
        ),
        SearchResultItem(
          id = "2",
          title = "Report A Duplicate",
          url = "https://example.com/report",
          publisher = "Publisher A Mirror",
          snippet = "Different snippet but identical canonical URL path.",
          retrievedTimestampMs = now,
          verificationStatus = SourceVerificationStatus.VERIFIED_HTTPS,
          sourceDomain = "example.com",
        ),
        SearchResultItem(
          id = "3",
          title = "Insecure Http Link",
          url = "http://insecure.example.org/doc",
          publisher = "Insecure Org",
          snippet = "Should be rejected by HTTPS validator.",
          retrievedTimestampMs = now,
          verificationStatus = SourceVerificationStatus.VERIFIED_HTTPS,
          sourceDomain = "insecure.example.org",
        ),
        SearchResultItem(
          id = "4",
          title = "Report B",
          url = "https://research.org/findings",
          publisher = "Publisher B",
          snippet = "Survey indicates adoption reached 68% in 2026 globally.",
          retrievedTimestampMs = now,
          verificationStatus = SourceVerificationStatus.VERIFIED_HTTPS,
          sourceDomain = "research.org",
        ),
      )

    val verified = sourceVerifier.verifyAndCompare(raw)
    assertEquals(2, verified.uniqueVerifiedSources.size)
    assertEquals(1, verified.duplicatesRemoved)
    assertEquals(1, verified.rejectedCount)
    assertTrue(verified.conflictingClaims.isNotEmpty())
  }

  @Test
  fun `deep research engine executes full 10-section pipeline, handles failures, and supports cancellation`() = runTest {
    val now = 1_750_000_000_000L
    val fakeSearch =
      object : SearchService {
        override suspend fun search(request: SearchQueryRequest, settings: AppSettings): Result<SearchResponse> {
          return Result.success(
            SearchResponse(
              query = request.query,
              results =
                listOf(
                  SearchResultItem(
                    id = "s1_${request.query.hashCode()}",
                    title = "Verified Benchmark Study",
                    url = "https://benchmark.example.com/study-${request.query.length}",
                    publisher = "Benchmark Lab",
                    snippet = "Measured 35% latency reduction in 2025.",
                    retrievedTimestampMs = now,
                    verificationStatus = SourceVerificationStatus.VERIFIED_HTTPS,
                    sourceDomain = "benchmark.example.com",
                  ),
                  SearchResultItem(
                    id = "s2_${request.query.hashCode()}",
                    title = "Independent Review",
                    url = "https://review.example.org/article-${request.query.length}",
                    publisher = "Tech Review",
                    snippet = "Independent test saw 48% latency improvement in 2026.",
                    retrievedTimestampMs = now,
                    verificationStatus = SourceVerificationStatus.VERIFIED_HTTPS,
                    sourceDomain = "review.example.org",
                  ),
                ),
              page = request.page,
              hasMorePages = false,
              providerUsed = "Fake Search",
              retrievedAtMs = now,
            )
          )
        }

        override suspend fun searchMultipleQueries(
          queries: List<String>,
          topic: SearchTopic,
          settings: AppSettings,
        ): Result<List<SearchResultItem>> = Result.success(emptyList())

        override fun clearCache() = Unit
      }

    val fakeGemini =
      object : AiProvider {
        override val providerType: ProviderType = ProviderType.GEMINI

        override fun streamChat(request: AiChatRequest): Flow<AiStreamEvent> = flow {
          emit(
            AiStreamEvent.Delta(
              """{"executiveSummary":"Verified executive synthesis.","objectives":["Verify benchmarks"],"methodology":"Multi-query HTTPS verification.","keyFindings":["35-48% latency reduction reported."],"detailedAnalysis":"Cross-checked study data.","limitations":["Public excerpts only."],"conclusion":"Consistent speedup observed."}"""
            )
          )
          emit(AiStreamEvent.Completed)
        }
      }

    val engine =
      DeepResearchEngine(
        searchService = fakeSearch,
        sourceVerifier = sourceVerifier,
        providers = mapOf(ProviderType.GEMINI to fakeGemini),
        json = json,
      )

    val events =
      engine.executeResearch(
        question = "Compare edge inference latency benchmarks in detail",
        model = VerifiedModelCatalog.createHydrogenSpec(),
        settings = AppSettings(geminiApiKey = "AIzaTestKey1234567890"),
      ).toList()

    assertTrue(events.first() is DeepResearchProgress.UnderstandingQuestion)
    assertTrue(events.any { it is DeepResearchProgress.PlanningQueries })
    assertTrue(events.any { it is DeepResearchProgress.SearchingWeb })
    assertTrue(events.any { it is DeepResearchProgress.DeduplicatingAndVerifying })
    val completed = events.filterIsInstance<DeepResearchProgress.Completed>().single()
    val md = completed.report.toMarkdownExport()
    assertTrue(md.contains("## Executive Summary"))
    assertTrue(md.contains("## Conflicting Information"))
    assertTrue(md.contains("## References"))
    assertTrue(md.contains("https://benchmark.example.com"))

    // Test failure handling on offline / IOException
    val failingSearch =
      object : SearchService {
        override suspend fun search(request: SearchQueryRequest, settings: AppSettings): Result<SearchResponse> =
          Result.failure(IOException("Network unreachable"))

        override suspend fun searchMultipleQueries(
          queries: List<String>,
          topic: SearchTopic,
          settings: AppSettings,
        ): Result<List<SearchResultItem>> = Result.failure(IOException("Network unreachable"))

        override fun clearCache() = Unit
      }
    val failEngine = DeepResearchEngine(failingSearch, sourceVerifier, mapOf(ProviderType.GEMINI to fakeGemini), json)
    val failEvents =
      failEngine.executeResearch(
        question = "Offline research check",
        model = VerifiedModelCatalog.createHydrogenSpec(),
        settings = AppSettings(geminiApiKey = "AIzaTestKey1234567890"),
      ).toList()
    assertTrue(failEvents.any { it is DeepResearchProgress.Failed })

    // Test cancellation mid-stream propagates cleanly
    var cancelledObserved = false
    try {
      engine.executeResearch(
        question = "Cancel midway",
        model = VerifiedModelCatalog.createHydrogenSpec(),
        settings = AppSettings(geminiApiKey = "AIzaTestKey1234567890"),
      ).take(2).toList()
    } catch (_: CancellationException) {
      cancelledObserved = true
    }
    // take(2) cancels downstream flow cleanly without throwing uncaught exceptions
    assertFalse(cancelledObserved)
  }

  @Test
  fun `data cleaner, statistical analyzer, and chart builder compute exact math and all 7 chart types`() = runTest {
    val rawTable =
      ParsedTable(
        name = "Quarterly Metrics",
        headers = listOf("Quarter", "Revenue", "Users"),
        rows =
          listOf(
            listOf("Q1", "$1,000", "100"),
            listOf("Q2", "$2,000", "200"),
            listOf("Q2", "$2,000", "200"), // duplicate row
            listOf("Q3", "N/A", "300"),
            listOf("Q4", "$4,000", "400"),
          ),
      )

    val cleaned = dataCleaner.clean(rawTable)
    assertEquals(5, cleaned.summary.originalRowCount)
    assertEquals(4, cleaned.summary.cleanedRowCount)
    assertEquals(1, cleaned.summary.duplicateRowsRemoved)
    assertEquals(1, cleaned.summary.missingValuesByColumn["Revenue"])

    val retrofit =
      Retrofit.Builder()
        .baseUrl("https://localhost/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    val analyzer =
      StatisticalAnalyzer(
        dataCleaner = dataCleaner,
        backendAnalyticsClient =
          BackendAnalyticsClient(
            proxyApiService = retrofit.create(com.example.ai.provider.api.ProxyApiService::class.java),
            securityValidator = securityValidator,
          ),
      )

    val result = analyzer.analyze(rawTable, AppSettings())
    val userStats = result.numericStatistics.first { it.columnName == "Users" }
    assertEquals(4, userStats.count)
    assertEquals(1000.0, userStats.sum, 1e-6)
    assertEquals(250.0, userStats.mean, 1e-6)
    assertEquals(250.0, userStats.median, 1e-6)
    assertEquals(100.0, userStats.min, 1e-6)
    assertEquals(400.0, userStats.max, 1e-6)

    // Correlation between Revenue (1000,2000,4000) and Users (100,200,400) should be r = 1.000
    assertTrue(result.correlationNotes.any { it.contains("1.000") })

    // Verify all 7 ChartTypes build accurately from cleaned data
    val promptsByType =
      mapOf(
        ChartType.BAR to "Draw a bar chart",
        ChartType.LINE to "Show a line chart",
        ChartType.PIE to "Make a pie chart",
        ChartType.AREA to "Build an area chart",
        ChartType.SCATTER to "Plot a scatter correlation",
        ChartType.COMPARISON to "Create a comparison chart",
        ChartType.TIME_SERIES to "Show a time series chart",
      )
    promptsByType.forEach { (expectedType, prompt) ->
      val chart =
        chartDataBuilder
          .buildChartFromVerifiedTable(cleaned.cleanedTable, prompt, "Verified Test CSV")
          .getOrThrow()
      assertEquals(expectedType, chart.chartType)
      assertTrue(chart.series.first().points.isNotEmpty())
    }

    // Empty / non-numeric table must fail rather than fabricate values
    val textOnlyTable =
      ParsedTable("Labels Only", listOf("Name", "Category"), listOf(listOf("Alpha", "A")))
    assertTrue(
      chartDataBuilder.buildChartFromVerifiedTable(textOnlyTable, "bar chart", "Test").isFailure
    )
  }

  @Test
  fun `audio validation and tabular inline parser enforce limits and RFC rules`() {
    assertTrue(
      securityValidator.validateMimeType("audio/mpeg", AudioProcessor.ALLOWED_AUDIO_MIMES, "mp3")
    )
    assertFalse(
      securityValidator.validateMimeType("application/x-msdownload", AudioProcessor.ALLOWED_AUDIO_MIMES, "exe")
    )
    assertTrue(
      securityValidator.validateAttachmentSize(1024L, AudioProcessor.MAX_AUDIO_BYTES, "clip.mp3").isSuccess
    )
    assertTrue(
      securityValidator.validateAttachmentSize(30L * 1024L * 1024L, AudioProcessor.MAX_AUDIO_BYTES, "huge.mp3").isFailure
    )

    val parser = createStubTabularDataParser()
    val mdTable =
      parser.parseInlineTabularText(
        "Inline",
        """
        | City | Temp |
        | --- | --- |
        | Dhaka | 32 |
        | Delhi | 36 |
        """.trimIndent(),
      )
    assertNotNull(mdTable)
    assertEquals(2, mdTable!!.rows.size)
    assertEquals("32", mdTable.rows[0][1])

    val jsonTable =
      parser.parseInlineTabularText(
        "JSON",
        """[{"item":"CPU","score":95},{"item":"GPU","score":99}]""",
      )
    assertNotNull(jsonTable)
    assertEquals(listOf("item", "score"), jsonTable!!.headers)
  }

  @Test
  fun `pdf text extractor handles uncompressed and FlateDecode streams with page markers`() {
    val rawPdf =
      """
      %PDF-1.4
      1 0 obj << /Length 44 >> stream
      BT /F1 12 Tf 72 720 Td (Revenue grew 24% YoY) Tj ET
      endstream
      endobj
      """.trimIndent().toByteArray(Charsets.ISO_8859_1)

    val extracted = com.example.multimodal.document.DocumentProcessor.extractTextFromPdfBytes(rawPdf)
    assertTrue(extracted.contains("[Page 1]"))
    assertTrue(extracted.contains("Revenue grew 24% YoY"))

    // Also test zlib FlateDecode compressed stream
    val innerStream = "BT /F1 12 Tf (Compressed Table Total: 990) Tj ET".toByteArray(Charsets.ISO_8859_1)
    val deflater = java.util.zip.Deflater()
    deflater.setInput(innerStream)
    deflater.finish()
    val buf = ByteArray(1024)
    val compressedLen = deflater.deflate(buf)
    deflater.end()
    val compressedBytes = buf.copyOf(compressedLen)
    val flatePdf =
      "stream\n".toByteArray(Charsets.ISO_8859_1) +
        compressedBytes +
        "\nendstream".toByteArray(Charsets.ISO_8859_1)

    val extractedFlate = com.example.multimodal.document.DocumentProcessor.extractTextFromPdfBytes(flatePdf)
    assertTrue(extractedFlate.contains("Compressed Table Total: 990"))
  }

  private fun createStubTabularDataParser(): TabularDataParser = TabularDataParser(json = json)
}
