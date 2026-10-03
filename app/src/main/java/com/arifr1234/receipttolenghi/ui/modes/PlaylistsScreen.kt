package com.arifr1234.receipttolenghi.ui.modes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arifr1234.receipttolenghi.ui.components.PrintableList
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistHeader
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistItem
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistManager
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
        val items = remember(playlists) { playlists.map { PlaylistItem(state = it) } }
        
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No playlists yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            PrintableList(
                modifier = Modifier.padding(innerPadding),
                items = items,
                listState = listState,
                isManageMode = isManageMode,
                onStateChange = { originalItem, updatedState ->
                    val playlistId = (originalItem.state as PlaylistHeader).id
                    PlaylistManager.updateUserMetadata(playlistId, (updatedState as PlaylistHeader).userMetadata)
                },
                onDelete = { PlaylistManager.deletePlaylist((it.state as PlaylistHeader).id) }
            )
        }
    }
}
