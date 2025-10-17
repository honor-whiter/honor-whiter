package com.example.motionanalysis.data

import android.content.Context
import android.net.Uri
import com.example.motionanalysis.data.cloud.DeepSeekClient
import com.example.motionanalysis.data.local.LocalPoseAnalyzer
import com.example.motionanalysis.domain.alignment.MotionAlignmentEngine
import com.example.motionanalysis.domain.analysis.MotionDifferenceCalculator
import com.example.motionanalysis.domain.model.MotionAnalysisResult
import com.example.motionanalysis.domain.model.PoseSequence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MotionAnalysisRepository(
    private val poseAnalyzer: LocalPoseAnalyzer,
    private val alignmentEngine: MotionAlignmentEngine,
    private val differenceCalculator: MotionDifferenceCalculator,
    private val deepSeekClient: DeepSeekClient
) {
    data class LocalAnalysis(
        val result: MotionAnalysisResult,
        val templateSequence: PoseSequence,
        val mimicSequence: PoseSequence
    )

    suspend fun analyzeLocally(
        context: Context,
        templateUri: Uri,
        mimicUri: Uri
    ): LocalAnalysis = withContext(Dispatchers.Default) {
        val templateSequence = poseAnalyzer.extractPoseSequence(context, templateUri)
        val mimicSequence = poseAnalyzer.extractPoseSequence(context, mimicUri)
        val alignment = alignmentEngine.align(templateSequence, mimicSequence)
        val (metrics, trajectories) = differenceCalculator.computeMetrics(
            alignment.template.toList(),
            alignment.mimic.toList()
        )

        val result = MotionAnalysisResult(
            alignmentOffsetMillis = alignment.offsetMillis,
            metrics = metrics,
            trajectories = trajectories,
            overlayVideoPath = null,
            summary = buildSummary(metrics)
        )

        LocalAnalysis(result, alignment.template, alignment.mimic)
    }

    suspend fun submitToCloud(
        templateSequence: PoseSequence,
        mimicSequence: PoseSequence
    ) = deepSeekClient.submitMotionAnalysis(templateSequence, mimicSequence)

    private fun buildSummary(metrics: List<com.example.motionanalysis.domain.model.MotionComparisonMetric>): String {
        if (metrics.isEmpty()) return "未检测到有效的动作差异。"
        val largest = metrics.maxBy { kotlin.math.abs(it.delta) }
        return "差异最大的关节是${'$'}{largest.label}，平均差值为${'$'}{String.format("%.2f", largest.delta)}。"
    }
}
