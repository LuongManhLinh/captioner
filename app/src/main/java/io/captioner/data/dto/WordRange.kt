package io.captioner.data.dto

import io.captioner.data.model.KaraokeWord

data class WordRange(
    val word: KaraokeWord,
    val start: Int,
    val end: Int
)