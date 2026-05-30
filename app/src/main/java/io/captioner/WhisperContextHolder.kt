package io.captioner

import com.whispercpp.whisper.WhisperContext

object WhisperContextHolder {
    var context: WhisperContext? = null
    var transcribeFunction: (suspend (java.io.File, String) -> String)? = null
}