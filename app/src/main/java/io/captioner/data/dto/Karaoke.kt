package io.captioner.data.dto

import io.captioner.data.model.CaptionStyle

data class KaraokeWordDto(
    val id: String,
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

data class KaraokeSegmentDto(
    val id: String,
    val x: Float,
    val y: Float,
    val style: CaptionStyle,
    val words: List<KaraokeWordDto>
)