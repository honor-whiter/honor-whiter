package com.example.motionanalysis.domain.alignment

import com.example.motionanalysis.domain.model.PoseFrame
import com.example.motionanalysis.domain.model.PoseSequence

class MotionAlignmentEngine {
    data class AlignmentResult(
        val template: PoseSequence,
        val mimic: PoseSequence,
        val offsetMillis: Long
    )

    fun align(template: PoseSequence, mimic: PoseSequence): AlignmentResult {
        if (template.size == 0 || mimic.size == 0) {
            return AlignmentResult(template, mimic, 0L)
        }
        val offset = estimateOffset(template.toList(), mimic.toList())
        val resampledSize = maxOf(template.size, mimic.size)
        val alignedTemplate = template.resample(resampledSize)
        val alignedMimic = mimic.resample(resampledSize)
        return AlignmentResult(alignedTemplate, alignedMimic, offset)
    }

    private fun estimateOffset(template: List<PoseFrame>, mimic: List<PoseFrame>): Long {
        if (template.isEmpty() || mimic.isEmpty()) return 0L
        return mimic.first().timestampMillis - template.first().timestampMillis
    }
}
