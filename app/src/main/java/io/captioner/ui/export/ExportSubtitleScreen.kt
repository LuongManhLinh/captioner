package io.captioner.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.captioner.service.GeneratorService
import io.captioner.ui.export.components.ExportTopBar
import io.captioner.ui.export.services.SubtitleFormat
import io.captioner.ui.theme.CaptionerTheme
import kotlinx.coroutines.launch
import java.io.File

fun openFileInExplorer(context: Context, fileUri: Uri) {
    try {
        Log.d("ExportSubtitle", "File URI = $fileUri")

        val contentUri = if (fileUri.scheme == "content") {
            // API 29+: already a content:// URI from MediaStore, use directly
            fileUri
        } else {
            // Legacy file:// URI: convert via FileProvider
            val filePath = fileUri.path ?: return
            val file = File(filePath)
            if (!file.exists()) {
                Log.e("ExportSubtitle", "File không tồn tại: $filePath")
                return
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }

        Log.d("ExportSubtitle", "Content URI = $contentUri")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "text/plain")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(intent, "Mở file SRT với"))

    } catch (e: Exception) {
        Log.e("ExportSubtitle", "Lỗi khi mở file", e)
    }
}

@Composable
fun ExportSubtitleScreen(
    exporting: Boolean,
    resultUri: Uri? = null,
    errorMessage: String? = null,
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    format: SubtitleFormat = SubtitleFormat.SRT
) {
    Scaffold(
        topBar = {
            ExportTopBar(
                onBack = onBack,
                onDone = onDone
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                exporting -> SubtitleExportingState(format)
                resultUri != null -> SubtitleSuccessState(uri = resultUri, format = format)
                errorMessage != null -> ExportErrorState(message = errorMessage)
            }
        }

    }
}

@Composable
private fun SubtitleExportingState(format: SubtitleFormat) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary


        Icon(
            imageVector = Icons.Default.Subtitles,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(64.dp)
        )


        Text(
            text = "Exporting ${format.name} file",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Writing subtitle file…",
            style = MaterialTheme.typography.bodyLarge,
        )

    }
}

@Composable
private fun SubtitleSuccessState(uri: Uri, format: SubtitleFormat) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    Log.d("ExportSubtitle", "URI = $uri, scheme = ${uri.scheme}")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 40.dp)
    ) {

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )

        Text(
            text = "${format.name} Subtitle Exported",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Your subtitle file is ready.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        // File link pill

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { openFileInExplorer(context, uri) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Open file location",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.primary,
                )
            }
            var sending by remember { mutableStateOf(false) }
            var sendResult by remember { mutableStateOf<Boolean?>(null) }
            val scope = rememberCoroutineScope()

            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!sending) {
                            sending = true
                            sendResult = null
                            scope.launch {
                                val ok = GeneratorService.sendSrtToServer(uri, context)
                                sendResult = ok
                                sending = false
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = when {
                        sending -> "Sending..."
                        sendResult == true -> "Sent!"
                        sendResult == false -> "Send failed, retry?"
                        else -> "Send to PC"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ExportErrorState(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(horizontal = 40.dp)
    ) {

        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Text(
            text = "Export Failed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewExporting() {
    CaptionerTheme {
        ExportSubtitleScreen(
            exporting = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewFail() {
    CaptionerTheme {
        ExportSubtitleScreen(
            exporting = false,
            resultUri = null,
            errorMessage = "Error exporting"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewDone() {
    CaptionerTheme {
        ExportSubtitleScreen(
            exporting = false,
            resultUri = "".toUri()
        )
    }
}

