package io.captioner.data.model

import java.util.UUID

interface Captionable {
    val id: String
    val text: String
    var startTimeMs: Long
    var endTimeMs: Long
}