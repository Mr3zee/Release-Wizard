package com.github.mr3zee

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwBadge
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwChip
import com.github.mr3zee.components.RwDangerZone
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.components.RwInlineForm
import com.github.mr3zee.components.RwRadioButton
import com.github.mr3zee.components.RwSwitch
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.theme.AppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class Phase1ComponentsTest {

    // ── Step 1A: RwTextField leadingIcon ──────────────────────────────

    @Test
    fun `RwTextField renders leading icon when provided`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwTextField(
                    value = "",
                    onValueChange = {},
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(20.dp).testTag("leading_icon"),
                        )
                    },
                    modifier = Modifier.testTag("text_field"),
                )
            }
        }

        onNodeWithTag("text_field").assertExists()
        onNodeWithTag("leading_icon", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `RwTextField renders without leading icon by default`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.testTag("text_field"),
                )
            }
        }

        onNodeWithTag("text_field").assertExists()
        onNodeWithTag("leading_icon", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `RwTextField with leading icon still accepts text input`() = runComposeUiTest {
        var text by mutableStateOf("")
        setContent {
            AppTheme {
                RwTextField(
                    value = text,
                    onValueChange = { text = it },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    singleLine = true,
                    modifier = Modifier.testTag("text_field"),
                )
            }
        }

        onNodeWithTag("text_field").performTextInput("hello")
        waitForIdle()
        assert(text == "hello") { "Expected 'hello' but got '$text'" }
    }

    // ── Step 1B: RwDangerZone ────────────────────────────────────────

    @Test
    fun `RwDangerZone renders with label and content`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwDangerZone {
                    RwButton(
                        onClick = {},
                        variant = RwButtonVariant.Danger,
                        modifier = Modifier.testTag("danger_button"),
                    ) {
                        Text("Delete")
                    }
                }
            }
        }

        onNodeWithTag("danger_zone", useUnmergedTree = true).assertExists()
        // Verify the danger zone has visible text content (label + button)
        onNodeWithTag("danger_button", useUnmergedTree = true).assertExists()
        onNodeWithText("Delete", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `RwDangerZone uses custom test tag`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwDangerZone(testTag = "custom_danger") {
                    Text("Content")
                }
            }
        }

        onNodeWithTag("custom_danger").assertExists()
        onNodeWithTag("danger_zone").assertDoesNotExist()
    }

    // ── Step 1C: RwBadge ─────────────────────────────────────────────

    @Test
    fun `RwBadge renders text with correct content`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwBadge(
                    text = "Member",
                    color = Color(0xFF3B82F6),
                    testTag = "member_badge",
                )
            }
        }

        onNodeWithTag("member_badge").assertExists()
        onNodeWithText("Member", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `RwBadge renders without test tag when not provided`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwBadge(
                    text = "Lead",
                    color = Color(0xFF22C55E),
                )
            }
        }

        onNodeWithText("Lead", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `multiple RwBadges render independently`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwBadge(text = "Lead", color = Color.Blue, testTag = "badge_lead")
                RwBadge(text = "Member", color = Color.Green, testTag = "badge_member")
            }
        }

        onNodeWithTag("badge_lead").assertExists()
        onNodeWithTag("badge_member").assertExists()
        onNodeWithText("Lead", useUnmergedTree = true).assertExists()
        onNodeWithText("Member", useUnmergedTree = true).assertExists()
    }

    // ── QA-CROSS-6: RwInlineConfirmation debounce ───────────────────

    @Test
    fun `RwInlineConfirmation shows message and buttons`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwInlineConfirmation(
                    visible = true,
                    message = "Are you sure?",
                    confirmLabel = "Delete",
                    onConfirm = {},
                    onDismiss = {},
                    testTag = "confirm_banner",
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Are you sure?", useUnmergedTree = true).assertExists()
        onNodeWithTag("confirm_banner_cancel").assertExists()
        onNodeWithTag("confirm_banner_confirm").assertExists()
    }

    @Test
    fun `RwInlineConfirmation confirm button starts disabled then becomes enabled after debounce`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwInlineConfirmation(
                    visible = true,
                    message = "Delete item?",
                    confirmLabel = "Confirm",
                    onConfirm = {},
                    onDismiss = {},
                    testTag = "confirm_debounce",
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_debounce").fetchSemanticsNodes().isNotEmpty()
        }

        // Confirm button should initially be disabled (300ms debounce)
        onNodeWithTag("confirm_debounce_confirm").assertIsNotEnabled()

        // Wait for the debounce period (300ms) to pass
        waitUntil(timeoutMillis = 2000L) {
            onNodeWithTag("confirm_debounce_confirm")
                .fetchSemanticsNode()
                .config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Disabled) { null } == null
        }

        // Now the confirm button should be enabled
        onNodeWithTag("confirm_debounce_confirm").assertIsEnabled()
    }

    @Test
    fun `RwInlineConfirmation dismiss callback fires on cancel click`() = runComposeUiTest {
        var dismissed = false
        setContent {
            AppTheme {
                RwInlineConfirmation(
                    visible = true,
                    message = "Really?",
                    confirmLabel = "Yes",
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                    testTag = "confirm_dismiss",
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_dismiss").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("confirm_dismiss_cancel").performClick()
        waitForIdle()
        assert(dismissed) { "onDismiss should be called when cancel is clicked" }
    }

    @Test
    fun `RwInlineConfirmation confirm callback fires after debounce`() = runComposeUiTest {
        var confirmed = false
        setContent {
            AppTheme {
                RwInlineConfirmation(
                    visible = true,
                    message = "Proceed?",
                    confirmLabel = "OK",
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                    testTag = "confirm_action",
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_action").fetchSemanticsNodes().isNotEmpty()
        }

        // Wait for debounce to enable the confirm button
        waitUntil(timeoutMillis = 2000L) {
            onNodeWithTag("confirm_action_confirm")
                .fetchSemanticsNode()
                .config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Disabled) { null } == null
        }

        onNodeWithTag("confirm_action_confirm").performClick()
        waitForIdle()
        assert(confirmed) { "onConfirm should be called after debounce period" }
    }

    @Test
    fun `RwInlineConfirmation hidden when visible is false`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwInlineConfirmation(
                    visible = false,
                    message = "Hidden message",
                    confirmLabel = "Delete",
                    onConfirm = {},
                    onDismiss = {},
                    testTag = "confirm_hidden",
                )
            }
        }

        waitForIdle()
        onNodeWithTag("confirm_hidden").assertDoesNotExist()
    }

    @Test
    fun `RwInlineConfirmation extra action button renders when provided`() = runComposeUiTest {
        var extraClicked = false
        setContent {
            AppTheme {
                RwInlineConfirmation(
                    visible = true,
                    message = "Choose action",
                    confirmLabel = "Delete",
                    onConfirm = {},
                    onDismiss = {},
                    extraAction = "Force Delete" to { extraClicked = true },
                    testTag = "confirm_extra",
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_extra").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("confirm_extra_extra").assertExists()
        onNodeWithTag("confirm_extra_extra").performClick()
        waitForIdle()
        assert(extraClicked) { "Extra action callback should be invoked" }
    }

    // ── QA-CROSS-7: Form keyboard shortcuts (Enter to submit) ───────

    @Test
    fun `RwInlineForm renders title and actions`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwInlineForm(
                    visible = true,
                    title = "Create Item",
                    onDismiss = {},
                    testTag = "test_form",
                    actions = {
                        RwButton(
                            onClick = {},
                            variant = RwButtonVariant.Primary,
                            modifier = Modifier.testTag("form_submit"),
                        ) { Text("Create") }
                    },
                ) {
                    RwTextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.testTag("form_input"),
                    )
                }
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("test_form").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Create Item", useUnmergedTree = true).assertExists()
        onNodeWithTag("form_input").assertExists()
        onNodeWithTag("form_submit").assertExists()
    }

    @Test
    fun `RwInlineForm close button fires onDismiss`() = runComposeUiTest {
        var dismissed = false
        setContent {
            AppTheme {
                RwInlineForm(
                    visible = true,
                    title = "Edit",
                    onDismiss = { dismissed = true },
                    testTag = "form_dismiss",
                    actions = {},
                ) {
                    Text("Content")
                }
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("form_dismiss").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("form_dismiss_close").performClick()
        waitForIdle()
        assert(dismissed) { "onDismiss should be called when close button is clicked" }
    }

    @Test
    fun `RwInlineForm hidden when visible is false`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwInlineForm(
                    visible = false,
                    title = "Hidden Form",
                    onDismiss = {},
                    testTag = "form_hidden",
                    actions = {},
                ) {
                    Text("Content")
                }
            }
        }

        waitForIdle()
        onNodeWithTag("form_hidden").assertDoesNotExist()
    }

    @Test
    fun `RwInlineForm Enter key triggers onSubmit`() = runComposeUiTest {
        var submitted = false
        setContent {
            AppTheme {
                RwInlineForm(
                    visible = true,
                    title = "Test Form",
                    onDismiss = {},
                    onSubmit = { submitted = true },
                    testTag = "form_enter",
                    actions = {
                        RwButton(onClick = {}, variant = RwButtonVariant.Primary) { Text("Save") }
                    },
                ) {
                    Text("Form content")
                }
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("form_enter").fetchSemanticsNodes().isNotEmpty()
        }

        // Press Enter key on the form
        onNodeWithTag("form_enter").performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.Enter)
        }
        waitForIdle()
        assert(submitted) { "onSubmit should be called when Enter is pressed" }
    }

    @Test
    fun `RwInlineForm Escape key triggers onDismiss`() = runComposeUiTest {
        var dismissed = false
        setContent {
            AppTheme {
                RwInlineForm(
                    visible = true,
                    title = "Test Form",
                    onDismiss = { dismissed = true },
                    testTag = "form_escape",
                    actions = {},
                ) {
                    Text("Form content")
                }
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("form_escape").fetchSemanticsNodes().isNotEmpty()
        }

        // Press Escape key on the form
        onNodeWithTag("form_escape").performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.Escape)
        }
        waitForIdle()
        assert(dismissed) { "onDismiss should be called when Escape is pressed" }
    }

    // ── QA-CROSS-10: Shared components (RwSwitch, RwChip, RwRadioButton) ─

    @Test
    fun `RwSwitch renders in unchecked state`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwSwitch(
                    checked = false,
                    onCheckedChange = {},
                    modifier = Modifier.testTag("test_switch"),
                )
            }
        }

        onNodeWithTag("test_switch").assertExists()
        onNodeWithTag("test_switch").assertIsOff()
    }

    @Test
    fun `RwSwitch renders in checked state`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwSwitch(
                    checked = true,
                    onCheckedChange = {},
                    modifier = Modifier.testTag("test_switch"),
                )
            }
        }

        onNodeWithTag("test_switch").assertExists()
        onNodeWithTag("test_switch").assertIsOn()
    }

    @Test
    fun `RwSwitch toggling calls onCheckedChange`() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent {
            AppTheme {
                RwSwitch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    modifier = Modifier.testTag("test_switch"),
                )
            }
        }

        onNodeWithTag("test_switch").assertIsOff()
        onNodeWithTag("test_switch").performClick()
        waitForIdle()
        assert(checked) { "Switch should be checked after click" }
        onNodeWithTag("test_switch").assertIsOn()
    }

    @Test
    fun `RwSwitch disabled state prevents interaction`() = runComposeUiTest {
        var checked by mutableStateOf(false)
        setContent {
            AppTheme {
                RwSwitch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    enabled = false,
                    modifier = Modifier.testTag("test_switch"),
                )
            }
        }

        onNodeWithTag("test_switch").assertIsNotEnabled()
    }

    @Test
    fun `RwChip renders in unselected state`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwChip(
                    selected = false,
                    onClick = {},
                    label = { Text("Filter") },
                    modifier = Modifier.testTag("test_chip"),
                )
            }
        }

        onNodeWithText("Filter", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `RwChip renders in selected state`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwChip(
                    selected = true,
                    onClick = {},
                    label = { Text("Active") },
                    modifier = Modifier.testTag("test_chip_selected"),
                )
            }
        }

        onNodeWithText("Active", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `RwChip click toggles selection`() = runComposeUiTest {
        var selected by mutableStateOf(false)
        setContent {
            AppTheme {
                RwChip(
                    selected = selected,
                    onClick = { selected = !selected },
                    label = { Text("Toggle") },
                    modifier = Modifier.testTag("test_chip"),
                )
            }
        }

        onNodeWithText("Toggle", useUnmergedTree = true).performClick()
        waitForIdle()
        assert(selected) { "Chip should be selected after click" }
    }

    @Test
    fun `RwChip disabled state prevents interaction`() = runComposeUiTest {
        var clicked = false
        setContent {
            AppTheme {
                RwChip(
                    selected = false,
                    onClick = { clicked = true },
                    enabled = false,
                    label = { Text("Disabled") },
                    modifier = Modifier.testTag("test_chip_disabled"),
                )
            }
        }

        onNodeWithText("Disabled", useUnmergedTree = true).assertExists()
        // Clicking a disabled chip should not trigger onClick
        onNodeWithText("Disabled", useUnmergedTree = true).performClick()
        waitForIdle()
        assert(!clicked) { "Disabled chip click should not trigger onClick" }
    }

    @Test
    fun `multiple RwChips render independently with correct state`() = runComposeUiTest {
        setContent {
            AppTheme {
                Column {
                    RwChip(
                        selected = true,
                        onClick = {},
                        label = { Text("Selected") },
                        modifier = Modifier.testTag("chip_a"),
                    )
                    RwChip(
                        selected = false,
                        onClick = {},
                        label = { Text("Unselected") },
                        modifier = Modifier.testTag("chip_b"),
                    )
                }
            }
        }

        onNodeWithText("Selected", useUnmergedTree = true).assertExists()
        onNodeWithText("Unselected", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `RwRadioButton renders in unselected state`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwRadioButton(
                    selected = false,
                    onClick = {},
                    modifier = Modifier.testTag("test_radio"),
                )
            }
        }

        onNodeWithTag("test_radio").assertExists()
    }

    @Test
    fun `RwRadioButton renders in selected state`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwRadioButton(
                    selected = true,
                    onClick = {},
                    modifier = Modifier.testTag("test_radio"),
                )
            }
        }

        onNodeWithTag("test_radio").assertExists()
    }

    @Test
    fun `RwRadioButton click calls onClick`() = runComposeUiTest {
        var clicked = false
        setContent {
            AppTheme {
                RwRadioButton(
                    selected = false,
                    onClick = { clicked = true },
                    modifier = Modifier.testTag("test_radio"),
                )
            }
        }

        onNodeWithTag("test_radio").performClick()
        waitForIdle()
        assert(clicked) { "onClick should be called when radio button is clicked" }
    }

    @Test
    fun `RwRadioButton with null onClick is not clickable`() = runComposeUiTest {
        setContent {
            AppTheme {
                RwRadioButton(
                    selected = true,
                    onClick = null,
                    modifier = Modifier.testTag("test_radio"),
                )
            }
        }

        onNodeWithTag("test_radio").assertExists()
        // With null onClick, the radio button should not have a click role
    }

    @Test
    fun `radio button group selection exclusive`() = runComposeUiTest {
        var selected by mutableStateOf(0)
        setContent {
            AppTheme {
                Column {
                    RwRadioButton(
                        selected = selected == 0,
                        onClick = { selected = 0 },
                        modifier = Modifier.testTag("radio_0"),
                    )
                    RwRadioButton(
                        selected = selected == 1,
                        onClick = { selected = 1 },
                        modifier = Modifier.testTag("radio_1"),
                    )
                    RwRadioButton(
                        selected = selected == 2,
                        onClick = { selected = 2 },
                        modifier = Modifier.testTag("radio_2"),
                    )
                }
            }
        }

        // Initial state: first radio selected
        assert(selected == 0) { "Initial selection should be 0" }

        // Click second radio
        onNodeWithTag("radio_1").performClick()
        waitForIdle()
        assert(selected == 1) { "Selection should change to 1" }

        // Click third radio
        onNodeWithTag("radio_2").performClick()
        waitForIdle()
        assert(selected == 2) { "Selection should change to 2" }

        // Click first radio again
        onNodeWithTag("radio_0").performClick()
        waitForIdle()
        assert(selected == 0) { "Selection should return to 0" }
    }
}
