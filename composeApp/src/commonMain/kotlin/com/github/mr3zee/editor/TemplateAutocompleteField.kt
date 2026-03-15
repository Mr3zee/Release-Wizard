package com.github.mr3zee.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@Composable
fun TemplateAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    projectParameters: List<Parameter>,
    predecessors: List<Block>,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    testTag: String = "",
) {
    val defaultValueMarker = "\u0000"
    val defaultValueTemplate = packStringResource(Res.string.editor_template_default_value, defaultValueMarker)
    val allSuggestions = remember(projectParameters, predecessors, defaultValueTemplate) {
        buildSuggestions(projectParameters, predecessors) { value ->
            defaultValueTemplate.replace(defaultValueMarker, value)
        }
    }

    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Sync external value changes — guard prevents recomposition loop
    if (tfv.text != value) {
        val clampedSel = TextRange(
            tfv.selection.start.coerceAtMost(value.length),
            tfv.selection.end.coerceAtMost(value.length),
        )
        tfv = TextFieldValue(text = value, selection = clampedSel)
    }

    var showDropdown by remember { mutableStateOf(false) }
    var filteredSuggestions by remember { mutableStateOf(emptyList<TemplateSuggestion>()) }
    var selectedIndex by remember { mutableStateOf(-1) }
    var interpolationContext by remember { mutableStateOf<InterpolationContext?>(null) }

    // Compute horizontal offset to position dropdown near the ${ trigger
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var containerWidthDp by remember { mutableStateOf(0.dp) }
    val dropdownOffset by remember {
        derivedStateOf {
            val ctx = interpolationContext ?: return@derivedStateOf DpOffset.Zero
            val triggerPos = ctx.triggerOffset.coerceAtMost(tfv.text.length)
            val textBeforeTrigger = tfv.text.substring(0, triggerPos)
            val layoutResult = textMeasurer.measure(text = textBeforeTrigger, style = textStyle)
            val textWidthPx = if (textBeforeTrigger.isEmpty()) 0f
                else layoutResult.getCursorRect(textBeforeTrigger.length).left
            with(density) {
                val triggerX = OUTLINED_FIELD_START_PADDING + textWidthPx.toDp()
                // Clamp so dropdown doesn't overflow the field's right edge
                val maxOffset = (containerWidthDp - DROPDOWN_MIN_WIDTH).coerceAtLeast(0.dp)
                DpOffset(x = triggerX.coerceAtMost(maxOffset), y = 0.dp)
            }
        }
    }

    fun updateSuggestions(textFieldValue: TextFieldValue) {
        val ctx = parseInterpolationContext(textFieldValue.text, textFieldValue.selection.start)
        interpolationContext = ctx
        if (ctx != null) {
            val filtered = filterSuggestions(allSuggestions, ctx)
            filteredSuggestions = filtered
            showDropdown = filtered.isNotEmpty()
            selectedIndex = -1
        } else {
            showDropdown = false
            filteredSuggestions = emptyList()
            selectedIndex = -1
        }
    }

    fun acceptSuggestion(suggestion: TemplateSuggestion) {
        val ctx = interpolationContext ?: return
        val before = tfv.text.substring(0, ctx.triggerOffset)
        val after = tfv.text.substring(tfv.selection.start)
        val newText = before + suggestion.insertText + after
        val newCursor = before.length + suggestion.insertText.length
        tfv = TextFieldValue(newText, TextRange(newCursor))
        onValueChange(newText)
        showDropdown = false
        filteredSuggestions = emptyList()
        selectedIndex = -1
        interpolationContext = null
    }

    // Pre-compute category splits and index offsets outside LazyColumn DSL
    val paramSuggestions = remember(filteredSuggestions) {
        filteredSuggestions.filter { it.category == SuggestionCategory.PARAMETER }
    }
    val outputSuggestions = remember(filteredSuggestions) {
        filteredSuggestions.filter { it.category == SuggestionCategory.BLOCK_OUTPUT }
    }
    val outputStartIndex = paramSuggestions.size

    Box(
        modifier = modifier.onSizeChanged { size ->
            with(density) { containerWidthDp = size.width.toDp() }
        },
    ) {
        OutlinedTextField(
            value = tfv,
            onValueChange = { newTfv ->
                tfv = newTfv
                onValueChange(newTfv.text)
                updateSuggestions(newTfv)
            },
            label = label,
            singleLine = singleLine,
            enabled = enabled,
            textStyle = textStyle,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .onPreviewKeyEvent { event ->
                    if (!showDropdown || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when (event.key) {
                        Key.DirectionDown -> {
                            if (filteredSuggestions.isNotEmpty()) {
                                selectedIndex = if (selectedIndex >= filteredSuggestions.size - 1) 0 else selectedIndex + 1
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (filteredSuggestions.isNotEmpty()) {
                                selectedIndex = if (selectedIndex <= 0) filteredSuggestions.size - 1 else selectedIndex - 1
                            }
                            true
                        }
                        Key.Enter -> {
                            if (selectedIndex in filteredSuggestions.indices) {
                                acceptSuggestion(filteredSuggestions[selectedIndex])
                                true
                            } else {
                                false
                            }
                        }
                        Key.Tab -> {
                            if (selectedIndex in filteredSuggestions.indices) {
                                acceptSuggestion(filteredSuggestions[selectedIndex])
                                true
                            } else if (filteredSuggestions.size == 1) {
                                acceptSuggestion(filteredSuggestions[0])
                                true
                            } else {
                                false
                            }
                        }
                        Key.Escape -> {
                            showDropdown = false
                            true
                        }
                        else -> false
                    }
                },
        )

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            offset = dropdownOffset,
            modifier = Modifier.testTag("${testTag}_autocomplete_dropdown"),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 200.dp, max = 280.dp)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (paramSuggestions.isNotEmpty()) {
                    Text(
                        packStringResource(Res.string.editor_template_parameters),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    paramSuggestions.forEachIndexed { itemIdx, suggestion ->
                        SuggestionItem(
                            suggestion = suggestion,
                            isSelected = itemIdx == selectedIndex,
                            onClick = { acceptSuggestion(suggestion) },
                            testTag = "${testTag}_suggestion_$itemIdx",
                        )
                    }
                }

                if (outputSuggestions.isNotEmpty()) {
                    Text(
                        packStringResource(Res.string.editor_template_block_outputs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    outputSuggestions.forEachIndexed { localIdx, suggestion ->
                        val itemIdx = outputStartIndex + localIdx
                        SuggestionItem(
                            suggestion = suggestion,
                            isSelected = itemIdx == selectedIndex,
                            onClick = { acceptSuggestion(suggestion) },
                            testTag = "${testTag}_suggestion_$itemIdx",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: TemplateSuggestion,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        color = bgColor,
        contentColor = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(testTag),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                suggestion.label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                suggestion.insertText,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (suggestion.description != null) {
                Text(
                    suggestion.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor,
                )
            }
        }
    }
}

/** Material3 OutlinedTextField default horizontal content padding (start). */
private val OUTLINED_FIELD_START_PADDING = 16.dp

/** Minimum dropdown width used for offset clamping. */
private val DROPDOWN_MIN_WIDTH = 200.dp
