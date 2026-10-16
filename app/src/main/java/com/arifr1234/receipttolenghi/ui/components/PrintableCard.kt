package com.arifr1234.receipttolenghi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import com.arifr1234.receipttolenghi.printer.PrinterManager
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistHeader
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistItem
import com.arifr1234.receipttolenghi.ui.modes.general.PlaylistManager
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableDefinition
import com.arifr1234.receipttolenghi.ui.modes.general.PrintableState

val LocalAddToPlaylist = compositionLocalOf<((PrintableState) -> Unit)?> { null }

@Composable
fun <T : PrintableState> PrintableCard(
    modifier: Modifier = Modifier,
    state: T,
    onStateChange: (T) -> Unit = {},
    onPrintSuccess: (T) -> Unit = { item ->
        val disk = PlaylistManager.DiskPlaylist(PlaylistManager.HISTORY_PLAYLIST_ID)
        disk.set(listOf(PlaylistItem(state = item)) + disk.get())
    },
    definition: PrintableDefinition<T>,
    clearAfterPrint: Boolean = false,
    isReadOnly: Boolean = false,
    isScrollable: Boolean = false,
    onAddToPlaylist: ((T) -> Unit)? = if (state is PlaylistHeader) null else LocalAddToPlaylist.current as? ((T) -> Unit)
) {
    val context = LocalContext.current
    var isConfigExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        val scrollState = androidx.compose.foundation.rememberScrollState()
        val clipboard = LocalClipboard.current
        val coroutineScope = rememberCoroutineScope()
        Column(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
                .let { if (isScrollable) it.verticalScroll(scrollState) else it }
        ) {
            // The dynamic UI editor for this state
            ReadOnlyBox(isReadOnly = isReadOnly) {
                definition.EditorUI(state, onStateChange)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                val clipboardText = definition.getClipboardText(state)
                if (clipboardText != null) {
                    IconButton(
                        modifier = Modifier.size(36.dp),
                        onClick = {
                            coroutineScope.launch {
                                clipboard.setClipEntry(ClipEntry(
                                    ClipData.newPlainText("text", clipboardText)
                                ))
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
                IconButton(
                    modifier = Modifier.size(36.dp),
                    onClick = { isConfigExpanded = !isConfigExpanded }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configure",
                        tint = if (isConfigExpanded) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                if (onAddToPlaylist != null) {
                    IconButton(
                        modifier = Modifier.size(36.dp),
                        onClick = { onAddToPlaylist(state) }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add to playlist")
                    }
                }
                var isPrinting by remember { mutableStateOf(false) }
                
                Box(contentAlignment = Alignment.Center) {
                    IconButton(
                        modifier = Modifier.size(36.dp),
                        onClick = {
                            PrinterManager.printAsync { printer ->
                                isPrinting = true
                                try {
                                    definition.print(context, state, printer)
                                    onPrintSuccess(state)
                                    if (clearAfterPrint) {
                                        onStateChange(definition.clearState(state))
                                    }
                                } finally {
                                    isPrinting = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Print")
                    }
                    
                    if (isPrinting) {
                        GlowingDot(
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }
            }
            
            AnimatedVisibility(
                visible = isConfigExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    ReadOnlyBox(isReadOnly = isReadOnly) {
                        definition.ConfigUI(state, onStateChange)
                    }
                }
            }
        }
    }
}
