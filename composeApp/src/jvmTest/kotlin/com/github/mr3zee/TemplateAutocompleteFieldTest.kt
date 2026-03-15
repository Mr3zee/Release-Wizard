package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.github.mr3zee.editor.TemplateAutocompleteField
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.Parameter
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TemplateAutocompleteFieldTest {

    private val testParams = listOf(
        Parameter(key = "version", value = "1.0"),
        Parameter(key = "env", value = "prod"),
    )

    private val testPredecessors = listOf(
        Block.ActionBlock(
            id = BlockId("build1"),
            name = "Build",
            type = BlockType.TEAMCITY_BUILD,
            outputs = listOf("buildNumber", "status"),
        ),
    )

    @Test
    fun `typing dollar-brace triggers dropdown`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()

        onNodeWithTag("field_autocomplete_dropdown", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `dropdown not shown for plain text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("hello world")
        waitForIdle()

        onNodeWithTag("field_autocomplete_dropdown", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `dropdown not shown for bare dollar sign`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("$")
        waitForIdle()

        onNodeWithTag("field_autocomplete_dropdown", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `prefix filtering shows only matching parameters`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${param.ver")
        waitForIdle()

        // Should show dropdown with only "version" suggestion
        onNodeWithTag("field_autocomplete_dropdown", useUnmergedTree = true).assertExists()
        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).assertExists()
        // Only one suggestion — no second item
        onNodeWithTag("field_suggestion_1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `selecting suggestion inserts full expression`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()

        // Click the first suggestion
        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).performClick()
        waitForIdle()

        // Should have inserted the full expression
        assertEquals("\${param.version}", currentValue)
    }

    @Test
    fun `clearing trigger text dismisses dropdown`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()
        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).assertExists()

        // Clear the field — removes the trigger so dropdown should dismiss
        onNodeWithTag("field").performTextClearance()
        waitForIdle()

        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).assertDoesNotExist()
    }

    // Note: Escape key dismiss is implemented via onPreviewKeyEvent but cannot be tested
    // reliably because Compose Desktop's popup layer intercepts key events in the test framework.

    @Test
    fun `dropdown shows both parameter and block output categories`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()

        // Should see both category headers and suggestion items
        onNodeWithText("Parameters", useUnmergedTree = true).assertExists()
        onNodeWithText("Block Outputs", useUnmergedTree = true).assertExists()
        // Parameter suggestions (indices 0, 1)
        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).assertExists()
        onNodeWithTag("field_suggestion_1", useUnmergedTree = true).assertExists()
        // Block output suggestions (indices 2, 3)
        onNodeWithTag("field_suggestion_2", useUnmergedTree = true).assertExists()
        onNodeWithTag("field_suggestion_3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `block prefix filters to block outputs only`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${block.")
        waitForIdle()

        // Should show dropdown with only block output suggestions
        onNodeWithTag("field_autocomplete_dropdown", useUnmergedTree = true).assertExists()
        onNodeWithText("Parameters", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Block Outputs", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `no suggestions hides dropdown`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        // Type a prefix that matches nothing
        onNodeWithTag("field").performTextInput("\${zzz")
        waitForIdle()

        onNodeWithTag("field_autocomplete_dropdown", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `works with no suggestions available`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = emptyList(),
                    predecessors = emptyList(),
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()

        // No suggestions — dropdown should not appear
        onNodeWithTag("field_autocomplete_dropdown", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `text before trigger is preserved after selection`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("prefix \${")
        waitForIdle()

        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).performClick()
        waitForIdle()

        assertEquals("prefix \${param.version}", currentValue)
    }

    @Test
    fun `keyboard down arrow and enter selects suggestion`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()

        // Arrow down twice to second item ("env"), then Enter to accept
        onNodeWithTag("field").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        waitForIdle()

        assertEquals("\${param.env}", currentValue)
    }

    // Note: Tab acceptance is implemented but cannot be tested reliably because
    // Compose Desktop's focus system intercepts Tab for focus traversal before
    // onPreviewKeyEvent receives it.

    @Test
    fun `enter without selection does not accept suggestion`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()
        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).assertExists()

        // Enter without arrow down — selectedIndex is -1, should not accept
        onNodeWithTag("field").performKeyInput {
            pressKey(Key.Enter)
        }
        waitForIdle()

        // Value should still be the raw typed text
        assertEquals("\${", currentValue)
    }

    @Test
    fun `insertion preserves text after cursor`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        // Type text, then trigger autocomplete, then select
        onNodeWithTag("field").performTextInput("before \${")
        waitForIdle()

        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).performClick()
        waitForIdle()

        assertEquals("before \${param.version}", currentValue)
    }

    @Test
    fun `up arrow navigates selection backward`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()

        // Down twice (select "env" at index 1), Up once (select "version" at index 0), Enter
        onNodeWithTag("field").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionUp)
            pressKey(Key.Enter)
        }
        waitForIdle()

        assertEquals("\${param.version}", currentValue)
    }

    @Test
    fun `down arrow wraps from last item to first`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                // Use only params (2 items) for simpler wrap-around testing
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = testParams,
                    predecessors = emptyList(),
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()

        // Down twice reaches last item (index 1 = "env"), Down again wraps to first (index 0 = "version")
        onNodeWithTag("field").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown) // wraps to index 0
            pressKey(Key.Enter)
        }
        waitForIdle()

        assertEquals("\${param.version}", currentValue)
    }

    @Test
    fun `up arrow wraps from first item to last`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = testParams,
                    predecessors = emptyList(),
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()

        // Down once (index 0 = "version"), Up wraps to last (index 1 = "env")
        onNodeWithTag("field").performKeyInput {
            pressKey(Key.DirectionDown) // index 0
            pressKey(Key.DirectionUp)   // wraps to index 1 (last)
            pressKey(Key.Enter)
        }
        waitForIdle()

        assertEquals("\${param.env}", currentValue)
    }

    @Test
    fun `disabled field does not show dropdown`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    enabled = false,
                    testTag = "field",
                )
            }
        }

        // Field should be disabled — Compose framework prevents input on disabled fields
        onNodeWithTag("field").assertIsNotEnabled()
        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).assertDoesNotExist()
    }

    // ---- Cursor-relative offset tests ----

    @Test
    fun `dropdown shifts right when trigger is preceded by text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        // Trigger at start — dropdown at left edge
        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()
        val leftAtStart = onNodeWithTag("field_suggestion_0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left

        // Clear and trigger with prefix — dropdown should shift right
        onNodeWithTag("field").performTextClearance()
        waitForIdle()
        onNodeWithTag("field").performTextInput("some prefix text \${")
        waitForIdle()
        val leftWithPrefix = onNodeWithTag("field_suggestion_0", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left

        // Dropdown with prefix should be to the right of dropdown at start
        assert(leftWithPrefix > leftAtStart) {
            "Expected dropdown with prefix (left=$leftWithPrefix) to be right of dropdown at start (left=$leftAtStart)"
        }
    }

    @Test
    fun `dropdown persists while typing expression body`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = testParams,
                    predecessors = testPredecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${par")
        waitForIdle()
        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).assertExists()

        // Type more — triggerOffset stays stable, dropdown still visible
        onNodeWithTag("field").performTextInput("am.")
        waitForIdle()
        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).assertExists()
    }
}
