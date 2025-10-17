package com.example.motionanalysis.domain.analysis

import com.example.motionanalysis.domain.model.MotionComparisonMetric
import com.example.motionanalysis.domain.model.MotionTrajectory
import com.example.motionanalysis.domain.model.PoseFrame
import kotlin.math.pow
import kotlin.math.sqrt

class MotionDifferenceCalculator {
    fun computeMetrics(
        templateFrames: List<PoseFrame>,
        mimicFrames: List<PoseFrame>
    ): Pair<List<MotionComparisonMetric>, List<MotionTrajectory>> {
        if (templateFrames.isEmpty() || mimicFrames.isEmpty()) {
            return emptyList<MotionComparisonMetric>() to emptyList()
        }

        val jointNames = templateFrames.first().keypoints.map { it.name }
        val metrics = mutableListOf<MotionComparisonMetric>()
        val trajectories = mutableListOf<MotionTrajectory>()

        for (joint in jointNames) {
            val templateSeries = templateFrames.mapNotNull { frame ->
                frame.keypoints.find { it.name == joint }?.let { sqrt(it.x.pow(2) + it.y.pow(2)) }
            }
            val mimicSeries = mimicFrames.mapNotNull { frame ->
                frame.keypoints.find { it.name == joint }?.let { sqrt(it.x.pow(2) + it.y.pow(2)) }
            }
            if (templateSeries.isEmpty() || mimicSeries.isEmpty()) continue

            val templateAvg = templateSeries.average().toFloat()
            val mimicAvg = mimicSeries.average().toFloat()
            metrics += MotionComparisonMetric(joint, templateAvg, mimicAvg)
            trajectories += MotionTrajectory(joint, templateSeries, mimicSeries)
        }

        return metrics to trajectories
    }
}
