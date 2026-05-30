package io.captioner.ui.editor

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.captioner.ui.editor.components.BottomControlBar
import io.captioner.ui.editor.components.CaptionStyleSheet
import io.captioner.ui.editor.components.Editor
import io.captioner.ui.editor.components.TopControlBar

@Composable
fun EditorScreen(
    projectId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onExport: (projectId: String, flag: Int) -> Unit = { _, _ -> },
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.factory(projectId))
) {
    val state by viewModel.state.collectAsState()
    val project = state.project
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.getOrInitializeThumbnails(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            if (!state.videoMaximized) {
                TopControlBar(onBack = onBack, onExport = { viewModel.showExportDialog(true) })
            }
        },
        bottomBar = {
            if (!state.videoMaximized) {
                BottomControlBar(
                    onGenerate = { viewModel.showGenerationDialog(true) },
                    onEditAllCaptions = viewModel::openSheetForProject,
                    onAddNormalCaption = viewModel::addNormalCaption,
                    onAddKaraokeCaption = viewModel::addKaraokeCaption,
                    onAddWord = viewModel::addWord,
                    onEditSelected = viewModel::openSheetForSelection,
                    onDeleteSelected = viewModel::deleteSelection,
                    showSelected = state.selection !is Selection.None,
                    karaoke = project?.karaoke ?: false,
                )
            }
        }
    ) { innerPadding ->
        if (state.projectLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (project == null) {
            onBack()
        } else {
            val viewScale = if (state.viewWidth != null) {

                state.viewWidth!!.toFloat() / project.videoWidth

            } else {
                1f
            }
            Editor(
                modifier = Modifier.padding(innerPadding),
                videoUri = project.videoUri,
                setDuration = viewModel::setDuration,
                isPlaying = state.isPlaying,
                captions = state.captions,
                thumbnailUris = state.thumbnailUris,
                karaoke = project.karaoke,
                selection = state.selection,
                durationMs = state.durationMs,
                currentTimeMs = state.currentTimeMs,
                updateProgress = viewModel::updateProgress,
                updateCaptionPosition = viewModel::updateCaptionPosition,
                onViewCaptionTap = viewModel::selectCaption,
                onViewCaptionDoubleTap = { id, karaoke ->
                    viewModel.selectCaption(id, karaoke)
                    viewModel.openSheetForSelection()
                },
                onNormalCaptionTimelineChange = viewModel::updateCaptionTimeline,
                onTimelineCaptionDoubleTap = { id, karaoke ->
                    viewModel.selectCaption(id, karaoke)
                    viewModel.openSheetForSelection()
                },
                onTimelineCaptionTap = viewModel::selectCaption,
                onWordTimelineChange = viewModel::updateWordTimeline,
                onWordTap = viewModel::selectWord,
                onWordDoubleTap = {
                    viewModel.selectWord(it)
                    viewModel.openSheetForSelection()
                },
                setPlaying = viewModel::setPlaying,
                videoWidth = project.videoWidth,
                videoHeight = project.videoHeight,
                viewX = state.viewX,
                viewY = state.viewY,
                viewWidth = state.viewWidth,
                viewHeight = state.viewHeight,
                onViewSizeChanged = viewModel::setViewSize,
                onViewPositionChanged = viewModel::setViewPosition,
                videoMaximized = state.videoMaximized,
                onVideoMaximizedChange = viewModel::changeVideoMaximized,
                pxPerMs = state.pxPerMs,
                onPxPerMsScale = viewModel::scalePxPerMs,
                zoomValue = state.zoomValue,
                onZoomOut = viewModel::zoomOut,
                onZoomIn = viewModel::zoomIn,
                onCaptionDrag = viewModel::dragCaption,
                onWordDrag = viewModel::dragWord,
                viewScale = viewScale,
                timelineHeight = state.timelineHeight,
                onTimelineHeightChange = viewModel::changeTimelineHeight,
                onHeightControllerDoubleTap = viewModel::resetTimelineHeight
            )

            when (val sheetContent = state.openingSheetContent) {
                is SheetContent.Project -> {
                    CaptionStyleSheet(
                        title = "Edit Project",
                        editText = false,
                        karaoke = project.karaoke,
                        captionStyle = sheetContent.captionStyle,
                        onCaptionStyleChange = viewModel::updateProjectSheet,
                        onDismiss = viewModel::closeSheet,
                        editTimeRange = false,
                        onConfirm = {
                            viewModel.applyProjectEdition()
                            viewModel.closeSheet()
                        },
                        viewScale = viewScale
                    )
                }
                is SheetContent.NormalCaption -> {
                    val editingCaption = sheetContent.item
                    CaptionStyleSheet(
                        title = "Edit Caption",
                        text = editingCaption.text,
                        onTextChange = { text ->
                            viewModel.updateCaptionSheet { it.copy(text = text) }
                        },
                        startTimeMs = editingCaption.startTimeMs,
                        onStartTimeChange = { startTime ->
                            viewModel.updateCaptionSheet {
                                it.copy(startTimeMs = startTime.coerceAtLeast(0L))
                            }
                        },
                        endTimeMs = editingCaption.endTimeMs,
                        onEndTimeChange = { endTime ->
                            viewModel.updateCaptionSheet {
                                it.copy(endTimeMs = endTime.coerceAtMost(state.durationMs))
                            }
                        },
                        captionStyle = editingCaption.style,
                        karaoke = false,
                        onCaptionStyleChange = { style ->
                            viewModel.updateCaptionSheet { it.copy(style = style) }
                        },
                        onDismiss = viewModel::closeSheet,
                        onConfirm = {
                            viewModel.applyCaptionEdition()
                            viewModel.closeSheet()
                        },
                        viewScale = viewScale
                    )
                }
                is SheetContent.KaraokeCaption -> {
                    val editingCaption = sheetContent.item
                    CaptionStyleSheet(
                        title = "Edit Karaoke Caption",
                        editText = false,
                        editTimeRange = false,
                        captionStyle = editingCaption.style,
                        karaoke = true,
                        onCaptionStyleChange = { style ->
                            viewModel.updateCaptionSheet { it.copy(style = style) }
                        },
                        onDismiss = viewModel::closeSheet,
                        onConfirm = {
                            viewModel.applyCaptionEdition()
                            viewModel.closeSheet()
                        },
                        viewScale = viewScale
                    )
                }
                is SheetContent.KaraokeWord -> {
                    val word = sheetContent.item
                    CaptionStyleSheet(
                        title = "Edit Karaoke Word",
                        text = word.text,
                        onTextChange = { text ->
                            viewModel.updateWordSheet { it.copy(text = text.trim()) }
                        },
                        startTimeMs = word.startTimeMs,
                        onStartTimeChange = { startTime ->
                            viewModel.updateWordSheet {
                                it.copy(startTimeMs = startTime.coerceAtLeast(0L))
                            }
                        },
                        endTimeMs = word.endTimeMs,
                        onEndTimeChange = { endTime ->
                            viewModel.updateWordSheet {
                                it.copy(endTimeMs = endTime.coerceAtMost(state.durationMs))
                            }
                        },
                        editStyle = false,
                        karaoke = true,
                        onDismiss = viewModel::closeSheet,
                        onConfirm = {
                            viewModel.applyCaptionEdition()
                            viewModel.closeSheet()
                        },
                        viewScale = viewScale
                    )
                }
                else -> {}
            }
            if (state.showGenDialog) {
              GenerationDialog(
                  allowVocalSeparation = project.karaoke,
                  onClose = { viewModel.showGenerationDialog(false) },
                  onGenerateLocally = { viewModel.generateLocally(context) },
                  onGenerateOnServer = { viewModel.generateOnServer(context, false) },
                  onGenerateOnServerAndSeparateVocal = { viewModel.generateOnServer(context, true) }
              )
            } else if (state.showGenExecutingDialog) {
                GenerationExecutingDialog(
                    generating = state.isGenerating,
                    isSuccessful = state.isSuccessful,
                    message = state.genMessage,
                    onClose = viewModel::closeGenerateDialog,
                    onCancel = viewModel::cancelGeneration,
                )
            } else if (state.showExportDialog) {
                ExportDialog(
                    onExportSubtitleFile = {
                        viewModel.showSubtitleFormatDialog(true)
                    },
                    onExportVideo = {
                        if (state.captions.isNotEmpty()) {
                            onExport(projectId, 1)
                        } else {
                            viewModel.toast("No caption to export")
                        }
                    },
                    onClose = { viewModel.showExportDialog(false) }
                )
            } else if (state.showSubFormatDialog) {
                SubtitleFormatDialog(
                    onExportSRT = {
                        if (state.captions.isNotEmpty()) {
                            onExport(projectId, 0)
                        } else {
                            viewModel.toast("No caption to export")
                        }
                    },
                    onExportVTT = {
                        if (state.captions.isNotEmpty()) {
                            onExport(projectId, 2)
                        } else {
                            viewModel.toast("No caption to export")
                        }
                    },
                    onClose = { viewModel.showSubtitleFormatDialog(false) }
                )
            }
        }
    }

    LaunchedEffect(state.toastShowId) {
        Toast.makeText(context, state.toastMessage, Toast.LENGTH_SHORT).show()
    }
}
