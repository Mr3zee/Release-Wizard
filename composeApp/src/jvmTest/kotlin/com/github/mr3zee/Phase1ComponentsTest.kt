package com.github.mr3zee

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwBadge
import com.github.mr3zee.components.RwDangerZone
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
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
                        androidx.compose.material3.Text("Delete")
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
                    androidx.compose.material3.Text("Content")
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
}
