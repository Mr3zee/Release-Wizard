package com.github.mr3zee.automation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.CreateScheduleRequest
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

private fun isValidCron(expression: String): Boolean {
    val parts = expression.trim().split("\\s+".toRegex())
    if (parts.size != 5) return false

    fun validField(field: String, min: Int, max: Int): Boolean {
        return field.split(",").all { item ->
            if (item.isEmpty()) return@all false
            val slashParts = item.split("/")
            if (slashParts.size > 2) return@all false
            val step = slashParts.getOrNull(1)
            if (step != null && ((step.toIntOrNull() ?: return@all false) < 1)) return@all false
            val base = slashParts[0]
            when {
                base == "*" -> true
                "-" in base -> {
                    val rangeParts = base.split("-")
                    if (rangeParts.size != 2) return@all false
                    val from = rangeParts[0].toIntOrNull() ?: return@all false
                    val to = rangeParts[1].toIntOrNull() ?: return@all false
                    from in min..max && to in min..max && from <= to
                }
                else -> {
                    val num = base.toIntOrNull() ?: return@all false
                    num in min..max
                }
            }
        }
    }

    return validField(parts[0], 0, 59) &&  // minute
        validField(parts[1], 0, 23) &&     // hour
        validField(parts[2], 1, 31) &&     // day of month
        validField(parts[3], 1, 12) &&     // month
        validField(parts[4], 0, 6)         // day of week
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScheduleDialog(
    isSaving: Boolean,
    onConfirm: (CreateScheduleRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var cronExpression by remember { mutableStateOf("") }
    var presetsExpanded by remember { mutableStateOf(false) }
    var selectedPresetLabel by remember { mutableStateOf("") }

    val presets = listOf(
        Triple(packStringResource(Res.string.schedule_preset_daily), "0 9 * * *", Res.string.schedule_preset_daily),
        Triple(packStringResource(Res.string.schedule_preset_weekdays), "0 9 * * 1-5", Res.string.schedule_preset_weekdays),
        Triple(packStringResource(Res.string.schedule_preset_monday), "0 12 * * 1", Res.string.schedule_preset_monday),
    )

    val isCronValid = remember(cronExpression) { isValidCron(cronExpression) }
    val showValidation = cronExpression.isNotBlank()

    // Human-readable hint for selected preset, shown below the cron field
    val nextRunHint: String? = when (cronExpression.trim()) {
        "0 9 * * *"   -> "Every day at 9:00 AM"
        "0 9 * * 1-5" -> "Every weekday (Mon–Fri) at 9:00 AM"
        "0 12 * * 1"  -> "Every Monday at 12:00 PM"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(packStringResource(Res.string.schedule_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Preset selector — shows selected preset name after selection
                ExposedDropdownMenuBox(
                    expanded = presetsExpanded,
                    onExpandedChange = { presetsExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedPresetLabel.ifBlank { packStringResource(Res.string.schedule_preset_label) },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(packStringResource(Res.string.schedule_preset_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetsExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = presetsExpanded,
                        onDismissRequest = { presetsExpanded = false },
                    ) {
                        presets.forEach { (label, cron, _) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    cronExpression = cron
                                    selectedPresetLabel = label
                                    presetsExpanded = false
                                },
                            )
                        }
                    }
                }

                // Cron expression field
                OutlinedTextField(
                    value = cronExpression,
                    onValueChange = { cronExpression = it; selectedPresetLabel = "" },
                    label = { Text(packStringResource(Res.string.schedule_cron_label)) },
                    placeholder = { Text(packStringResource(Res.string.schedule_cron_hint)) },
                    supportingText = {
                        when {
                            nextRunHint != null -> Text(
                                "${packStringResource(Res.string.schedule_next_run_label)}: $nextRunHint",
                                color = MaterialTheme.colorScheme.primary,
                            )
                            showValidation && isCronValid -> Text(
                                packStringResource(Res.string.schedule_cron_valid),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            showValidation && !isCronValid -> Text(
                                packStringResource(Res.string.schedule_cron_invalid),
                                color = MaterialTheme.colorScheme.error,
                            )
                            else -> {}
                        }
                    },
                    isError = showValidation && !isCronValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("schedule_cron_input"),
                )
            }
        },
        confirmButton = {
            RwButton(
                onClick = {
                    onConfirm(CreateScheduleRequest(cronExpression = cronExpression.trim()))
                },
                variant = RwButtonVariant.Primary,
                enabled = isCronValid && !isSaving,
                modifier = Modifier.testTag("schedule_create_button"),
            ) {
                Text(packStringResource(Res.string.common_create))
            }
        },
        dismissButton = {
            RwButton(onClick = onDismiss, variant = RwButtonVariant.Ghost) {
                Text(packStringResource(Res.string.common_cancel))
            }
        },
    )
}
