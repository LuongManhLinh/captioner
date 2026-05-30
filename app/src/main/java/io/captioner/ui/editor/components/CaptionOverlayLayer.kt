package io.captioner.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.captioner.data.dto.CaptionKaraokeDto
import io.captioner.data.dto.WordRange
import io.captioner.data.model.Caption
import io.captioner.data.model.CaptionAlignment
import io.captioner.data.model.CaptionStyle
import io.captioner.data.model.KaraokeWord
import io.captioner.ui.components.OutlinedText
import io.captioner.ui.theme.CaptionerTheme
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment as ComposeAlignment

private const val LOG_TAG = "CaptionOverlay"
@Composable
fun CaptionOverlayLayer(
    modifier: Modifier = Modifier,
    captions: List<CaptionKaraokeDto>,
    currentTimeMs: Long,
    onDrag: (String, Float, Float) -> Unit,
    onDoubleTap: (String, Boolean) -> Unit,
    onTap: (String, Boolean) -> Unit,
    selectedCaptionId: String? = null,
    viewX: Int? = null,
    viewY: Int? = null,
    viewWidth: Int? = null,
    viewHeight: Int? = null,
    viewScale: Float = 1f,
) {
    val density = LocalDensity.current
    var widthDp: Dp? = null

    val layerModifier = if (viewX != null && viewY != null
        && viewWidth != null && viewHeight != null) {
//        Log.d(LOG_TAG, "$viewWidth x $viewHeight at ($viewX, $viewY)")
        modifier
            .offset { IntOffset(viewX, viewY) }
            .size(
                width = with(density) { viewWidth.toDp().also { widthDp = it } },
                height = with(density) { viewHeight.toDp() }
            )
    } else {
//        Log.d(LOG_TAG, "FILL MAX SIZE")
        modifier.fillMaxSize()
    }


    Box(modifier = layerModifier) {
        var selected: CaptionKaraokeDto? = null

        captions.forEach { dto ->
            val caption = dto.caption
            val words = dto.karaokeWords
            val karaoke = words.isNotEmpty()
            if (currentTimeMs in caption.startTimeMs..caption.endTimeMs) {
                if (caption.id == selectedCaptionId) {
                    selected = dto
                } else {
                    CaptionText(
                        dto = dto,
                        modifier = getAlignmentModifier(caption, viewScale),
                        onDrag = { id, amountX, amountY ->
                            onDrag(id, amountX, amountY)
                        },
                        onDoubleTap = { onDoubleTap(it, karaoke) },
                        onTap = { onTap(it, karaoke) },
                        isSelected = false,
                        maxWidthDp = widthDp,
                        currentTimeMs = currentTimeMs,
                        viewScale = viewScale
                    )
                }
            }
        }

        if (selected != null) {
            val karaoke = selected.karaokeWords.isNotEmpty()
//            Log.d(LOG_TAG, "Selected caption is ${selected.caption.text}, karaoke words is empty: ${selected.karaokeWords.isEmpty()}")
            CaptionText(
                dto = selected,
                modifier = getAlignmentModifier(selected.caption, viewScale),
                onDrag = { id, amountX, amountY ->
                    onDrag(id, amountX, amountY)
                },
                onDoubleTap = { onDoubleTap(it, karaoke) },
                onTap = { onTap(it, karaoke) },
                isSelected = true,
                maxWidthDp = widthDp,
                currentTimeMs = currentTimeMs,
                viewScale = viewScale
            )
        }
    }
}

@Composable
fun BoxScope.getAlignmentModifier(caption: Caption, viewScale: Float = 1f): Modifier {
    val alignment = caption.style.alignment
    return if (alignment == CaptionAlignment.CUSTOM) {
        // Custom coordinates ignore parent padding and use absolute position
        Modifier.offset { IntOffset(caption.x.roundToInt(), caption.y.roundToInt()) }
    } else {
        val composeAlignment = when (alignment) {
            CaptionAlignment.TOP_LEFT -> ComposeAlignment.TopStart
            CaptionAlignment.TOP_CENTER -> ComposeAlignment.TopCenter
            CaptionAlignment.TOP_RIGHT -> ComposeAlignment.TopEnd
            CaptionAlignment.LEFT -> ComposeAlignment.CenterStart
            CaptionAlignment.CENTER -> ComposeAlignment.Center
            CaptionAlignment.RIGHT -> ComposeAlignment.CenterEnd
            CaptionAlignment.BOTTOM_LEFT -> ComposeAlignment.BottomStart
            CaptionAlignment.BOTTOM_CENTER -> ComposeAlignment.BottomCenter
            CaptionAlignment.BOTTOM_RIGHT -> ComposeAlignment.BottomEnd
            CaptionAlignment.CUSTOM -> ComposeAlignment.Center // Unreachable
        }

        // Applying padding here forces the aligned bounds to shift inward from the Box edges,
        // preventing the text from overflowing out of the video constraints.
        Modifier
            .align(composeAlignment)
            .padding(caption.style.outerPadding.dp * viewScale)
    }
}



