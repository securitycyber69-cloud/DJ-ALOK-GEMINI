package com.example.domain.repository

import com.example.domain.model.AppSettings
import com.example.domain.model.DomainConversation
import com.example.domain.model.DomainMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
  fun observeConversations(): Flow<List<DomainConversation>>

  fun observeMessages(conversationId: String): Flow<List<DomainMessage>>

  suspend fun getMessagesSnapshot(conversationId: String): List<DomainMessage>

  suspend fun ensureConversation(conversationId: String, initialTitle: String): DomainConversation

  suspend fun updateConversationTitle(conversationId: String, title: String)

  suspend fun deleteConversation(conversationId: String)

  suspend fun upsertMessage(message: DomainMessage)

  suspend fun deleteMessage(messageId: String)

  suspend fun deleteMessagesFrom(conversationId: String, fromTimestampMs: Long)
}

interface SettingsRepository {
  fun observeSettings(): Flow<AppSettings>

  suspend fun getSettingsSnapshot(): AppSettings

  suspend fun saveSettings(settings: AppSettings)
}
