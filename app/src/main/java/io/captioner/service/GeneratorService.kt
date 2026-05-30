package io.captioner.service

import android.content.Context
import android.net.Uri
import android.util.Log
import io.captioner.WhisperContextHolder
import io.captioner.data.dto.CaptionKaraokeDto
import io.captioner.data.model.Caption
import io.captioner.data.model.CaptionStyle
import io.captioner.data.model.KaraokeWord
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream

object GeneratorService {
    val retrofit: Retrofit by lazy {
        Log.d("GeneratorService", "Creating Retrofit with custom OkHttpClient...")
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                Log.d("GeneratorService", "OkHttp interceptor: ${chain.request().url}")
                val response = chain.proceed(chain.request())
                Log.d("GeneratorService", "OkHttp response code: ${response.code}")
                response
            }
            .build()

        Retrofit.Builder()
            .baseUrl("http://107.98.86.20:8000/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: GeneratorApi by lazy { retrofit.create(GeneratorApi::class.java) }

    suspend fun generateCaptions(
        projectId: String,
        audioPath: String,
        captionStyle: CaptionStyle? = null
    ): GenerationResult {
        try {
            val file = File(audioPath)
            Log.d("GeneratorService", "uploadFile: ${file.name}, size=${file.length()}")
            val requestFile = file.asRequestBody("audio/wav".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            Log.d("GeneratorService", "Sending request to server...")
            val response = api.generateCaptions(body)
            val responseBody = response.body()
            Log.d("GeneratorService", "response code = ${response.code()}")
            Log.d("GeneratorService", "response message = ${response.message()}")
            Log.d("GeneratorService", "response isSuccessful = ${response.isSuccessful}")
            Log.d("GeneratorService", "response errorBody = ${response.errorBody()?.string()}")

            if (response.isSuccessful && responseBody != null) {
                return GenerationResult(
                    isSuccessful = true,
                    message = response.message(),
                    language = responseBody.language,
                    processingTime = responseBody.processing_time,
                    captions = responseBody.data.map {
                        CaptionKaraokeDto(
                            caption = Caption(
                                projectId = projectId,
                                text = it.text,
                                startTimeMs = (it.start * 1000).toLong(),
                                endTimeMs = (it.end * 1000).toLong(),
                                style = captionStyle ?: CaptionStyle()
                            )
                        )
                    }
                )
            } else {
                return GenerationResult(
                    isSuccessful = false,
                    message = response.message()
                )
            }
        } catch (e: Exception) {
            Log.e("GeneratorService", "Exception type: ${e.javaClass.name}")
            Log.e("GeneratorService", "Exception message: ${e.message}")
            Log.e("GeneratorService", "Cause: ${e.cause?.javaClass?.name}: ${e.cause?.message}")
            e.printStackTrace()
            return GenerationResult(
                isSuccessful = false,
                message = "Error Captioning: ${e.message}"
            )
        }
    }

    suspend fun generateKaraoke(
        projectId: String,
        context: Context,
        videoUri: Uri,
        captionStyle: CaptionStyle? = null
    ): GenerationResult {
        try {
            val file = copyUriToFile(context, videoUri)
            Log.d("GeneratorService", "generateKaraoke uploadFile: ${file.name}, size=${file.length()}")
            val requestFile = file.asRequestBody("video/mp4".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            Log.d("GeneratorService", "generateKaraoke Sending request...")
            val response = api.generateKaraoke(body)
            val responseBody = response.body()
            Log.d("GeneratorService", "generateKaraoke response code = ${response.code()}")
            Log.d("GeneratorService", "generateKaraoke response message = ${response.message()}")

            if (response.isSuccessful && responseBody != null) {
                val captions = mutableListOf<Caption>()
                val karaokeWords = mutableListOf<List<KaraokeWord>>()
                for (segment in responseBody.data) {
                    val caption = Caption(
                        projectId = projectId,
                        text = segment.text,
                        startTimeMs = (segment.start * 1000).toLong(),
                        endTimeMs = (segment.end * 1000).toLong(),
                        style = captionStyle ?: CaptionStyle()
                    )
                    captions.add(caption)
                    karaokeWords.add(
                        segment.words.map {
                            KaraokeWord(
                                captionId = caption.id,
                                text = it.text,
                                startTimeMs = (it.start * 1000).toLong(),
                                endTimeMs = (it.end * 1000).toLong()
                            )
                        }
                    )
                }
                return GenerationResult(
                    isSuccessful = true,
                    message = response.message(),
                    language = responseBody.language,
                    captions = captions.mapIndexed { index, caption ->
                        CaptionKaraokeDto(
                            caption = caption,
                            karaokeWords = karaokeWords[index]
                        )
                    },
                    processingTime = responseBody.processing_time,
                    separatedVocalVideoId = responseBody.separated_vocal_id
                )
            } else {
                return GenerationResult(
                    isSuccessful = false,
                    message = response.message()
                )
            }
        } catch (e: Exception) {
            Log.e("GeneratorService", "generateKaraoke Exception type: ${e.javaClass.name}")
            Log.e("GeneratorService", "generateKaraoke Exception message: ${e.message}")
            Log.e("GeneratorService", "generateKaraoke Cause: ${e.cause?.javaClass?.name}: ${e.cause?.message}")
            e.printStackTrace()
            return GenerationResult(
                isSuccessful = false,
                message = "Error Captioning: ${e.message}"
            )
        }
    }

    suspend fun separateVocal(
        context: Context,
        videoId: String
    ): SeparateVocalResult {
        return try {
            val response = api.separateVocal(videoId)

            if (response.isSuccessful && response.body() != null) {
                // Lưu file trả về vào cùng thư mục với audioPath gốc
                val outputFile = File(context.filesDir, "no_vocals_${System.currentTimeMillis()}.mp4")
                response.body()!!.byteStream().use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("GeneratorService", "separateVocal saved to: ${outputFile.absolutePath}")
                SeparateVocalResult(isSuccessful = true, outputPath = outputFile.absolutePath)
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.message()
                Log.e("GeneratorService", "separateVocal failed: $errorMsg")
                SeparateVocalResult(isSuccessful = false, message = errorMsg)
            }
        } catch (e: Exception) {
            Log.e("GeneratorService", "separateVocal exception: ${e.message}", e)
            SeparateVocalResult(isSuccessful = false, message = "Error: ${e.message}")
        }
    }

    suspend fun mockGenerateCaptions(
        projectId: String,
        audioPath: String,
        captionStyle: CaptionStyle? = null,
        fail: Boolean = false
    ): GenerationResult {
        delay(3000)
        if (fail) {
            return GenerationResult(isSuccessful = false, message = "Fail to generate captions!")
        }
        val examples = listOf(
            Caption(projectId = projectId, text = "Hello guys", startTimeMs = 0, endTimeMs = 2000, style = captionStyle ?: CaptionStyle()),
            Caption(projectId = projectId, text = "I can't believe that", startTimeMs = 2001, endTimeMs = 4000, style = captionStyle ?: CaptionStyle()),
            Caption(projectId = projectId, text = "What the hell is going on", startTimeMs = 4001, endTimeMs = 8000, style = captionStyle ?: CaptionStyle()),
            Caption(projectId = projectId, text = "Nice try", startTimeMs = 9000, endTimeMs = 11000, style = captionStyle ?: CaptionStyle())
        )
        return GenerationResult(
            isSuccessful = true, message = "", language = "en",
            captions = examples.map { CaptionKaraokeDto(caption = it) }
        )
    }

    suspend fun mockGenerateKaraoke(
        projectId: String,
        audioPath: String,
        captionStyle: CaptionStyle? = null,
        fail: Boolean = false
    ): GenerationResult {
        delay(3000)
        val captions = listOf(
            Caption(projectId = projectId, text = "I found a love", startTimeMs = 0, endTimeMs = 5000),
            Caption(projectId = projectId, text = "For me", startTimeMs = 6000, endTimeMs = 8000)
        )
        val karaokeWords = listOf(
            listOf(
                KaraokeWord(captionId = captions[0].id, text = "I", startTimeMs = 0, endTimeMs = 500),
                KaraokeWord(captionId = captions[0].id, text = "found", startTimeMs = 700, endTimeMs = 2000),
                KaraokeWord(captionId = captions[0].id, text = "a", startTimeMs = 2500, endTimeMs = 3500),
                KaraokeWord(captionId = captions[0].id, text = "love", startTimeMs = 4000, endTimeMs = 5000)
            ),
            listOf(
                KaraokeWord(captionId = captions[1].id, text = "For", startTimeMs = 6000, endTimeMs = 7000),
                KaraokeWord(captionId = captions[1].id, text = "me", startTimeMs = 7000, endTimeMs = 8000)
            )
        )
        return GenerationResult(
            isSuccessful = true, message = "OK",
            captions = captions.mapIndexed { index, caption ->
                CaptionKaraokeDto(caption = caption, karaokeWords = karaokeWords[index])
            }
        )
    }

    suspend fun generateKaraokeLocally(
        context: Context,
        projectId: String,
        audioPath: String,
        captionStyle: CaptionStyle? = null
    ): GenerationResult {
        return try {
            val transcribe = WhisperContextHolder.transcribeFunction
                ?: return GenerationResult(isSuccessful = false, message = "Whisper not ready")
            val audioFile = File(audioPath)
            if (!audioFile.exists()) return GenerationResult(isSuccessful = false, message = "Audio file not found")
            val audioUri = Uri.fromFile(audioFile)
            val converter = io.captioner.audio.AudioConverter(context)
            val conversionResult = converter.convertToWav(audioUri)
            Log.d("GeneratorService", "converted WAV size = ${conversionResult.wavFile.length()}")
            val jsonString = transcribe(conversionResult.wavFile, "karaoke")
            Log.d("GeneratorService", "jsonString = $jsonString")
            val json = org.json.JSONObject(jsonString)
            if (json.optString("status") != "success") {
                return GenerationResult(isSuccessful = false, message = json.optString("message"))
            }
            val dataArray = json.getJSONArray("data")
            val captionDtos = mutableListOf<CaptionKaraokeDto>()
            for (i in 0 until dataArray.length()) {
                val seg = dataArray.getJSONObject(i)
                val caption = Caption(
                    projectId = projectId,
                    text = seg.getString("text"),
                    startTimeMs = (seg.getDouble("start") * 1000).toLong(),
                    endTimeMs = (seg.getDouble("end") * 1000).toLong(),
                    style = captionStyle ?: CaptionStyle()
                )
                val words = mutableListOf<KaraokeWord>()
                if (seg.has("words")) {
                    val wordsArray = seg.getJSONArray("words")
                    for (j in 0 until wordsArray.length()) {
                        val w = wordsArray.getJSONObject(j)
                        words.add(KaraokeWord(
                            captionId = caption.id,
                            text = w.getString("text"),
                            startTimeMs = (w.getDouble("start") * 1000).toLong(),
                            endTimeMs = (w.getDouble("end") * 1000).toLong()
                        ))
                    }
                }
                captionDtos.add(CaptionKaraokeDto(caption = caption, karaokeWords = words))
            }
            GenerationResult(isSuccessful = true, message = "OK", captions = captionDtos)
        } catch (e: Exception) {
            Log.e("GeneratorService", "ERROR: ${e.message}", e)
            GenerationResult(isSuccessful = false, message = "Error: ${e.message}")
        }
    }

    suspend fun generateCaptionsLocally(
        context: Context,
        projectId: String,
        audioPath: String,
        captionStyle: CaptionStyle? = null,
    ): GenerationResult {
        return try {
            val transcribe = WhisperContextHolder.transcribeFunction
                ?: return GenerationResult(isSuccessful = false, message = "Whisper not ready")
            val audioFile = File(audioPath)
            if (!audioFile.exists()) return GenerationResult(isSuccessful = false, message = "Audio file not found")

            val audioUri = android.net.Uri.fromFile(audioFile)
            val converter = io.captioner.audio.AudioConverter(context)
            val conversionResult = converter.convertToWav(audioUri)
            Log.d("GeneratorService", "converted WAV size = ${conversionResult.wavFile.length()}")

            val jsonString = transcribe(conversionResult.wavFile, "caption")
            Log.d("GeneratorService", "jsonString = $jsonString")

            val json = org.json.JSONObject(jsonString)
            if (json.optString("status") != "success") {
                return GenerationResult(isSuccessful = false, message = json.optString("message"))
            }
            val dataArray = json.getJSONArray("data")
            val captionDtos = mutableListOf<CaptionKaraokeDto>()
            for (i in 0 until dataArray.length()) {
                val seg = dataArray.getJSONObject(i)
                captionDtos.add(CaptionKaraokeDto(
                    caption = Caption(
                        projectId = projectId,
                        text = seg.getString("text"),
                        startTimeMs = (seg.getDouble("start") * 1000).toLong(),
                        endTimeMs = (seg.getDouble("end") * 1000).toLong(),
                        style = captionStyle ?: CaptionStyle()
                    )
                ))
            }
            GenerationResult(isSuccessful = true, message = "OK", captions = captionDtos)
        } catch (e: Exception) {
            Log.e("GeneratorService", "ERROR: ${e.message}", e)
            GenerationResult(isSuccessful = false, message = "Error: ${e.message}")
        }
    }

    suspend fun sendSrtToServer(
        srtUri: android.net.Uri,
        context: android.content.Context,
        serverUrl: String = "http://107.98.86.20:8000/upload-srt"
    ): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(srtUri) ?: return false
            val bytes = inputStream.readBytes()
            inputStream.close()
            val requestFile = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), bytes)
            val body = MultipartBody.Part.createFormData("file", "subtitle.srt", requestFile)
            val tempRetrofit = Retrofit.Builder()
                .baseUrl(serverUrl.substringBefore("/upload-srt") + "/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val tempApi = tempRetrofit.create(GeneratorApi::class.java)
            val response = tempApi.uploadSrt(body)
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("GeneratorService", "Send SRT error: ${e.message}", e)
            false
        }
    }

