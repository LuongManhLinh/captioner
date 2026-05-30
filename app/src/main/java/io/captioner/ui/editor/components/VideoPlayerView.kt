package io.captioner.ui.editor.components

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.captioner.ui.theme.CaptionerTheme
import kotlin.math.roundToInt

private const val LOG_TAG = "VideoPlayerView"
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
    onViewSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
    onViewPositionChanged: (x: Int, y: Int) -> Unit = { _, _ -> },
    videoWidth: Int,
    videoHeight: Int,
    maximized: Boolean = false,
    onMinimize: () -> Unit = {},
    isPlaying: Boolean = false,
    currentTimeMs: Long = 0L,
    durationMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {}
) {
    if (maximized) {
        BackHandler { onMinimize() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false // important
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.useController = false
            },
            modifier = Modifier
                .then(
                    if (maximized) Modifier.weight(1f) else Modifier
                )
                .onGloballyPositioned {
                    val rect = calculateVideoRect(
                        it.size.width,
                        it.size.height,
                        videoWidth,
                        videoHeight
                    )
                    val viewPos = it.positionInParent()
                    onViewSizeChanged(rect.width(), rect.height())
                    onViewPositionChanged(
                        (viewPos.x + rect.left).roundToInt(),
                        (viewPos.y + rect.top).roundToInt()
                    )
                }
                .background(MaterialTheme.colorScheme.surface)

        )

        if (maximized) {
            ExoControllerBar(
                isPlaying = isPlaying,
                currentTimeMs = currentTimeMs,
                durationMs = durationMs,
                onSeek = onSeek,
                onPause = onPause,
                onResume = onResume,
                onMinimize = onMinimize
            )
        }
    }



}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExoControllerBar(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    currentTimeMs: Long = 0L,
    durationMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onMinimize: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (isPlaying) onPause()
                else onResume()
            }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(30.dp)
            )
        }

        Text(
            text = formatDuration(currentTimeMs),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Center
        )

        Slider(
            value = if (durationMs > 0) currentTimeMs.toFloat() else 0f,
            onValueChange = {
                onSeek(it.toLong())
            },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),

            thumb = {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )
            },

            track = { sliderState ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                sliderState.value /
                                        sliderState.valueRange.endInclusive
                            )
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary
                            )
                    )
                }
            },

            modifier = Modifier.weight(1f)
        )

        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Center
        )

        IconButton(onClick = onMinimize) {
            Icon(
                imageVector = Icons.Default.FullscreenExit,
                contentDescription = "Minimize",
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExoControllerBarPreview() {
    CaptionerTheme {
        ExoControllerBar(
            currentTimeMs = 5210,
            durationMs = 10000
        ) { }
    }
}



private fun formatDuration(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun calculateVideoRect(
    containerW: Int,
    containerH: Int,
    videoW: Int,
    videoH: Int
): Rect {
    val videoAspect = videoW.toFloat() / videoH
    val containerAspect = containerW.toFloat() / containerH

    return if (videoAspect > containerAspect) {
        // fit width
        val width = containerW
        val height = (containerW / videoAspect).toInt()
        val top = (containerH - height) / 2
        Rect(0, top, width, top + height)
    } else {
        // fit height
        val height = containerH
        val width = (containerH * videoAspect).toInt()
        val left = (containerW - width) / 2
        Rect(left, 0, left + width, height)
    }
}