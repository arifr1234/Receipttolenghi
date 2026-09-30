package com.arifr1234.receipttolenghi.ui.modes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.ui.components.PrintableCard
import com.arifr1234.receipttolenghi.ui.components.PrintableCarousel
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistManager
import com.arifr1234.receipttolenghi.ui.modes.general.rememberObservablePersistentState
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableRegistry
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralPrintScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Compact Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Workspace",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Box(
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                val historyEditor = rememberObservablePersistentState(
                    persistentState = PlaylistManager.DiskPlaylist(PlaylistManager.HISTORY_PLAYLIST_ID),
                    debounceDuration = 0.milliseconds
                )
                val history by historyEditor.stateFlow.collectAsState()
                
                val listState = rememberLazyListState()
                
                LaunchedEffect(history.size) {
                    if (history.isNotEmpty()) {
                        listState.animateScrollToItem(0)
                    }
                }
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                    reverseLayout = true
                ) {
                    items(history) { item ->
                        PrintableCard(
                            modifier = Modifier.fillMaxWidth(),
                            state = item,
                            definition = PrintableRegistry.getTypedDefinitionFor(item),
                            isReadOnly = true,
                            onPrintSuccess = { printedState ->
                                // TODO: Are we adding a printable state with the same ID? Why do the printable state have an ID?
                                historyEditor.set { prev -> listOf(printedState) + prev }
                            }
                        )
                    }
                }
                
                PrintableCarousel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    onPrintSuccess = { printedState ->
                        historyEditor.set { prev -> listOf(printedState) + prev }
                    }
                )
            }
        }
    }
}