    private fun mergeTokensToWords(
        tokens: List<com.whispercpp.whisper.WhisperToken>
    ): List<com.whispercpp.whisper.WhisperWord> {
        val words = mutableListOf<com.whispercpp.whisper.WhisperWord>()
        var currentText = StringBuilder()
        var currentStart = -1f
        var currentEnd = -1f
        for (token in tokens) {
            val text = token.text
            if (text.isBlank()) continue
            val isNewWord = text.startsWith(" ") || text.startsWith("\u2581")
            if (isNewWord && currentText.isNotEmpty()) {
                words.add(com.whispercpp.whisper.WhisperWord(currentText.toString().trim(), currentStart, currentEnd))
                currentText.clear()
                currentStart = token.start
            }
            if (currentStart == -1f) currentStart = token.start
            currentText.append(text.replace(" ", "").replace("_", ""))
            currentEnd = token.end
        }
        if (currentText.isNotEmpty()) {
            words.add(com.whispercpp.whisper.WhisperWord(currentText.toString().trim(), currentStart, currentEnd))
        }
        for (i in 0 until words.size - 1) {
            if (words[i].end > words[i + 1].start) {
                words[i] = words[i].copy(end = words[i + 1].start)
            }
        }
        return words
    }

    private fun processWhisperTranscription(
        allWords: List<com.whispercpp.whisper.WhisperWord>,
        mode: String
    ): List<com.whispercpp.whisper.WhisperSegment> {
        if (allWords.isEmpty()) return emptyList()
        val majorChunks = mutableListOf<List<com.whispercpp.whisper.WhisperWord>>()
        var currentMajor = mutableListOf<com.whispercpp.whisper.WhisperWord>()
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
        val formattedData = mutableListOf<com.whispercpp.whisper.WhisperSegment>()
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
                formattedData.add(
                    com.whispercpp.whisper.WhisperSegment(
                        text = subChunk.joinToString(" ") { it.text },
                        start = subChunk.first().start,
                        end = subChunk.last().end,
                        words = if (mode == "karaoke") subChunk else null
                    )
                )
                i += take
            }
        }
        return formattedData
    }
}

fun copyUriToFile(context: Context, uri: Uri): File {
    val file = File(context.cacheDir, "${System.currentTimeMillis()}.mp4")
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        FileOutputStream(file).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }
    return file
}