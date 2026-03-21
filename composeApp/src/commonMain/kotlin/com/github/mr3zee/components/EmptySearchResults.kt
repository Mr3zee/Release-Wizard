package com.github.mr3zee.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import releasewizard.composeapp.generated.resources.*

/**
 * Empty search results composable with a search icon, "No results" message,
 * and a "Clear search" button. Used across list screens when a search/filter
 * yields no matches.
 */
@Composable
fun EmptySearchResults(
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = packStringResource(Res.string.common_no_search_results),
            style = AppTypography.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        RwButton(onClick = onClearSearch, variant = RwButtonVariant.Ghost) {
            Text(packStringResource(Res.string.common_clear_search))
        }
    }
}
