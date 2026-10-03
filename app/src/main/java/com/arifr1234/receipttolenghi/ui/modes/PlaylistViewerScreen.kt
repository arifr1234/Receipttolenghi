package com.arifr1234.receipttolenghi.ui.modes

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.arifr1234.receipttolenghi.printer.PrinterManager
import com.arifr1234.receipttolenghi.ui.components.PrintableList
import com.arifr1234.receipttolenghi.ui.modes.general.*
import androidx.compose.material.icons.filled.Share
import com.arifr1234.receipttolenghi.utils.AppNotifier
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistViewerScreen(
    playlistId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val disk = remember(playlistId) { PlaylistManager.DiskPlaylist(playlistId) }
    val editor = rememberObservablePersistentState(disk)
    val items by editor.stateFlow.collectAsState()
    
    val playlists by PlaylistManager.playlists.collectAsState()
    val header = playlists.find { it.id == playlistId }

    var isManageMode by remember { mutableStateOf(false) }
    var currentlyPrintingId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var cancelRequested by remember { mutableStateOf(false) }
    

    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(header?.userMetadata?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val coroutineScope = rememberCoroutineScope()
                    IconButton(onClick = {
                        coroutineScope.launch {
                            try {
                                val uri = PlaylistShareManager.exportPlaylist(context, playlistId)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Playlist"))
                            } catch (e: Exception) {
                                AppNotifier.handleError(e, "Failed to share playlist")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    
                    if (isPlaying) {
                        IconButton(onClick = { cancelRequested = true }) {
                            Icon(Icons.Default.Close, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = {
                            if (items.isEmpty()) return@IconButton
                            isPlaying = true
                            cancelRequested = false
                            
                            PrinterManager.printAsync { printer ->
                                try {
                                    for (item in items) {
                                        if (cancelRequested) break
                                        currentlyPrintingId = item.id
                                        val definition = PrintableRegistry.getTypedDefinitionFor(item.state)
                                        definition.print(context, item.state, printer)
                                    }
                                } finally {
                                    currentlyPrintingId = null
                                    isPlaying = false
                                }
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play All")
                        }
                    }
                    TextButton(onClick = { isManageMode = !isManageMode }) {
                        Text(if (isManageMode) "Done" else "Manage")
                    }
                }
            )
        }
    ) { innerPadding ->
        PrintableList(
            modifier = Modifier.padding(innerPadding),
            items = items,
            isManageMode = isManageMode,
            onStateChange = { original, updatedState ->
                editor.set(items.map { if (it.id == original.id) it.copy(state = updatedState) else it })
            },
            onDelete = { deletedItem ->
                editor.set(items.filter { it.id != deletedItem.id })
            },
            onReorder = { from, to ->
                val mutableItems = items.toMutableList()
                val item = mutableItems.removeAt(from)
                mutableItems.add(to, item)
                editor.set(mutableItems)
            },
            currentlyPrintingId = currentlyPrintingId
        )
    }
}
