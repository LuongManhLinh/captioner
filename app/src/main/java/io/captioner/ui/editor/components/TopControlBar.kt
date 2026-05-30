package io.captioner.ui.editor.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.captioner.ui.theme.CaptionerTheme

@Composable
fun TopControlBar(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onExport: () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = onBack
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Back"
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onExport
        ) {
            Icon(
                imageVector = Icons.Outlined.ExitToApp,
                contentDescription = "Export"
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    CaptionerTheme {
        TopControlBar {  }
    }
}