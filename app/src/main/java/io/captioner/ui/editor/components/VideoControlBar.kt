package io.captioner.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.captioner.ui.theme.CaptionerTheme

@Composable
fun VideoControlBar(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onPlayToggle: () -> Unit = {},
    onMaximize: () -> Unit = {},
    zoomValue: Int = 100,
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onDrag: (Dp) -> Unit = {},
    onDoubleTap: () -> Unit = {},
) {
    var isDragging by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(
                    if (isDragging) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        Color.Transparent
                    }
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { _, dragAmount ->
                        val height = with(density) {
                            dragAmount
                                .toDp()
                        }

                        onDrag(-height)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTap() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Resize",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onMaximize) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "maximize"
                    )
                }
            }

            IconButton(
                modifier = Modifier.align(Alignment.Center),
                onClick = onPlayToggle
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onZoomOut) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "maximize"
                    )
                }
                Text(
                    text = "$zoomValue%",
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(onClick = onZoomIn) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "maximize"
                    )
                }
            }
        }
    }

}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    CaptionerTheme {
        VideoControlBar()
    }
}