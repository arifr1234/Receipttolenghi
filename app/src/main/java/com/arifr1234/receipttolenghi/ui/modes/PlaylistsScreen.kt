package com.arifr1234.receipttolenghi.ui.modes

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.arifr1234.receipttolenghi.ui.components.PrintableCard
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistManager
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableRegistry
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistUserMetadata
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isManageMode by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Playlists") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { isManageMode = !isManageMode }) {
                        Text(if (isManageMode) "Done" else "Manage")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                PlaylistManager.createPlaylist(PlaylistUserMetadata("New Playlist"))
                coroutineScope.launch { listState.animateScrollToItem(0) }
            }) {
                Icon(Icons.Default.Add, contentDescription = "New Playlist")
            }
        }
    ) { innerPadding ->
        val playlistsRaw by PlaylistManager.playlists.collectAsState()
        val playlists = playlistsRaw.filter { it.id != PlaylistManager.HISTORY_PLAYLIST_ID }
        
        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No playlists yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isManageMode) {
                            IconButton(
                                onClick = { PlaylistManager.deletePlaylist(playlist.id) },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        PrintableCard(
                            modifier = Modifier.weight(1f),
                            state = playlist,
                            definition = PrintableRegistry.getTypedDefinitionFor(playlist),
                            onStateChange = { updated ->
                                PlaylistManager.updateUserMetadata(updated.id, updated.userMetadata)
                            },
                            onPrintSuccess = { }
                        )
                    }
                }
            }
        }
    }
}
