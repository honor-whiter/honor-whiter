package com.example.motionanalysis.domain.model

data class MotionTrajectory(
    val jointName: String,
    val templateSeries: List<Float>,
    val mimicSeries: List<Float>
)
