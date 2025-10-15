package com.example.jumprecorder

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class WorkoutSessionViewModel : ViewModel() {

    private val _activeType = MutableLiveData(WorkoutCounter.WorkoutType.UNKNOWN)
    val activeType: LiveData<WorkoutCounter.WorkoutType> = _activeType

    private val _counts = MutableLiveData(emptyCounts())
    val counts: LiveData<Map<WorkoutCounter.WorkoutType, Int>> = _counts

    private val _isRecording = MutableLiveData(false)
    val isRecording: LiveData<Boolean> = _isRecording

    private val _summary = MutableLiveData<Map<WorkoutCounter.WorkoutType, Int>?>(null)
    val summary: LiveData<Map<WorkoutCounter.WorkoutType, Int>?> = _summary

    private val _confidence = MutableLiveData(0f)
    val confidence: LiveData<Float> = _confidence

    private val _progress = MutableLiveData(0f)
    val progress: LiveData<Float> = _progress

    fun onSessionStart() {
        _isRecording.value = true
        _summary.value = null
        _counts.value = emptyCounts()
        _confidence.value = 0f
        _progress.value = 0f
        _activeType.value = WorkoutCounter.WorkoutType.UNKNOWN
    }

    fun onSessionStop(snapshot: WorkoutCounter.WorkoutUpdate) {
        _isRecording.value = false
        _counts.value = snapshot.counts
        _summary.value = snapshot.counts
        _confidence.value = snapshot.confidence
        _progress.value = snapshot.progress
        _activeType.value = snapshot.activeType
    }

    fun onWorkoutUpdate(update: WorkoutCounter.WorkoutUpdate) {
        _activeType.value = update.activeType
        _counts.value = update.counts
        _confidence.value = update.confidence
        _progress.value = update.progress
    }

    private fun emptyCounts(): Map<WorkoutCounter.WorkoutType, Int> = mapOf(
        WorkoutCounter.WorkoutType.PUSH_UP to 0,
        WorkoutCounter.WorkoutType.SQUAT to 0,
        WorkoutCounter.WorkoutType.PULL_UP to 0
    )
}
