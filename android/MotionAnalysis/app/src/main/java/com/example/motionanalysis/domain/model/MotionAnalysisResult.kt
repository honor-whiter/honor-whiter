package com.example.motionanalysis.domain.model

data class MotionAnalysisResult(
    val alignmentOffsetMillis: Long,
    val metrics: List<MotionComparisonMetric>,
    val trajectories: List<MotionTrajectory>,
    val overlayVideoPath: String?,
    val summary: String
)
