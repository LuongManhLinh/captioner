package io.captioner.ui.editor.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.captioner.data.model.Caption
import io.captioner.data.model.Captionable
import io.captioner.data.model.KaraokeWord
import io.captioner.ui.theme.CaptionerTheme
import kotlin.math.roundToInt


@Composable
fun TimelineSection(
    modifier: Modifier = Modifier,
    pxPerMs: Float,
    onPxPerMsScale: (Float) -> Unit = { },
    thumbnailUris: List<Uri?>,
    thumbnailDurationMs: Long = 1000L,
    isPlaying: Boolean,
    currentTimeMs: Long,
    durationMs: Long,
    normalCaptions: List<Caption>,
    karaokeCaptions: List<Caption> = emptyList(),
    karaokeWords: List<KaraokeWord> = emptyList(),
    karaoke: Boolean = false,
    selectedCaptionId: String? = null,
    selectedWordId: String? = null,
    onSeek: (Long) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onNormalCaptionTimelineChange: (String, Long, Long) -> Unit,
    onCaptionDoubleTap: (String, Boolean) -> Unit,
    onCaptionTap: (String, Boolean) -> Unit,
    onWordTimelineChange: (String, String, Long, Long) -> Unit = { _, _, _, _ -> },
    onWordDoubleTap: (String) -> Unit = {},
    onWordTap: (String) -> Unit = {},
    onCaptionDrag: (String, Long) -> Unit = { _, _ ->},
    onWordDrag: (String, String, Long) -> Unit = {_,_,_ ->},
) {
    val density = LocalDensity.current

    val videoTrackWidth = with(density) { (durationMs * pxPerMs).toDp() }
    val thumbWidthDp = with(density) { (thumbnailDurationMs * pxPerMs).toDp() }
    val lastThumbWidthDp = with(density) {
        val left = durationMs % thumbnailDurationMs
        if (left == 0L) thumbWidthDp else (left * pxPerMs).toDp()
    }

    val scrollState = rememberScrollState()
    val captionVerticalScrollState = rememberScrollState()

    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            wasPlayingBeforeScrub = isPlaying
            if (isPlaying) onPause()
        } else {
            if (wasPlayingBeforeScrub) onResume()
        }
    }

    LaunchedEffect(currentTimeMs, pxPerMs) {
        if (!scrollState.isScrollInProgress) {
            val targetScroll = (currentTimeMs * pxPerMs).roundToInt()
            scrollState.scrollTo(targetScroll)
        }
    }

    LaunchedEffect(scrollState, pxPerMs) {
        snapshotFlow { scrollState.value }.collect { scrollValue ->
            if (scrollState.isScrollInProgress) {
                val newTime = scrollValue / pxPerMs
                onSeek(newTime.toLong())
            }
        }
    }

    val normalCaptionsLanes = remember(normalCaptions) {
        arrangeCaptionsIntoLanes(normalCaptions)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val halfScreenWidth = maxWidth / 2

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        onPxPerMsScale(zoom)
                    }
                }
                .horizontalScroll(scrollState)
                .padding(horizontal = halfScreenWidth),
        ) {
            ScrollingTimelineTrack(
                modifier = Modifier.width(videoTrackWidth),
                durationMs = durationMs,
                pxPerMs = pxPerMs
            )

            Spacer(modifier = Modifier.height(8.dp))

            VideoThumbnailTrack(
                modifier = Modifier
                    .width(videoTrackWidth),
                thumbnailUris = thumbnailUris,
                thumbWidthDp = thumbWidthDp,
                lastThumbWidthDp = lastThumbWidthDp
            )

            Spacer(modifier = Modifier.height(10.dp))

            CaptionTrack(
                modifier = Modifier
                    .width(videoTrackWidth)
                    .verticalScroll(captionVerticalScrollState),
                karaoke = karaoke,
                karaokeCaptions = karaokeCaptions,
                karaokeWords = karaokeWords,
                normalCaptionsLanes = normalCaptionsLanes,
                selectedCaptionId = selectedCaptionId,
                selectedWordId = selectedWordId,
                pxPerMs = pxPerMs,
                onNormalCaptionTimelineChange = onNormalCaptionTimelineChange,
                onCaptionDoubleTap = onCaptionDoubleTap,
                onCaptionTap = onCaptionTap,
                onWordTimelineChange = onWordTimelineChange,
                onWordDoubleTap = onWordDoubleTap,
                onWordTap = onWordTap,
                onCaptionDrag = onCaptionDrag,
                onWordDrag = onWordDrag
            )
        }

        PlayHead(Modifier.align(Alignment.Center))

        FixedTimerOverlay(
            modifier = Modifier.align(Alignment.TopStart),
            currentTimeMs = currentTimeMs,
            durationMs = durationMs
        )
    }
}
@Composable
private fun ScrollingTimelineTrack(
    modifier: Modifier = Modifier,
    durationMs: Long,
    pxPerMs: Float,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .height(24.dp)
    ) {
        val totalSeconds = (durationMs / 1000).toInt()
        for (sec in 0..totalSeconds) {
            val offsetPx = sec * 1000 * pxPerMs
            val offsetDp = with(density) { offsetPx.toDp() }
            // mm:ss timestamp
            Text(
                text = formatTime(sec * 1000L),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .offset(x = offsetDp - 14.dp) // center the text over the exact second
                    .align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
private fun VideoThumbnailTrack(
    modifier: Modifier = Modifier,
    thumbnailUris: List<Uri?>,
    thumbWidthDp: Dp,
    lastThumbWidthDp: Dp
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(4.dp))

    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            thumbnailUris.forEachIndexed { index, uri ->
                val widthDp = if (index == thumbnailUris.lastIndex) {
                    lastThumbWidthDp
                } else {
                    thumbWidthDp
                }
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Video Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(widthDp)
                            .fillMaxHeight(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(widthDp)
                            .fillMaxHeight()
                            .background(
                                if (index % 2 == 0) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                                }
                            )
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", m, s)
}

@Composable
private fun FixedTimerOverlay(
    modifier: Modifier = Modifier,
    currentTimeMs: Long,
    durationMs: Long
) {
    Row(
        modifier = modifier
            .height(24.dp) // Covers the spacer and top half of the scrolling timeline
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "${formatTime(currentTimeMs)} / ${formatTime(durationMs)}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun PlayHead(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(2.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.onSurface)
    )
}
@Composable
private fun CaptionTrack(
    modifier: Modifier = Modifier,
    karaoke: Boolean,
    karaokeCaptions: List<Captionable>,
    karaokeWords: List<KaraokeWord>,
    normalCaptionsLanes: List<List<Captionable>>,
    selectedCaptionId: String?,
    selectedWordId: String?,
    pxPerMs: Float,
    onNormalCaptionTimelineChange: (String, Long, Long) -> Unit,
    onCaptionDoubleTap: (String, Boolean) -> Unit,
    onCaptionTap: (String, Boolean) -> Unit,
    onWordTimelineChange: (String, String, Long, Long) -> Unit,
    onWordDoubleTap: (String) -> Unit,
    onWordTap: (String) -> Unit,
    onCaptionDrag: (String, Long) -> Unit,
    onWordDrag: (String, String, Long) -> Unit,
) {
    val captionLaneColors = TimelineLaneColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    val wordLaneColors = TimelineLaneColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    )

    val otherLaneColors = TimelineLaneColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    Column(modifier = modifier) {
        if (karaoke) {
            // First lane: karaoke captions
            TimelineLane(
                items = karaokeCaptions,
                height = 40.dp,
                colors = captionLaneColors,
                pxPerMs = pxPerMs,
                selectedId = selectedCaptionId,
                timelineChangeable = false,
                onTap = { onCaptionTap(it, true) },
                onDoubleTap = { onCaptionDoubleTap(it, true) },
                onDrag = onCaptionDrag
            )

            Spacer(modifier = Modifier.height(4.dp))


            TimelineLane(
                items = karaokeWords,
                height = 40.dp,
                colors = wordLaneColors,
                pxPerMs = pxPerMs,
                selectedId = selectedWordId,
                timelineChangeable = true,
                onTimelineChange = onWordTimelineChange,
                onTap = { onWordTap(it) },
                onDoubleTap = { onWordDoubleTap(it) },
                onDrag = onWordDrag
            )

            if (normalCaptionsLanes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Remaining captions: packed into lanes below
            normalCaptionsLanes.forEachIndexed { laneIndex, lane ->
                TimelineLane(
                    items = lane,
                    height = 40.dp,
                    colors = otherLaneColors,
                    pxPerMs = pxPerMs,
                    selectedId = selectedCaptionId,
                    timelineChangeable = true,
                    onTimelineChange = { id, startDelta, endDelta ->
                        onNormalCaptionTimelineChange(id, startDelta, endDelta)
                    },
                    onTap = { onCaptionTap(it, false) },
                    onDoubleTap = { onCaptionDoubleTap(it, false) },
                    onDrag = onCaptionDrag
                )

                if (laneIndex != normalCaptionsLanes.lastIndex) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        } else {
            // Old behavior: all captions packed together
            val captionLanes = remember(karaokeCaptions, normalCaptionsLanes) {
                normalCaptionsLanes
            }

            captionLanes.forEachIndexed { laneIndex, lane ->
                TimelineLane(
                    items = lane,
                    height = 40.dp,
                    colors = otherLaneColors,
                    pxPerMs = pxPerMs,
                    selectedId = selectedCaptionId,
                    timelineChangeable = true,
                    onTimelineChange = { id, startDelta, endDelta ->
                        onNormalCaptionTimelineChange(id, startDelta, endDelta)
                    },
                    onTap = { onCaptionTap(it, false) },
                    onDoubleTap = { onCaptionDoubleTap(it, false) },
                    onDrag = onCaptionDrag
                )

                if (laneIndex != captionLanes.lastIndex) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun <T : Captionable> TimelineLane(
    items: List<T>,
    height: Dp,
    colors: TimelineLaneColors,
    pxPerMs: Float,
    selectedId: String?,
    timelineChangeable: Boolean,
    onTimelineChange: (String, Long, Long) -> Unit = {_, _, _ ->},
    onTap: (String) -> Unit,
    onDoubleTap: (String) -> Unit,
    onDrag: (String, Long) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
        var selectedItem: Captionable? = null
        items.forEach { item ->
            if (item.id == selectedId) {
                selectedItem = item
            } else {
                CaptionTimelineItem(
                    modifier = Modifier.height(height),
                    isSelected = false,
                    caption = item,
                    pxPerMs = pxPerMs,
                    onTimelineChange = { s, e -> onTimelineChange(item.id, s, e) },
                    onTap = { onTap(item.id) },
                    onDoubleTap = { onDoubleTap(item.id) },
                    borderColor = colors.borderColor,
                    containerColor = colors.containerColor,
                    contentColor = colors.contentColor,
                    timelineChangeable = timelineChangeable,
                    onDrag = { onDrag(item.id, it) }
                )
            }
        }

        selectedItem?.let { item ->
            CaptionTimelineItem(
                modifier = Modifier.height(height),
                isSelected = true,
                caption = item,
                pxPerMs = pxPerMs,
                onTimelineChange = { s, e -> onTimelineChange(item.id, s, e) },
                onTap = { onTap(item.id) },
                onDoubleTap = { onDoubleTap(item.id) },
                borderColor = colors.borderColor,
                containerColor = colors.containerColor,
                contentColor = colors.contentColor,
                timelineChangeable = timelineChangeable,
                onDrag = { onDrag(item.id, it) }
            )
        }
    }
}

@Composable
private fun TimelineLane(
    items: List<KaraokeWord>,
    height: Dp,
    colors: TimelineLaneColors,
    pxPerMs: Float,
    selectedId: String?,
    timelineChangeable: Boolean,
    onTimelineChange: (String, String, Long, Long) -> Unit = {_, _, _, _ ->},
    onTap: (String) -> Unit,
    onDoubleTap: (String) -> Unit,
    onDrag: (String, String, Long) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
        var selectedItem: KaraokeWord? = null
        items.forEach { item ->
            if (item.id == selectedId) {
                selectedItem = item
            } else
                CaptionTimelineItem(
                    modifier = Modifier.height(height),
                    isSelected = false,
                    caption = item,
                    pxPerMs = pxPerMs,
                    onTimelineChange = { s, e -> onTimelineChange(item.id, item.captionId, s, e) },
                    onTap = { onTap(item.id) },
                    onDoubleTap = { onDoubleTap(item.id) },
                    borderColor = colors.borderColor,
                    containerColor = colors.containerColor,
                    contentColor = colors.contentColor,
                    timelineChangeable = timelineChangeable,
                    onDrag = { onDrag(item.id, item.captionId, it) }
                )
        }


        selectedItem?.let { item ->
            CaptionTimelineItem(
                modifier = Modifier.height(height),
                isSelected = true,
                caption = item,
                pxPerMs = pxPerMs,
                onTimelineChange = { s, e -> onTimelineChange(item.id, item.captionId, s, e) },
                onTap = { onTap(item.id) },
                onDoubleTap = { onDoubleTap(item.id) },
                borderColor = colors.borderColor,
                containerColor = colors.containerColor,
                contentColor = colors.contentColor,
                timelineChangeable = timelineChangeable,
                onDrag = { onDrag(item.id, item.captionId, it) }
            )
        }

    }
}



private data class TimelineLaneColors(
    val containerColor: Color,
    val borderColor: Color,
    val contentColor: Color
)

@Composable
fun CaptionTimelineItem(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    caption: Captionable,
    pxPerMs: Float,
    timelineChangeable: Boolean = true,
    onTimelineChange: (Long, Long) -> Unit = { _, _ -> },
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onDrag: (Long) -> Unit,
    borderColor: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    val density = LocalDensity.current
    val startOffsetDp = with(density) { (caption.startTimeMs * pxPerMs).toDp() }
    val widthDp = with(density) {
        ((caption.endTimeMs - caption.startTimeMs) * pxPerMs)
            .coerceAtLeast(10f)
            .toDp()
    }

    Box(
        modifier = modifier
            .offset(x = startOffsetDp)
            .width(widthDp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .pointerInput("${caption.id}_tap") {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { onTap() }
                )
            }
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                        .pointerInput("${caption.id}_drag") {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val msDelta = (dragAmount / pxPerMs).toLong()
                                onDrag(msDelta)
                            }
                        }
                } else {
                    Modifier
                }
            )
    ) {
        if (isSelected && timelineChangeable) {
            CaptionHandler(
                modifier = Modifier.align(Alignment.CenterStart),
                pxPerMs = pxPerMs,
                outerColor = borderColor,
                innerColor = containerColor,
                key = "${caption.id}_left",
                onDrag = { onTimelineChange(it, 0L) }
            )
        }

        Text(
            text = caption.text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 16.dp)
        )

        if (isSelected && timelineChangeable) {
            CaptionHandler(
                modifier = Modifier.align(Alignment.CenterEnd),
                pxPerMs = pxPerMs,
                outerColor = borderColor,
                innerColor = containerColor,
                key = "${caption.id}_right",
                onDrag = { onTimelineChange(0L, it) }
            )
        }
    }
}

@Composable
private fun CaptionHandler(
    modifier: Modifier = Modifier,
    pxPerMs: Float,
    outerColor: Color,
    innerColor: Color,
    key: String,
    onDrag: (Long) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(12.dp)
            .background(outerColor)
            .pointerInput(key) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val msDelta = (dragAmount / pxPerMs).toLong()
                    onDrag(msDelta)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(8.dp)
                .background(innerColor)
        )
    }
}

