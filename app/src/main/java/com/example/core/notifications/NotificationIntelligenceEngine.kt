package com.example.core.notifications

import com.example.core.security.KeystoreCryptoManager
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.NotificationSummaryRecord
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class NotificationIntelligenceEngine @Inject constructor(
  private val cryptoManager: KeystoreCryptoManager,
) {
  private val _summaries = MutableStateFlow<List<NotificationSummaryRecord>>(emptyList())
  val summaries: StateFlow<List<NotificationSummaryRecord>> = _summaries.asStateFlow()

  private val _notificationPulseTick = MutableStateFlow(0L)
  val notificationPulseTick: StateFlow<Long> = _notificationPulseTick.asStateFlow()

  private var config = NotificationIntelligenceConfig()

  fun updateConfig(newConfig: NotificationIntelligenceConfig) {
    config = newConfig
  }

  fun onNotificationPosted(
    packageName: String,
    appLabel: String,
    title: String,
    bodyText: String,
  ): NotificationSummaryRecord? {
    if (!config.enabled) return null
    if (config.allowedPackages.isNotEmpty() && packageName !in config.allowedPackages) {
      return null
    }

    val combined = "$title $bodyText".trim()
    if (combined.isBlank()) return null

    // Security policy: strictly block OTPs, 2FA verification codes, PINs, passwords, and tokens
    if (config.redactSensitiveCodes && containsOtpOrPrivateCode(combined)) {
      return null
    }

    val importance = classifyImportance(title, bodyText)
    val conciseSummary =
      bodyText.replace(Regex("\\s+"), " ").trim().take(180).ifBlank { title }

    // Never auto-send; generate manual draft suggestions only when enabled
    val draftReply =
      if (config.generateDraftRepliesForReview && looksLikeMessage(title, bodyText)) {
        "Thanks for the update — I'll review this shortly."
      } else {
        null
      }

    val record =
      NotificationSummaryRecord(
        id = "notif_${UUID.randomUUID()}",
        packageName = packageName,
        appLabel = appLabel.ifBlank { packageName.substringAfterLast('.') },
        title = title.ifBlank { appLabel },
        summary = conciseSummary,
        importanceLevel = importance,
        suggestedDraftReply = draftReply,
        receivedAtMs = System.currentTimeMillis(),
      )

    _summaries.update { current -> (listOf(record) + current).take(25) }
    _notificationPulseTick.value = System.currentTimeMillis()
    return record
  }

  fun dismissSummary(id: String) {
    _summaries.update { list -> list.filterNot { it.id == id } }
  }

  fun clearAll() {
    _summaries.value = emptyList()
  }

  fun containsOtpOrPrivateCode(text: String): Boolean {
    if (cryptoManager.containsSensitiveCredentials(text)) return true
    val otpKeywords =
      Regex(
        "\\b(otp|verification code|verify code|2fa|one[- ]time|auth code|security code|pin is|do not share)\\b",
        RegexOption.IGNORE_CASE,
      )
    val digitsCode = Regex("\\b\\d{4,8}\\b")
    return otpKeywords.containsMatchIn(text) && digitsCode.containsMatchIn(text)
  }

  private fun classifyImportance(title: String, body: String): String {
    val text = "$title $body".lowercase()
    return when {
      text.containsAny("urgent", "missed call", "deadline", "alert", "emergency") -> "High"
      body.contains("?") ||
        text.containsAny("message", "reply", "meeting", "calendar", "reminder", "can you", "please") ->
        "Normal"
      else -> "Low"
    }
  }

  private fun looksLikeMessage(title: String, body: String): Boolean {
    val lower = "$title $body".lowercase()
    return body.contains("?") || lower.containsAny("can you", "please", "let me know", "when", "hey")
  }

  private fun String.containsAny(vararg tokens: String): Boolean = tokens.any { contains(it) }
}
