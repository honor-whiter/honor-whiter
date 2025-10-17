package com.example.motionanalysis.ui.state

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.motionanalysis.data.MotionAnalysisRepository
import com.example.motionanalysis.domain.model.MotionAnalysisResult
import com.example.motionanalysis.domain.model.PoseSequence
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repository: MotionAnalysisRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private var templateSequence: PoseSequence? = null
    private var mimicSequence: PoseSequence? = null

    private var currentJob: Job? = null

    fun onTemplateSelected(uri: Uri) {
        templateSequence = null
        _uiState.value = _uiState.value.copy(
            templateUri = uri,
            cloudResponse = null,
            errorMessage = null
        )
    }

    fun onMimicSelected(uri: Uri) {
        mimicSequence = null
        _uiState.value = _uiState.value.copy(
            mimicUri = uri,
            cloudResponse = null,
            errorMessage = null
        )
    }

    fun analyze(context: Context) {
        val templateUri = _uiState.value.templateUri
        val mimicUri = _uiState.value.mimicUri
        if (templateUri == null || mimicUri == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "请先选择两个视频文件")
            return
        }
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                cloudResponse = null,
                errorMessage = null
            )
            runCatching {
                repository.analyzeLocally(context, templateUri, mimicUri)
            }.onSuccess { analysis ->
                templateSequence = analysis.templateSequence
                mimicSequence = analysis.mimicSequence
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    result = analysis.result
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    errorMessage = throwable.message
                )
            }
        }
    }

    fun sendToCloud() {
        val template = templateSequence
        val mimic = mimicSequence
        if (template == null || mimic == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "请先完成本地分析")
            return
        }
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSendingToCloud = true,
                cloudResponse = null,
                errorMessage = null
            )
            repository.submitToCloud(template, mimic)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isSendingToCloud = false,
                        cloudResponse = response
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isSendingToCloud = false,
                        errorMessage = throwable.message
                    )
                }
        }
    }
}

data class AnalysisUiState(
    val templateUri: Uri? = null,
    val mimicUri: Uri? = null,
    val isAnalyzing: Boolean = false,
    val isSendingToCloud: Boolean = false,
    val result: MotionAnalysisResult? = null,
    val cloudResponse: String? = null,
    val errorMessage: String? = null
)
