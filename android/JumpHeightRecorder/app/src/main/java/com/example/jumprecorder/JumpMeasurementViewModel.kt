package com.example.jumprecorder

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 负责处理跳跃高度估算逻辑的 ViewModel。
 * 真正的计算工作委托给 [JumpAnalyzer]，这样方便未来替换为更复杂的算法。
 */
class JumpMeasurementViewModel : ViewModel() {

    private val analyzer = JumpAnalyzer()

    private val _jumpEstimate = MutableLiveData<String>("")
    val jumpEstimate: LiveData<String> = _jumpEstimate

    /**
     * 由界面输入的人物真实身高（厘米）。
     */
    var personHeightCm: Double = 0.0
        private set

    val referencePixelPerCm: Double
        get() = analyzer.pixelPerCentimeter

    fun updatePersonHeight(height: Double) {
        personHeightCm = height
        analyzer.updatePersonHeight(height)
    }

    fun onVideoReady(videoFile: File) {
        viewModelScope.launch(Dispatchers.Default) {
            val result = analyzer.estimateJumpHeight(videoFile)
            _jumpEstimate.postValue(result)
        }
    }
}