@Composable
fun CaptionText(
    dto: CaptionKaraokeDto,
    modifier: Modifier = Modifier,
    onDrag: (String, Float, Float) -> Unit = {_, _, _ ->},
    onDoubleTap: (String) -> Unit = { },
    onTap: (String) -> Unit = { },
    maxWidthDp: Dp? = null,
    isSelected: Boolean = false,
    currentTimeMs: Long,
    viewScale: Float = 1f
) {
    val caption = dto.caption
    val words = dto.karaokeWords
    val style = remember(caption) { caption.style }

    val dragModifier = remember(style.alignment, maxWidthDp, caption.id) {
        var m = if (style.alignment == CaptionAlignment.CUSTOM) {
            Modifier.pointerInput(caption.id + "drag") {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(caption.id, dragAmount.x, dragAmount.y)
                }
            }
        } else Modifier

        if (maxWidthDp != null) {
            m = m.widthIn(max = maxWidthDp)
        }
        m
    }

    val density = LocalDensity.current

    val innerPadding = style.innerPadding.dp * viewScale
    val cornerRadius = style.cornerRadius.dp * viewScale

    val fontSize = with(density) {
        (style.fontSize.dp * viewScale).toSp()
    }

    val lineHeight = with(density) {
        (style.fontSize.dp * viewScale * 1.2f).toSp()
    }

    val letterSpacing = if (style.letterSpacingEnabled) {
        with(density) {
            (style.letterSpacing.dp * viewScale).toSp()
        }
    } else {
        TextUnit.Unspecified
    }


    val fontWeight = if (style.bold) FontWeight.Bold else null
    val fontStyle = if (style.italic) FontStyle.Italic else null
    val textDecoration = if (style.underline) TextDecoration.Underline else null

    val selectedModifier = remember(isSelected) {
        if (isSelected) {
            Modifier.border(
                width = 1.dp * viewScale,
                color = style.textColor,
                shape = RoundedCornerShape(cornerRadius)
            )
        } else Modifier
    }

    Box(
        modifier = modifier
            .pointerInput(caption.id + "tap") {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap(caption.id) },
                    onTap = { onTap(caption.id) }
                )
            }
            .then(dragModifier)
            .background(
                style.backgroundColor,
                RoundedCornerShape(cornerRadius)
            )
            .then(selectedModifier)
            .padding(innerPadding),
        contentAlignment = ComposeAlignment.Center
    ) {
        if (words.isEmpty()) {
            OutlinedText(
                text = caption.text,
                fillColor = style.textColor,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontStyle = fontStyle,
                textDecoration = textDecoration,
                fontWeight = fontWeight,
                outlineColor = style.outlineColor,
                outlineWidth = style.outlineWidth,
                letterSpacing = letterSpacing,
                enabled = style.outlineEnabled
            )
        } else {
            val wordRanges = remember(words) {
                val list = mutableListOf<WordRange>()
                val builder = StringBuilder()

                words.forEachIndexed { index, w ->
                    val start = builder.length
                    builder.append(w.text)
                    val end = builder.length

                    list.add(WordRange(w, start, end))

                    if (index != words.lastIndex) {
                        builder.append(" ")
                    }
                }

                Pair(builder.toString(), list)
            }

            val fullText = wordRanges.first
            val ranges = wordRanges.second

            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

            val activeWordIndex = remember(currentTimeMs, ranges) {
                ranges.indexOfLast {
                    currentTimeMs >= it.word.startTimeMs
                }
            }
            OutlinedText(
                text = fullText,
                fillColor = style.textColor,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontStyle = fontStyle,
                textDecoration = textDecoration,
                fontWeight = fontWeight,
                outlineColor = style.outlineColor,
                outlineWidth = style.outlineWidth,
                letterSpacing = letterSpacing,
                onFillTextLayout = { textLayout = it },
                enabled = style.outlineEnabled,
                fillModifier = Modifier.drawWithContent {
                    drawContent()

                    val layout = textLayout ?: return@drawWithContent
                    if (activeWordIndex == -1) return@drawWithContent

                    val activeRange = ranges.getOrNull(activeWordIndex) ?: return@drawWithContent
                    val activeWord = activeRange.word

                    val activeLine = layout.getLineForOffset(activeRange.start)

                    val activeStartBox = layout.getBoundingBox(activeRange.start)
                    val activeEndBox = layout.getBoundingBox(activeRange.end - 1)

                    val progress =
                        if (currentTimeMs <= activeWord.endTimeMs) {
                            val duration = (activeWord.endTimeMs - activeWord.startTimeMs)
                                .coerceAtLeast(1L)

                            ((currentTimeMs - activeWord.startTimeMs).toFloat() / duration)
                                .coerceIn(0f, 1f)
                        } else {
                            1f
                        }

                    val activeHighlightX =
                        activeStartBox.left + (activeEndBox.right - activeStartBox.left) * progress

                    for (lineIndex in 0 until layout.lineCount) {
                        val lineLeft = layout.getLineLeft(lineIndex)
                        val lineRight = layout.getLineRight(lineIndex)
                        val lineTop = layout.getLineTop(lineIndex)
                        val lineBottom = layout.getLineBottom(lineIndex)

                        val clipRight = when {
                            // Previous lines should already be fully highlighted
                            lineIndex < activeLine -> lineRight

                            // Current line should be partially highlighted
                            lineIndex == activeLine -> activeHighlightX.coerceIn(lineLeft, lineRight)

                            // Future lines should not be highlighted yet
                            else -> lineLeft
                        }

                        if (clipRight > lineLeft) {
                            clipRect(
                                left = lineLeft,
                                top = lineTop,
                                right = clipRight,
                                bottom = lineBottom
                            ) {
                                drawText(
                                    textLayoutResult = layout,
                                    color = style.karaokeColor
                                )
                            }
                        }
                    }
                },

            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CaptionTextPreview() {
    CaptionerTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = ComposeAlignment.Center
        ) {
            CaptionText(
                dto = CaptionKaraokeDto(
                    caption = Caption(
                        id = "1",
                        projectId = "123",
                        startTimeMs = 0,
                        endTimeMs = 17000,
                        style = CaptionStyle(
                            innerPadding = 0,
                            fontSize = 16,
                            karaokeColor = Color.Red,
                            backgroundColor = Color.Transparent,
                            outlineWidth = 6f,
                            letterSpacingEnabled = true,
                            letterSpacing = 2f
                        )
                    ),
                    karaokeWords = listOf(
                        KaraokeWord(captionId = "1", text = "I", startTimeMs = 0, endTimeMs = 500),
                        KaraokeWord(
                            captionId = "1",
                            text = "found",
                            startTimeMs = 700,
                            endTimeMs = 2000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "a",
                            startTimeMs = 2500,
                            endTimeMs = 3500
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "love",
                            startTimeMs = 4000,
                            endTimeMs = 5000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "For",
                            startTimeMs = 5000,
                            endTimeMs = 6000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "me",
                            startTimeMs = 6000,
                            endTimeMs = 7000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "oh",
                            startTimeMs = 7000,
                            endTimeMs = 8000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "darling",
                            startTimeMs = 8000,
                            endTimeMs = 9000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "just",
                            startTimeMs = 9000,
                            endTimeMs = 10000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "dive",
                            startTimeMs = 10000,
                            endTimeMs = 11000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "right",
                            startTimeMs = 11000,
                            endTimeMs = 12000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "in",
                            startTimeMs = 12000,
                            endTimeMs = 13000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "and",
                            startTimeMs = 13000,
                            endTimeMs = 14000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "follow",
                            startTimeMs = 14000,
                            endTimeMs = 15000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "my",
                            startTimeMs = 15000,
                            endTimeMs = 16000
                        ),
                        KaraokeWord(
                            captionId = "1",
                            text = "lead",
                            startTimeMs = 16000,
                            endTimeMs = 17000
                        ),
                    )
                ),
                currentTimeMs = 15488
            )
        }
    }
}
