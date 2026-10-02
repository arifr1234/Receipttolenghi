package com.arifr1234.receipttolenghi.ui.modes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.ui.components.PrintableCard
import com.arifr1234.receipttolenghi.ui.components.PrintableCarousel
import com.arifr1234.receipttolenghi.ui.components.LocalAddToPlaylist
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistItem
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistManager
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableState
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableRegistry
import com.arifr1234.receipttolenghi.ui.modes.general.rememberObservablePersistentState
import kotlinx.coroutines.launch

enum class GeneralPrintPage { Main, Playlists }

@Composable
fun GeneralPrintScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(GeneralPrintPage.Main) }
    
    BackHandler(enabled = currentPage != GeneralPrintPage.Main) {
        currentPage = GeneralPrintPage.Main
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var changePlaylistContext by remember { mutableStateOf<Pair<PlaylistItem, String?>?>(null) }
    
    val handleAddToPlaylist: (PrintableState) -> Unit = { item ->
        val playlistItem = PlaylistItem(state = item)
        val defaultId = PlaylistManager.getLastModifiedPlaylistId()
        if (defaultId == null) {
            changePlaylistContext = Pair(playlistItem, null)
        } else {
            PlaylistManager.DiskPlaylist(defaultId).set { it + playlistItem }
            val playlistName = PlaylistManager.getPlaylistHeader(defaultId).userMetadata.name
            coroutineScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = "Added to $playlistName",
                    actionLabel = "Change",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    changePlaylistContext = Pair(playlistItem, defaultId)
                }
            }
        }
    }

    if (changePlaylistContext != null) {
        val (itemToMove, oldPlaylistId) = changePlaylistContext!!
        ChangePlaylistBottomSheet(
            onDismissRequest = { changePlaylistContext = null },
            onPlaylistSelected = { playlist ->
                // Remove from old
                if (oldPlaylistId != null) {
                    PlaylistManager.DiskPlaylist(oldPlaylistId).set { it.filter { i -> i.id != itemToMove.id } }
                }
                // Add to new
                PlaylistManager.DiskPlaylist(playlist.id).set { it + itemToMove }
                
                changePlaylistContext = null
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = "Added to ${playlist.userMetadata.name}",
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        CompositionLocalProvider(
            LocalAddToPlaylist provides handleAddToPlaylist
        ) {
            when (currentPage) {
                GeneralPrintPage.Main -> {
                    GeneralPrintMain(
                        onNavigateBack = onNavigateBack,
                        onNavigateToPlaylists = { currentPage = GeneralPrintPage.Playlists },
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                    )
                }
                GeneralPrintPage.Playlists -> {
                    PlaylistsScreen(
                        onNavigateBack = { currentPage = GeneralPrintPage.Main },
                        modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralPrintMain(
    onNavigateBack: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
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
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onNavigateToPlaylists) {
                Text("Playlists")
            }
        }
        
        Box(
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                val historyDisk = remember { PlaylistManager.DiskPlaylist(PlaylistManager.HISTORY_PLAYLIST_ID) }
                val historyEditor = rememberObservablePersistentState(historyDisk)
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
                    items(history, key = { it.id }) { playlistItem ->
                        PrintableCard(
                            modifier = Modifier.fillMaxWidth(),
                            state = playlistItem.state,
                            definition = PrintableRegistry.getTypedDefinitionFor(playlistItem.state),
                            isReadOnly = true,
                            onPrintSuccess = { item ->
                                historyEditor.set { listOf(PlaylistItem(state = item)) + it }
                            }
                        )
                    }
                }
                
                PrintableCarousel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    onPrintSuccess = { item ->
                        historyEditor.set { listOf(PlaylistItem(state = item)) + it }
                    }
                )
            }
        }
    }
}
