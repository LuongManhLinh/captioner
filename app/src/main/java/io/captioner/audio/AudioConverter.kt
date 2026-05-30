package io.captioner.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Khai báo data class trả về khớp với MainActivity của bạn
data class ConversionResult(
    val wavFile: File,
    val durationSeconds: Double,
    val sampleRate: Int,
    val channelCount: Int
)

class AudioConverter(private val context: Context) {

    init {
        // Tên này phải khớp với cấu hình trong CMakeLists.txt
        //System.loadLibrary("audio_converter")
    }

    // Hàm gọi xuống C++ (đã viết ở bước trước)
    external fun nativeResample(
        inputPcm: ShortArray,
        inputSampleRate: Int,
        inputChannels: Int
    ): ShortArray

    /**
     * Decode, resample và lưu thành file WAV 16kHz Mono
     */
    suspend fun convertToWav(uri: Uri): ConversionResult = withContext(Dispatchers.IO) {

        // 1. Dùng MediaExtractor/MediaCodec lấy PCM gốc
        val decoder = AudioDecoder(context)
        val decodedAudio = decoder.decode(uri)

        // 2. Chuyển xuống C++ (Speex) để downmix và resample về 16kHz Mono
        val resampled16kHzMono = nativeResample(
            inputPcm = decodedAudio.pcmShorts,
            inputSampleRate = decodedAudio.sampleRate,
            inputChannels = decodedAudio.channelCount
        )

        // 3. Ghi ra file .wav trong thư mục cache của app để test
        val outDir = context.cacheDir
        val outFile = File(outDir, "whisper_test_16khz.wav")

        FileOutputStream(outFile).use { fos ->
            val sampleRate = 16000
            val channels = 1
            val bitsPerSample = 16
            val byteRate = sampleRate * channels * bitsPerSample / 8

            // Tính kích thước phần data
            val audioDataLen = (resampled16kHzMono.size * 2).toLong() // 1 short = 2 bytes
            val totalDataLen = audioDataLen + 36

            // Ghi 44 bytes WAV Header
            writeWavHeader(fos, totalDataLen, audioDataLen, sampleRate.toLong(), channels, byteRate.toLong())

            // Đổi ShortArray sang ByteArray và ghi xuống file
            val byteBuffer = ByteBuffer.allocate(resampled16kHzMono.size * 2)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            byteBuffer.asShortBuffer().put(resampled16kHzMono)

            fos.write(byteBuffer.array())
        }

        // 4. Trả kết quả về cho UI
        ConversionResult(
            wavFile = outFile,
            durationSeconds = resampled16kHzMono.size / 16000.0,
            sampleRate = 16000,
            channelCount = 1
        )
    }

    /**
     * Tạo Header chuẩn cho file WAV
     */
    private fun writeWavHeader(
        out: FileOutputStream, totalDataLen: Long, audioDataLen: Long,
        longSampleRate: Long, channels: Int, byteRate: Long
    ) {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (audioDataLen and 0xff).toByte()
        header[41] = (audioDataLen shr 8 and 0xff).toByte()
        header[42] = (audioDataLen shr 16 and 0xff).toByte()
        header[43] = (audioDataLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }
    companion object {
        const val TARGET_SAMPLE_RATE = 16000
    }
}
