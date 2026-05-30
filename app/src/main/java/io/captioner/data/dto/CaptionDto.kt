package io.captioner.data.dto

import io.captioner.data.model.Caption
import io.captioner.data.model.CaptionStyle


data class CaptionDto(
    val id: String,
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val x: Float, // Use if Alignment is CUSTOM
    val y: Float, // Use if Alignment is CUSTOM,
    val style: CaptionStyle
) {
    constructor(caption: Caption) : this(
        caption.id,
        caption.text,
        caption.startTimeMs,
        caption.endTimeMs,
        caption.x,
        caption.y,
        caption.style
    )
}