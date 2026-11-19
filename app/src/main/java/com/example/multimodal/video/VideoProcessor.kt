package com.example.multimodal.video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.example.core.image.ImageProcessor
import com.example.core.security.SecurityValidator
import com.example.core.storage.TempMediaCacheManager
import com.example.domain.model.ProcessedImagePayload
import com.example.domain.model.VideoMetadataInfo
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PreparedVideoAnalysisInput(
  val metadata: VideoMetadataInfo,
  val sampledFrames: List<SampledVideoFrame>,
  val chunkSummaries: List<String>,
  val analysisMethodDisclosure: String,
)

data class SampledVideoFrame(
  val timestampMs: Long,
  val formattedTimestamp: String,
  val imagePayload: ProcessedImagePayload,
)

@Singleton
class VideoProcessor @Inject constructor(
  private val tempMediaCacheManager: TempMediaCacheManager,
  private val imageProcessor: ImageProcessor,
  private val securityValidator: SecurityValidator,
) {
  companion object {
    val ALLOWED_VIDEO_MIMES =
      setOf(
        "video/mp4",
        "video/quicktime",
        "video/webm",
        "video/3gpp",
        "video/x-matroska",
        "video/*",
      )
    const val MAX_VIDEO_BYTES = 150L * 1024L * 1024L
    const val DEFAULT_MAX_FRAMES = 8
  }

  suspend fun processVideoUri(
    uriString: String,
    requestedTimestampsMs: List<Long> = emptyList(),
    maxFrames: Int = DEFAULT_MAX_FRAMES,
    onProgress: suspend (String) -> Unit = {},
  ): Result<PreparedVideoAnalysisInput> =
    withContext(Dispatchers.IO) {
      var tempFile: File? = null
      try {
        onProgress("Copying & validating video stream…")
        val copied =
          tempMediaCacheManager
            .copyUriToTempFile(
              uriString = uriString,
              extensionHint = "mp4",
              maxAllowedBytes = MAX_VIDEO_BYTES,
            )
            .getOrThrow()
        tempFile = copied.file

        securityValidator
          .validateAttachmentSize(copied.byteSize, MAX_VIDEO_BYTES, copied.displayName)
          .getOrThrow()

        val ext = copied.displayName.substringAfterLast('.', "").lowercase()
        val mimeValid =
          securityValidator.validateMimeType(copied.mimeType, ALLOWED_VIDEO_MIMES, ext) ||
            ext in setOf("mp4", "mov", "webm", "mkv", "3gp")
        require(mimeValid) {
          "Unsupported video format (${copied.mimeType}). Supported: MP4, MOV, WEBM."
        }

        coroutineContext.ensureActive()
        onProgress("Extracting video & audio track metadata…")
        val metadata = extractMetadata(copied.file, copied.displayName, copied.mimeType, copied.byteSize)

        val targetTimestamps = computeSampleTimestamps(metadata.durationMs, requestedTimestampsMs, maxFrames)
        val sampledFrames = mutableListOf<SampledVideoFrame>()

        val retriever = MediaMetadataRetriever()
        try {
          retriever.setDataSource(copied.file.absolutePath)
          targetTimestamps.forEachIndexed { idx, tsMs ->
            coroutineContext.ensureActive()
            onProgress("Extracting representative frame ${idx + 1}/${targetTimestamps.size} (${formatTimestamp(tsMs)})…")
            val bitmap =
              retriever.getFrameAtTime(
                tsMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
              )
            if (bitmap != null) {
              val formatted = formatTimestamp(tsMs)
              val payload =
                imageProcessor.compressBitmapPublic(
                  uriString = "$uriString#t=$formatted",
                  bitmap = bitmap,
                  maxDimensionPx = 896,
                  maxBytes = 280_000,
                  note = "Video frame at $formatted",
                )
              sampledFrames += SampledVideoFrame(tsMs, formatted, payload)
            }
          }
        } finally {
          runCatching { retriever.release() }
        }

        require(sampledFrames.isNotEmpty()) {
          "Could not decode representative frames from video '${copied.displayName}'."
        }

        val chunkWindowSec = if (metadata.durationMs > 120_000L) 30 else 15
        val chunkCount = ((metadata.durationMs / 1000L) / chunkWindowSec).toInt().coerceAtLeast(1)
        val chunkNotes =
          (0 until chunkCount.coerceAtMost(8)).map { c ->
            val start = formatTimestamp(c * chunkWindowSec * 1000L)
            val end = formatTimestamp(minOf((c + 1) * chunkWindowSec * 1000L, metadata.durationMs))
            "Segment ${c + 1}: $start–$end"
          }

        val sampledLabel = sampledFrames.joinToString(", ") { it.formattedTimestamp }
        val disclosure =
          "Frame-and-Metadata Fallback Method: Sampled ${sampledFrames.size} key frame(s) at [$sampledLabel] " +
            "from ${formatTimestamp(metadata.durationMs)} total duration (${metadata.width}x${metadata.height}). " +
            "Audio track ${if (metadata.hasAudioTrack) "present (${metadata.audioMimeType ?: "audio"})" else "not detected"}. " +
            "Analysis reflects these sampled frames rather than every individual raw frame."

        Result.success(
          PreparedVideoAnalysisInput(
            metadata = metadata,
            sampledFrames = sampledFrames,
            chunkSummaries = chunkNotes,
            analysisMethodDisclosure = disclosure,
          )
        )
      } catch (e: Throwable) {
        Result.failure(e)
      } finally {
        tempMediaCacheManager.cleanupFile(tempFile)
      }
    }

  fun parseRequestedTimestampsMs(prompt: String): List<Long> {
    val regex = Regex("\\b(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})\\b")
    return regex.findAll(prompt).mapNotNull { match ->
      val g1 = match.groupValues[1]
      val g2 = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
      val g3 = match.groupValues[3].toLongOrNull() ?: return@mapNotNull null
      if (g1.isNotEmpty()) {
        val hrs = g1.toLongOrNull() ?: 0L
        ((hrs * 3600L) + (g2 * 60L) + g3) * 1000L
      } else {
        ((g2 * 60L) + g3) * 1000L
      }
    }.toList()
  }

  fun computeSampleTimestamps(
    durationMs: Long,
    requestedMs: List<Long>,
    maxFrames: Int,
  ): List<Long> {
    if (durationMs <= 0L) return listOf(0L)
    val safeMax = maxFrames.coerceIn(1, 10)
    val result = linkedSetOf<Long>()
    requestedMs.filter { it in 0L..durationMs }.take(4).forEach { result.add(it) }

    val remainingSlots = (safeMax - result.size).coerceAtLeast(0)
    if (remainingSlots > 0) {
      for (i in 0 until remainingSlots) {
        val ratio =
          if (remainingSlots == 1) 0.5
          else i.toDouble() / (remainingSlots - 1).toDouble()
        val ts = (durationMs * ratio).toLong().coerceIn(0L, (durationMs - 200L).coerceAtLeast(0L))
        result.add(ts)
      }
    }
    return result.sorted()
  }

  fun formatTimestamp(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hrs > 0L) "%02d:%02d:%02d".format(hrs, mins, secs) else "%02d:%02d".format(mins, secs)
  }

  private fun extractMetadata(
    file: File,
    displayName: String,
    mimeType: String,
    byteSize: Long,
  ): VideoMetadataInfo {
    val retriever = MediaMetadataRetriever()
    var durationMs = 0L
    var width = 0
    var height = 0
    var bitrateBps = 0L
    var hasAudio = false
    try {
      retriever.setDataSource(file.absolutePath)
      durationMs =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
      width =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
      height =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
      bitrateBps =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
      hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
    } finally {
      runCatching { retriever.release() }
    }

    var audioMime: String? = null
    var audioSampleRate: Int? = null
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(file.absolutePath)
      for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val trackMime = format.getString(MediaFormat.KEY_MIME)
        if (trackMime?.startsWith("audio/") == true) {
          hasAudio = true
          audioMime = trackMime
          if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
          }
          break
        }
      }
    } catch (_: Throwable) {
      // Fallback to MediaMetadataRetriever results if extractor cannot inspect container
    } finally {
      runCatching { extractor.release() }
    }

    return VideoMetadataInfo(
      displayName = displayName,
      mimeType = mimeType,
      byteSize = byteSize,
      durationMs = durationMs,
      width = width,
      height = height,
      bitrateBps = bitrateBps,
      hasAudioTrack = hasAudio,
      audioMimeType = audioMime,
      audioSampleRateHz = audioSampleRate,
    )
  }
}
