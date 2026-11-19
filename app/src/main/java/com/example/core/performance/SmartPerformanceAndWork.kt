package com.example.core.performance

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.core.storage.TempMediaCacheManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartPerformanceMonitor @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  fun isPowerSaveMode(): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return pm?.isPowerSaveMode == true
  }

  fun isLowMemoryDevice(): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return am?.isLowRamDevice == true
  }

  fun schedulePeriodicCacheCleanup(enabled: Boolean) {
    val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
    if (!enabled) {
      wm.cancelUniqueWork(MousomiMaintenanceWorker.UNIQUE_WORK_NAME)
      return
    }
    val request =
      PeriodicWorkRequestBuilder<MousomiMaintenanceWorker>(24, TimeUnit.HOURS).build()
    wm.enqueueUniquePeriodicWork(
      MousomiMaintenanceWorker.UNIQUE_WORK_NAME,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }
}

class MousomiMaintenanceWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val cacheManager = TempMediaCacheManager(applicationContext)
    cacheManager.cleanupExpiredTempFiles(maxAgeMs = 12L * 60L * 60L * 1000L)
    return Result.success()
  }

  companion object {
    const val UNIQUE_WORK_NAME = "mousomi_temp_cache_cleanup"
  }
}
