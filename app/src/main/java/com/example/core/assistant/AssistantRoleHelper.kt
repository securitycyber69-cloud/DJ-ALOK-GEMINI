package com.example.core.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object AssistantRoleHelper {

  fun isDefaultAssistant(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = context.getSystemService(RoleManager::class.java)
      if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
        return roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
      }
    }
    val current =
      runCatching {
        Settings.Secure.getString(context.contentResolver, "assistant")
      }.getOrNull().orEmpty()
    return current.contains(context.packageName)
  }

  fun createRequestAssistantIntent(context: Context): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = context.getSystemService(RoleManager::class.java)
      if (roleManager != null &&
        roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
        !roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
      ) {
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
      }
    }
    return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }
}
