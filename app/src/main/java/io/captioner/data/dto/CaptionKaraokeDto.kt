package io.captioner.data.dto

import io.captioner.data.model.Caption
import io.captioner.data.model.KaraokeWord

data class CaptionKaraokeDto(
    val caption: Caption,
    val karaokeWords: List<KaraokeWord> = emptyList()
)