// Logic to pack captions into horizontal lanes
fun arrangeCaptionsIntoLanes(captions: List<Captionable>): List<List<Captionable>> {
    val sortedCaptions = captions.sortedWith(
        compareBy({ it.startTimeMs }, { it.endTimeMs }, { it.id })
    )

    val lanes = mutableListOf<MutableList<Captionable>>()

    for (caption in sortedCaptions) {
        var placed = false

        for (lane in lanes) {
            if (lane.last().endTimeMs <= caption.startTimeMs) {
                lane.add(caption)
                placed = true
                break
            }
        }

        if (!placed) {
            lanes.add(mutableListOf(caption))
        }
    }

    return lanes
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    CaptionerTheme {
        TimelineSection(
            thumbnailUris = emptyList(),
            pxPerMs = 0.1f,
            isPlaying = false,
            currentTimeMs = 10000,
            durationMs = 20000,
            normalCaptions = listOf(
                Caption(
                    id = "1",
                    projectId = "sfs",
                    startTimeMs = 0,
                    endTimeMs = 1000
                ),
                Caption(
                    id = "2",
                    projectId = "ahhahahaha",
                    startTimeMs = 1100,
                    endTimeMs = 10000
                ),
                Caption(
                    id = "3",
                    projectId = "ahhahahaha",
                    startTimeMs = 2000,
                    endTimeMs = 5000
                )

            ),
            selectedCaptionId = "3",
            onSeek = {},
            onPause = {},
            onResume = {},
            onCaptionTap = {_, _ ->},
            onCaptionDoubleTap = {_, _ ->},
            onNormalCaptionTimelineChange = { _, _, _ ->},
            karaoke = false,
        )
    }
}