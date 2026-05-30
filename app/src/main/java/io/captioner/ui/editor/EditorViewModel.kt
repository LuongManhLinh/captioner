package io.captioner.ui.editor

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.captioner.WhisperContextHolder
import io.captioner.data.DatabaseContainer
import io.captioner.data.dao.CaptionDao
import io.captioner.data.dao.KaraokeWordDao
import io.captioner.data.dao.ProjectDao
import io.captioner.data.dao.VideoThumbnailDao
import io.captioner.data.dto.CaptionKaraokeDto
import io.captioner.data.model.Caption
import io.captioner.data.model.CaptionStyle
import io.captioner.data.model.KaraokeWord
import io.captioner.data.model.Project
import io.captioner.data.model.VideoThumbnail
import io.captioner.service.GeneratorService
import io.captioner.service.GeneratorService.separateVocal
import io.captioner.utils.extractThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.Exception
import kotlin.math.max
import kotlin.math.min

const val DEFAULT_PX_PER_MS = 0.1f
const val DEFAULT_CAPTION_DURATION = 2000L
const val DEFAULT_WORD_DURATION = 100L

const val INITIAL_TIMELINE_HEIGHT = 250
data class EditorState(
    val projectLoading: Boolean = true,
    val project: Project? = null,
    val thumbnailUris: List<Uri?> = emptyList(),

    val captions: List<CaptionKaraokeDto> = emptyList(),

    val isPlaying: Boolean = false,
    val currentTimeMs: Long = 0L,
    val durationMs: Long = 0L,
    val viewWidth: Int? = null,
    val viewHeight: Int? = null,
    val viewX: Int? = null,
    val viewY: Int? = null,
    val pxPerMs: Float = DEFAULT_PX_PER_MS,
    val openingSheetContent: SheetContent = SheetContent.None,
    val selection: Selection = Selection.None,

    val videoMaximized: Boolean = false,
    val showGenExecutingDialog: Boolean = false,
    val isGenerating: Boolean = false,
    val isSuccessful: Boolean = false,
    val genMessage: String = "",

    val zoomValue: Int = 100,

    val toastShowId: Int = 0,
    val toastMessage: String = "",
    val showExportDialog: Boolean = false,

    val timelineHeight: Dp = INITIAL_TIMELINE_HEIGHT.dp,

    val showGenDialog: Boolean = false,
    val showSubFormatDialog: Boolean = false
)
val maxTimelineHeight = 400.dp
val minTimelineHeight = 64.dp
sealed interface Selection {
    interface Caption: Selection {
        val id: String
    }
    data class NormalCaption(override val id: String): Selection.Caption
    data class KaraokeCaption(override val id: String): Selection.Caption
    data class KaraokeWord(val id: String): Selection
    object None: Selection
}

sealed interface SheetContent {
    data class Project(val captionStyle: CaptionStyle): SheetContent
    interface Caption: SheetContent {
        val item: io.captioner.data.model.Caption
    }
    data class NormalCaption(override val item: io.captioner.data.model.Caption): SheetContent.Caption
    data class KaraokeCaption(override val item: io.captioner.data.model.Caption): SheetContent.Caption
    data class KaraokeWord(val item: io.captioner.data.model.KaraokeWord): SheetContent
    object None: SheetContent
}

private fun updateCaptionByWords(caption: Caption, words: List<KaraokeWord>): Caption {
    if (words.isEmpty()) return caption

    val textSb = StringBuilder()
    words.forEachIndexed { index, word ->
        textSb.append(word.text)
        if (index != words.lastIndex) {
            textSb.append(" ")
        }
    }

    return caption.copy(
        text = textSb.toString(),
        startTimeMs = words.first().startTimeMs,
        endTimeMs = words.last().endTimeMs
    )
}

