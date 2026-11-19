package com.example.core.screen

import android.graphics.Bitmap
import com.example.core.storage.TempMediaCacheManager
import com.example.domain.model.ScreenFrameRecord
import com.example.domain.model.ScreenIntelligenceConfig
import com.example.domain.model.ScreenShareStatus
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ScreenFrameDeduplicator {
  /**
   * Computes a 64-bit difference hash (dHash) over a 9x8 downsampled luminance grid.
   */
  fun computePerceptualHash(bitmap: Bitmap): Long {
    val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
    var hash = 0L
    var bitIndex = 0
    for (y in 0 until 8) {
      for (x in 0 until 8) {
        val left = luminance(scaled.getPixel(x, y))
        val right = luminance(scaled.getPixel(x + 1, y))
        if (left > right) {
          hash = hash or (1L shl bitIndex)
        }
        bitIndex++
      }
    }
    if (scaled != bitmap) {
      scaled.recycle()
    }
    return hash
  }

  fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

  fun isDuplicate(previousHash: Long?, newHash: Long, thresholdBits: Int = 5): Boolean {
    if (previousHash == null) return false
    return hammingDistance(previousHash, newHash) <= thresholdBits.coerceIn(0, 16)
  }

  private fun luminance(color: Int): Int {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    return (r * 299 + g * 587 + b * 114) / 1000
  }
}

@Singleton
class ScreenShareSessionManager @Inject constructor(
  private val tempMediaCacheManager: TempMediaCacheManager,
) {
  private val _status = MutableStateFlow(ScreenShareStatus.STOPPED)
  val status: StateFlow<ScreenShareStatus> = _status.asStateFlow()

  private val _frames = MutableStateFlow<List<ScreenFrameRecord>>(emptyList())
  val frames: StateFlow<List<ScreenFrameRecord>> = _frames.asStateFlow()

  private val _lastStatusMessage = MutableStateFlow<String?>(null)
  val lastStatusMessage: StateFlow<String?> = _lastStatusMessage.asStateFlow()

  private var config: ScreenIntelligenceConfig = ScreenIntelligenceConfig()

  fun updateConfig(newConfig: ScreenIntelligenceConfig) {
    config = newConfig
  }

  fun markRequestingConsent() {
    _status.value = ScreenShareStatus.REQUESTING_CONSENT
    _lastStatusMessage.value = "Waiting for user MediaProjection consent…"
  }

  fun markSessionActive() {
    _status.value = ScreenShareStatus.ACTIVE
    _lastStatusMessage.value =
      "Live screen sharing active • Capturing up to 1 frame every ${config.captureIntervalSeconds}s"
  }

  fun markAnalyzing() {
    _status.value = ScreenShareStatus.ANALYZING
  }

  fun markCaptureError(reason: String) {
    _lastStatusMessage.value = reason
    if (_status.value != ScreenShareStatus.STOPPED) {
      _status.value = ScreenShareStatus.ACTIVE
    }
  }

  fun ingestCapturedBitmap(bitmap: Bitmap): ScreenFrameRecord? {
    val hash = ScreenFrameDeduplicator.computePerceptualHash(bitmap)
    val previousHash = _frames.value.lastOrNull()?.perceptualHash
    if (ScreenFrameDeduplicator.isDuplicate(previousHash, hash, config.duplicateThresholdBits)) {
      return null
    }

    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 76, baos)
    val file = tempMediaCacheManager.saveTempJpeg(baos.toByteArray(), prefix = "mp_frame_")

    val record =
      ScreenFrameRecord(
        id = "scr_${UUID.randomUUID()}",
        filePath = file.absolutePath,
        width = bitmap.width,
        height = bitmap.height,
        perceptualHash = hash,
        capturedAtMs = System.currentTimeMillis(),
        disclosureNote =
          "User-consented MediaProjection periodic frame (interval: ${config.captureIntervalSeconds}s)",
      )

    _frames.update { current ->
      val updated = current + record
      val limit = config.maxSessionFrames.coerceIn(1, 12)
      if (updated.size > limit) {
        val dropped = updated.take(updated.size - limit)
        dropped.forEach { old -> File(old.filePath).delete() }
        updated.drop(updated.size - limit)
      } else {
        updated
      }
    }
    return record
  }

  fun getLatestFrame(): ScreenFrameRecord? = _frames.value.lastOrNull()

  fun stopSession(reason: String = "Screen sharing stopped & temporary frames cleared.") {
    val toDelete = _frames.value
    _frames.value = emptyList()
    toDelete.forEach { frame -> File(frame.filePath).delete() }
    _status.value = ScreenShareStatus.STOPPED
    _lastStatusMessage.value = reason
  }
}
