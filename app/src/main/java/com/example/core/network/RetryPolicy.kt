package com.example.core.network

import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.delay

class AiProviderException(
  override val message: String,
  val httpStatusCode: Int? = null,
  val isRetryable: Boolean = false,
  cause: Throwable? = null,
) : Exception(message, cause)

object RetryPolicy {
  private val retryableStatusCodes = setOf(408, 429, 500, 502, 503, 504)

  fun isRetryableStatus(code: Int): Boolean = code in retryableStatusCodes

  fun mapHttpError(code: Int, rawBody: String? = null): AiProviderException {
    val detail = rawBody?.take(220)?.takeIf { it.isNotBlank() }
    val msg =
      when (code) {
        400 -> "Invalid request payload${detail?.let { ": $it" } ?: "."}"
        401, 403 -> "Authentication failed (HTTP $code). Verify your API key or proxy credentials in Settings."
        404 -> "Model or endpoint not found (HTTP 404)."
        429 -> "Rate limit or quota exceeded (HTTP 429). Retrying or switch models."
        in 500..599 -> "AI provider service temporarily unavailable (HTTP $code)."
        else -> "Request failed with HTTP $code${detail?.let { ": $it" } ?: "."}"
      }
    return AiProviderException(
      message = msg,
      httpStatusCode = code,
      isRetryable = isRetryableStatus(code),
    )
  }

  suspend fun <T> withExponentialBackoff(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 800L,
    maxDelayMs: Long = 6_000L,
    block: suspend (attempt: Int) -> T,
  ): T {
    var currentDelay = initialDelayMs
    var lastError: Throwable? = null

    for (attempt in 1..maxAttempts) {
      try {
        return block(attempt)
      } catch (e: CancellationException) {
        throw e
      } catch (e: AiProviderException) {
        lastError = e
        if (!e.isRetryable || attempt == maxAttempts) throw e
      } catch (e: SocketTimeoutException) {
        lastError =
          AiProviderException(
            message = "Request timed out. Check network connectivity and retry.",
            isRetryable = true,
            cause = e,
          )
        if (attempt == maxAttempts) throw lastError
      } catch (e: IOException) {
        lastError =
          AiProviderException(
            message = "Network connection error: ${e.localizedMessage ?: "unreachable"}",
            isRetryable = true,
            cause = e,
          )
        if (attempt == maxAttempts) throw lastError
      }

      val jitter = Random.nextLong(100L, 350L)
      delay((currentDelay + jitter).coerceAtMost(maxDelayMs))
      currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
    }

    throw (lastError ?: AiProviderException("Unknown network failure"))
  }
}
