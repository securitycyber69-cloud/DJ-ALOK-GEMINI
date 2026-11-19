package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
  @PrimaryKey val id: String,
  val title: String,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

@Entity(
  tableName = "messages",
  foreignKeys = [
    ForeignKey(
      entity = ConversationEntity::class,
      parentColumns = ["id"],
      childColumns = ["conversationId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("conversationId")],
)
data class MessageEntity(
  @PrimaryKey val id: String,
  val conversationId: String,
  val isUser: Boolean,
  val text: String,
  val timestampMs: Long,
  val imageUrisJson: String,
  val isSampleArtwork: Boolean,
  val modelUsed: String?,
  val categoryLabel: String?,
  val promptTokens: Int?,
  val completionTokens: Int?,
  val totalTokens: Int?,
  val isError: Boolean,
  val blocksJson: String = "[]",
  val attachedMediaJson: String = "[]",
)

@Dao
interface ChatDao {
  @Query("SELECT * FROM conversations ORDER BY updatedAtMs DESC")
  fun observeConversations(): Flow<List<ConversationEntity>>

  @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
  suspend fun getConversationById(id: String): ConversationEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertConversation(entity: ConversationEntity)

  @Query("UPDATE conversations SET title = :title, updatedAtMs = :updatedAtMs WHERE id = :id")
  suspend fun updateConversationTitle(id: String, title: String, updatedAtMs: Long)

  @Query("DELETE FROM conversations WHERE id = :id")
  suspend fun deleteConversation(id: String)

  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestampMs ASC")
  fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestampMs ASC")
  suspend fun getMessagesSnapshot(conversationId: String): List<MessageEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertMessage(entity: MessageEntity)

  @Query("DELETE FROM messages WHERE id = :messageId")
  suspend fun deleteMessage(messageId: String)

  @Query("DELETE FROM messages WHERE conversationId = :conversationId AND timestampMs >= :fromTimestampMs")
  suspend fun deleteMessagesFrom(conversationId: String, fromTimestampMs: Long)
}

@Database(
  entities = [
    ConversationEntity::class,
    MessageEntity::class,
    UserPreferenceEntity::class,
    MemoryItemEntity::class,
    MemorySummaryEntity::class,
    AgentTaskEntity::class,
    ResearchSourceEntity::class,
    MediaMetadataEntity::class,
    AutomationTaskEntity::class,
  ],
  version = 3,
  exportSchema = false,
)
abstract class MousomiDatabase : RoomDatabase() {
  abstract fun chatDao(): ChatDao
  abstract fun memoryDao(): MemoryDao
  abstract fun part4OperationsDao(): Part4OperationsDao

  companion object {
    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE messages ADD COLUMN blocksJson TEXT NOT NULL DEFAULT '[]'")
          db.execSQL("ALTER TABLE messages ADD COLUMN attachedMediaJson TEXT NOT NULL DEFAULT '[]'")
        }
      }

    val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_preferences (
              prefKey TEXT NOT NULL PRIMARY KEY,
              prefValue TEXT NOT NULL,
              encrypted INTEGER NOT NULL DEFAULT 0,
              updatedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_items (
              id TEXT NOT NULL PRIMARY KEY,
              category TEXT NOT NULL,
              contentPayload TEXT NOT NULL,
              keywordsCsv TEXT NOT NULL,
              importance REAL NOT NULL,
              isApproved INTEGER NOT NULL,
              isEncrypted INTEGER NOT NULL,
              createdAtMs INTEGER NOT NULL,
              updatedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_items_category ON memory_items(category)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_items_updatedAtMs ON memory_items(updatedAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_summaries (
              conversationId TEXT NOT NULL PRIMARY KEY,
              summaryText TEXT NOT NULL,
              updatedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_summaries_conversationId ON memory_summaries(conversationId)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS agent_tasks (
              id TEXT NOT NULL PRIMARY KEY,
              goal TEXT NOT NULL,
              selectedAgentsJson TEXT NOT NULL,
              status TEXT NOT NULL,
              summary TEXT NOT NULL,
              tokenUsage INTEGER NOT NULL,
              durationMs INTEGER NOT NULL,
              createdAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_tasks_createdAtMs ON agent_tasks(createdAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS research_sources (
              url TEXT NOT NULL PRIMARY KEY,
              title TEXT NOT NULL,
              publisher TEXT NOT NULL,
              domain TEXT NOT NULL,
              verificationStatus TEXT NOT NULL,
              retrievedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_research_sources_domain ON research_sources(domain)")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_research_sources_retrievedAtMs ON research_sources(retrievedAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media_metadata (
              id TEXT NOT NULL PRIMARY KEY,
              kind TEXT NOT NULL,
              displayName TEXT NOT NULL,
              mimeType TEXT NOT NULL,
              byteSize INTEGER NOT NULL,
              summaryNote TEXT NOT NULL,
              createdAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_media_metadata_createdAtMs ON media_metadata(createdAtMs)")

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS automation_tasks (
              id TEXT NOT NULL PRIMARY KEY,
              workflowType TEXT NOT NULL,
              title TEXT NOT NULL,
              targetPackageOrArg TEXT NOT NULL,
              status TEXT NOT NULL,
              resultLog TEXT NOT NULL,
              triggerSource TEXT NOT NULL,
              createdAtMs INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_tasks_createdAtMs ON automation_tasks(createdAtMs)")
        }
      }
  }
}
