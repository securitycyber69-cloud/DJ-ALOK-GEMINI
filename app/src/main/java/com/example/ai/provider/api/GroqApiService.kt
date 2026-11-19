package com.example.ai.provider.api

import com.example.ai.provider.dto.GroqChatRequest
import com.example.ai.provider.dto.GroqTranscriptionResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming

interface GroqApiService {

  @Streaming
  @POST("v1/chat/completions")
  suspend fun streamChatCompletion(
    @Header("Authorization") authorization: String,
    @Body request: GroqChatRequest,
  ): Response<ResponseBody>

  @Multipart
  @POST("v1/audio/transcriptions")
  suspend fun transcribeAudio(
    @Header("Authorization") authorization: String,
    @Part file: MultipartBody.Part,
    @Part("model") model: RequestBody,
    @Part("response_format") responseFormat: RequestBody,
    @Part("language") language: RequestBody? = null,
  ): Response<GroqTranscriptionResponse>
}
