package com.example.jumprecorder

import android.content.res.AssetManager
import android.util.Log
import com.google.mlkit.vision.pose.Pose
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.exp

class PoseClassifier private constructor(
    private val featureNames: List<String>,
    private val angleFeatures: Set<String>,
    private val featureMeans: FloatArray,
    private val featureStds: FloatArray,
    private val prototypes: Map<WorkoutCounter.WorkoutType, FloatArray>,
) {

    data class ClassificationResult(
        val probabilities: Map<WorkoutCounter.WorkoutType, Float>,
        val featureConfidence: Float,
    ) {
        val topType: WorkoutCounter.WorkoutType? = probabilities.maxByOrNull { it.value }?.key
        val topConfidence: Float = topType?.let { (probabilities[it] ?: 0f) * featureConfidence } ?: 0f
    }

    fun classify(pose: Pose): ClassificationResult? {
        val features = PoseFeatureExtractor.extractClassifierFeatures(pose) ?: return null
        val normalized = normalizeFeatures(features.values)
        val distances = prototypes.mapValues { (_, prototype) -> squaredDistance(normalized, prototype) }
        if (distances.isEmpty()) return null
        val probabilities = softmaxFromDistances(distances)
        return ClassificationResult(probabilities, features.confidence)
    }

    private fun normalizeFeatures(rawValues: FloatArray): FloatArray {
        val normalized = FloatArray(rawValues.size)
        for (index in rawValues.indices) {
            var value = rawValues[index]
            val featureName = featureNames.getOrNull(index)
            if (featureName != null && angleFeatures.contains(featureName)) {
                value /= 180f
            }
            val mean = featureMeans.getOrNull(index) ?: 0f
            val std = featureStds.getOrNull(index)?.takeIf { it != 0f } ?: 1f
            normalized[index] = (value - mean) / std
        }
        return normalized
    }

    private fun squaredDistance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        val size = minOf(a.size, b.size)
        for (i in 0 until size) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sum / size
    }

    private fun softmaxFromDistances(distances: Map<WorkoutCounter.WorkoutType, Float>): Map<WorkoutCounter.WorkoutType, Float> {
        val scores = distances.mapValues { (_, distance) -> exp(-0.5f * distance) }
        val total = scores.values.sum()
        if (total == 0f) {
            return distances.keys.associateWith { 0f }
        }
        return scores.mapValues { (_, value) -> value / total }
    }

    companion object {
        private const val TAG = "PoseClassifier"

        fun fromAsset(assetManager: AssetManager, fileName: String): PoseClassifier {
            assetManager.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val content = reader.readText()
                    val json = JSONObject(content)
                    val featureNames = json.getJSONArray("feature_names").let { array ->
                        List(array.length()) { index -> array.getString(index) }
                    }
                    val angleFeatures = json.optJSONObject("metadata")?.optJSONArray("angle_features")?.let { array ->
                        buildSet {
                            for (index in 0 until array.length()) {
                                add(array.getString(index))
                            }
                        }
                    } ?: emptySet()
                    val featureMeans = json.getJSONArray("feature_means").let { array ->
                        FloatArray(array.length()) { index -> array.getDouble(index).toFloat() }
                    }
                    val featureStds = json.getJSONArray("feature_stds").let { array ->
                        FloatArray(array.length()) { index ->
                            val value = array.getDouble(index).toFloat()
                            if (value == 0f) 1f else value
                        }
                    }
                    val prototypesJson = json.getJSONObject("prototypes")
                    val prototypes = mutableMapOf<WorkoutCounter.WorkoutType, FloatArray>()
                    prototypesJson.keys().forEach { label ->
                        val type = WorkoutCounter.WorkoutType.values().firstOrNull { it.name == label }
                        if (type == null) {
                            Log.w(TAG, "Unknown workout type in classifier: $label")
                        } else {
                            val vectorJson = prototypesJson.getJSONArray(label)
                            prototypes[type] = FloatArray(vectorJson.length()) { index ->
                                vectorJson.getDouble(index).toFloat()
                            }
                        }
                    }
                    return PoseClassifier(featureNames, angleFeatures, featureMeans, featureStds, prototypes)
                }
            }
        }
    }
}
