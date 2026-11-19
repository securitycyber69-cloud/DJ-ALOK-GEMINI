package com.example.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TempMediaCacheManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val tempRootDir: File by lazy {
    File(context.cacheDir, "mousomi_media_tmp").apply { mkdirs() }
  }

  suspend fun copyUriToTempFile(
    uriString: String,
    extensionHint: String = "bin",
    maxAllowedBytes: Long = 150L * 1024L * 1024L,
  ): Result<TempCopiedFile> =
    withContext(Dispatchers.IO) {
      runCatching {
        val uri = Uri.parse(uriString)
        val displayName = resolveDisplayName(uri) ?: "attachment.$extensionHint"
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"

        val safeExt =
          displayName.substringAfterLast('.', extensionHint)
            .replace(Regex("[^A-Za-z0-9]"), "")
            .ifBlank { extensionHint }
            .take(8)
        val target = File(tempRootDir, "tmp_${UUID.randomUUID()}.$safeExt")

        var totalBytes = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
          target.outputStream().use { output ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
              val read = input.read(buffer)
              if (read <= 0) break
              totalBytes += read
              if (totalBytes > maxAllowedBytes) {
                target.delete()
                error(
                  "File '$displayName' exceeds maximum allowed upload limit of ${maxAllowedBytes / (1024 * 1024)} MB."
                )
              }
              output.write(buffer, 0, read)
            }
          }
        } ?: error("Unable to read selected file stream.")

        TempCopiedFile(
          file = target,
          displayName = displayName,
          mimeType = mime,
          byteSize = totalBytes,
        )
      }
    }

  fun resolveDisplayName(uri: Uri): String? {
    if (uri.scheme.equals("file", ignoreCase = true)) {
      return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }
    val queried =
      runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
          } else null
        }
      }.getOrNull()
    return queried ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
  }

  fun resolveFileSize(uri: Uri): Long? {
    return runCatching {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
        } else null
      }
    }.getOrNull()
  }

  fun cleanupFile(file: File?) {
    runCatching {
      if (file != null && file.exists()) {
        file.delete()
      }
    }
  }

  fun saveTempJpeg(jpegBytes: ByteArray, prefix: String = "screen_"): File {
    val file = File(tempRootDir, "${prefix}${UUID.randomUUID()}.jpg")
    file.writeBytes(jpegBytes)
    return file
  }

  fun cleanupExpiredTempFiles(maxAgeMs: Long = 24L * 60L * 60L * 1000L): Int {
    val cutoff = System.currentTimeMillis() - maxAgeMs.coerceAtLeast(60_000L)
    var removed = 0
    runCatching {
      tempRootDir.listFiles()?.forEach { file ->
        if (file.lastModified() < cutoff) {
          if (file.deleteRecursively()) removed++
        }
      }
    }
    return removed
  }

  fun cleanupAllTempFiles() {
    runCatching {
      tempRootDir.listFiles()?.forEach { it.deleteRecursively() }
    }
  }
}

data class TempCopiedFile(
  val file: File,
  val displayName: String,
  val mimeType: String,
  val byteSize: Long,
)
