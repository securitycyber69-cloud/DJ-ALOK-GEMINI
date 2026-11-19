package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
  @PrimaryKey val prefKey: String,
  val prefValue: String,
  val encrypted: Boolean = false,
  val updatedAtMs: Long,
)

@Entity(
  tableName = "memory_items",
  indices = [Index("category"), Index("updatedAtMs")],
)
data class MemoryItemEntity(
  @PrimaryKey val id: String,
  val category: String,
  val contentPayload: String,
  val keywordsCsv: String,
  val importance: Float,
  val isApproved: Boolean,
  val isEncrypted: Boolean,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

@Entity(
  tableName = "memory_summaries",
  indices = [Index("conversationId")],
)
data class MemorySummaryEntity(
  @PrimaryKey val conversationId: String,
  val summaryText: String,
  val updatedAtMs: Long,
)

@Entity(
  tableName = "agent_tasks",
  indices = [Index("createdAtMs")],
)
data class AgentTaskEntity(
  @PrimaryKey val id: String,
  val goal: String,
  val selectedAgentsJson: String,
  val status: String,
  val summary: String,
  val tokenUsage: Int,
  val durationMs: Long,
  val createdAtMs: Long,
)

@Entity(
  tableName = "research_sources",
  indices = [Index("domain"), Index("retrievedAtMs")],
)
data class ResearchSourceEntity(
  @PrimaryKey val url: String,
  val title: String,
  val publisher: String,
  val domain: String,
  val verificationStatus: String,
  val retrievedAtMs: Long,
)

@Entity(
  tableName = "media_metadata",
  indices = [Index("createdAtMs")],
)
data class MediaMetadataEntity(
  @PrimaryKey val id: String,
  val kind: String,
  val displayName: String,
  val mimeType: String,
  val byteSize: Long,
  val summaryNote: String,
  val createdAtMs: Long,
)

@Entity(
  tableName = "automation_tasks",
  indices = [Index("createdAtMs")],
)
data class AutomationTaskEntity(
  @PrimaryKey val id: String,
  val workflowType: String,
  val title: String,
  val targetPackageOrArg: String,
  val status: String,
  val resultLog: String,
  val triggerSource: String,
  val createdAtMs: Long,
)

@Dao
interface MemoryDao {
  @Query("SELECT * FROM memory_items ORDER BY importance DESC, updatedAtMs DESC")
  fun observeAllMemories(): Flow<List<MemoryItemEntity>>

  @Query("SELECT * FROM memory_items WHERE isApproved = 1 ORDER BY importance DESC, updatedAtMs DESC")
  suspend fun getApprovedMemoriesSnapshot(): List<MemoryItemEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertMemory(entity: MemoryItemEntity)

  @Query("DELETE FROM memory_items WHERE id = :id")
  suspend fun deleteMemory(id: String)

  @Query("DELETE FROM memory_items")
  suspend fun clearAllMemories()

  @Query("SELECT * FROM memory_summaries ORDER BY updatedAtMs DESC LIMIT 20")
  fun observeSummaries(): Flow<List<MemorySummaryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSummary(entity: MemorySummaryEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertUserPreference(entity: UserPreferenceEntity)

  @Query("SELECT * FROM user_preferences")
  suspend fun getAllPreferences(): List<UserPreferenceEntity>
}

@Dao
interface Part4OperationsDao {
  @Query("SELECT * FROM agent_tasks ORDER BY createdAtMs DESC LIMIT 40")
  fun observeAgentTasks(): Flow<List<AgentTaskEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAgentTask(entity: AgentTaskEntity)

  @Query("DELETE FROM agent_tasks")
  suspend fun clearAgentTasks()

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertResearchSource(entity: ResearchSourceEntity)

  @Query("SELECT * FROM research_sources ORDER BY retrievedAtMs DESC LIMIT 50")
  fun observeResearchSources(): Flow<List<ResearchSourceEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertMediaMetadata(entity: MediaMetadataEntity)

  @Query("SELECT * FROM media_metadata ORDER BY createdAtMs DESC LIMIT 50")
  fun observeMediaMetadata(): Flow<List<MediaMetadataEntity>>

  @Query("SELECT * FROM automation_tasks ORDER BY createdAtMs DESC LIMIT 40")
  fun observeAutomationTasks(): Flow<List<AutomationTaskEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAutomationTask(entity: AutomationTaskEntity)

  @Query("DELETE FROM automation_tasks")
  suspend fun clearAutomationTasks()
}
