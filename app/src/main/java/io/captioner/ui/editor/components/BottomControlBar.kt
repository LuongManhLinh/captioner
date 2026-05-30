package io.captioner.ui.editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditRoad
import androidx.compose.material.icons.filled.GeneratingTokens
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.captioner.ui.theme.CaptionerTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.runtime.remember

private data class ButtonProps(
    val text: String,
    val imageVector: ImageVector,
    val onClick: () -> Unit
)
@Composable
fun BottomControlBar(
    modifier: Modifier = Modifier,
    onGenerate: () -> Unit = {},
    onEditAllCaptions: () -> Unit = {},
    onAddNormalCaption: () -> Unit = {},
    onAddKaraokeCaption: () -> Unit = {},
    onAddWord: () -> Unit = {},
    showSelected: Boolean = false,
    karaoke: Boolean = false,
    onEditSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
) {
    val items = if (showSelected) {
        listOf(
            ButtonProps(
                "Edit", Icons.Default.Edit, onEditSelected
            ),
            ButtonProps(
                "Delete", Icons.Default.Delete, onDeleteSelected
            )
        )
    } else {
        val items = mutableListOf(
            ButtonProps(
                "Generate", Icons.Default.GeneratingTokens, onGenerate
            ),
            ButtonProps(
                "Edit All", Icons.Default.EditNote, onEditAllCaptions
            )
        )

        if (karaoke) {
            items.addAll(
                listOf(
                    ButtonProps(
                        "Add Cap", Icons.Default.Add, onAddNormalCaption
                    ),
                    ButtonProps(
                        "Add kCap", Icons.Default.Add, onAddKaraokeCaption
                    ),
                    ButtonProps(
                        "Add Word", Icons.Default.Add, onAddWord
                    )
                )
            )
        } else {
            items.add(
                ButtonProps(
                    "Add", Icons.Default.Add, onAddNormalCaption
                ),
            )
        }
        items
    }

    LazyRow (
        modifier = modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items) {
            BottomButton(
                onClick = it.onClick,
                imageVector = it.imageVector,
                text = it.text
            )
        }
    }
}

@Composable
private fun BottomButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    imageVector: ImageVector,
    text: String
) {
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,  // hoặc ripple nếu muốn có hiệu ứng
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = imageVector, contentDescription = text)
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ButtonPreview() {
    CaptionerTheme {
        BottomControlBar(showSelected = true, karaoke = true)
    }
}