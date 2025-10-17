package com.example.motionanalysis.domain.model

data class PoseFrame(
    val timestampMillis: Long,
    val keypoints: List<PoseKeypoint>
)
