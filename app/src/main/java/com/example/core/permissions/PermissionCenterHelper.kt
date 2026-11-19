package com.example.core.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.core.assistant.AssistantRoleHelper
import com.example.domain.model.PermissionDashboardItem
import com.example.domain.model.ScreenShareStatus

object PermissionCenterHelper {

  fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

  fun hasNotificationListenerAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

  fun hasPostNotificationsPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

  fun buildDashboardItems(
    context: Context,
    screenShareStatus: ScreenShareStatus,
  ): List<PermissionDashboardItem> =
    listOf(
      PermissionDashboardItem(
        id = "mic",
        title = "Microphone (Voice Input & Live)",
        reason = "Captures speech only when you tap the mic or enable Continuous Voice.",
        isGranted = hasAudioPermission(context),
        howToDisable = "Revoke Microphone access in Android App Info > Permissions.",
      ),
      PermissionDashboardItem(
        id = "overlay",
        title = "Display Over Other Apps (Floating Orb & Edge Light)",
        reason = "Allows the optional Floating AI Orb to stay accessible across apps.",
        isGranted = canDrawOverlays(context),
        howToDisable = "Turn off System Overlay toggle or disable in Special App Access.",
      ),
      PermissionDashboardItem(
        id = "screen_share",
        title = "Screen Capture (MediaProjection)",
        reason = "Temporarily reads screen frames only after explicit system consent until Stop.",
        isGranted = screenShareStatus != ScreenShareStatus.STOPPED,
        howToDisable = "Tap Stop Screen Sharing pill or notification action anytime.",
      ),
      PermissionDashboardItem(
        id = "notif_listener",
        title = "Notification Listener (Smart Summary)",
        reason = "Reads user-allowlisted alerts locally; OTPs, passwords, and codes are blocked.",
        isGranted = hasNotificationListenerAccess(context),
        howToDisable = "Disable Mousomi AI in Settings > Notification Access.",
      ),
      PermissionDashboardItem(
        id = "post_notif",
        title = "App Notifications (Foreground Status)",
        reason = "Displays ongoing indicator while Screen Sharing or Floating Orb service runs.",
        isGranted = hasPostNotificationsPermission(context),
        howToDisable = "Manage in Android App Info > Notifications.",
      ),
      PermissionDashboardItem(
        id = "assistant",
        title = "Default Digital Assistant Role",
        reason = "Lets long-press home/power gesture launch Mousomi Voice & Quick Assistant.",
        isGranted = AssistantRoleHelper.isDefaultAssistant(context),
        howToDisable = "Switch default assistant in Android Default Apps settings.",
      ),
      PermissionDashboardItem(
        id = "media_picker",
        title = "Photos, Videos & Documents (Zero-Permission Picker)",
        reason = "Uses privacy-preserving Android Photo Picker & SAF; broad storage permission is never requested.",
        isGranted = true,
        howToDisable = "Only single files you explicitly select are temporarily read.",
        isOptionalZeroPermissionNote = true,
      ),
      PermissionDashboardItem(
        id = "camera_note",
        title = "Camera (Not Requested)",
        reason = "Mousomi does not request or access the Camera in the background.",
        isGranted = false,
        howToDisable = "Unused by design for strict privacy.",
        isOptionalZeroPermissionNote = true,
      ),
    )

  fun createOverlaySettingsIntent(context: Context): Intent =
    Intent(
      Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
      Uri.parse("package:${context.packageName}"),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

  fun createNotificationListenerSettingsIntent(): Intent =
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
