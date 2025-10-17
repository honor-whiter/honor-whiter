package com.example.motionanalysis.domain.model

data class MotionComparisonMetric(
    val label: String,
    val templateValue: Float,
    val mimicValue: Float
) {
    val delta: Float get() = mimicValue - templateValue
}
