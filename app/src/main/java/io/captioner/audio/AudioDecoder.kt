package io.captioner.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteOrder

class AudioDecoder(private val context: Context) {

    data class DecodedAudio(
        val pcmShorts: ShortArray,
        val sampleRate: Int,
        val channelCount: Int
    )

    suspend fun decode(uri: Uri): DecodedAudio = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor().apply {
            setDataSource(context, uri, null)
        }

        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IOException("No audio track found")

        extractor.selectTrack(trackIndex)
        val format       = extractor.getTrackFormat(trackIndex)
        val mime         = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate   = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val pcmBuffer = decodeTrack(extractor, format, mime)
        extractor.release()

        DecodedAudio(
            pcmShorts    = pcmBuffer,
            sampleRate   = sampleRate,
            channelCount = channelCount
        )
    }

    private fun decodeTrack(
        extractor: MediaExtractor,
        format: MediaFormat,
        mime: String
    ): ShortArray {
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val output = ArrayList<Short>(1024 * 1024)
        val info   = MediaCodec.BufferInfo()
        var eos    = false

        while (!eos) {
            val inIdx = decoder.dequeueInputBuffer(10_000L)
            if (inIdx >= 0) {
                val buf  = decoder.getInputBuffer(inIdx)!!
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) {
                    decoder.queueInputBuffer(
                        inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    eos = true
                } else {
                    decoder.queueInputBuffer(
                        inIdx, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }

            val outIdx = decoder.dequeueOutputBuffer(info, 10_000L)
            if (outIdx >= 0) {
                val outBuf = decoder.getOutputBuffer(outIdx)!!
                    .order(ByteOrder.LITTLE_ENDIAN)
                val shorts = outBuf.asShortBuffer()
                repeat(shorts.remaining()) { output.add(shorts.get()) }
                decoder.releaseOutputBuffer(outIdx, false)
            }
        }

        decoder.stop()
        decoder.release()
        return output.toShortArray()
    }
}