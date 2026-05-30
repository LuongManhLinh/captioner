package io.captioner.ui.editor.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment as ComposeAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import io.captioner.R
import io.captioner.data.dto.CaptionKaraokeDto
import io.captioner.data.model.Caption
import io.captioner.data.model.CaptionAlignment
import io.captioner.data.model.CaptionStyle
import io.captioner.data.model.KaraokeWord
import io.captioner.ui.components.AdvancedColorPicker
import io.captioner.ui.theme.CaptionerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionStyleSheet(
    title: String,
    editText: Boolean = true,
    text: String = "",
    karaoke: Boolean,
    onTextChange: (String) -> Unit = {},
    editTimeRange: Boolean = true,
    startTimeMs: Long = 0L,
    endTimeMs: Long = 0L,
    onStartTimeChange: (Long) -> Unit = {},
    onEndTimeChange: (Long) -> Unit = {},
    editStyle: Boolean = true,
    captionStyle: CaptionStyle? = null,
    onCaptionStyleChange: (CaptionStyle) -> Unit = { },
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    viewScale: Float = 1f
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val allowEditingStyle = editStyle && captionStyle != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            if (allowEditingStyle) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .fillMaxWidth(),
                    contentAlignment = ComposeAlignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.preview_background),
                        contentDescription = "Background",
                        modifier = Modifier
                            .height(200.dp)
                            .fillMaxWidth(),
                        contentScale = ContentScale.Crop
                    )

                    val exampleCaption = Caption(
                        text = if (editText) text else "Preview",
                        style = captionStyle,
                        projectId = "",
                        startTimeMs = 0,
                        endTimeMs = 1000
                    )

                    val karaokeWords = if (karaoke) {
                        listOf(KaraokeWord(
                            captionId = exampleCaption.id,
                            text = if (editText) text else "Preview",
                            startTimeMs = 0,
                            endTimeMs = 1000
                        ))
                    } else {
                        emptyList()
                    }

                    CaptionText(
                        dto = CaptionKaraokeDto(
                            caption = exampleCaption,
                            karaokeWords = karaokeWords
                        ),
                        modifier = getAlignmentModifier(caption = exampleCaption, viewScale = viewScale),
                        currentTimeMs = 500,
                        onDrag = { _, _, _ -> },
                        onDoubleTap = {},
                        onTap = {},
                        viewScale = viewScale
                    )
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(state = rememberScrollState())
                    .weight(1f)
            ) {
                TextTimeRangeEditor(
                    editText = editText,
                    text = text,
                    onTextChange = onTextChange,
                    editTimeRange = editTimeRange,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    onStartTimeChange = onStartTimeChange,
                    onEndTimeChange = onEndTimeChange
                )

                if (allowEditingStyle) {
                    StyleEditor(
                        captionStyle = captionStyle,
                        onCaptionStyleChange = onCaptionStyleChange,
                        karaoke = karaoke
                    )
                }
            }


            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun TextTimeRangeEditor(
    modifier: Modifier = Modifier,
    editText: Boolean = true,
    text: String = "",
    onTextChange: (String) -> Unit = {},
    editTimeRange: Boolean = true,
    startTimeMs: Long = 0L,
    endTimeMs: Long = 0L,
    onStartTimeChange: (Long) -> Unit = {},
    onEndTimeChange: (Long) -> Unit = {},
) {
    Column(
        modifier = modifier
    ) {
        if (editText) {

            Text("Edit text", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter caption...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

        }

        if (editTimeRange) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NumberStepper(
                    label = "Start time (ms)",
                    value = startTimeMs,
                    onValueChange = onStartTimeChange,
                    modifier = Modifier.weight(1f)
                )
                NumberStepper(
                    label = "End time (ms)",
                    value = endTimeMs,
                    onValueChange = onEndTimeChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StyleEditor(
    captionStyle: CaptionStyle,
    onCaptionStyleChange: (CaptionStyle) -> Unit = {},
    karaoke: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextStyler(
            label = "Text Style",
            bold = captionStyle.bold,
            italic = captionStyle.italic,
            underline = captionStyle.underline,
            onBoldToggle = {
                onCaptionStyleChange(
                    captionStyle.copy(bold = !captionStyle.bold)
                )
            },
            onItalicToggle = {
                onCaptionStyleChange(
                    captionStyle.copy(italic = !captionStyle.italic)
                )
            },
            onUnderlineToggle = {
                onCaptionStyleChange(
                    captionStyle.copy(underline = !captionStyle.underline)
                )
            },
        )


        // Font + Padding
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NumberStepper(
                label = "Font Size",
                value = captionStyle.fontSize,
                onValueChange = { onCaptionStyleChange(captionStyle.copy(fontSize = it)) },
                modifier = Modifier.weight(1f)
            )
            NumberStepper(
                label = "Outer Padding",
                value = captionStyle.outerPadding,
                onValueChange = { onCaptionStyleChange(captionStyle.copy(outerPadding = it)) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NumberStepper(
                label = "Inner Padding",
                value = captionStyle.innerPadding,
                onValueChange = { onCaptionStyleChange(captionStyle.copy(innerPadding = it)) },
                modifier = Modifier.weight(1f)
            )
            NumberStepper(
                label = "Corner Radius",
                value = captionStyle.cornerRadius,
                onValueChange = { onCaptionStyleChange(captionStyle.copy(cornerRadius = it)) },
                modifier = Modifier.weight(1f)
            )
        }

        Column {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = ComposeAlignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Outline", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = captionStyle.outlineEnabled,
                        onCheckedChange = {
                            onCaptionStyleChange(
                                captionStyle.copy(outlineEnabled = it)
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = ComposeAlignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Letter Spacing", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = captionStyle.letterSpacingEnabled,
                        onCheckedChange = {
                            onCaptionStyleChange(
                                captionStyle.copy(letterSpacingEnabled = it)
                            )
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (captionStyle.outlineEnabled) {
                    NumberStepper(
                        label = "Outline Width",
                        value = captionStyle.outlineWidth,
                        onValueChange = {
                            onCaptionStyleChange(
                                captionStyle.copy(outlineWidth = it)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (captionStyle.letterSpacingEnabled) {
                    NumberStepper(
                        label = "Letter Spacing",
                        value = captionStyle.letterSpacing,
                        onValueChange = {
                            onCaptionStyleChange(
                                captionStyle.copy(letterSpacing = it)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // Color Pickers
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorPickerLauncher(
                title = "Text Color",
                iniColor = captionStyle.textColor,
                onColorChange = { onCaptionStyleChange(captionStyle.copy(textColor = it)) }
            )
            ColorPickerLauncher(
                title = "Background Color",
                iniColor = captionStyle.backgroundColor,
                onColorChange = { onCaptionStyleChange(captionStyle.copy(backgroundColor = it)) }
            )
            if (karaoke) {
                ColorPickerLauncher(
                    title = "Karaoke Color",
                    iniColor = captionStyle.karaokeColor,
                    onColorChange = {
                        onCaptionStyleChange(
                            captionStyle.copy(
                                karaokeColor = it
                            )
                        )
                    }
                )
            }
            if (captionStyle.outlineEnabled) {
                ColorPickerLauncher(
                    title = "Outline Color",
                    iniColor = captionStyle.outlineColor,
                    onColorChange = {
                        onCaptionStyleChange(
                            captionStyle.copy(outlineColor = it)
                        )
                    },
                )
            }
        }

        // Alignment
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Position", style = MaterialTheme.typography.titleMedium)

            AlignmentVisualizer(
                currentAlignment = captionStyle.alignment,
                onAlignmentSelected = { onCaptionStyleChange(captionStyle.copy(alignment = it)) }
            )

            Row(verticalAlignment = ComposeAlignment.CenterVertically) {
                RadioButton(
                    selected = captionStyle.alignment == CaptionAlignment.CUSTOM,
                    onClick = {
                        onCaptionStyleChange(
                            captionStyle.copy(alignment = CaptionAlignment.CUSTOM)
                        )
                    }
                )
                Text("Custom Position")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerLauncher(
    modifier: Modifier = Modifier,
    title: String,
    iniColor: Color,
    onColorChange: (Color) -> Unit
) {
    var dialogOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { dialogOpen = true }
                    .background(iniColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(8.dp))
            )
        }
    }

    if (dialogOpen) {
        ColorPickerDialog(
            title = title,
            iniColor = iniColor,
            onDismiss = { dialogOpen = false },
            onApply = {
                onColorChange(it)
                dialogOpen = false
            }
        )
    }
}

@Composable
fun ColorPickerDialog(
    title: String,
    iniColor: Color,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit
) {
    var tempColor by remember { mutableStateOf(iniColor) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )

                AdvancedColorPicker(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(state = rememberScrollState()),
                    color = iniColor,
                    onColorChange = { tempColor = it }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onApply(tempColor)
                        }
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}


@Composable
fun TextStyler(
    modifier: Modifier = Modifier,
    label: String,
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    onBoldToggle: () -> Unit,
    onItalicToggle: () -> Unit,
    onUnderlineToggle: () -> Unit,
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBoldToggle,
                modifier = Modifier.background(
                    color = if (bold) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(4.dp)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.FormatBold,
                    contentDescription = "Bold"
                )
            }
            IconButton(
                onClick = onItalicToggle,
                modifier = Modifier.background(
                    color = if (italic) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(4.dp)
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.FormatItalic,
                    contentDescription = "Italic"
                )
            }
            IconButton(
                onClick = onUnderlineToggle,
                modifier = Modifier.background(
                    color = if (underline) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(4.dp)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.FormatUnderlined,
                    contentDescription = "Underline"
                )
            }
        }
    }
}

@Composable
fun NumberStepper(
    label: String,
    value: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { newValue ->
                newValue.toLongOrNull()?.let { onValueChange(it) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = {
                IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(0)) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrement")
                }
            },
            trailingIcon = {
                IconButton(onClick = { onValueChange(value + 1) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Increment")
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
fun NumberStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { onValueChange(it) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = {
                IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(0)) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrement")
                }
            },
            trailingIcon = {
                IconButton(onClick = { onValueChange(value + 1) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Increment")
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
fun NumberStepper(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { newValue ->
                newValue.toFloatOrNull()?.let { onValueChange(it) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = {
                IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(0f)) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrement")
                }
            },
            trailingIcon = {
                IconButton(onClick = { onValueChange(value + 1) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Increment")
                }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center
            )
        )
    }
}

val alignments = listOf(
    listOf(CaptionAlignment.TOP_LEFT, CaptionAlignment.TOP_CENTER, CaptionAlignment.TOP_RIGHT),
    listOf(CaptionAlignment.LEFT, CaptionAlignment.CENTER, CaptionAlignment.RIGHT),
    listOf(CaptionAlignment.BOTTOM_LEFT, CaptionAlignment.BOTTOM_CENTER, CaptionAlignment.BOTTOM_RIGHT)
)
@Composable
private fun AlignmentVisualizer(
    currentAlignment: CaptionAlignment,
    onAlignmentSelected: (CaptionAlignment) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                MaterialTheme.colorScheme.surfaceVariant
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            alignments.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { alignment ->
                        AlignmentNode(
                            isSelected = alignment == currentAlignment,
                            onClick = { onAlignmentSelected(alignment) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlignmentNode(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val iconColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = ComposeAlignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


//@Preview(showBackground = true)
//@Composable
//private fun TextPreview() {
//    CaptionerTheme {
//        TextTimeRangeEditor {  }
//    }
//}

@Preview(showBackground = true)
@Composable
private fun StylePreview() {
    CaptionerTheme {
        StyleEditor(
            captionStyle = CaptionStyle(
                outlineEnabled = true,
                letterSpacingEnabled = true
            )
        )
    }
}