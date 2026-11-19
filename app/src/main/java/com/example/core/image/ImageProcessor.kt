package com.example.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.example.core.security.SecurityValidator
import com.example.domain.model.ProcessedImagePayload
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ImageProcessor @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val securityValidator = SecurityValidator()

  companion object {
    val ALLOWED_IMAGE_MIMES =
      setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
        "image/heic",
        "image/heif",
      )
    const val MAX_RAW_IMAGE_BYTES = 25L * 1024L * 1024L
  }

  suspend fun processUri(
    uriString: String,
    maxDimensionPx: Int = 1536,
    maxBytes: Int = 1_500_000,
  ): Result<ProcessedImagePayload> =
    withContext(Dispatchers.IO) {
      runCatching {
        val uri = Uri.parse(uriString)
        val detectedMime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val extHint = uriString.substringAfterLast('.', "")
        if (!securityValidator.validateMimeType(detectedMime, ALLOWED_IMAGE_MIMES, extHint)) {
          error("Unsupported image MIME type: $detectedMime. Supported: JPEG, PNG, WEBP, HEIC.")
        }

        val exifResult = readExifSummaryAndOrientation(uri)

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
          BitmapFactory.decodeStream(input, null, boundsOptions)
        } ?: error("Unable to open selected image stream.")

        val sampleSize =
          calculateInSampleSize(
            rawWidth = boundsOptions.outWidth,
            rawHeight = boundsOptions.outHeight,
            reqMaxDimension = maxDimensionPx,
          )

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val rawBitmap =
          context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
          } ?: error("Unable to decode selected image bitmap.")

        val orientedBitmap = applyExifOrientation(rawBitmap, exifResult.orientation)
        if (orientedBitmap !== rawBitmap) rawBitmap.recycle()

        // Re-compressing cleanly into JPEG/PNG automatically strips location/sensitive EXIF tags
        compressBitmapToPayload(
          uriString = uriString,
          bitmap = orientedBitmap,
          originalMime = detectedMime,
          maxDimensionPx = maxDimensionPx,
          maxBytes = maxBytes,
          exifSummary = exifResult.summary,
        )
      }
    }

  suspend fun processDrawableResource(
    resId: Int,
    labelUri: String = "android.resource://sample/inline_preview",
    maxDimensionPx: Int = 1280,
    maxBytes: Int = 1_200_000,
  ): Result<ProcessedImagePayload> =
    withContext(Dispatchers.IO) {
      runCatching {
        val bitmap =
          BitmapFactory.decodeResource(context.resources, resId)
            ?: error("Unable to decode bundled preview image.")
        compressBitmapToPayload(
          uriString = labelUri,
          bitmap = bitmap,
          originalMime = "image/jpeg",
          maxDimensionPx = maxDimensionPx,
          maxBytes = maxBytes,
          exifSummary = "Bundled sample artwork (EXIF stripped)",
        )
      }
    }

  fun compressBitmapPublic(
    uriString: String,
    bitmap: Bitmap,
    maxDimensionPx: Int = 960,
    maxBytes: Int = 450_000,
    note: String? = null,
  ): ProcessedImagePayload {
    return compressBitmapToPayload(
      uriString = uriString,
      bitmap = bitmap,
      originalMime = "image/jpeg",
      maxDimensionPx = maxDimensionPx,
      maxBytes = maxBytes,
      exifSummary = note,
    )
  }

  private fun compressBitmapToPayload(
    uriString: String,
    bitmap: Bitmap,
    originalMime: String,
    maxDimensionPx: Int,
    maxBytes: Int,
    exifSummary: String?,
  ): ProcessedImagePayload {
    val scaled = scaleDownIfNeeded(bitmap, maxDimensionPx)
    if (scaled !== bitmap) bitmap.recycle()

    val compressFormat =
      if (originalMime.equals("image/png", ignoreCase = true) && scaled.hasAlpha()) {
        Bitmap.CompressFormat.PNG
      } else {
        Bitmap.CompressFormat.JPEG
      }

    val finalMime =
      if (compressFormat == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"

    var quality = 88
    var bytes: ByteArray
    do {
      val out = ByteArrayOutputStream()
      scaled.compress(compressFormat, quality, out)
      bytes = out.toByteArray()
      quality -= 10
    } while (bytes.size > maxBytes && quality >= 35 && compressFormat == Bitmap.CompressFormat.JPEG)

    val outW = scaled.width
    val outH = scaled.height
    scaled.recycle()

    val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return ProcessedImagePayload(
      uriString = uriString,
      mimeType = finalMime,
      base64Data = encoded,
      width = outW,
      height = outH,
      byteSize = bytes.size,
      exifSummary = exifSummary,
    )
  }

  private data class ExifOrientationData(val orientation: Int, val summary: String?)

  private fun readExifSummaryAndOrientation(uri: Uri): ExifOrientationData {
    return runCatching {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val exif = ExifInterface(stream)
        val orientation =
          exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
          )
        val make = exif.getAttribute(ExifInterface.TAG_MAKE)
        val date = exif.getAttribute(ExifInterface.TAG_DATETIME)
        val summaryParts =
          listOfNotNull(
            make?.let { "Device=$it" },
            date?.let { "Captured=$it" },
            if (orientation != ExifInterface.ORIENTATION_NORMAL) "OrientationFixed=$orientation" else null,
          )
        ExifOrientationData(
          orientation = orientation,
          summary = summaryParts.joinToString(", ").ifBlank { null },
        )
      }
    }.getOrNull() ?: ExifOrientationData(ExifInterface.ORIENTATION_NORMAL, null)
  }

  private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
      else -> return bitmap
    }
    return runCatching {
      Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)
  }

  private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    if (maxSide <= maxDimensionPx) return bitmap
    val ratio = maxDimensionPx.toFloat() / maxSide.toFloat()
    val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
    val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
  }

  private fun calculateInSampleSize(
    rawWidth: Int,
    rawHeight: Int,
    reqMaxDimension: Int,
  ): Int {
    var inSampleSize = 1
    if (rawHeight > reqMaxDimension || rawWidth > reqMaxDimension) {
      val halfHeight = rawHeight / 2
      val halfWidth = rawWidth / 2
      while ((halfHeight / inSampleSize) >= reqMaxDimension &&
        (halfWidth / inSampleSize) >= reqMaxDimension
      ) {
        inSampleSize *= 2
      }
    }
    return inSampleSize
  }
}
