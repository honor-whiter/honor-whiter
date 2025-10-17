package com.example.motionanalysis.domain.model

import kotlin.math.max

class PoseSequence(private val frames: List<PoseFrame>) {
    val size: Int get() = frames.size
    val durationMillis: Long get() = frames.lastOrNull()?.timestampMillis ?: 0L

    fun frameAt(index: Int): PoseFrame = frames[index]

    fun toList(): List<PoseFrame> = frames

    fun resample(targetSize: Int): PoseSequence {
        if (frames.isEmpty() || targetSize <= 0) return PoseSequence(emptyList())
        if (targetSize == frames.size) return PoseSequence(frames)
        val step = (frames.last().timestampMillis - frames.first().timestampMillis).toFloat() / max(targetSize - 1, 1)
        val resampledFrames = buildList {
            for (i in 0 until targetSize) {
                val targetTimestamp = frames.first().timestampMillis + (step * i)
                val frame = frames.minByOrNull { kotlin.math.abs(it.timestampMillis - targetTimestamp.toLong()) }
                if (frame != null) add(frame)
            }
        }
        return PoseSequence(resampledFrames)
    }
}
