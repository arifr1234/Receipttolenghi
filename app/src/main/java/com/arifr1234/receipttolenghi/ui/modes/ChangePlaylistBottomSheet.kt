package com.arifr1234.receipttolenghi.ui.modes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistManager
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistUserMetadata
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePlaylistBottomSheet(
    onDismissRequest: () -> Unit,
    onPlaylistSelected: (PlaylistHeader?) -> Unit,
    showRemoveOption: Boolean = false
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        val playlistsRaw by PlaylistManager.playlists.collectAsState()
        val availablePlaylists = playlistsRaw.filter { it.id != PlaylistManager.HISTORY_PLAYLIST_ID }
        
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Add to Playlist",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (showRemoveOption) {
                    item {
                        ListItem(
                            headlineContent = { Text("Remove from playlist", color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable { onPlaylistSelected(null) }
                        )
                    }
                }
                
                item {
                    ListItem(
                        headlineContent = { Text("Create new playlist") },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val newId = PlaylistManager.createPlaylist(PlaylistUserMetadata("New Playlist"))
                            onPlaylistSelected(PlaylistManager.getPlaylistHeader(newId))
                        }
                    )
                }
                
                items(availablePlaylists, key = { it.id }) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.userMetadata.name) },
                        supportingContent = { 
                            if (playlist.userMetadata.description.isNotBlank()) {
                                Text(playlist.userMetadata.description)
                            }
                        },
                        modifier = Modifier.clickable {
                            onPlaylistSelected(playlist)
                        }
                    )
                }
            }
        }
    }
}
