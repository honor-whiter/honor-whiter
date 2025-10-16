package com.example.jumprecorder

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** Utility helpers for extracting geometric features from ML Kit poses. */
object PoseFeatureExtractor {

    data class AngleFeature(val angle: Float, val confidence: Float)

    data class FeatureVector(val values: FloatArray, val confidence: Float)

    private val REQUIRED_CLASSIFIER_LANDMARKS = listOf(
        PoseLandmark.LEFT_SHOULDER,
        PoseLandmark.RIGHT_SHOULDER,
        PoseLandmark.LEFT_ELBOW,
        PoseLandmark.RIGHT_ELBOW,
        PoseLandmark.LEFT_WRIST,
        PoseLandmark.RIGHT_WRIST,
        PoseLandmark.LEFT_HIP,
        PoseLandmark.RIGHT_HIP,
        PoseLandmark.LEFT_KNEE,
        PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE,
        PoseLandmark.RIGHT_ANKLE,
    )

    private const val MIN_VISIBILITY = 0.3f

    fun computeJointAngle(
        pose: Pose,
        firstType: Int,
        middleType: Int,
        lastType: Int,
    ): AngleFeature? {
        val first = pose.getPoseLandmark(firstType) ?: return null
        val middle = pose.getPoseLandmark(middleType) ?: return null
        val last = pose.getPoseLandmark(lastType) ?: return null
        val angle = computeAngleDegrees(first, middle, last)
        val confidence = min(
            first.inFrameLikelihood,
            min(middle.inFrameLikelihood, last.inFrameLikelihood),
        )
        return AngleFeature(angle, confidence)
    }

    fun extractClassifierFeatures(pose: Pose): FeatureVector? {
        val landmarks = REQUIRED_CLASSIFIER_LANDMARKS.map { type ->
            pose.getPoseLandmark(type) ?: return null
        }

        val minVisibility = landmarks.minOf { it.inFrameLikelihood }
        if (minVisibility < MIN_VISIBILITY) {
            return null
        }

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER) ?: return null
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER) ?: return null
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP) ?: return null
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP) ?: return null
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST) ?: return null
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST) ?: return null
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE) ?: return null
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE) ?: return null

        val torsoLength = distance3D(leftShoulder, leftHip).takeIf { it > 0f } ?: 1f

        val leftElbow = computeJointAngle(pose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)
            ?: return null
        val rightElbow = computeJointAngle(pose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
            ?: return null
        val leftKneeAngle = computeJointAngle(pose, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
            ?: return null
        val rightKneeAngle = computeJointAngle(pose, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
            ?: return null

        val midShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
        val midWristY = (leftWrist.position.y + rightWrist.position.y) / 2f
        val midHipY = (leftHip.position.y + rightHip.position.y) / 2f
        val midKneeY = (leftKnee.position.y + rightKnee.position.y) / 2f

        val wristToShoulder = (midShoulderY - midWristY) / torsoLength
        val hipToKnee = (midHipY - midKneeY) / torsoLength

        return FeatureVector(
            values = floatArrayOf(
                leftElbow.angle,
                rightElbow.angle,
                leftKneeAngle.angle,
                rightKneeAngle.angle,
                wristToShoulder,
                hipToKnee,
            ),
            confidence = minVisibility,
        )
    }

    private fun computeAngleDegrees(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Float {
        val abX = a.position.x - b.position.x
        val abY = a.position.y - b.position.y
        val acX = c.position.x - b.position.x
        val acY = c.position.y - b.position.y
        var angle = Math.toDegrees(kotlin.math.atan2(acY.toDouble(), acX.toDouble()) -
            kotlin.math.atan2(abY.toDouble(), abX.toDouble())).toFloat()
        angle = abs(angle)
        if (angle > 180f) {
            angle = 360f - angle
        }
        return angle
    }

    private fun distance3D(a: PoseLandmark, b: PoseLandmark): Float {
        val dx = a.position3D.x - b.position3D.x
        val dy = a.position3D.y - b.position3D.y
        val dz = a.position3D.z - b.position3D.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
