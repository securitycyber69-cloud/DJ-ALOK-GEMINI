package com.example.core.security

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SecurityValidator @Inject constructor() {

  fun validateHttpsUrl(rawUrl: String): Result<String> {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) {
      return Result.failure(IllegalArgumentException("Endpoint URL cannot be empty."))
    }
    return runCatching {
      val uri = URI(trimmed)
      val scheme = uri.scheme?.lowercase()
      val host = uri.host
      require(scheme == "https") {
        "Only HTTPS endpoints are permitted for security."
      }
      require(!host.isNullOrBlank()) {
        "Endpoint URL must include a valid host."
      }
      if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
  }

  fun validateHttpsResourceUrl(rawUrl: String): Boolean {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return false
    return runCatching {
      val uri = URI(trimmed)
      uri.scheme?.equals("https", ignoreCase = true) == true && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
  }

  fun sanitizeUserText(raw: String, maxChars: Int = 32_000): String {
    return raw.replace("\u0000", "").trim().take(maxChars)
  }

  fun validateApiKeyFormat(key: String): Boolean {
    val trimmed = key.trim()
    return trimmed.length >= 16 && !trimmed.contains(" ") && !trimmed.contains("\n")
  }

  fun validateAttachmentSize(byteSize: Long, maxBytes: Long, label: String): Result<Unit> {
    if (byteSize <= 0L) {
      return Result.failure(IllegalArgumentException("$label appears empty (0 bytes)."))
    }
    if (byteSize > maxBytes) {
      val maxMb = (maxBytes / (1024.0 * 1024.0))
      return Result.failure(
        IllegalArgumentException(
          "$label exceeds maximum allowed size of ${"%.1f".format(maxMb)} MB."
        )
      )
    }
    return Result.success(Unit)
  }

  fun validateMimeType(mimeType: String?, allowedTypes: Set<String>, fallbackExt: String = ""): Boolean {
    val normalized = mimeType?.lowercase()?.trim().orEmpty()
    if (allowedTypes.any { allowed ->
        if (allowed.endsWith("/*")) {
          normalized.startsWith(allowed.removeSuffix("*"))
        } else {
          normalized == allowed
        }
      }
    ) {
      return true
    }
    val ext = fallbackExt.lowercase().trimStart('.')
    return ext.isNotBlank() && allowedTypes.any { it.endsWith("/$ext") || it == ext }
  }

  fun sanitizeFilename(name: String): String {
    return name.replace(Regex("[^A-Za-z0-9._\\- ]"), "_").take(120).ifBlank { "attachment" }
  }

  fun redactForDiagnostic(secret: String): String {
    val trimmed = secret.trim()
    if (trimmed.length <= 8) return "****"
    return "${trimmed.take(4)}****${trimmed.takeLast(2)}"
  }
}

@Singleton
class ClientRateLimiter @Inject constructor() {
  private val mutex = Mutex()
  private val timestampsMs = ArrayDeque<Long>()

  suspend fun checkAndRecord(
    maxRequests: Int = 25,
    windowMs: Long = 60_000L,
    nowMs: Long = System.currentTimeMillis(),
  ): Result<Unit> =
    mutex.withLock {
      while (timestampsMs.isNotEmpty() && nowMs - timestampsMs.first() > windowMs) {
        timestampsMs.removeFirst()
      }
      if (timestampsMs.size >= maxRequests) {
        val waitSec = ((windowMs - (nowMs - timestampsMs.first())) / 1000L).coerceAtLeast(1L)
        return Result.failure(
          IllegalStateException("Client rate limit reached. Please wait ${waitSec}s before sending again.")
        )
      }
      timestampsMs.addLast(nowMs)
      Result.success(Unit)
    }
}
