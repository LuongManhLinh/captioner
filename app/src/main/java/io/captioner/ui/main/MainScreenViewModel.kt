package io.captioner.ui.main

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whispercpp.whisper.WhisperContext
import com.whispercpp.whisper.WhisperSegment
import com.whispercpp.whisper.WhisperToken
import com.whispercpp.whisper.WhisperWord
import io.captioner.WhisperContextHolder
import io.captioner.audio.AudioConverter
import io.captioner.media.decodeWaveFile
import io.captioner.recorder.Recorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

fun round2(value: Float): Double {
    return String.format(Locale.US, "%.2f", value). toDouble()
}

private const val LOG_TAG = "MainScreenViewModel"

class MainScreenViewModel(private val application: Application) : ViewModel() {
    var canTranscribe by mutableStateOf(false)
        private set
    var dataLog by mutableStateOf("")
        private set
    var isRecording by mutableStateOf(false)
        private set
    private fun mergeTokensToWords(tokens: List<WhisperToken>): List<WhisperWord> {
        val words = mutableListOf<WhisperWord>()
        val currentText = StringBuilder()
        var currentStart = -1f
        var currentEnd = -1f

        for (token in tokens) {
            val text = token.text
            if (text.isBlank()) continue

            // Token mới bắt đầu bằng khoảng trắng hoặc ký tự BPE đặc biệt ("_" / " ")
            val isNewWord = text.startsWith(" ") || text.startsWith(" ") || text.startsWith("_")

            if (isNewWord && currentText.isNotEmpty()) {
                words.add(WhisperWord(currentText.toString().trim(), currentStart, currentEnd))
                currentText.clear()
                currentStart = token.start
            }

            if (currentStart == -1f) currentStart = token.start
            currentText.append(text.replace(" ", "").replace("_", ""))
            currentEnd = token.end
        }

        if (currentText.isNotEmpty()) {
            words.add(WhisperWord(currentText.toString().trim(), currentStart, currentEnd))
        }

        // Làm mượt: Đảm bảo thời gian kết thúc của từ trước không vượt quá từ sau
        for (i in 0 until words.size - 1) {
            if (words[i].end > words[i + 1].start) {
                words[i] = words[i].copy(end = words[i + 1].start)
            }
        }
        return words
    }

    private fun processWhisperTranscription(allWords: List<WhisperWord>, mode: String): List<WhisperSegment> {
        if (allWords.isEmpty()) return emptyList()

        // 1. GIAI ĐOẠN 1: GOM THÀNH "CÂU LỚN" DỰA TRÊN CHỮ IN HOA
        val majorChunks = mutableListOf<List<WhisperWord>>()
        var currentMajor = mutableListOf<WhisperWord>()

        for (w in allWords) {
            val firstLetter = w.text.find { it.isLetter() }
            val isUpper = firstLetter?.isUpperCase() ?: false

            if (isUpper && currentMajor.isNotEmpty()) {
                majorChunks.add(currentMajor)
                currentMajor = mutableListOf(w)
            } else {
                currentMajor.add(w)
            }
        }
        if (currentMajor.isNotEmpty()) majorChunks.add(currentMajor)

        // 2. GIAI ĐOẠN 2: CHIA CÂU LỚN THÀNH SEGMENT CON (MAX 6, MIN 2)
        val formattedData = mutableListOf<WhisperSegment>()

        for (chunk in majorChunks) {
            val n = chunk.size
            var i = 0
            while (i < n) {
                val remaining = n - i
                val take = when {
                    remaining <= 6 -> remaining
                    remaining == 7 -> 4
                    else -> 6
                }

                val subChunk = chunk.subList(i, i + take)
                val segmentData = WhisperSegment(
                    text = subChunk.joinToString(" ") { it.text },
                    start = subChunk.first().start,
                    end = subChunk.last().end,
                    words = if (mode == "karaoke") subChunk else null
                )
                formattedData.add(segmentData)
                i += take
            }
        }
        return formattedData
    }

    private val modelsPath = File(application.filesDir, "models")
    private val samplesPath = File(application.filesDir, "samples")
    private var recorder: Recorder = Recorder()
    var whisperContext: WhisperContext? = null
        private set
    private var mediaPlayer: MediaPlayer? = null
    private var recordedFile: File? = null

