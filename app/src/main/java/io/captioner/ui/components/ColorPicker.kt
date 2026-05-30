package io.captioner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.AlphaTile
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import io.captioner.ui.theme.CaptionerTheme


@Composable
fun AdvancedColorPicker(
    modifier: Modifier = Modifier,
    color: Color,
    onColorChange: (Color) -> Unit
) {
    val controller = rememberColorPickerController()
    var r by remember { mutableIntStateOf((color.red * 255).toInt()) }
    var g by remember { mutableIntStateOf((color.green * 255).toInt()) }
    var b by remember { mutableIntStateOf((color.blue * 255).toInt()) }
    var a by remember { mutableIntStateOf((color.alpha * 255).toInt()) }

    fun updateRGBA(color: Color) {
        r = (color.red * 255).toInt()
        g = (color.green * 255).toInt()
        b = (color.blue * 255).toInt()
        a = (color.alpha * 255).toInt()
    }

    fun update() {
        val color = Color(r, g, b, a)
        controller.selectByColor(color, fromUser = true)
        onColorChange(color)
    }



    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Preview", style = MaterialTheme.typography.labelLarge)
            AlphaTile(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                controller = controller
            )
        }

        RGBSection(
            r = r,
            g = g,
            b = b,
            onRChange = { r = it; update() },
            onGChange = { g = it; update() },
            onBChange = { b = it; update() },
        )


        HsvColorPicker(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp)),
            controller = controller,
            initialColor = color,
            onColorChanged = {
                val color = it.color
                updateRGBA(color)
                onColorChange(color)
            }
        )

        Column {
            Text(
                "Alpha",
                style = MaterialTheme.typography.titleMedium,
            )

            AlphaSlider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                controller = controller,
                tileOddColor = Color.LightGray,
                tileEvenColor = Color.White,
                initialColor = color
            )
        }

        Column {
            Text(
                "Brightness",
                style = MaterialTheme.typography.titleMedium,
            )
            BrightnessSlider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                controller = controller,
                initialColor = color
            )
        }


        Column {
            Text(
                "Preset",
                style = MaterialTheme.typography.titleMedium,
            )
            ColorPresetGrid { color ->
                updateRGBA(color)
                controller.selectByColor(color, fromUser = true)
                onColorChange(color)
            }
        }
    }
}

@Composable
fun RGBSection(
    r: Int,
    g: Int,
    b: Int,
    onRChange: (Int) -> Unit,
    onGChange: (Int) -> Unit,
    onBChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ColorSlider("R", r, onRChange)
        ColorSlider("G", g, onGChange)
        ColorSlider("B", b, onBChange)
    }
}
@Composable
fun ColorSlider(
    label: String,
    value: Int,
    onChange: (Int) -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 12.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = value.toString(),
            onValueChange = {
                try {
                    onChange(it.toInt())
                } catch(_: Exception) {
                    onChange(0)
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(80.dp).padding(start = 12.dp),
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center
            )
        )
    }
}

val defaultColorPreset = listOf(
    Color.Black,
    Color.DarkGray,
    Color.Gray,
    Color.LightGray,
    Color.White,
    Color.Red,
    Color.Green,
    Color.Blue,
    Color.Yellow,
    Color.Cyan,
    Color.Magenta
)

@Composable
fun ColorPresetGrid(
    colorPreset: List<Color> = defaultColorPreset,
    onColorSelect: (Color) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 5,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        for (color in colorPreset) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(color)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onColorSelect(color)
                    }
            )
        }
    }
}



@Preview(showBackground = true)
@Composable
private fun Preview() {
    CaptionerTheme {
        AdvancedColorPicker(color = Color.Blue) { }
    }
}