package com.eqcoach.network

import com.eqcoach.config.AppConfig
import com.eqcoach.model.Verdict
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AnalyzeClient(
    baseUrl: String = AppConfig.SERVER_URL,
    timeoutSeconds: Long = AppConfig.TIMEOUT_SECONDS,
) {
    private val analyzeUrl = "${baseUrl.trimEnd('/')}${AppConfig.ANALYZE_ENDPOINT}"

    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var inflightCall: Call? = null

    suspend fun analyze(frame: ByteArray, audio: ByteArray): AnalyzeResponse {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "frame", "frame.jpg",
                frame.toRequestBody("image/jpeg".toMediaType()),
            )
            .addFormDataPart(
                "audio", "audio.wav",
                audio.toRequestBody("audio/wav".toMediaType()),
            )
            .build()

        val request = Request.Builder()
            .url(analyzeUrl)
            .post(body)
            .build()

        val call = client.newCall(request)
        inflightCall = call

        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                call.cancel()
                inflightCall = null
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    inflightCall = null
                    if (cont.isActive) {
                        cont.resumeWithException(
                            ServerException("Connection failed: ${e.message}", e),
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    inflightCall = null
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    ServerException("Server error: ${resp.code}"),
                                )
                            }
                            return
                        }
                        try {
                            val bodyStr = resp.body?.string()
                                ?: throw ServerException("Empty response body")
                            val result = json.decodeFromString<AnalyzeResponse>(bodyStr)
                            if (cont.isActive) cont.resume(result)
                        } catch (e: ServerException) {
                            if (cont.isActive) cont.resumeWithException(e)
                        } catch (e: Exception) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    ServerException("Invalid response: ${e.message}", e),
                                )
                            }
                        }
                    }
                }
            })
        }
    }

    fun cancelInflight() {
        inflightCall?.cancel()
        inflightCall = null
    }

    fun shutdown() {
        cancelInflight()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}

@Serializable
data class DebugInfo(
    val facial_emotions: Map<String, Double> = emptyMap(),
    val facial_dominant: String = "",
    val speech_emotions: Map<String, Double> = emptyMap(),
    val speech_dominant: String = "",
    val fused_score: Double = 0.0,
)

@Serializable
data class AnalyzeResponse(
    val verdict: Verdict,
    val debug: DebugInfo? = null,
)

class ServerException(message: String, cause: Throwable? = null) : Exception(message, cause)