const val LOG_TAG = "EditorViewModel"
class EditorViewModel(
    private val projectId: String,
    private val projectDao: ProjectDao,
    private val captionDao: CaptionDao,
    private val karaokeWordDao: KaraokeWordDao,
    private val videoThumbnailDao: VideoThumbnailDao
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val project = projectDao.getById(projectId)
            if (project == null) {
                _state.update {
                    it.copy(
                        projectLoading = false
                    )
                }
            } else {
                val captions = captionDao.getALlByProjectId(projectId)
                val thumbnailUris = videoThumbnailDao.getALlByProjectId(project.id).map {
                    it.uri
                }
                if (project.karaoke) {
                    _state.update {
                        it.copy(
                            project = project,
                            thumbnailUris = thumbnailUris,
                            projectLoading = false,
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
                            thumbnailUris = thumbnailUris,
                            projectLoading = false,
                            captions = captions.map { c -> CaptionKaraokeDto(caption = c) },
                        )
                    }
                }
            }
        }
    }

    val state: StateFlow<EditorState> = _state.asStateFlow()

    fun getOrInitializeThumbnails(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val project = _state.value.project ?: return@launch
            val thumbnailUris = videoThumbnailDao.getALlByProjectId(project.id).map {
                it.uri
            }

            if (thumbnailUris.isNotEmpty()) {
                _state.update {
                    it.copy(
                        thumbnailUris = thumbnailUris
                    )
                }
                return@launch
            }

            val msPerThumb = 1000L
            val durationMs = project.durationMs
            val thumbCount = if (durationMs % msPerThumb != 0L) {
                (durationMs / msPerThumb).toInt() + 1
            } else {
                (durationMs / msPerThumb).toInt()
            }

            Log.d(LOG_TAG, "I need to extract $thumbCount thumbnails")

            _state.update {
                it.copy(
                    thumbnailUris = List(thumbCount) { null }
                )
            }

            val newThumbnailUris = extractThumbnails(
                context = context,
                videoUri = project.videoUri,
                durationMs = durationMs,
                onThumbUriEmerge = { nIdx, nUri ->

                    _state.update {
                        Log.d(LOG_TAG, "Total count ${it.thumbnailUris.size}, updating idx $nIdx")
                        it.copy(
                            thumbnailUris = it.thumbnailUris.mapIndexed { idx, uri ->
                                if (nIdx == idx) nUri else uri
                            }
                        )
                    }
                },
                expectedCount = thumbCount
            )

            if (newThumbnailUris.isNullOrEmpty()) return@launch

            val videoThumbnails = newThumbnailUris.mapIndexed { index, uri ->
                VideoThumbnail(
                    projectId = project.id,
                    uri = uri,
                    idx = index
                )
            }

            videoThumbnailDao.saveAll(videoThumbnails)
        }
    }

    fun setViewSize(width: Int, height: Int) {
//        Log.d(LOG_TAG, "Setting size $width x $height")
        _state.update {
            it.copy(
                viewWidth = width,
                viewHeight = height
            )
        }
    }

    fun setViewPosition(x: Int, y: Int) {
//        Log.d(LOG_TAG, "Setting position ($x, $y)")
        _state.update {
            it.copy(
                viewX = x,
                viewY = y
            )
        }
    }

    fun setDuration(duration: Long) {
        _state.update {
            it.copy(durationMs = duration)
        }
    }

    fun setPlaying(isPlaying: Boolean) {
        _state.update {
            it.copy(isPlaying = isPlaying)
        }
    }

    fun updateProgress(currentTimeMs: Long) {
        _state.update {
            it.copy(
                currentTimeMs = currentTimeMs.coerceIn(0L, it.durationMs)
            )
        }
    }

    fun toast(message: String) {
        _state.update {
            it.copy(
                toastShowId = it.toastShowId + 1,
                toastMessage = message
            )
        }
    }

    fun addNormalCaption() {
        val curState = _state.value
        val project = curState.project ?: return
        val currentTimeMs = curState.currentTimeMs

        val newCaption = Caption(
            projectId = projectId,
            startTimeMs = currentTimeMs,
            endTimeMs = (currentTimeMs + DEFAULT_CAPTION_DURATION).coerceAtMost(curState.durationMs),
            style = project.captionStyle
        )
        _state.update {
            it.copy(
                captions = curState.captions + CaptionKaraokeDto(newCaption)
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            captionDao.save(newCaption)
        }
    }

    fun addKaraokeCaption() {
        val curState = _state.value
        val project = curState.project ?: return
        val currentTimeMs = curState.currentTimeMs

        val newCaption = Caption(
            projectId = projectId,
            text = "New Karaoke",
            startTimeMs = currentTimeMs,
            endTimeMs = (currentTimeMs + DEFAULT_CAPTION_DURATION)
                .coerceAtMost(curState.durationMs),
            style = project.captionStyle
        )

        val halfDuration = (newCaption.endTimeMs - currentTimeMs) / 2 - 50

        val newKaraokeWords = listOf(
            KaraokeWord(
                captionId = newCaption.id,
                text = "New",
                startTimeMs = currentTimeMs,
                endTimeMs = currentTimeMs + halfDuration
            ),
            KaraokeWord(
                captionId = newCaption.id,
                text = "Karaoke",
                startTimeMs = currentTimeMs + halfDuration + 100,
                endTimeMs = newCaption.endTimeMs
            )
        )

        _state.update {
            it.copy(
                captions = curState.captions + CaptionKaraokeDto(newCaption, newKaraokeWords)
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            captionDao.save(newCaption)
            karaokeWordDao.saveAll(newKaraokeWords)
        }
    }

    fun showExportDialog(open: Boolean) {
        _state.update {
            it.copy(showExportDialog = open)
        }
    }

    fun changeTimelineHeight(delta: Dp) {
        _state.update {
            it.copy(
                timelineHeight = (it.timelineHeight + delta).coerceIn(
                    minTimelineHeight, maxTimelineHeight
                )
            )
        }
    }

    fun resetTimelineHeight() {
        Log.d(LOG_TAG, "Resetting timeline height")
        _state.update {
            it.copy(
                timelineHeight = INITIAL_TIMELINE_HEIGHT.dp
            )
        }
    }

    private fun updateCaptionLocallyAndDb(id: String, transform: (Caption) -> Caption) {
        var updatedCaption: Caption? = null
        val updatedCaptions = _state.value.captions.map {
            val caption = it.caption
            if (caption.id == id) {
                updatedCaption = transform(caption)
                it.copy(caption = updatedCaption)
            } else {
                it
            }
        }

        if (updatedCaption != null) {
            _state.update {
                it.copy(captions = updatedCaptions)
            }
            viewModelScope.launch(Dispatchers.IO) {
                captionDao.update(updatedCaption)
            }
        }
    }

    fun updateCaptionTimeline(id: String, startDeltaMs: Long, endDeltaMs: Long) {
        updateCaptionLocallyAndDb(id) { caption ->
            val newStart = (caption.startTimeMs + startDeltaMs)
                .coerceAtLeast(0L)
            val newEnd = (caption.endTimeMs + endDeltaMs)
                .coerceAtMost(_state.value.durationMs)

            if (newStart < newEnd) {
                caption.copy(startTimeMs = newStart, endTimeMs = newEnd)
            } else caption
        }
    }

    fun updateCaptionPosition(id: String, dx: Float, dy: Float) {
        updateCaptionLocallyAndDb(id) {
            it.copy(x = it.x + dx, y = it.y + dy)
        }
    }

    fun selectCaption(id: String, karaoke: Boolean) {
        val selection = _state.value.selection
//        Log.d(LOG_TAG, "Select Caption with karaoke $karaoke")
        val newSelection = if (selection is Selection.Caption && selection.id == id) {
            Selection.None
        } else if (karaoke) {
            Selection.KaraokeCaption(id)
        } else {
            Selection.NormalCaption(id)
        }
        _state.update {
            it.copy(selection = newSelection)
        }
    }

    fun changeOpeningSheetType(sheetContent: SheetContent) {
        _state.update {
            it.copy(
                openingSheetContent = sheetContent
            )
        }
    }

    fun closeSheet() {
        changeOpeningSheetType(SheetContent.None)
    }

    fun openSheetForProject() {
        val project = _state.value.project ?: return
        changeOpeningSheetType(
            SheetContent.Project(project.captionStyle)
        )
    }

    fun openSheetForSelection() {
        val curState = _state.value
        val sheetContent = when (val selection = curState.selection) {
            is Selection.Caption -> {
                val dto = curState.captions.find { it.caption.id == selection.id }
                if (dto == null) {
                    SheetContent.None
                } else if (selection is Selection.KaraokeCaption) {
                    SheetContent.KaraokeCaption(dto.caption.copy())
                } else {
                    SheetContent.NormalCaption(dto.caption.copy())
                }
            }

            is Selection.KaraokeWord -> {
                var word: KaraokeWord? = null
                for (dto in curState.captions) {
                    word = dto.karaokeWords.find { it.id == selection.id }
                    if (word != null) break
                }

                if (word == null) {
                    SheetContent.None
                } else {
                    SheetContent.KaraokeWord(word.copy())
                }
            }

            else -> SheetContent.None
        }
        changeOpeningSheetType(sheetContent)
    }
    fun closeGenerateDialog() {
        _state.update {
            it.copy(showGenExecutingDialog = false)
        }
    }
    fun deleteSelection() {
        val curState = _state.value
        val selection = curState.selection

        viewModelScope.launch(Dispatchers.IO) {
            if (selection is Selection.Caption) {
                val id = selection.id
                captionDao.deleteById(id)
                _state.update {
                    it.copy(
                        selection = Selection.None,
                        captions = it.captions.filter { dto -> dto.caption.id != id }
                    )
                }
            } else if (selection is Selection.KaraokeWord) {
                val id = selection.id
                karaokeWordDao.deleteById(id)
                val updatedCaptions = mutableListOf<CaptionKaraokeDto>()
                _state.value.captions.forEach {
                    val wordCount = it.karaokeWords.count()
                    val updatedWords = it.karaokeWords.filter { w -> w.id != id }

                    if (wordCount == updatedWords.count()) {
                        updatedCaptions.add(it)
                    } else if (updatedWords.isNotEmpty()) {
                        updatedCaptions.add(
                            CaptionKaraokeDto(
                                caption = updateCaptionByWords(it.caption, updatedWords),
                                karaokeWords = updatedWords
                            )
                        )
                    }
                }
                _state.update {
                    it.copy(captions = updatedCaptions)
                }
            }
        }
    }

    fun updateProjectSheet(captionStyle: CaptionStyle) {
        if (_state.value.openingSheetContent is SheetContent.Project) {
            _state.update {
                it.copy(openingSheetContent = SheetContent.Project(captionStyle))
            }
        }
    }

    fun applyProjectEdition() {
        val curState = _state.value
        val sheetContent = curState.openingSheetContent
        val captionStyle = if (sheetContent is SheetContent.Project) {
            sheetContent.captionStyle
        } else {
            return
        }

        val project = curState.project?.copy(captionStyle = captionStyle) ?: return

        val updatedCaptions = curState.captions.map {
            it.copy(caption = it.caption.copy(style = captionStyle))
        }

        _state.update {
            it.copy(
                project = project,
                captions = updatedCaptions
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            projectDao.update(project)
            captionDao.updateAll(updatedCaptions.map { it.caption })
        }
    }

    fun updateCaptionSheet(transform: (Caption) -> Caption) {
        val sheetContent = _state.value.openingSheetContent
        if (sheetContent is SheetContent.NormalCaption) {
            _state.update {
                it.copy(
                    openingSheetContent = SheetContent.NormalCaption(
                        transform(sheetContent.item)
                    )
                )
            }
        } else if (sheetContent is SheetContent.KaraokeCaption) {
            _state.update {
                it.copy(
                    openingSheetContent = SheetContent.KaraokeCaption(
                        transform(sheetContent.item)
                    )
                )
            }
        }
    }

    fun updateWordSheet(transform: (KaraokeWord) -> KaraokeWord) {
        val sheetContent = _state.value.openingSheetContent
        if (sheetContent is SheetContent.KaraokeWord) {
            _state.update {
                it.copy(
                    openingSheetContent = SheetContent.KaraokeWord(
                        transform(sheetContent.item)
                    )
                )
            }
        }
    }

    fun applyCaptionEdition() {
        val sheetContent = _state.value.openingSheetContent
        if (sheetContent is SheetContent.Caption) {
            val caption = sheetContent.item
            updateCaptionLocallyAndDb(caption.id) { caption.copy() }
        }
    }


    private var job: Job? = null

    fun generateLocally(context: Context) {
        _state.update {
            it.copy(
                showGenExecutingDialog = true,
                isGenerating = true,
                isPlaying = false,
                genMessage = ""
            )
        }

        val project = _state.value.project
        if (project == null) {
            _state.update {
                it.copy(
                    showGenExecutingDialog = false,
                    isGenerating = false,
                    isPlaying = false,
                )
            }
            return
        }
        val whisperCtx = WhisperContextHolder.context
        Log.d(LOG_TAG, "whisperCtx = $whisperCtx")

        if (whisperCtx == null) {
            _state.update {
                it.copy(
                    showGenExecutingDialog = true,
                    isGenerating = false,
                    isSuccessful = false,
                    genMessage = "Whisper model not loaded yet. Please wait."
                )
            }
            viewModelScope.launch {
                delay(3000)
                _state.update { it.copy(showGenExecutingDialog = false) }
            }
            return
        }

        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                generateLocally(context, project)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isGenerating = false,
                        isSuccessful = false,
                        genMessage = "Failed to generate: ${e.message}",
                    )
                }
            }
        }
    }

    private suspend fun generateLocally(context: Context, project: Project) {
        _state.update {
            it.copy(
                genMessage = "Generating locally..."
            )
        }
        Log.d(LOG_TAG, "project.karaoke = ${project.karaoke}")
        Log.d(LOG_TAG, "audioPath = ${project.audioPath}")
        Log.d(LOG_TAG, "calling generateKaraokeLocal...")

        val result = if (project.karaoke) {
            GeneratorService.generateKaraokeLocally(
                context = context,
                projectId = project.id,
                audioPath = project.audioPath,
                captionStyle = project.captionStyle
            )
        } else {
            GeneratorService.generateCaptionsLocally(
                context = context,
                projectId = project.id,
                audioPath = project.audioPath,
                captionStyle = project.captionStyle
            )
        }

        if (result.isSuccessful) {
            Log.d(LOG_TAG, "result.captions size = ${result.captions.size}")
            result.captions.forEach {
                Log.d(LOG_TAG, "caption: ${it.caption.text} | words: ${it.karaokeWords.size}")
            }
            if (project.karaoke) {
                val captionsToDelete = mutableListOf<Caption>()
                val updatedCaptions = mutableListOf<CaptionKaraokeDto>()

                _state.value.captions.forEach {
                    if (it.karaokeWords.isEmpty()) {
                        updatedCaptions.add(it)
                    } else {
                        captionsToDelete.add(it.caption)
                    }
                }

                captionDao.deleteAll(captionsToDelete)

                val newCaptions = result.captions

                updatedCaptions.addAll(newCaptions)

                _state.update {
                    it.copy(
                        isGenerating = false,
                        isSuccessful = true,
                        captions = updatedCaptions,
                        genMessage = result.message
                    )
                }

                newCaptions.forEach {
                    captionDao.save(it.caption)
                    karaokeWordDao.saveAll(it.karaokeWords)
                }

            } else {
                captionDao.deleteAll(_state.value.captions.map { it.caption })
                val newCaptions = result.captions.map {
                    CaptionKaraokeDto(it.caption)
                }

                _state.update {
                    it.copy(
                        isGenerating = false,
                        isSuccessful = true,
                        genMessage = result.message,
                        captions = newCaptions
                    )
                }
                captionDao.saveAll(newCaptions.map { it.caption })
            }
        } else {
            _state.update {
                it.copy(
                    isGenerating = false,
                    isSuccessful = false,
                    genMessage = result.message,
                )
            }
        }

        // Wait a few seconds to let user read the message
        delay(3000)

        _state.update {
            it.copy(showGenExecutingDialog = false)
        }
    }

    fun generateOnServer(context: Context, separateVocal: Boolean) {
        _state.update {
            it.copy(
                showGenExecutingDialog = true,
                isGenerating = true,
                isPlaying = false,
                genMessage = ""
            )
        }

        val project = _state.value.project
        if (project == null) {
            _state.update { it.copy(showGenExecutingDialog = false, isGenerating = false) }
            return
        }

        Log.d(LOG_TAG, "=== FASTER GENERATE START ===")
        Log.d(LOG_TAG, "project.karaoke = ${project.karaoke}")
        Log.d(LOG_TAG, "audioPath = ${project.audioPath}")

        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                generateOnServer(context, project, separateVocal)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isGenerating = false,
                        isSuccessful = false,
                        genMessage = "Failed to generate: ${e.message}",
                    )
                }
            }
        }
    }

    private suspend fun generateOnServer(context: Context, project: Project, separateVocal: Boolean) {
        Log.d(LOG_TAG, "Calling API...")
        _state.update {
            it.copy(
                genMessage = "Generating on server..."
            )
        }
        val startTime = System.currentTimeMillis()

        val result = if (project.karaoke) {
            Log.d(LOG_TAG, "→ generateKaraoke()")
            GeneratorService.generateKaraoke(
                projectId = project.id,
                context = context,
                videoUri = project.videoUri,
                captionStyle = project.captionStyle
            )
        } else {
            Log.d(LOG_TAG, "→ generateCaptions()")
            GeneratorService.generateCaptions(
                projectId = project.id,
                audioPath = project.audioPath,
                captionStyle = project.captionStyle
            )
        }

        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
        Log.d(LOG_TAG, "API done in ${elapsed}s")
        Log.d(LOG_TAG, "isSuccessful = ${result.isSuccessful}")
        Log.d(LOG_TAG, "message = ${result.message}")
        Log.d(LOG_TAG, "captions size = ${result.captions.size}")

        if (result.isSuccessful) {
            Log.d(LOG_TAG, "✓ Success — updating state")
            if (project.karaoke) {
                val captionsToDelete = mutableListOf<Caption>()
                val updatedCaptions = mutableListOf<CaptionKaraokeDto>()
                _state.value.captions.forEach {
                    if (it.karaokeWords.isEmpty()) updatedCaptions.add(it)
                    else captionsToDelete.add(it.caption)
                }
                captionDao.deleteAll(captionsToDelete)
                val newCaptions = result.captions.map {
                    CaptionKaraokeDto(it.caption, it.karaokeWords)
                }
                updatedCaptions.addAll(newCaptions)
                _state.update {
                    it.copy(
                        captions = updatedCaptions,
                    )
                }
                newCaptions.forEach {
                    captionDao.save(it.caption)
                    karaokeWordDao.saveAll(it.karaokeWords)
                }
            } else {
                captionDao.deleteAll(_state.value.captions.map { it.caption })
                val newCaptions = result.captions.map { CaptionKaraokeDto(it.caption) }
                _state.update {
                    it.copy(
                        captions = newCaptions,
                    )
                }
                captionDao.saveAll(newCaptions.map { it.caption })
            }
            if (separateVocal) {
                _state.update {
                    it.copy(
                        genMessage = "Separating vocal..."
                    )
                }
                executeSeparateVocal(context, project, result.separatedVocalVideoId)
            }
        }

        _state.update {
            it.copy(
                isGenerating = false,
                isSuccessful = result.isSuccessful,
                genMessage = result.message
            )
        }

        delay(3000)
        _state.update { it.copy(showGenExecutingDialog = false) }
    }

    private suspend fun executeSeparateVocal(context: Context, project: Project, separatedVocalVideoId: String) {
        val result = separateVocal(context, separatedVocalVideoId)
        if (result.isSuccessful) {
            val newVideoUri = result.outputPath?.toUri() ?: return
            Log.d(LOG_TAG, "Separating vocal successful to $newVideoUri")

            val updatedProject = project.copy(
                videoUri = newVideoUri
            )

            _state.update {
                it.copy(
                    project = updatedProject
                )
            }

            projectDao.update(updatedProject)
        }
    }

    fun cancelGeneration() {
        if (_state.value.isGenerating) {
            job?.let {
                if (it.isActive) {
                    it.cancel("Generation has been cancelled by user")
                    Log.d(LOG_TAG, "Generation has been cancelled by user")
                    _state.update { s ->
                        s.copy(
                            isGenerating = false,
                            isSuccessful = false,
                        )
                    }
                }
            }
        }
    }

    fun changeVideoMaximized(maximized: Boolean) {
        _state.update {
            it.copy(
                videoMaximized = maximized
            )
        }
    }

    fun scalePxPerMs(scale: Float) {
        val zoomValue = _state.value.zoomValue * scale
        _state.update {
            it.copy(
                zoomValue = zoomValue.toInt(),
                pxPerMs = (zoomValue / 100f) * DEFAULT_PX_PER_MS
            )
        }
    }

    fun zoomIn() {
        val zoomValue = _state.value.zoomValue + 5
        _state.update {
            it.copy(
                zoomValue = zoomValue,
                pxPerMs = (zoomValue.toFloat() / 100f) * DEFAULT_PX_PER_MS
            )
        }

    }

    fun zoomOut() {
        val zoomValue = _state.value.zoomValue - 5
        _state.update {
            it.copy(
                zoomValue = zoomValue,
                pxPerMs = (zoomValue.toFloat() / 100f) * DEFAULT_PX_PER_MS
            )
        }
    }

    fun selectWord(id: String) {
        val selection = _state.value.selection
        val newSelection = if (selection is Selection.KaraokeWord && selection.id == id) {
            Selection.None
        } else {
            Selection.KaraokeWord(id)
        }
        _state.update {
            it.copy(selection = newSelection)
        }
    }

    fun addWord() {
        val curState = _state.value
        val currentTimeMs = curState.currentTimeMs
        var newCaption: Caption? = null
        var newWord: KaraokeWord? = null
        val captions = curState.captions.map { dto ->
            val caption = dto.caption
            if (currentTimeMs in caption.startTimeMs..caption.endTimeMs) {
                newWord = KaraokeWord(
                    captionId = caption.id,
                    text = "Karaoke",
                    startTimeMs = currentTimeMs,
                    endTimeMs = (currentTimeMs + DEFAULT_WORD_DURATION)
                        .coerceAtMost(curState.durationMs)
                )
                val words = dto.karaokeWords.toMutableList()
                words.add(newWord)
                words.sortBy { it.startTimeMs }

                newCaption = updateCaptionByWords(caption, words)
                CaptionKaraokeDto(newCaption, words)
            } else {
                dto
            }
        }

        if (newCaption == null || newWord == null) {
            addKaraokeCaption()
        } else {
            _state.update {
                it.copy(
                    captions = captions
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                captionDao.update(newCaption)
                karaokeWordDao.save(newWord)
            }
        }
    }


    fun updateWordTimeline(
        id: String,
        captionId: String,
        startDeltaMs: Long,
        endDeltaMs: Long
    ) {
        var wordToUpdate: KaraokeWord? = null

        _state.update { state ->
            val updatedCaptions = state.captions.map { dto ->
                if (dto.caption.id != captionId) return@map dto

                val updatedWords = dto.karaokeWords.map { word ->
                    if (word.id != id) return@map word

                    val newStart = (word.startTimeMs + startDeltaMs).coerceAtLeast(0L)
                    val newEnd = (word.endTimeMs + endDeltaMs)
                        .coerceAtMost(state.durationMs)

                    if (newStart >= newEnd) return@map word

                    wordToUpdate = word.copy(
                        startTimeMs = newStart,
                        endTimeMs = newEnd
                    )
                    wordToUpdate
                }

                CaptionKaraokeDto(
                    caption = updateCaptionByWords(dto.caption, updatedWords),
                    karaokeWords = updatedWords
                )
            }

            state.copy(captions = updatedCaptions)
        }

        wordToUpdate?.let {
            viewModelScope.launch(Dispatchers.IO) {
                karaokeWordDao.update(wordToUpdate)
            }
        }
    }

    fun dragCaption(id: String, deltaMs: Long) {
        val curState = _state.value

        var newCaption: Caption? = null
        var newWords: List<KaraokeWord>? = null

        _state.update { state ->
            state.copy(
                captions = state.captions.map { dto ->
                    val caption = dto.caption

                    if (caption.id != id) return@map dto

                    val clampedDeltaMs = if (deltaMs < 0) {
                        max(deltaMs, -caption.startTimeMs)
                    } else {
                        min(deltaMs, curState.durationMs - caption.endTimeMs)
                    }

                    newCaption = caption.copy(
                        startTimeMs = caption.startTimeMs + clampedDeltaMs,
                        endTimeMs = caption.endTimeMs + clampedDeltaMs
                    )

                    newWords = dto.karaokeWords.map { w ->
                        w.copy(
                            startTimeMs = w.startTimeMs + clampedDeltaMs,
                            endTimeMs = w.endTimeMs + clampedDeltaMs
                        )
                    }

                    CaptionKaraokeDto(
                        caption = newCaption,
                        karaokeWords = newWords
                    )
                }
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            newCaption?.let {
                captionDao.update(newCaption)
            }

            if (!newWords.isNullOrEmpty()) {
                karaokeWordDao.updateAll(newWords)
            }
        }
    }

    fun dragWord(id: String, captionId: String, deltaMs: Long) {
        val curState = _state.value
        var newCaption: Caption? = null
        var newWord: KaraokeWord? = null
        _state.update { state ->
            state.copy(
                captions = state.captions.map { dto ->
                    val caption = dto.caption
                    if (caption.id != captionId) return@map dto

                    val clampedDeltaMs = if (deltaMs < 0) {
                        max(deltaMs, -caption.startTimeMs)
                    } else {
                        min(deltaMs, curState.durationMs - caption.endTimeMs)
                    }

                    val newWords = dto.karaokeWords.map { w ->
                        if (w.id != id) return@map w
                        newWord = w.copy(
                            startTimeMs = w.startTimeMs + clampedDeltaMs,
                            endTimeMs = w.endTimeMs + clampedDeltaMs
                        )
                        newWord
                    }

                    if (newWord == null) return@map dto

                    var newCaptionStart = caption.startTimeMs
                    var updateCaption = false
                    if (newWord.startTimeMs < newCaptionStart) {
                        newCaptionStart = newWord.startTimeMs
                        updateCaption = true
                    }
                    var newCaptionEnd = caption.endTimeMs
                    if (newWord.endTimeMs > newCaptionEnd) {
                        newCaptionEnd = newWord.endTimeMs
                        updateCaption = true
                    }

                    if (updateCaption) {
                        newCaption = caption.copy(
                            startTimeMs = newCaptionStart,
                            endTimeMs = newCaptionEnd
                        )
                    }

                    CaptionKaraokeDto(
                        caption = newCaption ?: caption,
                        karaokeWords = newWords
                    )

                }
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            newCaption?.let {
                captionDao.update(newCaption)
            }

            newWord?.let {
                karaokeWordDao.update(newWord)
            }
        }
    }

    fun showGenerationDialog(open: Boolean) {
        _state.update {
            it.copy(
                showGenDialog = open
            )
        }
    }

    fun showSubtitleFormatDialog(open: Boolean) {
        _state.update {
            it.copy(
                showSubFormatDialog = open
            )
        }
    }

    companion object {
        fun factory(projectId: String): ViewModelProvider.Factory {
            val factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val dbInstance = DatabaseContainer.instance.database

                    @Suppress("UNCHECKED_CAST")
                    return EditorViewModel(
                        projectId = projectId,
                        projectDao = dbInstance.projectDao(),
                        captionDao = dbInstance.captionDao(),
                        karaokeWordDao = dbInstance.karaokeWordDao(),
                        videoThumbnailDao = dbInstance.videoThumbnailDao()
                    ) as T
                }
            }

            return factory
        }
    }
}

