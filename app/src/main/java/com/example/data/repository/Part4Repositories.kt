package com.example.data.repository

import com.example.data.local.db.AgentTaskEntity
import com.example.data.local.db.AutomationTaskEntity
import com.example.data.local.db.MediaMetadataEntity
import com.example.data.local.db.Part4OperationsDao
import com.example.data.local.db.ResearchSourceEntity
import com.example.domain.model.AgentTaskLogItem
import com.example.domain.model.AutomationTaskRecord
import com.example.domain.model.AutomationWorkflowType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class Part4OperationsRepository @Inject constructor(
  private val dao: Part4OperationsDao,
) {
  private val json = Json { ignoreUnknownKeys = true }

  fun observeAgentTasks(): Flow<List<AgentTaskLogItem>> =
    dao.observeAgentTasks().map { list ->
      list.map { entity ->
        val agents =
          runCatching { json.decodeFromString<List<String>>(entity.selectedAgentsJson) }
            .getOrDefault(emptyList())
        AgentTaskLogItem(
          id = entity.id,
          goal = entity.goal,
          selectedAgents = agents,
          status = entity.status,
          summary = entity.summary,
          tokenUsage = entity.tokenUsage,
          durationMs = entity.durationMs,
          createdAtMs = entity.createdAtMs,
        )
      }
    }

  suspend fun recordAgentTask(item: AgentTaskLogItem) {
    dao.upsertAgentTask(
      AgentTaskEntity(
        id = item.id,
        goal = item.goal,
        selectedAgentsJson = json.encodeToString(item.selectedAgents),
        status = item.status,
        summary = item.summary,
        tokenUsage = item.tokenUsage,
        durationMs = item.durationMs,
        createdAtMs = item.createdAtMs,
      )
    )
  }

  suspend fun clearAgentTasks() = dao.clearAgentTasks()

  fun observeAutomationTasks(): Flow<List<AutomationTaskRecord>> =
    dao.observeAutomationTasks().map { list ->
      list.map { entity ->
        val wf =
          runCatching { AutomationWorkflowType.valueOf(entity.workflowType) }
            .getOrDefault(AutomationWorkflowType.RUN_ROUTINE)
        AutomationTaskRecord(
          id = entity.id,
          workflowType = wf,
          title = entity.title,
          targetPackageOrArg = entity.targetPackageOrArg,
          status = entity.status,
          resultLog = entity.resultLog,
          triggerSource = entity.triggerSource,
          createdAtMs = entity.createdAtMs,
        )
      }
    }

  suspend fun recordAutomationTask(record: AutomationTaskRecord) {
    dao.upsertAutomationTask(
      AutomationTaskEntity(
        id = record.id,
        workflowType = record.workflowType.name,
        title = record.title,
        targetPackageOrArg = record.targetPackageOrArg,
        status = record.status,
        resultLog = record.resultLog,
        triggerSource = record.triggerSource,
        createdAtMs = record.createdAtMs,
      )
    )
  }

  suspend fun clearAutomationTasks() = dao.clearAutomationTasks()

  suspend fun recordResearchSource(
    url: String,
    title: String,
    publisher: String,
    domain: String,
    verificationStatus: String,
  ) {
    dao.upsertResearchSource(
      ResearchSourceEntity(
        url = url,
        title = title,
        publisher = publisher,
        domain = domain,
        verificationStatus = verificationStatus,
        retrievedAtMs = System.currentTimeMillis(),
      )
    )
  }

  suspend fun recordMediaMetadata(
    id: String,
    kind: String,
    displayName: String,
    mimeType: String,
    byteSize: Long,
    summaryNote: String,
  ) {
    dao.upsertMediaMetadata(
      MediaMetadataEntity(
        id = id,
        kind = kind,
        displayName = displayName,
        mimeType = mimeType,
        byteSize = byteSize,
        summaryNote = summaryNote,
        createdAtMs = System.currentTimeMillis(),
      )
    )
  }
}
