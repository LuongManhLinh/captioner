package io.captioner.service

import io.captioner.data.dto.CaptionKaraokeDto

data class SegmentResponse(
    val text: String,
    val start: Float,
    val end: Float
)

data class CaptionResponse(
    val language: String?,
    val processing_time: Float?,
    val data: List<SegmentResponse>
)



data class GenerationResult(
    val isSuccessful: Boolean = false,
    val message: String = "",
    val language: String? = null,
    val processingTime: Float? = null,
    val captions: List<CaptionKaraokeDto> = listOf(),
    val separatedVocalVideoId: String = ""
)

data class KaraokeSegmentResponse(
    val text: String,
    val start: Float,
    val end: Float,
    val words: List<SegmentResponse>
)

data class KaraokeResponse(
    val language: String?,
    val processing_time: Float?,
    val separated_vocal_id: String,
    val data: List<KaraokeSegmentResponse>
)

data class SeparateVocalResult(
    val isSuccessful: Boolean = false,
    val message: String = "",
    val outputPath: String? = null
)