package com.example.research.verification

import com.example.core.security.SecurityValidator
import com.example.domain.model.ConflictingClaim
import com.example.domain.model.SearchResultItem
import com.example.domain.model.SourceVerificationStatus
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class VerifiedSourceBundle(
  val uniqueVerifiedSources: List<SearchResultItem>,
  val duplicatesRemoved: Int,
  val rejectedCount: Int,
  val conflictingClaims: List<ConflictingClaim>,
  val sourceComparisonNotes: List<String>,
)

@Singleton
class SourceVerifier @Inject constructor(
  private val securityValidator: SecurityValidator,
) {
  fun verifyAndCompare(rawSources: List<SearchResultItem>): VerifiedSourceBundle {
    val seenCanonicalUrls = HashSet<String>()
    val seenSnippets = HashSet<String>()
    val kept = mutableListOf<SearchResultItem>()
    var duplicates = 0
    var rejected = 0

    for (item in rawSources) {
      val trimmedUrl = item.url.trim()
      if (!securityValidator.validateHttpsResourceUrl(trimmedUrl)) {
        rejected++
        continue
      }

      val canonical = canonicalizeUrl(trimmedUrl)
      val normSnippet = item.snippet.trim().lowercase().take(120)
      if (!seenCanonicalUrls.add(canonical)) {
        duplicates++
        continue
      }
      if (normSnippet.length > 35 && !seenSnippets.add(normSnippet)) {
        duplicates++
        continue
      }

      val verifiedStatus =
        when {
          trimmedUrl.contains("vertexaisearch.cloud.google.com/grounding-api-redirect") ->
            SourceVerificationStatus.GROUNDED_REDIRECT
          else -> SourceVerificationStatus.VERIFIED_HTTPS
        }
      kept += item.copy(url = trimmedUrl, verificationStatus = verifiedStatus)
    }

    val conflicts = detectNumericOrPolarityConflicts(kept)
    val comparisonNotes =
      kept.take(6).map { src ->
        val dateLabel = src.publishedTimestamp ?: "date unconfirmed"
        "${src.publisher} (${src.sourceDomain}, $dateLabel) — ${src.verificationStatus.label}"
      }

    return VerifiedSourceBundle(
      uniqueVerifiedSources = kept,
      duplicatesRemoved = duplicates,
      rejectedCount = rejected,
      conflictingClaims = conflicts,
      sourceComparisonNotes = comparisonNotes,
    )
  }

  private fun canonicalizeUrl(url: String): String {
    return runCatching {
      val uri = URI(url)
      val host = uri.host?.lowercase()?.removePrefix("www.").orEmpty()
      val path = uri.path?.trimEnd('/').orEmpty()
      "$host$path"
    }.getOrDefault(url.lowercase().trimEnd('/'))
  }

  private fun detectNumericOrPolarityConflicts(sources: List<SearchResultItem>): List<ConflictingClaim> {
    if (sources.size < 2) return emptyList()
    val conflicts = mutableListOf<ConflictingClaim>()
    val yearRegex = Regex("\\b(19|20)\\d{2}\\b")
    val percentRegex = Regex("\\b\\d{1,3}(?:\\.\\d+)?%")

    val first = sources.first()
    val firstYears = yearRegex.findAll(first.snippet).map { it.value }.toSet()
    val firstPercents = percentRegex.findAll(first.snippet).map { it.value }.toSet()

    for (other in sources.drop(1).take(5)) {
      val otherYears = yearRegex.findAll(other.snippet).map { it.value }.toSet()
      if (firstYears.isNotEmpty() && otherYears.isNotEmpty() && firstYears.intersect(otherYears).isEmpty()) {
        conflicts +=
          ConflictingClaim(
            topic = "Reported Timeline / Year",
            claimA = "References year(s) ${firstYears.joinToString()}: ${first.snippet.take(120)}",
            sourceA = first.publisher,
            claimB = "References year(s) ${otherYears.joinToString()}: ${other.snippet.take(120)}",
            sourceB = other.publisher,
          )
      }
      val otherPercents = percentRegex.findAll(other.snippet).map { it.value }.toSet()
      if (firstPercents.isNotEmpty() && otherPercents.isNotEmpty() && firstPercents.intersect(otherPercents).isEmpty()) {
        conflicts +=
          ConflictingClaim(
            topic = "Quantitative Metric Variance",
            claimA = "Cites ${firstPercents.joinToString()}: ${first.snippet.take(110)}",
            sourceA = first.publisher,
            claimB = "Cites ${otherPercents.joinToString()}: ${other.snippet.take(110)}",
            sourceB = other.publisher,
          )
      }
    }
    return conflicts.distinctBy { it.topic + it.sourceA + it.sourceB }.take(3)
  }
}
