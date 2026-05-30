package io.captioner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.captioner.ui.theme.CaptionerTheme

@Composable
fun SelectBar(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSelectAll: () -> Unit = {}
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onClose
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close"
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = onShare
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share"
            )
        }
        IconButton(
            onClick = onDelete
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete"
            )
        }
        IconButton(
            onClick = onSelectAll
        ) {
            Icon(
                imageVector = Icons.Default.SelectAll,
                contentDescription = "SelectAll"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    CaptionerTheme() {
        SelectBar(onClose = {})
    }
}