package io.captioner.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import io.captioner.R
import io.captioner.ui.export.components.ExportTopBar
import io.captioner.ui.theme.CaptionerTheme

fun openVideoInGalleryOrExplorer(context: Context, uri: Uri) {
    val gallery = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(gallery) }.onFailure {
        val fallback = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(fallback, "Open with")) }
    }
}

@Composable
fun ExportVideoScreen(
    exporting: Boolean,
    progress: Float,
    thumbnailUri: Uri? = null,
    resultUri: Uri? = null,
    errorMessage: String? = null,
    onBack: () -> Unit = {},
    onDone: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            ExportTopBar(
                onBack = onBack,
                onDone = onDone
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProgressArea(progress, thumbnailUri, done = resultUri != null)

            ResultArea(
                exporting = exporting,
                resultUri = resultUri,
                errorMessage = errorMessage
            )
        }
    }
}


@Composable
private fun ProgressArea(
    progress: Float,
    thumbnailUri: Uri? = null,
    done: Boolean = false
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "video_progress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        val ringColor = MaterialTheme.colorScheme.inversePrimary
//                val trackColor = MaterialTheme.colorScheme.surfaceVariant

        Box(
            modifier = Modifier
                .size(212.dp)
                .drawBehind {
                    val strokePx = 10.dp.toPx()
                    val inset = strokePx / 2 + 6.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    // track
//                            drawArc(
//                                color = trackColor,
//                                startAngle = -90f, sweepAngle = 360f, useCenter = false,
//                                topLeft = topLeft, size = arcSize,
//                                style = Stroke(strokePx, cap = StrokeCap.Round)
//                            )

                    // progress
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(strokePx, cap = StrokeCap.Round)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = painterResource(R.drawable.preview_background)
                )
                // Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.secondary.copy(0.5f),
                                    MaterialTheme.colorScheme.secondary.copy(0.2f)
                                )
                            )
                        )
                )
                // Percentage
                if (!done) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-1).sp
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun ResultArea(
    exporting: Boolean,
    resultUri: Uri?,
    errorMessage: String?
) {
    val context = LocalContext.current

    when {
        exporting -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Rendering your video",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "This may take a few minutes",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        resultUri != null -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = "Export Successful!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() },
                            indication = null) { openVideoInGalleryOrExplorer(context, resultUri) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Open in Gallery",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        errorMessage != null -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = "Export Failed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

}



@Preview(showBackground = true)
@Composable
private fun PreviewVideo() {
    CaptionerTheme(
        dynamicColor = false
    ) {
        ExportVideoScreen(
            exporting = true,
            progress = 0.5f,
            thumbnailUri = "".toUri()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewVideoFail() {
    CaptionerTheme(
        dynamicColor = false
    ) {
        ExportVideoScreen(
            exporting = false,
            progress = 0.5f,
            thumbnailUri = "".toUri(),
            errorMessage = "Fail to export"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewVideoSuccess() {
    CaptionerTheme(
        dynamicColor = false
    ) {
        ExportVideoScreen(
            exporting = false,
            progress = 1f,
            thumbnailUri = "".toUri(),
            resultUri = "".toUri()
        )
    }
}
