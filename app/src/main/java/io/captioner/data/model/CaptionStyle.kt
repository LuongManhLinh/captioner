package io.captioner.data.model

import androidx.compose.ui.graphics.Color

data class CaptionStyle(
    val alignment: CaptionAlignment = CaptionAlignment.BOTTOM_CENTER,
    val fontSize: Int = 12,
    val outerPadding: Int = 0,
    val innerPadding: Int = 2,
    val cornerRadius: Int = 0,
    val textColor: Color = Color.White,
    val backgroundColor: Color = Color.Black.copy(alpha = 0.5f),
    val karaokeColor: Color = Color.Yellow,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,

    val letterSpacingEnabled: Boolean = false,
    val letterSpacing: Float = 0f,

    val outlineEnabled: Boolean = false,
    val outlineColor: Color = Color.Black,
    val outlineWidth: Float = 0f
)