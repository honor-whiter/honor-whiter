package com.example.motionanalysis.data.cloud

import com.example.motionanalysis.domain.model.PoseSequence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class DeepSeekClient(
    private val apiKeyProvider: () -> String?,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun submitMotionAnalysis(
        templateSequence: PoseSequence,
        mimicSequence: PoseSequence
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
            ?: return@withContext Result.failure(IllegalStateException("Missing DeepSeek API key"))

        val requestJson = JSONObject().apply {
            put("template", sequenceToJson(templateSequence))
            put("mimic", sequenceToJson(mimicSequence))
            put("instructions", "Compare athlete motion trajectories and highlight key deviations.")
        }

        val request = Request.Builder()
            .url("https://api.deepseek.com/v1/motion/analyze")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("DeepSeek request failed: ${'$'}{response.code}")
                }
                response.body?.string() ?: "{}"
            }
        }
    }

    private fun sequenceToJson(sequence: PoseSequence): JSONArray {
        val framesArray = JSONArray()
        sequence.toList().forEach { frame ->
            val frameObject = JSONObject().apply {
                put("timestamp", frame.timestampMillis)
                val keypointsArray = JSONArray()
                frame.keypoints.forEach { keypoint ->
                    keypointsArray.put(
                        JSONObject().apply {
                            put("name", keypoint.name)
                            put("x", keypoint.x)
                            put("y", keypoint.y)
                            put("confidence", keypoint.confidence)
                        }
                    )
                }
                put("keypoints", keypointsArray)
            }
            framesArray.put(frameObject)
        }
        return framesArray
    }
}