    init {
        viewModelScope.launch {
            printSystemInfo()
            loadData()
        }
    }

    private suspend fun printSystemInfo() {
        printMessage(String.format("System Info: %s\n", WhisperContext.getSystemInfo()))
    }

    private suspend fun loadData() {
        printMessage("Loading data...\n")
        try {
            copyAssets()
            loadBaseModel()
            canTranscribe = true
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
        }
    }

    private suspend fun printMessage(msg: String) = withContext(Dispatchers.Main) {
        dataLog += msg
    }

    private suspend fun copyAssets() = withContext(Dispatchers.IO) {
        modelsPath.mkdirs()
        samplesPath.mkdirs()
        //application.copyData("models", modelsPath, ::printMessage)
        application.copyData("samples", samplesPath, ::printMessage)
        printMessage("All data copied to working directory.\n")
    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        WhisperContextHolder.context = whisperContext
        WhisperContextHolder.transcribeFunction = { file, mode -> transcribeAudio(file, mode) }
        printMessage("Loading model...\n")
        val models = application.assets.list("models/")
        if (models != null) {
            whisperContext = WhisperContext.createContextFromAsset(application.assets, "models/" + models[0])

            WhisperContextHolder.context = whisperContext
            printMessage("Loaded model ${models[0]}.\n")
        }

        //val firstModel = modelsPath.listFiles()!!.first()
        //whisperContext = WhisperContext.createContextFromFile(firstModel.absolutePath)
    }

    fun benchmark() = viewModelScope.launch {
        runBenchmark(6)
    }

    fun transcribeSample() = viewModelScope.launch {
        transcribeAudio(getFirstSample())
    }

    private suspend fun runBenchmark(nthreads: Int) {
        if (!canTranscribe) {
            return
        }

        canTranscribe = false

        printMessage("Running benchmark. This will take minutes...\n")
        whisperContext?.benchMemory(nthreads)?.let{ printMessage(it) }
        printMessage("\n")
        whisperContext?.benchGgmlMulMat(nthreads)?.let{ printMessage(it) }

        canTranscribe = true
    }

    private suspend fun getFirstSample(): File = withContext(Dispatchers.IO) {
        samplesPath.listFiles()!!.first()
    }

    private suspend fun readAudioSamples(file: File): FloatArray = withContext(Dispatchers.IO) {
        stopPlayback()
        //startPlayback(file)
        return@withContext decodeWaveFile(file)
    }

    private suspend fun stopPlayback() = withContext(Dispatchers.Main) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private suspend fun startPlayback(file: File) = withContext(Dispatchers.Main) {
        mediaPlayer = MediaPlayer.create(application, Uri.fromFile(file))
        mediaPlayer?.start()
    }

    private suspend fun transcribeAudio(file: File, mode: String = "karaoke"): String {
        Log.d("MainScreenViewModel", "transcribeAudio file = ${file.absolutePath}")
        Log.d("MainScreenViewModel", "file size = ${file.length()}")
        if (!canTranscribe) return ""
        canTranscribe = false

        val resultJson = JSONObject()

        try {
            printMessage("\n--- Đang xử lý chế độ: ${mode.uppercase()} ---\n")
            val data = readAudioSamples(file)
            Log.d("MainScreenViewModel", "audio samples size = ${data.size}")
            val startTimestamp = System.currentTimeMillis()

            // 1. Lấy token thô
            val rawTokens = whisperContext?.transcribeDataWithTokens(data) ?: emptyList()

            // 2. Gộp token thành từ
            val allWords = mergeTokensToWords(rawTokens)

            // 3. Chia đoạn
            val segments = processWhisperTranscription(allWords, mode)

            val processingTime = (System.currentTimeMillis() - startTimestamp) / 1000.0

            // 4. Đóng gói vào JSON thay vì StringBuilder
            resultJson.put("status", "success")
            resultJson.put("message", "Xử lý thành công")
            resultJson.put("processing_time", processingTime)

            val segmentsJsonArray = JSONArray()
            segments.forEach { seg ->
                val segmentObj = JSONObject()
                segmentObj.put("text", seg.text)
                segmentObj.put("start", round2(seg.start))
                segmentObj.put("end", round2(seg.end))

                // Nếu là karaoke thì nhét thêm mảng words vào
                if (mode == "karaoke" && seg.words != null) {
                    val wordsJsonArray = JSONArray()
                    seg.words?.forEach { w ->
                        val wordObj = JSONObject()
                        wordObj.put("text", w.text)
                        wordObj.put("start", round2(w.start))
                        wordObj.put("end", round2(w.end))
                        wordsJsonArray.put(wordObj)
                    }
                    segmentObj.put("words", wordsJsonArray)
                }
                segmentsJsonArray.put(segmentObj)
            }
            resultJson.put("data", segmentsJsonArray)

            val jsonString = resultJson.toString(4) // Fomat JSON đẹp với 4 spaces

            // In thẳng JSON ra màn hình app để bạn xem
            printMessage("Hoàn thành trong ${processingTime}ms:\n$jsonString\n")

            canTranscribe = true
            return jsonString

        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Unknown Error"
            printMessage("Lỗi: $errorMsg\n")

            // Trả về JSON chứa lỗi
            resultJson.put("error", errorMsg)
            canTranscribe = true
            return resultJson.toString(4)
        }
    }

