package io.captioner.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import io.captioner.data.dto.ProjectThumbnailDto
import io.captioner.ui.theme.CaptionerTheme
import io.captioner.utils.formatTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION


@SuppressLint("FrequentlyChangingValue")
@Composable
fun ProjectView(
    projects: List<ProjectThumbnailDto>,
    onProjectClick: (String) -> Unit,
    onProjectPress: (String) -> Unit,
    onPickVideo: (Uri, Boolean) -> Unit,
    selectedIds: Set<String>
) {
    val context = LocalContext.current

    var karaoke by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()

    var lastScrollOffset by remember { mutableIntStateOf(0) }
    var showText by remember { mutableStateOf(true) }

    LaunchedEffect(gridState.firstVisibleItemScrollOffset) {
        val current = gridState.firstVisibleItemScrollOffset
        showText = current <= lastScrollOffset // scrolling up → show text
        lastScrollOffset = current
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            onPickVideo(it, karaoke)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    karaoke = false
                    launcher.launch(arrayOf("video/*"))
                },
                imageVector = Icons.Filled.Add,
                text = "New Caption",
                showText = showText
            )

            ActionButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    karaoke = true
                    launcher.launch(arrayOf("video/*"))
                },
                imageVector = Icons.Filled.MusicNote,
                text = "New Karaoke",
                showText = showText
            )
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(128.dp),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(projects) { project ->
                ProjectItem(
                    isSelected = selectedIds.contains(project.id),
                    project = project,
                    onPress = { onProjectPress(project.id) },
                    onClick = { onProjectClick(project.id) }
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000

    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun ProjectItem(
    project: ProjectThumbnailDto,
    onClick: () -> Unit,
    onPress: () -> Unit = {},
    isSelected: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(project.id) {
                detectTapGestures(
                    onLongPress = { onPress() },
                    onTap = { onClick() }
                )
            }
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isSelected) 12.dp else 0.dp)
        ) {
            Card {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    AsyncImage(
                        model = project.primaryThumbnailUri,
                        contentDescription = "Project Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Optional: Overlay for timestamp/title
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.BottomStart),
                        shape = RoundedCornerShape(topEnd = 8.dp)
                    ) {
                        Text(
                            text = "${formatTime(project.createdAt)} ${formatDuration(project.durationMs)}",
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (project.karaoke) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                            modifier = Modifier.align(Alignment.TopStart),
                            shape = RoundedCornerShape(bottomEnd = 8.dp)
                        ) {
                            Text(
                                text = "Karaoke",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }


                }
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )

            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProjectItemPreview() {
    CaptionerTheme {
        Column {

            ProjectItem(
                project = ProjectThumbnailDto(
                    id = "1",
                    primaryThumbnailUri = "".toUri(),
                    createdAt = LocalDateTime.now(),
                    karaoke = true,
                    durationMs = 9000
                ),
                onClick = {},
                isSelected = false,
            )

            ProjectItem(
                project = ProjectThumbnailDto(
                    id = "2",
                    primaryThumbnailUri = "".toUri(),
                    createdAt = LocalDateTime.now(),
                    karaoke = true,
                    durationMs = 9812301
                ),
                onClick = {},
                isSelected = true
            )
        }
    }
}
