package com.example.jumprecorder

import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.math.roundToInt

/**
 * 该类对视频帧做非常粗略的处理，演示如何根据人物身高反推跳跃高度。
 * 实际项目中应该使用 ML Kit、MediaPipe Pose 等骨骼识别工具替换此演示算法。
 */
class JumpAnalyzer {

    private var personHeightCm: Double = 170.0

    /**
     * 每厘米对应的像素数量。真实算法应当依据标定获得，这里提供一个简单估算。
     */
    var pixelPerCentimeter: Double = 5.0
        private set

    fun updatePersonHeight(heightCm: Double) {
        personHeightCm = heightCm
        pixelPerCentimeter = (DEFAULT_PIXEL_PER_CM_REFERENCE * (heightCm / DEFAULT_REFERENCE_HEIGHT_CM))
    }

    fun estimateJumpHeight(videoFile: File): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val frameTimeUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                (it * 1000) / SAMPLE_FRAME_COUNT
            } ?: 0L

            var maxToeOffsetPixels = 0.0
            var sampleIndex = 0
            while (sampleIndex < SAMPLE_FRAME_COUNT) {
                val bitmap = retriever.getFrameAtTime(
                    frameTimeUs * sampleIndex,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: break
                val brightestPixelRow = findBrightestPixelRow(bitmap.width, bitmap.height) { x, y ->
                    bitmap.getPixel(x, y)
                }
                maxToeOffsetPixels = maxOf(maxToeOffsetPixels, brightestPixelRow.toDouble())
                bitmap.recycle()
                sampleIndex++
            }

            val estimatedJumpCm = (maxToeOffsetPixels / pixelPerCentimeter).roundToInt()
            "基于身高 ${personHeightCm.roundToInt()} cm 的估算跳跃高度：$estimatedJumpCm cm (仅示意，需结合真实算法校正)"
        } catch (e: Exception) {
            "视频解析失败：${e.localizedMessage}"
        } finally {
            retriever.release()
        }
    }

    private fun findBrightestPixelRow(width: Int, height: Int, pixelFetcher: (Int, Int) -> Int): Int {
        var brightestRow = 0
        var highestLuma = 0.0
        for (y in 0 until height) {
            var rowLuma = 0.0
            for (x in 0 until width) {
                val pixel = pixelFetcher(x, y)
                val r = (pixel shr 16 and 0xFF).toDouble()
                val g = (pixel shr 8 and 0xFF).toDouble()
                val b = (pixel and 0xFF).toDouble()
                rowLuma += 0.299 * r + 0.587 * g + 0.114 * b
            }
            val averageLuma = rowLuma / width
            if (averageLuma > highestLuma) {
                highestLuma = averageLuma
                brightestRow = y
            }
        }
        return height - brightestRow
    }

    companion object {
        private const val SAMPLE_FRAME_COUNT = 20
        private const val DEFAULT_PIXEL_PER_CM_REFERENCE = 4.5
        private const val DEFAULT_REFERENCE_HEIGHT_CM = 170.0
    }
}
