package com.example.research.deepresearch

import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiProvider
import com.example.ai.provider.AiStreamEvent
import com.example.ai.provider.AiTurn
import com.example.domain.model.AiModelSpec
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DeepResearchProgress
import com.example.domain.model.ProviderType
import com.example.domain.model.ResearchReport
import com.example.domain.model.SearchResultItem
import com.example.research.search.SearchQueryRequest
import com.example.research.search.SearchService
import com.example.research.search.SearchTopic
import com.example.research.verification.SourceVerifier
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LlmResearchSynthesis(
  val executiveSummary: String = "",
  val objectives: List<String> = emptyList(),
  val methodology: String = "",
  val keyFindings: List<String> = emptyList(),
  val detailedAnalysis: String = "",
  val limitations: List<String> = emptyList(),
  val conclusion: String = "",
)

@Singleton
class DeepResearchEngine @Inject constructor(
  private val searchService: SearchService,
  private val sourceVerifier: SourceVerifier,
  private val providers: Map<ProviderType, @JvmSuppressWildcards AiProvider>,
  private val json: Json,
) {

  fun executeResearch(
    question: String,
    model: AiModelSpec,
    settings: AppSettings,
  ): Flow<DeepResearchProgress> = flow {
    try {
      emit(DeepResearchProgress.UnderstandingQuestion)
      val complexity = detectComplexity(question)
      emit(DeepResearchProgress.DetectingComplexity(complexity))

      val queries = buildResearchQueries(question, complexity)
      emit(DeepResearchProgress.PlanningQueries(queries))

      val topic =
        when {
          "news" in question.lowercase() || "today" in question.lowercase() || "latest" in question.lowercase() ->
            SearchTopic.NEWS
          "ai" in question.lowercase() || "kotlin" in question.lowercase() || "software" in question.lowercase() ->
            SearchTopic.TECHNOLOGY
          else -> SearchTopic.RESEARCH
        }

      val rawResults = mutableListOf<SearchResultItem>()
      var lastError: Throwable? = null
      queries.forEachIndexed { idx, q ->
        emit(
          DeepResearchProgress.SearchingWeb(
            currentQuery = q,
            queryIndex = idx + 1,
            totalQueries = queries.size,
            sourcesCollected = rawResults.size,
          )
        )
        val res =
          searchService.search(
            SearchQueryRequest(
              query = q,
              topic = topic,
              page = 1,
              pageSize = 6,
              includePageContent = idx == 0,
            ),
            settings,
          )
        if (res.isSuccess) {
          rawResults += res.getOrThrow().results
        } else {
          lastError = res.exceptionOrNull()
        }
      }

      if (rawResults.isEmpty()) {
        val msg =
          lastError?.message
            ?: "Unable to retrieve live web sources for deep research. Check your API key or Search configuration in Settings."
        emit(DeepResearchProgress.Failed(msg))
        return@flow
      }

      val verifiedBundle = sourceVerifier.verifyAndCompare(rawResults)
      val verifiedSources = verifiedBundle.uniqueVerifiedSources
      emit(
        DeepResearchProgress.DeduplicatingAndVerifying(
          rawCount = rawResults.size,
          verifiedCount = verifiedSources.size,
        )
      )

      if (verifiedSources.isEmpty()) {
        emit(
          DeepResearchProgress.Failed(
            "Retrieved links failed HTTPS source verification (${verifiedBundle.rejectedCount} rejected). Refusing to fabricate research citations."
          )
        )
        return@flow
      }

      emit(DeepResearchProgress.SynthesizingReport(verifiedSources.size))

      val synthesis = synthesizeWithModel(question, queries, verifiedSources, model, settings)
      val report =
        ResearchReport(
          query = question,
          complexity = complexity,
          executiveSummary = synthesis.executiveSummary,
          researchObjectives = synthesis.objectives.ifEmpty {
            listOf(
              "Investigate verified current facts regarding: $question",
              "Cross-check claims across ${verifiedSources.size} retrieved HTTPS sources",
            )
          },
          methodology = synthesis.methodology.ifBlank {
            "Executed ${queries.size} live search queries, removed ${verifiedBundle.duplicatesRemoved} duplicate entries, verified HTTPS links, and compared claims across ${verifiedSources.size} distinct sources."
          },
          keyFindings = synthesis.keyFindings.ifEmpty {
            verifiedSources.take(4).map { "[${it.publisher}] ${it.snippet.take(180)}" }
          },
          detailedAnalysis = synthesis.detailedAnalysis,
          sourceComparison = verifiedBundle.sourceComparisonNotes,
          conflictingInformation = verifiedBundle.conflictingClaims,
          limitations = synthesis.limitations.ifEmpty {
            listOf(
              "Findings reflect snippets and retrieved excerpts available at query time.",
              "Paywalled or script-rendered pages beyond retrieved excerpts were not fully crawled.",
            )
          },
          conclusion = synthesis.conclusion,
          references = verifiedSources,
          generatedAtMs = System.currentTimeMillis(),
        )

      emit(DeepResearchProgress.Completed(report))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      emit(DeepResearchProgress.Failed(e.localizedMessage ?: "Deep research encountered an error."))
    }
  }

  fun detectComplexity(question: String): String {
    val words = question.trim().split(Regex("\\s+")).size
    val lower = question.lowercase()
    return when {
      words > 18 || "compare" in lower || "architecture" in lower || "detailed" in lower ->
        "High (Multi-Angle Investigation)"
      words > 8 || "why" in lower || "how" in lower ->
        "Moderate (Structured Analysis)"
      else -> "Focused (Fact & Source Verification)"
    }
  }

  fun buildResearchQueries(question: String, complexity: String): List<String> {
    val base = question.trim()
    val queries = mutableListOf(base)
    if (!complexity.startsWith("Focused")) {
      queries += "$base latest data benchmarks research"
      queries += "$base limitations criticism comparison"
    } else {
      queries += "$base latest verified facts"
    }
    return queries.distinct().take(3)
  }

  private suspend fun synthesizeWithModel(
    question: String,
    queries: List<String>,
    sources: List<SearchResultItem>,
    model: AiModelSpec,
    settings: AppSettings,
  ): LlmResearchSynthesis {
    val targetProvider =
      if (settings.connectionMode == ConnectionMode.BACKEND_PROXY) ProviderType.PROXY
      else model.provider
    val provider = providers[targetProvider] ?: return fallbackSynthesis(question, sources)

    val sourceDigest =
      sources.take(10).mapIndexed { i, s ->
        "[${i + 1}] Title: ${s.title} | Publisher: ${s.publisher} | Date: ${s.publishedTimestamp ?: "unknown"} | URL: ${s.url}\nSnippet: ${s.snippet}\n${s.pageExcerpt?.let { "Excerpt: ${it.take(600)}\n" }.orEmpty()}"
      }.joinToString("\n")

    val prompt =
      """
      Synthesize a rigorous research report answering: "$question"
      Queries run: ${queries.joinToString("; ")}
      Verified retrieved sources:
      $sourceDigest

      Return ONLY valid JSON matching this schema:
      {
        "executiveSummary": "...",
        "objectives": ["..."],
        "methodology": "...",
        "keyFindings": ["..."],
        "detailedAnalysis": "...",
        "limitations": ["..."],
        "conclusion": "..."
      }
      Strictly base every claim on the retrieved sources above. Do not invent URLs or facts.
      """.trimIndent()

    val rawBuilder = StringBuilder()
    runCatching {
      provider
        .streamChat(
          AiChatRequest(
            model = model,
            systemPrompt = "You are a rigorous Research Synthesis Engine. Output valid JSON strictly grounded in provided sources.",
            turns = listOf(AiTurn(isUser = true, text = prompt)),
            settings = settings,
            temperature = 0.25,
          )
        )
        .collect { evt ->
          if (evt is AiStreamEvent.Delta) rawBuilder.append(evt.text)
        }
    }

    val rawText = rawBuilder.toString().trim()
    val jsonSlice =
      rawText
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
        .let { s ->
          val start = s.indexOf('{')
          val end = s.lastIndexOf('}')
          if (start >= 0 && end > start) s.substring(start, end + 1) else s
        }

    return runCatching { json.decodeFromString<LlmResearchSynthesis>(jsonSlice) }
      .getOrElse {
        if (rawText.length > 80) {
          LlmResearchSynthesis(
            executiveSummary = rawText.take(450),
            detailedAnalysis = rawText,
            conclusion = "Synthesized from ${sources.size} verified web sources.",
          )
        } else {
          fallbackSynthesis(question, sources)
        }
      }
  }

  private fun fallbackSynthesis(question: String, sources: List<SearchResultItem>): LlmResearchSynthesis {
    return LlmResearchSynthesis(
      executiveSummary = "Collected ${sources.size} verified sources addressing \"$question\".",
      detailedAnalysis =
        sources.take(5).joinToString("\n\n") { "**${it.title}** (${it.publisher}): ${it.snippet}" },
      conclusion = "Review the verified references below for primary source details.",
    )
  }
}
