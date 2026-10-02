package com.arifr1234.receipttolenghi.ui.modes.general

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dantsu.escposprinter.EscPosPrinter
import java.text.SimpleDateFormat
import java.util.*

object PlaylistPrintableDefinition : PrintableDefinition<PlaylistHeader> {
    override val displayName: String = "Playlist"
    override val stateClass = PlaylistHeader::class

    override fun createDefaultState(): PlaylistHeader {
        return PlaylistHeader(
            id = UUID.randomUUID().toString(),
            userMetadata = PlaylistUserMetadata("New Playlist"),
            systemMetadata = PlaylistSystemMetadata(0, System.currentTimeMillis())
        )
    }

    @Composable
    override fun EditorUI(state: PlaylistHeader, onStateChange: (PlaylistHeader) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.userMetadata.name,
                style = MaterialTheme.typography.titleMedium
            )
            if (state.userMetadata.description.isNotBlank()) {
                Text(
                    text = state.userMetadata.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    @Composable
    override fun ConfigUI(state: PlaylistHeader, onStateChange: (PlaylistHeader) -> Unit) {
        val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.userMetadata.name,
                onValueChange = { 
                    onStateChange(state.copy(userMetadata = state.userMetadata.copy(name = it)))
                },
                label = { Text("Playlist Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.userMetadata.description,
                onValueChange = { 
                    onStateChange(state.copy(userMetadata = state.userMetadata.copy(description = it)))
                },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = "Items: ${state.systemMetadata.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Last Modified: ${dateFormat.format(Date(state.systemMetadata.lastModified))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    override fun print(context: Context, state: PlaylistHeader, printer: EscPosPrinter) {
        val items = PlaylistManager.DiskPlaylist(state.id).get()
        items.forEach { item ->
            PrintableRegistry.getTypedDefinitionFor(item.state).print(context, item.state, printer)
        }
    }
}