    fun transcribeFromUri(uri: Uri, mode: String = "karaoke") = viewModelScope.launch {
        if (!canTranscribe) return@launch
        canTranscribe = false

        try {
            printMessage("\n--- Convert audio từ URI ---\n")

            // 1. Convert URI → ShortArray 16kHz Mono (Speex)
            val converter = AudioConverter(application)
            val result = converter.convertToWav(uri)

            printMessage("Convert xong: ${result.durationSeconds.toFloat().let { "%.2f".format(it) }}s\n")

            // 2. Đọc WAV file → FloatArray cho whisper
            val floatData = decodeWaveFile(result.wavFile)

            // 3. Transcribe
            canTranscribe = true
            transcribeAudio(result.wavFile, mode)

        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("Lỗi convert: ${e.localizedMessage}\n")
            canTranscribe = true
        }
    }

    fun transcribeFromUriKaraoke(uri: Uri) = transcribeFromUri(uri, "karaoke")
    fun transcribeFromUriCaption(uri: Uri) = transcribeFromUri(uri, "caption")

    fun toggleRecord() = viewModelScope.launch {
        try {
            if (isRecording) {
                recorder.stopRecording()
                isRecording = false
                recordedFile?.let { transcribeAudio(it) }
            } else {
                stopPlayback()
                val file = getTempFileForRecording()
                recorder.startRecording(file) { e ->
                    viewModelScope.launch {
                        withContext(Dispatchers.Main) {
                            printMessage("${e.localizedMessage}\n")
                            isRecording = false
                        }
                    }
                }
                isRecording = true
                recordedFile = file
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, e)
            printMessage("${e.localizedMessage}\n")
            isRecording = false
        }
    }

    private suspend fun getTempFileForRecording() = withContext(Dispatchers.IO) {
        File.createTempFile("recording", "wav")
    }

    override fun onCleared() {
        runBlocking {
            whisperContext?.release()
            whisperContext = null
            stopPlayback()
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                val application =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainScreenViewModel(application)
            }
        }
    }
    fun transcribeCaption() = viewModelScope.launch {
        transcribeAudio(getFirstSample(), "caption")
    }

    fun transcribeKaraoke() = viewModelScope.launch {
        transcribeAudio(getFirstSample(), "karaoke")
    }
    suspend fun transcribeAudioForGeneration(
        audioPath: String,
        mode: String
    ): String {
        val file = File(audioPath)
        return transcribeAudio(file, mode)
    }
}

private suspend fun Context.copyData(
    assetDirName: String,
    destDir: File,
    printMessage: suspend (String) -> Unit
) = withContext(Dispatchers.IO) {
    assets.list(assetDirName)?.forEach { name ->
        val assetPath = "$assetDirName/$name"
        Log.v(LOG_TAG, "Processing $assetPath...")
        val destination = File(destDir, name)
        Log.v(LOG_TAG, "Copying $assetPath to $destination...")
        printMessage("Copying $name...\n")
        assets.open(assetPath).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.v(LOG_TAG, "Copied $assetPath to $destination")
    }
}