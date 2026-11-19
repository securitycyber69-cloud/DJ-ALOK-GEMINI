package com.example.ai.provider.api

import com.example.ai.provider.dto.GeminiGenerateRequest
import com.example.ai.provider.dto.GeminiGenerateResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface GeminiApiService {

  @Streaming
  @POST("v1beta/models/{model}:streamGenerateContent")
  suspend fun streamGenerateContent(
    @Path("model") model: String,
    @Query("alt") alt: String = "sse",
    @Header("x-goog-api-key") apiKey: String,
    @Body request: GeminiGenerateRequest,
  ): Response<ResponseBody>

  @POST("v1beta/models/{model}:generateContent")
  suspend fun generateContent(
    @Path("model") model: String,
    @Header("x-goog-api-key") apiKey: String,
    @Body request: GeminiGenerateRequest,
  ): Response<GeminiGenerateResponse>
}
