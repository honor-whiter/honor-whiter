package com.example.motionanalysis.data.local

import android.content.Context
import android.net.Uri
import com.example.motionanalysis.domain.model.PoseFrame
import com.example.motionanalysis.domain.model.PoseKeypoint
import com.example.motionanalysis.domain.model.PoseSequence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

class LocalPoseAnalyzer {
    suspend fun extractPoseSequence(context: Context, uri: Uri): PoseSequence = withContext(Dispatchers.Default) {
        // Placeholder implementation. Replace with ML Kit or MediaPipe integration.
        delay(300) // Simulate processing delay
        val random = Random(uri.hashCode())
        val frames = buildList {
            val startTimestamp = 0L
            for (i in 0 until 60) {
                val timestamp = startTimestamp + i * 50L
                add(
                    PoseFrame(
                        timestamp,
                        listOf(
                            PoseKeypoint("左肩", random.nextFloat(), random.nextFloat(), 0.9f),
                            PoseKeypoint("右肩", random.nextFloat(), random.nextFloat(), 0.9f),
                            PoseKeypoint("左膝", random.nextFloat(), random.nextFloat(), 0.85f),
                            PoseKeypoint("右膝", random.nextFloat(), random.nextFloat(), 0.85f)
                        )
                    )
                )
            }
        }
        PoseSequence(frames)
    }
}
