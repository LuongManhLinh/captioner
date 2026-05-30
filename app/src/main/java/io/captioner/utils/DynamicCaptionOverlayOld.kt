package io.captioner.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import io.captioner.data.dto.CaptionKaraokeDto
import io.captioner.data.model.Caption
import io.captioner.data.model.CaptionAlignment
import java.util.UUID
import androidx.core.graphics.withSave

@UnstableApi
class DynamicCaptionOverlay(
    private val captions: List<CaptionKaraokeDto>,
    private val videoWidth: Int,
    private val videoHeight: Int,
    private val ratio: Float,
    private val density: Float
) : BitmapOverlay() {

    // Reuse objects to prevent OutOfMemory errors and garbage collection stutters
    private val overlayBitmap = createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
    private val overlayCanvas = Canvas(overlayBitmap)
    private val transparentBitmap = createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    // Reusable Path for multi-line precise clipping
    private val clipPath = Path()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val timeMs = presentationTimeUs / 1000

        // Support multiple sactive captions simultaneously
        val activeCaptions = captions.filter { timeMs in it.caption.startTimeMs..it.caption.endTimeMs }

        if (activeCaptions.isEmpty()) {
            return transparentBitmap
        }

        // Clear the canvas from the previous frame
        overlayCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Draw all active captions onto the shared canvas
        activeCaptions.forEach { dto ->
            drawCaptionOntoCanvas(dto, timeMs, overlayCanvas)
        }

        return overlayBitmap
    }

    private fun drawCaptionOntoCanvas(dto: CaptionKaraokeDto, currentTimeMs: Long, canvas: Canvas) {
        val caption = dto.caption
        val style = caption.style
        val hasKaraoke = dto.karaokeWords.isNotEmpty()

        // 1. Scale dimensions using density AND ratio
        val scaleFactor = density * ratio
        val textSizePx = style.fontSize * scaleFactor
        val innerPaddingPx = style.innerPadding * scaleFactor
        val outerPaddingPx = style.outerPadding * scaleFactor
        val cornerRadiusPx = style.cornerRadius * scaleFactor

        // 2. Setup Typeface
        val typefaceStyle = when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val captionTypeface = Typeface.create(Typeface.DEFAULT, typefaceStyle)

        // 3. Setup Text Paints
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.textColor.toArgb()
            textSize = textSizePx
            typeface = captionTypeface
            isUnderlineText = style.underline
        }

        var karaokePaint: TextPaint? = null
        if (hasKaraoke) {
            karaokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.karaokeColor.toArgb()
                textSize = textSizePx
                typeface = captionTypeface
                isUnderlineText = style.underline
            }
        }

        // 4. Layout the text (handles multi-line text)
        var staticLayout = StaticLayout.Builder.obtain(
            caption.text, 0, caption.text.length, textPaint, videoWidth
        ).setAlignment(Layout.Alignment.ALIGN_CENTER).build()

        var maxLineWidth = 0f
        for (i in 0 until staticLayout.lineCount) {
            maxLineWidth = maxOf(maxLineWidth, staticLayout.getLineWidth(i))
        }

        val tightWidth = kotlin.math.ceil(maxLineWidth.toDouble()).toInt()

        staticLayout = StaticLayout.Builder.obtain(
            caption.text, 0, caption.text.length, textPaint, tightWidth
        ).setAlignment(Layout.Alignment.ALIGN_CENTER).build()

        var karaokeLayout: StaticLayout? = null
        if (hasKaraoke && karaokePaint != null) {
            karaokeLayout = StaticLayout.Builder.obtain(
                caption.text, 0, caption.text.length, karaokePaint, tightWidth
            ).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        }

        // 5. Calculate background dimensions
        val bgWidth = staticLayout.width + (innerPaddingPx * 2)
        val bgHeight = staticLayout.height + (innerPaddingPx * 2)

        // 6. Calculate Coordinates
        var startX: Float
        var startY: Float

        when (style.alignment) {
            CaptionAlignment.TOP_LEFT -> {
                startX = outerPaddingPx
                startY = outerPaddingPx
            }
            CaptionAlignment.TOP_CENTER -> {
                startX = (videoWidth - bgWidth) / 2f
                startY = outerPaddingPx
            }
            CaptionAlignment.TOP_RIGHT -> {
                startX = videoWidth - bgWidth - outerPaddingPx
                startY = outerPaddingPx
            }
            CaptionAlignment.LEFT -> {
                startX = outerPaddingPx
                startY = (videoHeight - bgHeight) / 2f
            }
            CaptionAlignment.CENTER -> {
                startX = (videoWidth - bgWidth) / 2f
                startY = (videoHeight - bgHeight) / 2f
            }
            CaptionAlignment.RIGHT -> {
                startX = videoWidth - bgWidth - outerPaddingPx
                startY = (videoHeight - bgHeight) / 2f
            }
            CaptionAlignment.BOTTOM_LEFT -> {
                startX = outerPaddingPx
                startY = videoHeight - bgHeight - outerPaddingPx
            }
            CaptionAlignment.BOTTOM_CENTER -> {
                startX = (videoWidth - bgWidth) / 2f
                startY = videoHeight - bgHeight - outerPaddingPx
            }
            CaptionAlignment.BOTTOM_RIGHT -> {
                startX = videoWidth - bgWidth - outerPaddingPx
                startY = videoHeight - bgHeight - outerPaddingPx
            }
            CaptionAlignment.CUSTOM -> {
                startX = caption.x * ratio
                startY = caption.y * ratio
            }
        }

        // 7. Draw Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.backgroundColor.toArgb()
        }
        val bgRect = RectF(startX, startY, startX + bgWidth, startY + bgHeight)
        canvas.drawRoundRect(bgRect, cornerRadiusPx, cornerRadiusPx, bgPaint)

        // 8. Draw Text with Frame-by-Frame Karaoke Effect
        canvas.withTranslation(startX + innerPaddingPx, startY + innerPaddingPx) {
            // Draw standard base text
            staticLayout.draw(this)

            if (hasKaraoke && karaokeLayout != null) {
                var targetCharIndex = 0f
                var searchStartIndex = 0

                // Find the exact character progress based on the current word timings
                for (i in dto.karaokeWords.indices) {
                    val word = dto.karaokeWords[i]
                    // Safe indexOf to bypass punctuation marks attached to words in the main text
                    val wordIndex = caption.text.indexOf(word.text, searchStartIndex)
                    val wordStartChar = if (wordIndex != -1) wordIndex else searchStartIndex
                    val wordEndChar = wordStartChar + word.text.length

                    if (currentTimeMs < word.startTimeMs) {
                        targetCharIndex = searchStartIndex.toFloat()
                        break
                    } else if (currentTimeMs <= word.endTimeMs) {
                        val duration = word.endTimeMs - word.startTimeMs
                        val elapsed = currentTimeMs - word.startTimeMs
                        val fraction = if (duration > 0) elapsed.toFloat() / duration else 1f
                        targetCharIndex = wordStartChar + (word.text.length * fraction)
                        break
                    } else {
                        searchStartIndex = wordEndChar
                        targetCharIndex = wordEndChar.toFloat()
                    }
                }

                if (targetCharIndex > 0f) {
                    this.withSave {
                        clipPath.reset()

                        // Build a clipping path that handles multi-line wrapping properly
                        for (i in 0 until karaokeLayout.lineCount) {
                            val lineStart = karaokeLayout.getLineStart(i)
                            val lineEnd = karaokeLayout.getLineEnd(i)
                            val lineTop = karaokeLayout.getLineTop(i).toFloat()
                            val lineBottom = karaokeLayout.getLineBottom(i).toFloat()
                            val lineLeft = karaokeLayout.getLineLeft(i)
                            val lineRight = karaokeLayout.getLineRight(i)

                            if (targetCharIndex >= lineEnd) {
                                // The entire line has been spoken
                                clipPath.addRect(
                                    lineLeft,
                                    lineTop,
                                    lineRight,
                                    lineBottom,
                                    Path.Direction.CW
                                )
                            } else if (targetCharIndex > lineStart) {
                                // The current speaking word is actively on this line
                                val xProgress =
                                    karaokeLayout.getPrimaryHorizontal(targetCharIndex.toInt())
                                clipPath.addRect(
                                    lineLeft,
                                    lineTop,
                                    xProgress,
                                    lineBottom,
                                    Path.Direction.CW
                                )
                            }
                        }

                        this.clipPath(clipPath)
                        karaokeLayout.draw(this)
                    }
                }
            }
        }
    }
}
