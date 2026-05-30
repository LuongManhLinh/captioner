package io.captioner.ui.export

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.extractor.text.Subtitle
import io.captioner.data.DatabaseContainer
import io.captioner.data.dao.CaptionDao
import io.captioner.data.dao.KaraokeWordDao
import io.captioner.data.dao.ProjectDao
import io.captioner.data.dto.CaptionKaraokeDto
import io.captioner.data.model.Project
import io.captioner.ui.export.services.SubtitleFormat
import io.captioner.ui.export.services.exportSubtitleFile
import io.captioner.ui.export.services.exportVideo
import io.captioner.ui.export.services.getVideoName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val LOG_TAG = "ExportViewModel"
data class ExportState(
    val project: Project? = null,
    val captions: List<CaptionKaraokeDto> = emptyList(),
    val exporting: Boolean = false,
    val progress: Float = 0f,
    val resultUri: Uri? = null,
    val errorMessage: String? = null
)

class ExportViewModel(
    private val projectId: String,
    private val projectDao: ProjectDao,
    private val captionDao: CaptionDao,
    private val karaokeWordDao: KaraokeWordDao
) : ViewModel() {
    private val _state = MutableStateFlow(ExportState())
    val state = _state.asStateFlow()

    private var exportStarted = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val project = projectDao.getById(projectId)
            if (project == null) {
                _state.update {
                    it.copy(
                        errorMessage = "Cannot export video because project does not exist"
                    )
                }
            } else {
                val captions = captionDao.getALlByProjectId(projectId)
                if (project.karaoke) {
                    _state.update {
                        it.copy(
                            project = project,
                            captions = captions.map { c ->
                                val words = karaokeWordDao.getAllByCaptionId(c.id)
                                CaptionKaraokeDto(
                                    caption = c,
                                    karaokeWords = words
                                )
                            }
                        )
                    }


                } else {
                    _state.update {
                        it.copy(
                            project = project,
                            captions = captions.map { c -> CaptionKaraokeDto(caption = c) },
                        )
                    }
                }
            }
        }
    }

    fun startExporting(context: Context, flag: Int) {
        if (exportStarted) return
        exportStarted = true

        if (flag == 1) {
            exportVideo(context)
        } else {
            val subtitleFormat = if (flag == 0) SubtitleFormat.SRT else SubtitleFormat.VTT
            exportSubtitle(context, subtitleFormat)
        }
    }

    private fun exportVideo(context: Context) {
        val curState = _state.value
        val project = curState.project ?: return
        _state.update {
            it.copy(
                exporting = true,
                progress = 0f,
                errorMessage = null,
                resultUri = null
            )
        }
        viewModelScope.launch {
            var resultUri: Uri? = null
            var errorMessage: String? = null
            try {
                resultUri = exportVideo(
                    context = context,
                    inputUri = project.videoUri,
                    captions = curState.captions,
                    videoWidth = project.videoWidth,
                    videoHeight = project.videoHeight,
                    ratio = 1f,
                    onProgressChange = { progress ->
                        _state.update {
                            it.copy(
                                progress = progress.toFloat() / 100
                            )
                        }
                    },
                    karaoke = project.karaoke
                )
            } catch (e: Exception) {
                errorMessage = e.message
            }
            Log.d(LOG_TAG, "Exported to $resultUri")

            _state.update {
                it.copy(
                    exporting = false,
                    progress = if (resultUri == null) it.progress else 1f,
                    errorMessage = if (resultUri == null) {
                        errorMessage ?: "Failed to export"
                    } else null,
                    resultUri = resultUri
                )
            }
        }
    }

    private fun exportSubtitle(context: Context, format: SubtitleFormat) {
        val curState = _state.value
        val project = curState.project ?: return

        _state.update {
            it.copy(exporting = true, progress = 0f, errorMessage = null, resultUri = null)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rawFileName = getVideoName(context, project.videoUri)
                val fileName = rawFileName ?: "subtitle"
                val uri = exportSubtitleFile(
                    context = context,
                    captions = curState.captions,
                    fileName = fileName,
                    format = format
                )
                _state.update {
                    it.copy(
                        exporting = false,
                        resultUri = uri,
                        errorMessage = if (uri == null) "Failed to export subtitle" else null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        exporting = false,
                        errorMessage = e.message ?: "Failed to export subtitle"
                    )
                }
            }
        }
    }

    companion object {
        fun factory(projectId: String): ViewModelProvider.Factory {
            val factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val dbInstance = DatabaseContainer.instance.database

                    @Suppress("UNCHECKED_CAST")
                    return ExportViewModel(
                        projectId = projectId,
                        projectDao = dbInstance.projectDao(),
                        captionDao = dbInstance.captionDao(),
                        karaokeWordDao = dbInstance.karaokeWordDao(),
                    ) as T
                }
            }

            return factory
        }
    }
}