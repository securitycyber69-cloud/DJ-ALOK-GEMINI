package com.example.data.repository

import com.example.data.local.db.ChatDao
import com.example.data.local.db.ConversationEntity
import com.example.data.local.db.MessageEntity
import com.example.data.local.prefs.SettingsDataStore
import com.example.domain.model.AppSettings
import com.example.domain.model.DomainConversation
import com.example.domain.model.DomainMessage
import com.example.domain.model.PendingMediaItem
import com.example.domain.model.ResponseBlock
import com.example.domain.model.TokenUsage
import com.example.domain.repository.ChatRepository
import com.example.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class ChatRepositoryImpl @Inject constructor(
  private val chatDao: ChatDao,
  private val json: Json,
) : ChatRepository {

  override fun observeConversations(): Flow<List<DomainConversation>> =
    chatDao.observeConversations().map { list ->
      list.map {
        DomainConversation(
          id = it.id,
          title = it.title,
          createdAtMs = it.createdAtMs,
          updatedAtMs = it.updatedAtMs,
        )
      }
    }

  override fun observeMessages(conversationId: String): Flow<List<DomainMessage>> =
    chatDao.observeMessages(conversationId).map { list -> list.map(::toDomainMessage) }

  override suspend fun getMessagesSnapshot(conversationId: String): List<DomainMessage> =
    chatDao.getMessagesSnapshot(conversationId).map(::toDomainMessage)

  override suspend fun ensureConversation(
    conversationId: String,
    initialTitle: String,
  ): DomainConversation {
    val existing = chatDao.getConversationById(conversationId)
    if (existing != null) {
      return DomainConversation(
        id = existing.id,
        title = existing.title,
        createdAtMs = existing.createdAtMs,
        updatedAtMs = existing.updatedAtMs,
      )
    }
    val now = System.currentTimeMillis()
    val entity =
      ConversationEntity(
        id = conversationId,
        title = initialTitle,
        createdAtMs = now,
        updatedAtMs = now,
      )
    chatDao.upsertConversation(entity)
    return DomainConversation(
      id = entity.id,
      title = entity.title,
      createdAtMs = entity.createdAtMs,
      updatedAtMs = entity.updatedAtMs,
    )
  }

  override suspend fun updateConversationTitle(conversationId: String, title: String) {
    chatDao.updateConversationTitle(
      id = conversationId,
      title = title,
      updatedAtMs = System.currentTimeMillis(),
    )
  }

  override suspend fun deleteConversation(conversationId: String) {
    chatDao.deleteConversation(conversationId)
  }

  override suspend fun upsertMessage(message: DomainMessage) {
    val existing = chatDao.getConversationById(message.conversationId)
    val now = System.currentTimeMillis()
    if (existing == null) {
      chatDao.upsertConversation(
        ConversationEntity(
          id = message.conversationId,
          title = "New Conversation",
          createdAtMs = now,
          updatedAtMs = now,
        )
      )
    } else {
      chatDao.upsertConversation(existing.copy(updatedAtMs = now))
    }

    val urisJson = json.encodeToString(message.imageUris)
    val blocksJson =
      runCatching { json.encodeToString(message.blocks) }.getOrDefault("[]")
    val mediaJson =
      runCatching { json.encodeToString(message.attachedMediaSummaries) }.getOrDefault("[]")

    chatDao.upsertMessage(
      MessageEntity(
        id = message.id,
        conversationId = message.conversationId,
        isUser = message.isUser,
        text = message.text,
        timestampMs = message.timestampMs,
        imageUrisJson = urisJson,
        isSampleArtwork = message.isSampleArtwork,
        modelUsed = message.modelUsed,
        categoryLabel = message.categoryLabel,
        promptTokens = message.tokenUsage?.promptTokens,
        completionTokens = message.tokenUsage?.completionTokens,
        totalTokens = message.tokenUsage?.totalTokens,
        isError = message.isError,
        blocksJson = blocksJson,
        attachedMediaJson = mediaJson,
      )
    )
  }

  override suspend fun deleteMessage(messageId: String) {
    chatDao.deleteMessage(messageId)
  }

  override suspend fun deleteMessagesFrom(conversationId: String, fromTimestampMs: Long) {
    chatDao.deleteMessagesFrom(conversationId, fromTimestampMs)
  }

  private fun toDomainMessage(entity: MessageEntity): DomainMessage {
    val uris =
      runCatching { json.decodeFromString<List<String>>(entity.imageUrisJson) }
        .getOrDefault(emptyList())
    val blocks =
      runCatching { json.decodeFromString<List<ResponseBlock>>(entity.blocksJson) }
        .getOrDefault(emptyList())
    val attachedMedia =
      runCatching { json.decodeFromString<List<PendingMediaItem>>(entity.attachedMediaJson) }
        .getOrDefault(emptyList())
    val usage =
      if (entity.totalTokens != null) {
        TokenUsage(
          promptTokens = entity.promptTokens ?: 0,
          completionTokens = entity.completionTokens ?: 0,
          totalTokens = entity.totalTokens,
        )
      } else {
        null
      }
    return DomainMessage(
      id = entity.id,
      conversationId = entity.conversationId,
      isUser = entity.isUser,
      text = entity.text,
      timestampMs = entity.timestampMs,
      imageUris = uris,
      isSampleArtwork = entity.isSampleArtwork,
      modelUsed = entity.modelUsed,
      categoryLabel = entity.categoryLabel,
      tokenUsage = usage,
      isError = entity.isError,
      blocks = blocks,
      attachedMediaSummaries = attachedMedia,
    )
  }
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
  private val dataStore: SettingsDataStore,
) : SettingsRepository {
  override fun observeSettings(): Flow<AppSettings> = dataStore.settingsFlow

  override suspend fun getSettingsSnapshot(): AppSettings = dataStore.getSnapshot()

  override suspend fun saveSettings(settings: AppSettings) {
    dataStore.save(settings)
  }
}
