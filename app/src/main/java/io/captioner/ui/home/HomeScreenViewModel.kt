package io.captioner.ui.home

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.captioner.data.DatabaseContainer
import io.captioner.data.dao.ProjectDao
import io.captioner.data.dao.VideoThumbnailDao
import io.captioner.data.dto.ProjectThumbnailDto
import io.captioner.data.model.Project
import io.captioner.utils.extractAudioAndSave
import io.captioner.utils.extractThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime


private const val LOG_TAG = "HomeViewModel"
data class HomeScreenState(
    val creating: Boolean = false,
    val message: String = "",
    val progress: Float = 0f,
    val projects: List<ProjectThumbnailDto> = emptyList(),
    val selectModeOn: Boolean = false,
    val selectedProjectIds: Set<String> = emptySet()
)
class HomeScreenViewModel(
    private val projectDao: ProjectDao,
    private val videoThumbnailDao: VideoThumbnailDao
) : ViewModel() {
    private val _state = MutableStateFlow(HomeScreenState())
    val state: StateFlow<HomeScreenState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    projects = projectDao.getAllThumbnails()
                )
            }
        }
    }

    fun createNewProject(context: Context, videoUri: Uri, karaoke: Boolean) {
        _state.update {
            it.copy(
                creating = true,
                progress = 0f,
                message = "Extracting thumbnails..."
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val thumbnailResult = extractThumbnail(context, videoUri)

                if (thumbnailResult == null) {
                    _state.update {
                        it.copy(
                            message = "CANNOT EXTRACT THUMBNAIL!"
                        )
                    }
                    return@launch
                }


                val audioPath = extractAudioAndSave(context, videoUri) { progress, msg ->
                    _state.update {
                        it.copy(
                            progress = 0.4f + 0.59f * progress,
                            message = msg
                        )
                    }
                }

                if (audioPath == null) {
                    _state.update {
                        it.copy(
                            message = "CANNOT EXTRACT AUDIO!",
                            creating = false
                        )
                    }
                    return@launch
                }

                val project = Project(
                    videoUri = videoUri,
                    primaryThumbnailUri = thumbnailResult.primaryThumbnailUri,
                    audioPath = audioPath,
                    createdAt = LocalDateTime.now(),
                    karaoke = karaoke,
                    videoWidth = thumbnailResult.width,
                    videoHeight = thumbnailResult.height,
                    durationMs = thumbnailResult.durationMs
                )

                projectDao.save(project)

                _state.update {
                    it.copy(
                        creating = false,
                        progress = 1f,
                        projects = projectDao.getAllThumbnails()
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace() // Handle safely if extracting fails
            }

        }
    }

    fun enterSelectMode(id: String) {
        Log.d(LOG_TAG, "Entering select mode $id")
        _state.update {
            it.copy(
                selectModeOn = true,
                selectedProjectIds = setOf(id)
            )
        }
    }

    fun selectProject(id: String) {
        _state.update {
            val selectedIds = it.selectedProjectIds.toMutableSet()
            if (selectedIds.contains(id)) {
                selectedIds.remove(id)
            } else {
                selectedIds.add(id)
            }
            it.copy(
                selectedProjectIds = selectedIds,
                selectModeOn = selectedIds.isNotEmpty()
            )
        }
    }

    fun exitSelectMode() {
        Log.d(LOG_TAG, "Exiting select mode")
        _state.update {
            it.copy(
                selectModeOn = false,
                selectedProjectIds = emptySet()
            )
        }
    }

    fun deleteSelectedProjects(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val selectedIds = _state.value.selectedProjectIds
            val uris = videoThumbnailDao.getAllUrisByProjectIds(selectedIds)
            for (uri in uris) {
                uri?.let {
                    deleteFileFromUri(context, it)
                }
            }

            projectDao.deleteAllByIds(selectedIds)
            _state.update {
                it.copy(
                    selectModeOn = false,
                    projects = projectDao.getAllThumbnails(),
                    selectedProjectIds = emptySet()
                )
            }
        }
    }

    fun selectAllProjects() {
        _state.update {
            it.copy(
                selectedProjectIds = it.projects.map { pj -> pj.id }.toSet()
            )
        }
    }

    companion object {
        val factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val dbInstance = DatabaseContainer.instance.database
                @Suppress("UNCHECKED_CAST")
                return HomeScreenViewModel(
                    dbInstance.projectDao(),
                    dbInstance.videoThumbnailDao()
                ) as T
            }
        }
    }
}

fun deleteFileFromUri(context: Context, uri: Uri): Boolean {
    return try {
        when (uri.scheme) {
            "file" -> {
                File(uri.path!!).delete()
            }

            "content" -> {
                context.contentResolver.delete(uri, null, null) > 0
            }

            else -> false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}