package com.example.jumprecorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.Recording
import androidx.core.content.ContextCompat
import com.example.jumprecorder.databinding.ActivityJumpHeightBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class JumpHeightActivity : ComponentActivity() {

    private lateinit var binding: ActivityJumpHeightBinding
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val viewModel: JumpMeasurementViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJumpHeightBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvResult.text = getString(R.string.estimation_placeholder)

        binding.btnEstimate.setOnClickListener {
            val inputHeight = binding.etPersonHeight.text?.toString()?.toDoubleOrNull()
            if (inputHeight != null) {
                viewModel.updatePersonHeight(inputHeight)
                val placeholder = getString(
                    R.string.estimated_ratio_template,
                    viewModel.referencePixelPerCm
                )
                binding.tvResult.text = placeholder
            } else {
                Toast.makeText(this, R.string.invalid_height, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRecord.setOnClickListener {
            if (activeRecording != null) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        viewModel.jumpEstimate.observe(this) { estimate ->
            if (!estimate.isNullOrBlank()) {
                binding.tvResult.text = estimate
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(VideoCapture.Quality.HD))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    videoCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, R.string.camera_initialization_failed, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return
        val videoFile = createVideoFile()
        val fileOutputOptions = FileOutputOptions.Builder(videoFile).build()
        val currentRecording = videoCapture.output
            .prepareRecording(this, fileOutputOptions)
            .apply {
                if (allPermissionsGranted()) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        binding.btnRecord.text = getString(R.string.stop_recording)
                    }

                    is VideoRecordEvent.Finalize -> {
                        binding.btnRecord.text = getString(R.string.start_recording)
                        if (!recordEvent.hasError()) {
                            viewModel.onVideoReady(videoFile)
                            Toast.makeText(
                                this,
                                getString(R.string.video_saved, videoFile.absolutePath),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            videoFile.delete()
                            Toast.makeText(this, R.string.video_save_failed, Toast.LENGTH_LONG).show()
                        }
                        activeRecording = null
                    }
                }
            }
        activeRecording = currentRecording
    }

    private fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    private fun createVideoFile(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        val outputDirectory = if (mediaDir != null && mediaDir.exists()) {
            mediaDir
        } else {
            File(filesDir, resources.getString(R.string.app_name)).apply { mkdirs() }
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(System.currentTimeMillis())
        return File(outputDirectory, "JUMP_$timestamp.mp4")
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(baseContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}
