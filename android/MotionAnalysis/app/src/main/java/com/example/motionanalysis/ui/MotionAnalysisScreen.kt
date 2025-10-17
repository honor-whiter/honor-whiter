package com.example.motionanalysis.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.motionanalysis.R
import com.example.motionanalysis.RememberVideoPicker
import com.example.motionanalysis.domain.model.MotionAnalysisResult
import com.example.motionanalysis.domain.model.MotionTrajectory
import com.example.motionanalysis.ui.state.MainViewModel
import kotlin.math.max

@Composable
fun MotionAnalysisScreen(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val state = viewModel.uiState.collectAsState().value

    val context = LocalContext.current

    val templatePicker = RememberVideoPicker { uri ->
        uri?.let(viewModel::onTemplateSelected)
    }
    val mimicPicker = RememberVideoPicker { uri ->
        uri?.let(viewModel::onMimicSelected)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.app_name)) })
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VideoSelectionSection(
                templateUri = state.templateUri,
                mimicUri = state.mimicUri,
                onTemplateClick = { templatePicker.launch(arrayOf("video/*")) },
                onMimicClick = { mimicPicker.launch(arrayOf("video/*")) }
            )

            Button(
                onClick = { viewModel.analyze(context) },
                enabled = !state.isAnalyzing && state.templateUri != null && state.mimicUri != null
            ) {
                if (state.isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(text = stringResource(id = R.string.start_analysis))
                }
            }

            state.result?.let { result ->
                AnalysisResultSection(result = result)
            } ?: run {
                Text(text = stringResource(id = R.string.no_results))
            }

            Divider()

            Button(
                onClick = { viewModel.sendToCloud() },
                enabled = state.result != null && !state.isSendingToCloud
            ) {
                if (state.isSendingToCloud) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(text = stringResource(id = R.string.send_to_cloud))
                }
            }

            state.cloudResponse?.let { response ->
                Text(text = response, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun VideoSelectionSection(
    templateUri: Uri?,
    mimicUri: Uri?,
    onTemplateClick: () -> Unit,
    onMimicClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onTemplateClick) {
            Text(text = stringResource(id = R.string.template_button))
        }
        Text(text = templateUri?.toString() ?: "未选择模板视频", fontSize = 12.sp)

        Button(onClick = onMimicClick) {
            Text(text = stringResource(id = R.string.mimic_button))
        }
        Text(text = mimicUri?.toString() ?: "未选择模仿者视频", fontSize = 12.sp)
    }
}

@Composable
private fun AnalysisResultSection(result: MotionAnalysisResult) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(id = R.string.analysis_results),
            style = MaterialTheme.typography.titleLarge
        )
        Text(text = result.summary)
        MetricsTable(result)
        TrajectoryChart(result.trajectories)
        OverlayPlaceholder()
    }
}

@Composable
private fun MetricsTable(result: MotionAnalysisResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(id = R.string.metrics_header_metric),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.metrics_header_template),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.metrics_header_mimic),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.metrics_header_delta),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        result.metrics.forEach { metric ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = metric.label, modifier = Modifier.weight(1f))
                Text(text = metric.templateValue.format(), modifier = Modifier.weight(1f))
                Text(text = metric.mimicValue.format(), modifier = Modifier.weight(1f))
                Text(text = metric.delta.format(), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrajectoryChart(trajectories: List<MotionTrajectory>) {
    if (trajectories.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val background = MaterialTheme.colorScheme.surfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        trajectories.forEach { trajectory ->
            Text(text = trajectory.jointName, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(background, MaterialTheme.shapes.medium)
                    .padding(12.dp)
            ) {
                LineChart(trajectory, primary, secondary)
            }
        }
    }
}

@Composable
private fun LineChart(trajectory: MotionTrajectory, primary: Color, secondary: Color) {
    val maxPoints = max(trajectory.templateSeries.size, trajectory.mimicSeries.size)
    if (maxPoints <= 1) {
        Text(text = "数据不足以绘制折线图")
        return
    }
    val templateMax = trajectory.templateSeries.maxOrNull() ?: 1f
    val mimicMax = trajectory.mimicSeries.maxOrNull() ?: 1f
    val overallMax = max(templateMax, mimicMax)

    Canvas(modifier = Modifier.fillMaxSize()) {
        fun drawSeries(series: List<Float>, color: Color) {
            val stepX = size.width / (series.size - 1)
            val points = series.mapIndexed { index, value ->
                val x = index * stepX
                val y = size.height - (value / overallMax) * size.height
                Offset(x, y)
            }
            for (i in 0 until points.lastIndex) {
                drawLine(
                    color = color,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }

        drawSeries(trajectory.templateSeries, primary)
        drawSeries(trajectory.mimicSeries, secondary)
    }
}

@Composable
private fun OverlayPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Text(text = stringResource(id = R.string.overlay_video_title), fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "该区域将展示叠加关键点的视频预览，以及多角度的动作截图。")
    }
}

private fun Float.format(): String = String.format("%.2f", this)
