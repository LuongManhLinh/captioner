package io.captioner.ui.editor.components

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.captioner.data.dto.CaptionKaraokeDto
import io.captioner.data.model.Caption
import io.captioner.data.model.KaraokeWord
import io.captioner.ui.editor.Selection
import kotlinx.coroutines.delay

private const val LOG_TAG = "Editor"
@Composable
fun Editor(
    modifier: Modifier = Modifier,
    videoUri: Uri,
    isPlaying: Boolean,
    setPlaying: (Boolean) -> Unit,
    captions: List<CaptionKaraokeDto>,
    karaoke: Boolean,
    thumbnailUris: List<Uri?>,
    selection: Selection,
    durationMs: Long,
    currentTimeMs: Long,
    setDuration: (Long) -> Unit,
    updateProgress: (Long) -> Unit,
    updateCaptionPosition: (String, Float, Float) -> Unit,
    onViewCaptionDoubleTap: (String, Boolean) -> Unit,
    onViewCaptionTap: (String, Boolean) -> Unit,
    onTimelineCaptionDoubleTap: (String, Boolean) -> Unit,
    onTimelineCaptionTap: (String, Boolean) -> Unit,
    onNormalCaptionTimelineChange: (String, Long, Long) -> Unit,
    onWordTimelineChange: (String, String, Long, Long) -> Unit = { _, _, _, _ -> },
    onWordDoubleTap: (String) -> Unit = {},
    onWordTap: (String) -> Unit = {},
    videoWidth: Int,
    videoHeight: Int,
    viewX: Int? = null,
    viewY: Int? = null,
    viewWidth: Int? = null,
    viewHeight: Int? = null,
    onViewSizeChanged: (width: Int, height: Int) -> Unit = { _, _ ->},
    onViewPositionChanged: (x: Int, y: Int) -> Unit = { _, _ ->},
    videoMaximized: Boolean = false,
    onVideoMaximizedChange: (Boolean) -> Unit = {},
    pxPerMs: Float,
    onPxPerMsScale: (Float) -> Unit,
    zoomValue: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onCaptionDrag: (String, Long) -> Unit = {_, _ ->},
    onWordDrag: (String, String, Long) -> Unit = {_,_,_ ->},
    viewScale: Float = 1f,
    timelineHeight: Dp = 250.dp,
    onTimelineHeightChange: (Dp) -> Unit = {},
    onHeightControllerDoubleTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

    LaunchedEffect(videoUri) {
        videoUri.let { uri ->
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
    }

    // 1. Listen for player state changes to get the correct duration
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val duration = exoPlayer.duration
                    if (duration != androidx.media3.common.C.TIME_UNSET && duration > 0) {
                        setDuration(duration)
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // 2. Separate LaunchedEffect specifically for the progress loop
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                updateProgress(exoPlayer.currentPosition)
            }
            delay(50)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val (normalCaptions, karaokeCaptions, karaokeWords) = remember(captions) {
        val normalCaptions = mutableListOf<Caption>()
        val karaokeCaptions = mutableListOf<Caption>()
        val karaokeWords = mutableListOf<KaraokeWord>()

        captions.forEach {
            if (it.karaokeWords.isEmpty()) {
                normalCaptions.add(it.caption)
            } else {
                karaokeCaptions.add(it.caption)
                karaokeWords.addAll(it.karaokeWords)
            }
        }

        Triple(normalCaptions, karaokeCaptions, karaokeWords)
    }

    val (selectedCaptionId, selectedWordId) = remember(selection) {
        when (selection) {
            is Selection.Caption -> {
                selection.id to null
            }

            is Selection.KaraokeWord -> {
                null to selection.id
            }

            else -> {
                null to null
            }
        }
    }

    val onSeek: (Long) -> Unit = { timeMs ->
        exoPlayer.seekTo(timeMs)
        updateProgress(timeMs)
    }

    val onPause = {
        exoPlayer.pause()
        setPlaying(false)
    }

    val onResume = {
        exoPlayer.play()
        setPlaying(true)
    }


    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .then(
                    if (videoMaximized) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth()
                            .weight(1f)
                    }
                )
                .background(MaterialTheme.colorScheme.primaryContainer)

        ) {
            VideoPlayerView(
                exoPlayer,
                modifier = Modifier.fillMaxSize(),
                onViewSizeChanged = { width, height ->
                    onViewSizeChanged(width, height)
                },
                onViewPositionChanged = { x, y ->
                    onViewPositionChanged(x, y)
                },
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                maximized = videoMaximized,
                onMinimize = { onVideoMaximizedChange(false) },
                currentTimeMs = currentTimeMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                onSeek = onSeek,
                onPause = onPause,
                onResume = onResume
            )

            if (videoMaximized) {
                CaptionOverlayLayer(
                    captions = captions,
                    currentTimeMs = currentTimeMs,
                    onDrag = {_, _, _ ->},
                    onDoubleTap = {_, _ ->},
                    onTap = {_, _ ->},
                    selectedCaptionId = null,
                    viewX = viewX,
                    viewY = viewY,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    viewScale = viewScale,
                )
            } else {
                CaptionOverlayLayer(
                    captions = captions,
                    currentTimeMs = currentTimeMs,
                    onDrag = updateCaptionPosition,
                    onDoubleTap = onViewCaptionDoubleTap,
                    onTap = onViewCaptionTap,
                    selectedCaptionId = selectedCaptionId,
                    viewX = viewX,
                    viewY = viewY,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    viewScale = viewScale,
                )
            }
        }

        if (!videoMaximized) {
            VideoControlBar(
                isPlaying = isPlaying,
                onPlayToggle = { setPlaying(!isPlaying) },
                onMaximize = {
                    Log.d(LOG_TAG, "Maximized from VideoControlBar")
                    onVideoMaximizedChange(true)
                },
                zoomValue = zoomValue,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onDrag = onTimelineHeightChange,
                onDoubleTap = onHeightControllerDoubleTap
            )

            TimelineSection(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(timelineHeight),
                thumbnailUris = thumbnailUris,
                isPlaying = isPlaying,
                currentTimeMs = currentTimeMs,
                durationMs = durationMs,
                normalCaptions = normalCaptions,
                karaokeCaptions = karaokeCaptions,
                karaokeWords = karaokeWords,
                karaoke = karaoke,
                selectedCaptionId = selectedCaptionId,
                selectedWordId = selectedWordId,
                onSeek = onSeek,
                onPause = onPause,
                onResume = onResume,
                onNormalCaptionTimelineChange = onNormalCaptionTimelineChange,
                onCaptionDoubleTap = onTimelineCaptionDoubleTap,
                onCaptionTap = onTimelineCaptionTap,
                onWordTimelineChange = onWordTimelineChange,
                onWordTap = onWordTap,
                onWordDoubleTap = onWordDoubleTap,
                pxPerMs = pxPerMs,
                onPxPerMsScale = onPxPerMsScale,
                onCaptionDrag = onCaptionDrag,
                onWordDrag = onWordDrag
            )
        }

    }

}







