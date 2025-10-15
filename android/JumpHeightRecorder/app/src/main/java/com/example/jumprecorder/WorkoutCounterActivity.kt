package com.example.jumprecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.Recording
import androidx.core.content.ContextCompat
import com.example.jumprecorder.databinding.ActivityWorkoutCounterBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

class WorkoutCounterActivity : ComponentActivity() {

    private lateinit var binding: ActivityWorkoutCounterBinding
    private val viewModel: WorkoutSessionViewModel by viewModels()

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var latestVideoFile: File? = null

    private lateinit var poseAnalyzer: WorkoutPoseAnalyzer
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

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
        binding = ActivityWorkoutCounterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        poseAnalyzer = WorkoutPoseAnalyzer(
            counter = WorkoutCounter(),
            mainExecutor = mainExecutor()
        ) { update ->
            viewModel.onWorkoutUpdate(update)
        }

        binding.btnToggleWorkout.setOnClickListener {
            if (viewModel.isRecording.value == true) {
                stopSession()
            } else {
                startSession()
            }
        }

        binding.btnShareSummary.setOnClickListener { shareSummary() }

        observeViewModel()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun mainExecutor(): Executor = ContextCompat.getMainExecutor(this)

    private fun observeViewModel() {
        viewModel.activeType.observe(this) { type ->
            binding.tvDetectedAction.text = getString(
                R.string.workout_active_template,
                workoutLabel(type)
            )
        }

        viewModel.counts.observe(this) { counts ->
            binding.tvActionCounts.text = formatCountsText(counts)
        }

        viewModel.isRecording.observe(this) { recording ->
            binding.btnToggleWorkout.text = getString(
                if (recording) R.string.workout_stop_session else R.string.workout_start_session
            )
            if (recording) {
                binding.tvWorkoutSummary.text = getString(R.string.workout_recording_hint)
                binding.btnShareSummary.isEnabled = false
            } else if (viewModel.summary.value == null) {
                binding.tvWorkoutSummary.text = ""
                binding.btnShareSummary.isEnabled = false
            }
        }

        viewModel.summary.observe(this) { summary ->
            if (summary != null) {
                val hasData = summary.values.any { it > 0 }
                binding.tvWorkoutSummary.text = if (hasData) {
                    Toast.makeText(this, R.string.workout_summary_ready, Toast.LENGTH_SHORT).show()
                    formatSummary(summary)
                } else {
                    getString(R.string.workout_counts_empty)
                }
                binding.btnShareSummary.isEnabled = hasData && viewModel.isRecording.value == false
            } else if (viewModel.isRecording.value != true) {
                binding.tvWorkoutSummary.text = ""
                binding.btnShareSummary.isEnabled = false
            }
        }
    }

    private fun startSession() {
        if (!allPermissionsGranted()) {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
            return
        }
        if (videoCapture == null) {
            Toast.makeText(this, R.string.camera_initialization_failed, Toast.LENGTH_LONG).show()
            startCamera()
            return
        }
        viewModel.onSessionStart()
        poseAnalyzer.setCountingEnabled(true)
        startRecording()
        Toast.makeText(this, R.string.workout_recording_hint, Toast.LENGTH_SHORT).show()
    }

    private fun stopSession() {
        poseAnalyzer.setCountingEnabled(false)
        val snapshot = poseAnalyzer.snapshot()
        viewModel.onSessionStop(snapshot)
        activeRecording?.stop()
        activeRecording = null
    }

    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return
        val videoFile = createVideoFile()
        latestVideoFile = null
        val fileOutputOptions = FileOutputOptions.Builder(videoFile).build()
        activeRecording = videoCapture.output
            .prepareRecording(this, fileOutputOptions)
            .apply {
                if (allPermissionsGranted()) {
                    withAudioEnabled()
                }
            }
            .start(mainExecutor()) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        // no-op, UI 已通过 ViewModel 更新
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            latestVideoFile = videoFile
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
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.workoutPreview.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(VideoCapture.Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analysis ->
                    analysis.setAnalyzer(mainExecutor(), poseAnalyzer)
                }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    videoCapture,
                    imageAnalysis
                )
            } catch (exception: Exception) {
                Toast.makeText(this, R.string.camera_initialization_failed, Toast.LENGTH_LONG).show()
            }
        }, mainExecutor())
    }

    private fun shareSummary() {
        val summary = viewModel.summary.value ?: return
        val hasData = summary.values.any { it > 0 }
        if (!hasData) return
        val summaryText = formatSummary(summary)
        val builder = StringBuilder(summaryText)
        latestVideoFile?.let {
            builder.append('\n').append(getString(R.string.video_saved, it.absolutePath))
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.workout_share_title))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.workout_share_message, builder.toString()))
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.workout_share_title)))
    }

    private fun formatCountsText(counts: Map<WorkoutCounter.WorkoutType, Int>): String {
        val segments = listOf(
            WorkoutCounter.WorkoutType.PULL_UP,
            WorkoutCounter.WorkoutType.PUSH_UP,
            WorkoutCounter.WorkoutType.SQUAT
        ).joinToString(" / ") { type ->
            getString(
                R.string.workout_summary_template,
                workoutLabel(type),
                counts[type] ?: 0
            )
        }
        return getString(R.string.workout_counts_template, segments)
    }

    private fun formatSummary(counts: Map<WorkoutCounter.WorkoutType, Int>): String {
        val meaningful = counts.filter { it.value > 0 }
        return if (meaningful.isEmpty()) {
            getString(R.string.workout_counts_empty)
        } else {
            meaningful.entries.joinToString("\n") { (type, value) ->
                getString(R.string.workout_summary_template, workoutLabel(type), value)
            }
        }
    }

    private fun workoutLabel(type: WorkoutCounter.WorkoutType): String = when (type) {
        WorkoutCounter.WorkoutType.PUSH_UP -> getString(R.string.workout_type_push_up)
        WorkoutCounter.WorkoutType.SQUAT -> getString(R.string.workout_type_squat)
        WorkoutCounter.WorkoutType.PULL_UP -> getString(R.string.workout_type_pull_up)
        WorkoutCounter.WorkoutType.UNKNOWN -> getString(R.string.workout_type_unknown)
    }

    private fun allPermissionsGranted(): Boolean = REQUIRED_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
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
        return File(outputDirectory, "WORKOUT_$timestamp.mp4")
    }

    override fun onDestroy() {
        super.onDestroy()
        poseAnalyzer.setCountingEnabled(false)
        poseAnalyzer.close()
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}
