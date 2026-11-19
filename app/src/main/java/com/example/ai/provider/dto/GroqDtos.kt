package com.example.ai.provider.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroqChatRequest(
  val model: String,
  val messages: List<GroqMessage>,
  val stream: Boolean = true,
  val temperature: Double = 0.7,
  @SerialName("stream_options") val streamOptions: GroqStreamOptions? = GroqStreamOptions(),
)

@Serializable
data class GroqStreamOptions(
  @SerialName("include_usage") val includeUsage: Boolean = true,
)

@Serializable
data class GroqMessage(
  val role: String,
  val content: String,
)

@Serializable
data class GroqStreamChunk(
  val choices: List<GroqChoice> = emptyList(),
  val usage: GroqUsage? = null,
)

@Serializable
data class GroqChoice(
  val delta: GroqDelta? = null,
  @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class GroqDelta(
  val content: String? = null,
)

@Serializable
data class GroqUsage(
  @SerialName("prompt_tokens") val promptTokens: Int = 0,
  @SerialName("completion_tokens") val completionTokens: Int = 0,
  @SerialName("total_tokens") val totalTokens: Int = 0,
)

@Serializable
data class GroqTranscriptionResponse(
  val text: String = "",
  val language: String? = null,
  val duration: Double? = null,
  val segments: List<GroqTranscriptionSegment> = emptyList(),
)

@Serializable
data class GroqTranscriptionSegment(
  val id: Int = 0,
  val start: Double = 0.0,
  val end: Double = 0.0,
  val text: String = "",
)
