package io.captioner.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.captioner.ui.theme.CaptionerTheme
import kotlinx.coroutines.delay

val frames = listOf("   ", ".  ", ".. ", "...")
@Composable
fun GenerationExecutingDialog(
    generating: Boolean = false,
    isSuccessful: Boolean = false,
    message: String = "Generating...",
    onCancel: () -> Unit = {},
    onClose: () -> Unit = {},
) {

    var fIdx by remember { mutableIntStateOf(0) }
    LaunchedEffect(generating) {
        if (generating) {
            while (true) {
                delay(1000)
                fIdx = (fIdx + 1) % frames.size
            }
        }
    }

    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier.padding(0.dp)
                .padding(horizontal = 24.dp)
        ) {
            if (generating) {
                Column (
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp)
                    )

                    Text(
                        text = "Generating${frames[fIdx]}",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onCancel
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            } else {
                val color = if (isSuccessful) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }

                val icon = if (isSuccessful) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.Error
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Column (
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier
                                .size(36.dp)
                        )

                        Text(
                            text = if (isSuccessful) "Success" else "Failed",
                            style = MaterialTheme.typography.titleLarge,
                            color = color
                        )
                    }

                    if (!isSuccessful && message.isNotBlank()) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onClose
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    CaptionerTheme {
        GenerationExecutingDialog(
            generating = true,
            message = "hahaha"
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewDone() {
    CaptionerTheme {
        GenerationExecutingDialog(
            generating = false,
            message = "Done",
            isSuccessful = true
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewFail() {
    CaptionerTheme {
        GenerationExecutingDialog(
            generating = false,
            message = "Failed",
            isSuccessful = false
        )
    }
}
