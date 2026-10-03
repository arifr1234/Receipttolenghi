package com.arifr1234.receipttolenghi.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableRegistry
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableState

import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistItem

@Composable
fun PrintableList(
    modifier: Modifier = Modifier,
    items: List<PlaylistItem>,
    listState: LazyListState = rememberLazyListState(),
    isManageMode: Boolean,
    onStateChange: (PlaylistItem, PrintableState) -> Unit,
    onDelete: (PlaylistItem) -> Unit,
    onReorder: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    currentlyPrintingId: String? = null,
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No items yet", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val printableState = item.state

                Row(
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isManageMode) {
                        ItemManageControls(
                            onDelete = { onDelete(item) },
                            onMoveUp = if (onReorder != null && index > 0) { { onReorder(index, index - 1) } } else null,
                            onMoveDown = if (onReorder != null && index < items.size - 1) { { onReorder(index, index + 1) } } else null
                        )
                    }
                    
                    Box(modifier = Modifier.weight(1f)) {
                        PrintableCard(
                            state = printableState,
                            definition = PrintableRegistry.getTypedDefinitionFor(printableState),
                            onStateChange = { updated ->
                                onStateChange(item, updated)
                            }
                        )
                        if (currentlyPrintingId == item.id) {
                            GlowingDot(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemManageControls(
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    IconButton(
        onClick = onDelete,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.error
        )
    }
    
    if (onMoveUp != null || onMoveDown != null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = { onMoveUp?.invoke() },
                enabled = onMoveUp != null,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
            }
            IconButton(
                onClick = { onMoveDown?.invoke() },
                enabled = onMoveDown != null,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
            }
        }
    }
}
