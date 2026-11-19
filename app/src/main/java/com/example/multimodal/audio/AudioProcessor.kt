package com.example.multimodal.audio

import android.media.MediaMetadataRetriever
import android.util.Base64
import com.example.core.security.SecurityValidator
import com.example.core.storage.TempMediaCacheManager
import com.example.domain.model.AudioMetadataInfo
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PreparedAudioFile(
  val tempFile: File,
  val metadata: AudioMetadataInfo,
)

@Singleton
class AudioProcessor @Inject constructor(
  private val tempMediaCacheManager: TempMediaCacheManager,
  private val securityValidator: SecurityValidator,
) {
  companion object {
    val ALLOWED_AUDIO_MIMES =
      setOf(
        "audio/wav",
        "audio/x-wav",
        "audio/mpeg",
        "audio/mp3",
        "audio/mp4",
        "audio/m4a",
        "audio/x-m4a",
        "audio/aac",
        "audio/ogg",
        "audio/opus",
        "audio/*",
      )
    val ALLOWED_EXTENSIONS = setOf("wav", "mp3", "m4a", "aac", "ogg", "opus")
    const val MAX_AUDIO_BYTES = 25L * 1024L * 1024L
  }

  suspend fun prepareAudioTempFile(
    uriString: String,
    onProgress: suspend (String) -> Unit = {},
  ): Result<PreparedAudioFile> =
    withContext(Dispatchers.IO) {
      runCatching {
        onProgress("Validating audio file & size limits…")
        val copied =
          tempMediaCacheManager
            .copyUriToTempFile(
              uriString = uriString,
              extensionHint = "m4a",
              maxAllowedBytes = MAX_AUDIO_BYTES,
            )
            .getOrThrow()

        coroutineContext.ensureActive()
        securityValidator
          .validateAttachmentSize(copied.byteSize, MAX_AUDIO_BYTES, copied.displayName)
          .onFailure {
            tempMediaCacheManager.cleanupFile(copied.file)
            throw it
          }

        val ext = copied.displayName.substringAfterLast('.', "").lowercase()
        val mimeValid =
          securityValidator.validateMimeType(copied.mimeType, ALLOWED_AUDIO_MIMES, ext) ||
            ext in ALLOWED_EXTENSIONS
        if (!mimeValid) {
          tempMediaCacheManager.cleanupFile(copied.file)
          error("Unsupported audio format (${copied.mimeType}). Supported: WAV, MP3, M4A, AAC, OGG.")
        }

        val normalizedMime =
          normalizeAudioMime(copied.mimeType, ext)
        val metadata = extractAudioMetadata(copied.file, copied.displayName, normalizedMime, copied.byteSize)
        PreparedAudioFile(tempFile = copied.file, metadata = metadata)
      }
    }

  suspend fun readAsBase64(file: File): String =
    withContext(Dispatchers.IO) {
      val bytes = file.readBytes()
      Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

  fun cleanup(file: File?) {
    tempMediaCacheManager.cleanupFile(file)
  }

  fun formatSeconds(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    val mins = total / 60L
    val secs = total % 60L
    return "%02d:%02d".format(mins, secs)
  }

  private fun normalizeAudioMime(rawMime: String, ext: String): String {
    val lower = rawMime.lowercase()
    return when {
      lower != "application/octet-stream" && lower.startsWith("audio/") -> lower
      ext == "wav" -> "audio/wav"
      ext == "mp3" -> "audio/mpeg"
      ext == "m4a" -> "audio/mp4"
      ext == "aac" -> "audio/aac"
      ext == "ogg" || ext == "opus" -> "audio/ogg"
      else -> "audio/mp4"
    }
  }

  private fun extractAudioMetadata(
    file: File,
    displayName: String,
    mimeType: String,
    byteSize: Long,
  ): AudioMetadataInfo {
    var durationMs = 0L
    var bitrateBps = 0L
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(file.absolutePath)
      durationMs =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
      bitrateBps =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
    } catch (_: Throwable) {
      // Metadata best-effort on malformed headers
    } finally {
      runCatching { retriever.release() }
    }
    return AudioMetadataInfo(
      displayName = displayName,
      mimeType = mimeType,
      byteSize = byteSize,
      durationMs = durationMs,
      bitrateBps = bitrateBps,
    )
  }
}
