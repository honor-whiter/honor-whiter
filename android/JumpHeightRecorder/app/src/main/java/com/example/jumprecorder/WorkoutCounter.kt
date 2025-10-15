package com.example.jumprecorder

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.min

class WorkoutCounter {

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

    fun reset() {
        counts.keys.forEach { counts[it] = 0 }
        activeType = WorkoutType.UNKNOWN
        lastExtremum = null
        lastProgress = 1f
        lastConfidence = 0f
        framesWithoutConfidence = 0
    }

    fun snapshot(): WorkoutUpdate = WorkoutUpdate(activeType, counts.toMap(), lastConfidence, lastProgress)

    fun onPoseDetected(pose: Pose): WorkoutUpdate? {
        val metrics = buildMap {
            computePushUpProgress(pose)?.let { put(WorkoutType.PUSH_UP, it) }
            computeSquatProgress(pose)?.let { put(WorkoutType.SQUAT, it) }
            computePullUpProgress(pose)?.let { put(WorkoutType.PULL_UP, it) }
        }

        if (metrics.isEmpty()) {
            handleLowConfidence()
            lastProgress = 1f
            lastConfidence = 0f
            return WorkoutUpdate(activeType, counts.toMap(), lastConfidence, lastProgress)
        }

        val bestEntry = metrics.maxByOrNull { it.value.confidence }
        if (activeType == WorkoutType.UNKNOWN) {
            if (bestEntry != null && bestEntry.value.confidence >= CONFIDENCE_THRESHOLD) {
                activateType(bestEntry.key, bestEntry.value.progress)
            }
        } else {
            val activeMetrics = metrics[activeType]
            if (activeMetrics == null || activeMetrics.confidence < MAINTAIN_THRESHOLD) {
                framesWithoutConfidence++
                if (framesWithoutConfidence > RESET_AFTER_FRAMES) {
                    if (bestEntry != null && bestEntry.value.confidence >= CONFIDENCE_THRESHOLD) {
                        activateType(bestEntry.key, bestEntry.value.progress)
                    } else {
                        deactivateType()
                    }
                }
            } else {
                framesWithoutConfidence = 0
                updateProgress(activeMetrics.progress)
                lastConfidence = activeMetrics.confidence
            }
        }

        val activeMetrics = metrics[activeType]
        if (activeMetrics != null) {
            lastConfidence = activeMetrics.confidence
            updateProgress(activeMetrics.progress)
        }

        return WorkoutUpdate(activeType, counts.toMap(), lastConfidence, lastProgress)
    }

    private fun activateType(type: WorkoutType, initialProgress: Float) {
        activeType = type
        framesWithoutConfidence = 0
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

    private fun handleLowConfidence() {
        framesWithoutConfidence++
        if (framesWithoutConfidence > RESET_AFTER_FRAMES) {
            deactivateType()
        }
    }

    private data class Metrics(val progress: Float, val confidence: Float)

    private fun computePushUpProgress(pose: Pose): Metrics? {
        val left = computeElbowAngle(pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
        val right = computeElbowAngle(pose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
        val angle = listOfNotNull(left?.first, right?.first)
        if (angle.isEmpty()) return null
        val avgAngle = angle.average().toFloat()
        val confidence = min(left?.second ?: 1f, right?.second ?: 1f)
        val progress = normalize(avgAngle, PUSH_UP_MIN_ANGLE, PUSH_UP_MAX_ANGLE)
        return Metrics(progress, confidence)
    }

    private fun computeSquatProgress(pose: Pose): Metrics? {
        val left = computeKneeAngle(pose, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
        val right = computeKneeAngle(pose, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
        val angle = listOfNotNull(left?.first, right?.first)
        if (angle.isEmpty()) return null
        val avgAngle = angle.average().toFloat()
        val confidence = min(left?.second ?: 1f, right?.second ?: 1f)
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

    private fun computeElbowAngle(
        pose: Pose,
        shoulderType: Int,
        elbowType: Int,
        wristType: Int
    ): Pair<Float, Float>? {
        val shoulder = pose.getPoseLandmark(shoulderType) ?: return null
        val elbow = pose.getPoseLandmark(elbowType) ?: return null
        val wrist = pose.getPoseLandmark(wristType) ?: return null
        val angle = computeAngleDegrees(shoulder, elbow, wrist)
        val confidence = min(shoulder.inFrameLikelihood, min(elbow.inFrameLikelihood, wrist.inFrameLikelihood))
        return angle to confidence
    }

    private fun computeKneeAngle(
        pose: Pose,
        hipType: Int,
        kneeType: Int,
        ankleType: Int
    ): Pair<Float, Float>? {
        val hip = pose.getPoseLandmark(hipType) ?: return null
        val knee = pose.getPoseLandmark(kneeType) ?: return null
        val ankle = pose.getPoseLandmark(ankleType) ?: return null
        val angle = computeAngleDegrees(hip, knee, ankle)
        val confidence = min(hip.inFrameLikelihood, min(knee.inFrameLikelihood, ankle.inFrameLikelihood))
        return angle to confidence
    }

    private fun computeAngleDegrees(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Float {
        val radians = kotlin.math.atan2(c.position.y - b.position.y, c.position.x - b.position.x) -
            kotlin.math.atan2(a.position.y - b.position.y, a.position.x - b.position.x)
        var angle = Math.toDegrees(radians.toDouble()).toFloat()
        angle = abs(angle)
        if (angle > 180f) {
            angle = 360f - angle
        }
        return angle
    }

    private fun normalize(value: Float, min: Float, max: Float): Float {
        if (max - min == 0f) return 0f
        return ((value - min) / (max - min)).coerceIn(0f, 1f)
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.45f
        private const val MAINTAIN_THRESHOLD = 0.35f
        private const val RESET_AFTER_FRAMES = 15
        private const val TOP_THRESHOLD = 0.72f
        private const val BOTTOM_THRESHOLD = 0.35f
        private const val HYSTERESIS = 0.05f

        private const val PUSH_UP_MIN_ANGLE = 55f
        private const val PUSH_UP_MAX_ANGLE = 165f
        private const val SQUAT_MIN_ANGLE = 60f
        private const val SQUAT_MAX_ANGLE = 170f
        private const val PULL_UP_MIN_PROGRESS = -0.2f
        private const val PULL_UP_MAX_PROGRESS = 0.8f
    }
}
