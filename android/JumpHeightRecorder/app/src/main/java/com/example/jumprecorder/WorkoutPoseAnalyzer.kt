package com.example.jumprecorder

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import java.util.concurrent.Executor

class WorkoutPoseAnalyzer(
    private val counter: WorkoutCounter,
    private val mainExecutor: Executor,
    private val onUpdate: (WorkoutCounter.WorkoutUpdate) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    private var countingEnabled: Boolean = false

    private val poseDetector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    )

    fun setCountingEnabled(enabled: Boolean) {
        countingEnabled = enabled
        if (enabled) {
            counter.reset()
            onUpdate(counter.snapshot())
        }
    }

    fun snapshot(): WorkoutCounter.WorkoutUpdate = counter.snapshot()

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        if (!countingEnabled) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        poseDetector
            .process(image)
            .addOnSuccessListener(mainExecutor) { pose ->
                if (countingEnabled) {
                    counter.onPoseDetected(pose)?.let { update ->
                        onUpdate(update)
                    }
                }
            }
            .addOnFailureListener(mainExecutor) {
                // Ignore individual frame failures; a continuous stream will recover automatically.
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        poseDetector.close()
    }
}
