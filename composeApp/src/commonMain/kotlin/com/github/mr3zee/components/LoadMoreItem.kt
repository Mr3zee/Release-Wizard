package com.github.mr3zee.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.PaginationInfo

/**
 * Adds a "load more" item to a [LazyListScope] that triggers pagination.
 */
fun LazyListScope.loadMoreItem(
    pagination: PaginationInfo?,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val currentPagination = pagination ?: return
    val hasMore = (currentPagination.offset + currentPagination.limit) < currentPagination.totalCount
    if (hasMore) {
        item {
            LaunchedEffect(currentPagination.offset) { onLoadMore() }
            if (isLoadingMore) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
