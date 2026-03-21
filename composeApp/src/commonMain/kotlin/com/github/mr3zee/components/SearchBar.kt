package com.github.mr3zee.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.theme.Spacing

/**
 * Standard search bar used across list screens. Renders an RwTextField with a search icon,
 * constrained to 1200dp max width and standard horizontal/vertical padding.
 *
 * @param query The current search query.
 * @param onQueryChange Callback when the query changes.
 * @param placeholder Placeholder text shown when the query is empty.
 * @param focusRequester FocusRequester for keyboard shortcut integration.
 * @param testTag Test tag for the search field. Defaults to "search_field".
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    testTag: String = "search_field",
) {
    RwTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = placeholder,
        singleLine = true,
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 1200.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .focusRequester(focusRequester)
            .testTag(testTag),
    )
}
