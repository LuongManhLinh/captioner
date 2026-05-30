package io.captioner.ui.export.services

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import io.captioner.data.dto.WordRange
import io.captioner.data.model.CaptionAlignment
import io.captioner.data.model.KaraokeWord
import kotlin.math.ceil

@UnstableApi
class DynamicCaptionOverlay(
    private val captions: List<CaptionKaraokeDto>,
    private val videoWidth: Int,
    private val videoHeight: Int,
    private val ratio: Float,
    private val density: Float,
    private val karaoke: Boolean
) : BitmapOverlay() {

    // Reuse a single Bitmap and Canvas to prevent OutOfMemory errors while
    // allowing frame-by-frame animation (like karaoke) without creating new objects
    private val overlayBitmap = createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
    private val overlayCanvas = Canvas(overlayBitmap)
    private val transparentBitmap = createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val timeMs = presentationTimeUs / 1000

        // Support multiple active captions simultaneously
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

        // 1. Scale dimensions using density (for dp/sp) AND ratio (preview to original mapping)
        val scaleFactor = density * ratio
        val textSizePx = style.fontSize * scaleFactor
        val innerPaddingPx = style.innerPadding * scaleFactor
        val outerPaddingPx = style.outerPadding * scaleFactor
        val cornerRadiusPx = style.cornerRadius * scaleFactor
        val outlineWidthPx = style.outlineWidth * ratio

        // 2. Setup Typeface handling bold and italic
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

        val outlinePaint = TextPaint(textPaint).apply {
            color = style.outlineColor.toArgb()
            this.style = Paint.Style.STROKE
            strokeWidth = outlineWidthPx
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val fm = textPaint.fontMetrics
        val defaultHeight = fm.descent - fm.ascent
        val targetHeight = textSizePx * 1.2f
        val multiplier = targetHeight / defaultHeight

        // 4. Layout the text (handles multi-line text)
        // First pass: Build layout to calculate natural line wrapping
        val maxBgWidth = videoWidth - outerPaddingPx * 2
        val maxTextWidth = (maxBgWidth - innerPaddingPx * 2).coerceAtLeast(1f)
        val measureLayout = StaticLayout.Builder.obtain(
            caption.text, 0, caption.text.length, outlinePaint, maxTextWidth.toInt()
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, multiplier)
            .build()

        // Measure the exact width of the longest line after wrapping
        var maxLineWidth = 0f
        for (i in 0 until measureLayout.lineCount) {
            maxLineWidth = maxOf(maxLineWidth, measureLayout.getLineWidth(i))
        }

        // Second pass: Rebuild layouts with the exact, tight bounding width
        val tightWidth = ceil(maxLineWidth)
            .toInt()
            .coerceIn(1, maxTextWidth.toInt())

        val outlineLayout = StaticLayout.Builder.obtain(
            caption.text, 0, caption.text.length, outlinePaint, tightWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, multiplier)
            .build()

        val fillLayout = StaticLayout.Builder.obtain(
            caption.text, 0, caption.text.length, textPaint, tightWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, multiplier)
            .build()

        // 5. Calculate background dimensions
        val bgWidth = tightWidth + innerPaddingPx * 2
        val bgHeight = outlineLayout.height + (innerPaddingPx * 2)

        // 6. Calculate Coordinates based on Alignment and scaled outerPadding
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

        startX = startX.coerceIn(outerPaddingPx, videoWidth - bgWidth - outerPaddingPx)

        // 7. Draw Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.backgroundColor.toArgb()
        }
        val bgRect = RectF(startX, startY, startX + bgWidth, startY + bgHeight)
        canvas.drawRoundRect(bgRect, cornerRadiusPx, cornerRadiusPx, bgPaint)


        val karaokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.karaokeColor.toArgb()
            textSize = textSizePx
            typeface = captionTypeface
            isUnderlineText = style.underline
        }

        val karaokeLayout = StaticLayout.Builder.obtain(
            caption.text,
            0,
            caption.text.length,
            karaokePaint,
            tightWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, multiplier)
            .build()



        // 8. Draw Text with Frame-by-Frame Karaoke Effect
        canvas.withTranslation(startX + innerPaddingPx, startY + innerPaddingPx) {
            // Draw standard base text
            outlineLayout.draw(this)
            fillLayout.draw(this)

            if (karaoke) {
                val words = dto.karaokeWords

                val ranges = buildWordRanges(words)
                val activeWordIndex = ranges.indexOfLast {
                    currentTimeMs >= it.word.startTimeMs
                }

                if (activeWordIndex >= 0) {
                    val activeRange = ranges[activeWordIndex]
                    val activeWord = activeRange.word

                    val activeLine = fillLayout.getLineForOffset(activeRange.start)

                    val startX = fillLayout.getPrimaryHorizontal(activeRange.start)
                    val endX = fillLayout.getPrimaryHorizontal(activeRange.end)

                    val wordProgress =
                        if (currentTimeMs <= activeWord.endTimeMs) {
                            val duration = (activeWord.endTimeMs - activeWord.startTimeMs)
                                .coerceAtLeast(1L)

                            ((currentTimeMs - activeWord.startTimeMs).toFloat() / duration)
                                .coerceIn(0f, 1f)
                        } else {
                            1f
                        }

                    val activeHighlightX = startX + (endX - startX) * wordProgress

                    for (lineIndex in 0 until fillLayout.lineCount) {
                        val lineLeft = fillLayout.getLineLeft(lineIndex)
                        val lineRight = fillLayout.getLineRight(lineIndex)
                        val lineTop = fillLayout.getLineTop(lineIndex).toFloat()
                        val lineBottom = fillLayout.getLineBottom(lineIndex).toFloat()

                        val clipRight = when {
                            lineIndex < activeLine -> lineRight
                            lineIndex == activeLine -> activeHighlightX.coerceIn(lineLeft, lineRight)
                            else -> lineLeft
                        }

                        if (clipRight > lineLeft) {
                            this.save()
                            this.clipRect(
                                lineLeft,
                                lineTop,
                                clipRight,
                                lineBottom
                            )
                            karaokeLayout.draw(this)
                            this.restore()
                        }
                    }
                }
            }
        }
    }

}


private fun buildWordRanges(words: List<KaraokeWord>): List<WordRange> {
    val ranges = mutableListOf<WordRange>()
    val builder = StringBuilder()

    words.forEachIndexed { index, word ->
        val start = builder.length
        builder.append(word.text)
        val end = builder.length

        ranges.add(
            WordRange(
                word = word,
                start = start,
                end = end
            )
        )

        if (index != words.lastIndex) {
            builder.append(" ")
        }
    }

    return ranges
}

