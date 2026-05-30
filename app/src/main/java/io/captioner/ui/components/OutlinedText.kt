package io.captioner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.captioner.ui.theme.CaptionerTheme

@Composable
fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    fillModifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    fillColor: Color = Color.White,
    outlineColor: Color = Color.Black,
    outlineWidth: Float = 6f,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    textDecoration: TextDecoration? = null,
    onFillTextLayout: ((TextLayoutResult) -> Unit)? = null,
    enabled: Boolean = true
) {
    Box(modifier) {
        if (enabled) {
            Text(
                text = text,
                style = TextStyle(
                    fontSize = fontSize,
                    color = outlineColor,
                    drawStyle = Stroke(
                        width = outlineWidth,
                        join = StrokeJoin.Round,
                        cap = StrokeCap.Round
                    ),
                    letterSpacing = letterSpacing,
                    textAlign = TextAlign.Center,
                    lineHeight = lineHeight,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    fontStyle = fontStyle,
                    fontWeight = fontWeight,
                    textDecoration = textDecoration
                )
            )
        }

        Text(
            text = text,
            modifier = fillModifier,
            style = TextStyle(
                fontSize = fontSize,
                color = fillColor,
                letterSpacing = letterSpacing,
                textAlign = TextAlign.Center,
                lineHeight = lineHeight,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                fontStyle = fontStyle,
                fontWeight = fontWeight,
                textDecoration = textDecoration,
            ),
            onTextLayout = onFillTextLayout
        )
    }
}


@Preview(showBackground = true)
@Composable
private fun Preview() {
    CaptionerTheme() {
        Column(
            modifier = Modifier.width(200.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedText(
                "what the hell is going on here",
                outlineWidth = 1f,
                fontSize = 24.sp,
                modifier = Modifier.background(Color.Yellow),
                fontWeight = FontWeight.Bold
            )
            OutlinedText(
                "what the hell",
                outlineWidth = 6f,
                fontSize = 24.sp,
                modifier = Modifier.background(Color.Red),
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Bold
            )
        }
    }
}