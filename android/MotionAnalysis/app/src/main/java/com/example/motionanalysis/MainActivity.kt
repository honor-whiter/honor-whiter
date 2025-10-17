package com.example.motionanalysis

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.motionanalysis.data.MotionAnalysisRepository
import com.example.motionanalysis.data.cloud.DeepSeekClient
import com.example.motionanalysis.data.local.LocalPoseAnalyzer
import com.example.motionanalysis.domain.alignment.MotionAlignmentEngine
import com.example.motionanalysis.domain.analysis.MotionDifferenceCalculator
import com.example.motionanalysis.ui.MotionAnalysisScreen
import com.example.motionanalysis.ui.state.MainViewModel
import com.example.motionanalysis.ui.theme.MotionAnalysisTheme
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val repository = MotionAnalysisRepository(
            poseAnalyzer = LocalPoseAnalyzer(),
            alignmentEngine = MotionAlignmentEngine(),
            differenceCalculator = MotionDifferenceCalculator(),
            deepSeekClient = DeepSeekClient(
                apiKeyProvider = { null },
                httpClient = OkHttpClient()
            )
        )
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MotionAnalysisTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                Surface(modifier = Modifier.fillMaxSize()) {
                    MotionAnalysisScreen(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }
}

@Composable
fun RememberVideoPicker(onResult: (Uri?) -> Unit) = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
) { uri ->
    onResult(uri)
}

