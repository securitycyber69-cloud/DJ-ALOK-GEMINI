package com.example.ai.sse

import com.example.core.network.AiProviderException
import com.example.core.network.RetryPolicy
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody
import retrofit2.Response

object SseStreamParser {

  fun parseDataEvents(executeCall: suspend () -> Response<ResponseBody>): Flow<String> =
    flow {
      val response =
        RetryPolicy.withExponentialBackoff {
          val res = executeCall()
          if (!res.isSuccessful) {
            val errBody = res.errorBody()?.string()
            res.errorBody()?.close()
            throw RetryPolicy.mapHttpError(res.code(), errBody)
          }
          res
        }

      val body =
        response.body() ?: throw AiProviderException("Provider returned empty response body.")

      try {
        val source = body.source()
        val dataBuffer = StringBuilder()

        while (!source.exhausted()) {
          currentCoroutineContext().ensureActive()
          val line = source.readUtf8Line() ?: break

          when {
            line.isEmpty() -> {
              if (dataBuffer.isNotEmpty()) {
                val payload = dataBuffer.toString().trim()
                dataBuffer.clear()
                if (payload.isNotEmpty() && payload != "[DONE]") {
                  emit(payload)
                }
              }
            }
            line.startsWith("data:") -> {
              val chunk = line.removePrefix("data:").trimStart()
              if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
              dataBuffer.append(chunk)
            }
          }
        }

        if (dataBuffer.isNotEmpty()) {
          val payload = dataBuffer.toString().trim()
          if (payload.isNotEmpty() && payload != "[DONE]") {
            emit(payload)
          }
        }
      } catch (e: CancellationException) {
        throw e
      } finally {
        body.close()
      }
    }
      .flowOn(Dispatchers.IO)
}
