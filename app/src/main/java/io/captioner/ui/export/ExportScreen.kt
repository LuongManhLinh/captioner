package io.captioner.ui.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.captioner.ui.export.services.SubtitleFormat

@Composable
fun ExportScreen(
    projectId: String,
    flag: Int, // 1 - Video, 0 - Subtitle SRT, else Subtitle VTT
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    viewModel: ExportViewModel = viewModel(factory = ExportViewModel.factory(projectId))
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.project) {
        if (state.project != null) {
            viewModel.startExporting(context, flag)
        }
    }

    if (flag == 1) {
        ExportVideoScreen(
            exporting = state.exporting,
            progress = state.progress,
            thumbnailUri = state.project?.primaryThumbnailUri,
            resultUri = state.resultUri,
            errorMessage = state.errorMessage,
            onBack = onBack,
            onDone = onDone
        )
    } else {
        ExportSubtitleScreen(
            exporting = state.exporting,
            resultUri = state.resultUri,
            onBack = onBack,
            onDone = onDone,
            format = if (flag == 0) SubtitleFormat.SRT else SubtitleFormat.VTT
        )
    }

}