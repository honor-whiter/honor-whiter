package com.example.jumprecorder

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class WorkoutCounter(private val classifier: PoseClassifier? = null) {

    enum class WorkoutType {
        PUSH_UP,
        SQUAT,
        PULL_UP,
        UNKNOWN
    }

    data class WorkoutUpdate(
        val activeType: WorkoutType,
        val counts: Map<WorkoutType, Int>,
        val confidence: Float,
        val progress: Float
    )

    private enum class Extremum {
        TOP,
        BOTTOM
    }

    private val counts: MutableMap<WorkoutType, Int> = mutableMapOf(
        WorkoutType.PUSH_UP to 0,
        WorkoutType.SQUAT to 0,
        WorkoutType.PULL_UP to 0
    )

    private var activeType: WorkoutType = WorkoutType.UNKNOWN
    private var lastExtremum: Extremum? = null
    private var lastProgress: Float = 1f
    private var lastConfidence: Float = 0f
    private var framesWithoutConfidence: Int = 0
    private var classifierFramesWithoutConfidence: Int = 0

    fun reset() {
        counts.keys.forEach { counts[it] = 0 }
        activeType = WorkoutType.UNKNOWN
        lastExtremum = null
        lastProgress = 1f
        lastConfidence = 0f
        framesWithoutConfidence = 0
        classifierFramesWithoutConfidence = 0
    }

    fun snapshot(): WorkoutUpdate = WorkoutUpdate(activeType, counts.toMap(), lastConfidence, lastProgress)

    fun onPoseDetected(pose: Pose): WorkoutUpdate? {
        val classification = classifier?.classify(pose)
        val classifierProbabilities = classification?.probabilities ?: emptyMap()
        val classifierTopType = classification?.topType
        val classifierTopScore = classification?.topConfidence ?: 0f

        val metrics = buildMap {
            computePushUpProgress(pose)?.let { put(WorkoutType.PUSH_UP, it) }
            computeSquatProgress(pose)?.let { put(WorkoutType.SQUAT, it) }
            computePullUpProgress(pose)?.let { put(WorkoutType.PULL_UP, it) }
        }

        if (metrics.isEmpty()) {
            framesWithoutConfidence++
            if (classifier != null) {
                if (classifierTopScore >= CLASSIFIER_MAINTAIN_THRESHOLD) {
                    classifierFramesWithoutConfidence = 0
                } else {
                    classifierFramesWithoutConfidence++
                }
            }
            if (shouldReset()) {
                if (classifierTopType != null && classifierTopScore >= CLASSIFIER_ACTIVATION_THRESHOLD) {
                    activateType(classifierTopType, 1f)
                } else {
                    deactivateType()
                }
            }
            lastProgress = 1f
            lastConfidence = classifierTopScore
            return WorkoutUpdate(activeType, counts.toMap(), lastConfidence, lastProgress)
        }

        val bestEntry = metrics.maxByOrNull { it.value.confidence }
        if (activeType == WorkoutType.UNKNOWN) {
            when {
                classifierTopType != null && classifierTopScore >= CLASSIFIER_ACTIVATION_THRESHOLD -> {
                    activateType(classifierTopType, metrics[classifierTopType]?.progress ?: 1f)
                }

                bestEntry != null && bestEntry.value.confidence >= CONFIDENCE_THRESHOLD -> {
                    activateType(bestEntry.key, bestEntry.value.progress)
                }
            }
        } else {
            val activeMetrics = metrics[activeType]
            if (activeMetrics == null || activeMetrics.confidence < MAINTAIN_THRESHOLD) {
                framesWithoutConfidence++
            } else {
                framesWithoutConfidence = 0
            }
        }

        if (classifier != null) {
            val scoreForActive = if (activeType == WorkoutType.UNKNOWN) {
                classifierTopScore
            } else {
                val probability = classifierProbabilities[activeType] ?: 0f
                probability * (classification?.featureConfidence ?: 1f)
            }
            if (scoreForActive >= CLASSIFIER_MAINTAIN_THRESHOLD) {
                classifierFramesWithoutConfidence = 0
            } else {
                classifierFramesWithoutConfidence++
            }
        }

        if (shouldReset()) {
            when {
                classifierTopType != null && classifierTopScore >= CLASSIFIER_ACTIVATION_THRESHOLD -> {
                    activateType(classifierTopType, metrics[classifierTopType]?.progress ?: 1f)
                }

                bestEntry != null && bestEntry.value.confidence >= CONFIDENCE_THRESHOLD -> {
                    activateType(bestEntry.key, bestEntry.value.progress)
                }

                else -> deactivateType()
            }
        }

        val activeMetrics = metrics[activeType]
        if (activeMetrics != null) {
            val classifierScore = if (classifier != null) {
                val probability = classifierProbabilities[activeType] ?: 0f
                probability * (classification?.featureConfidence ?: 1f)
            } else {
                0f
            }
            lastConfidence = combineConfidence(activeMetrics.confidence, classifierScore)
            updateProgress(activeMetrics.progress)
        } else {
            lastConfidence = classifierTopScore
        }

        return WorkoutUpdate(activeType, counts.toMap(), lastConfidence, lastProgress)
    }

    private fun activateType(type: WorkoutType, initialProgress: Float) {
        activeType = type
        framesWithoutConfidence = 0
        classifierFramesWithoutConfidence = 0
        lastConfidence = 0f
        lastProgress = initialProgress
        lastExtremum = if (initialProgress > 0.5f) Extremum.TOP else Extremum.BOTTOM
    }

    private fun deactivateType() {
        activeType = WorkoutType.UNKNOWN
        lastExtremum = null
        lastProgress = 1f
        lastConfidence = 0f
        framesWithoutConfidence = 0
        classifierFramesWithoutConfidence = 0
    }

    private fun updateProgress(progress: Float) {
        if (activeType == WorkoutType.UNKNOWN) {
            lastProgress = progress
            return
        }

        if (progress <= BOTTOM_THRESHOLD && lastProgress > BOTTOM_THRESHOLD + HYSTERESIS) {
            if (lastExtremum == Extremum.TOP) {
                lastExtremum = Extremum.BOTTOM
            } else if (lastExtremum == null) {
                lastExtremum = Extremum.BOTTOM
            }
        } else if (progress >= TOP_THRESHOLD && lastProgress < TOP_THRESHOLD - HYSTERESIS) {
            if (lastExtremum == Extremum.BOTTOM) {
                counts[activeType] = counts.getValue(activeType) + 1
                lastExtremum = Extremum.TOP
            } else if (lastExtremum == null) {
                lastExtremum = Extremum.TOP
            }
        }

        lastProgress = progress
    }

    private data class Metrics(val progress: Float, val confidence: Float)

    private fun computePushUpProgress(pose: Pose): Metrics? {
        val left = PoseFeatureExtractor.computeJointAngle(
            pose,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_WRIST,
        )
        val right = PoseFeatureExtractor.computeJointAngle(
            pose,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_WRIST,
        )
        val angle = listOfNotNull(left?.angle, right?.angle)
        if (angle.isEmpty()) return null
        val avgAngle = angle.average().toFloat()
        val confidence = min(left?.confidence ?: 1f, right?.confidence ?: 1f)
        val progress = normalize(avgAngle, PUSH_UP_MIN_ANGLE, PUSH_UP_MAX_ANGLE)
        return Metrics(progress, confidence)
    }

    private fun computeSquatProgress(pose: Pose): Metrics? {
        val left = PoseFeatureExtractor.computeJointAngle(
            pose,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.LEFT_ANKLE,
        )
        val right = PoseFeatureExtractor.computeJointAngle(
            pose,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.RIGHT_ANKLE,
        )
        val angle = listOfNotNull(left?.angle, right?.angle)
        if (angle.isEmpty()) return null
        val avgAngle = angle.average().toFloat()
        val confidence = min(left?.confidence ?: 1f, right?.confidence ?: 1f)
        val progress = normalize(avgAngle, SQUAT_MIN_ANGLE, SQUAT_MAX_ANGLE)
        return Metrics(progress, confidence)
    }

    private fun computePullUpProgress(pose: Pose): Metrics? {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        if (leftShoulder == null || rightShoulder == null || leftWrist == null || rightWrist == null) {
            return null
        }

        val leftDiff = leftShoulder.position.y - leftWrist.position.y
        val rightDiff = rightShoulder.position.y - rightWrist.position.y
        val torsoLeft = if (leftHip != null) abs(leftHip.position.y - leftShoulder.position.y) else null
        val torsoRight = if (rightHip != null) abs(rightHip.position.y - rightShoulder.position.y) else null
        val torso = listOfNotNull(torsoLeft, torsoRight).maxOrNull() ?: return null
        if (torso <= 0f) return null

        val diff = (leftDiff + rightDiff) / 2f
        val normalized = diff / torso
        val progress = normalize(normalized, PULL_UP_MIN_PROGRESS, PULL_UP_MAX_PROGRESS)
        val confidence = min(
            min(leftShoulder.inFrameLikelihood, rightShoulder.inFrameLikelihood),
            min(leftWrist.inFrameLikelihood, rightWrist.inFrameLikelihood)
        )
        return Metrics(progress, confidence)
    }

    private fun normalize(value: Float, min: Float, max: Float): Float {
        if (max - min == 0f) return 0f
        return ((value - min) / (max - min)).coerceIn(0f, 1f)
    }

    private fun shouldReset(): Boolean {
        return framesWithoutConfidence > RESET_AFTER_FRAMES ||
            (classifier != null && classifierFramesWithoutConfidence > RESET_AFTER_FRAMES)
    }

    private fun combineConfidence(heuristicConfidence: Float, classifierScore: Float): Float {
        if (heuristicConfidence <= 0f && classifierScore <= 0f) {
            return 0f
        }
        if (heuristicConfidence <= 0f) {
            return classifierScore.coerceIn(0f, 1f)
        }
        if (classifierScore <= 0f) {
            return heuristicConfidence.coerceIn(0f, 1f)
        }
        return sqrt(heuristicConfidence * classifierScore).coerceIn(0f, 1f)
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.45f
        private const val MAINTAIN_THRESHOLD = 0.35f
        private const val RESET_AFTER_FRAMES = 15
        private const val TOP_THRESHOLD = 0.72f
        private const val BOTTOM_THRESHOLD = 0.35f
        private const val HYSTERESIS = 0.05f

        private const val CLASSIFIER_ACTIVATION_THRESHOLD = 0.55f
        private const val CLASSIFIER_MAINTAIN_THRESHOLD = 0.4f

        private const val PUSH_UP_MIN_ANGLE = 55f
        private const val PUSH_UP_MAX_ANGLE = 165f
        private const val SQUAT_MIN_ANGLE = 60f
        private const val SQUAT_MAX_ANGLE = 170f
        private const val PULL_UP_MIN_PROGRESS = -0.2f
        private const val PULL_UP_MAX_PROGRESS = 0.8f
    }
}
