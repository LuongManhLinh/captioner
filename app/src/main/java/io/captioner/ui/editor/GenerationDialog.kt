package io.captioner.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.captioner.ui.theme.CaptionerTheme

@Composable
fun GenerationDialog(
    onGenerateLocally: () -> Unit = {},
    onGenerateOnServer: () -> Unit = {},
    allowVocalSeparation: Boolean = true,
    onGenerateOnServerAndSeparateVocal: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Generation Options",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() },
                                indication = null, onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Choose a generation type",
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer(Modifier.height(20.dp))

                // Options
                DialogOption(
                    icon = Icons.Default.PhoneAndroid,
                    title = "Generate Locally",
                    subtitle = "Run offline on your phone.",
                    onClick = { onGenerateLocally(); onClose() }
                )

                Spacer(Modifier.height(12.dp))

                DialogOption(
                    icon = Icons.Outlined.CloudQueue,
                    title = "Generate on server",
                    subtitle = "Run on server. Internet is required.",
                    onClick = { onGenerateOnServer(); onClose() }
                )

                if (allowVocalSeparation) {
                    Spacer(Modifier.height(12.dp))

                    DialogOption(
                        icon = Icons.Default.VoiceOverOff,
                        title = "Generate on server and separate vocal",
                        subtitle = "Run on server. Then your video will be replaced with a new video without vocal",
                        onClick = { onGenerateOnServerAndSeparateVocal(); onClose() }
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
        GenerationDialog()
    }
}