package com.arifr1234.receipttolenghi.ui.modes.general

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class PlaylistUserMetadata(
    val name: String,
    val description: String = ""
)

data class PlaylistSystemMetadata(
    val size: Int,
    val lastModified: Long
)

data class PlaylistHeader(
    val id: String,
    val userMetadata: PlaylistUserMetadata,
    val systemMetadata: PlaylistSystemMetadata
) : PrintableState

data class PlaylistItem(
    val id: String = UUID.randomUUID().toString(),
    val state: PrintableState
)

internal data class PlaylistItemRaw(val id: String, val type: PrintableType, val stateJson: String)

object PlaylistManager {
    const val HISTORY_PLAYLIST_ID = "history"
    internal val gson = Gson()
    private var playlistsDir: File? = null
    private var indexState: ObservablePersistentState<List<PlaylistHeader>>? = null
    val playlists: StateFlow<List<PlaylistHeader>>
        get() = indexState?.stateFlow ?: MutableStateFlow(emptyList())

    fun init(playlistsDir: File) {
        if (indexState != null) return
        this.playlistsDir = playlistsDir.apply { mkdirs() }
        
        indexState = ObservablePersistentState(
            JsonListDiskState(File(playlistsDir, "index.json"), PlaylistHeader::class.java),
            0.milliseconds
        ).apply { open() }
        
        // Ensure history playlist exists
        if (indexState!!.get().none { it.id == HISTORY_PLAYLIST_ID }) {
            createPlaylistInternal(
                id = HISTORY_PLAYLIST_ID,
                userMetadata = PlaylistUserMetadata("History", "Automatically saved printed items")
            )
        }
    }

    fun writePlaylistHeader(header: PlaylistHeader) {
        val index = indexState?.get() ?: emptyList()
        if (index.none { it.id == header.id }) {
            indexState?.set { it + header }
        } else {
            indexState?.set { list -> 
                list.map { if (it.id == header.id) header else it } 
            }
        }
    }

    fun getPlaylistHeader(id: String): PlaylistHeader {
        return indexState?.get()?.find { it.id == id } 
            ?: throw IllegalArgumentException("Playlist with id $id not found in index")
    }

    fun updateUserMetadata(id: String, metadata: PlaylistUserMetadata) {
        val header = getPlaylistHeader(id)
        val updatedHeader = header.copy(userMetadata = metadata)
        indexState?.set { list -> list.map { if (it.id == id) updatedHeader else it } }
    }

    private fun createPlaylistInternal(id: String, userMetadata: PlaylistUserMetadata) {
        val header = PlaylistHeader(
            id = id,
            userMetadata = userMetadata,
            systemMetadata = PlaylistSystemMetadata(0, System.currentTimeMillis())
        )
        indexState?.set { listOf(header) + it }
        DiskPlaylist(id).set(emptyList())
    }

    fun createPlaylist(userMetadata: PlaylistUserMetadata): String {
        val id = UUID.randomUUID().toString()
        createPlaylistInternal(id, userMetadata)
        return id
    }

    fun deletePlaylist(id: String) {
        if (id == HISTORY_PLAYLIST_ID) throw IllegalArgumentException("Cannot delete the history playlist")
        indexState?.set { list -> list.filter { it.id != id } }
        getPlaylistFile(id).delete()
    }

    private fun getPlaylistFile(id: String): File {
        return File(playlistsDir, "$id.json")
    }

    fun getLastModifiedPlaylistId(): String? {
        val available = playlists.value.filter { it.id != HISTORY_PLAYLIST_ID }
        return available.maxByOrNull { it.systemMetadata.lastModified }?.id
    }

    class DiskPlaylist(private val id: String) : IPersistentState<List<PlaylistItem>> {
        private val rawState = JsonListDiskState(getPlaylistFile(id), PlaylistItemRaw::class.java)

        override fun get(): List<PlaylistItem> {
            return rawState.get().map { raw ->
                PlaylistItem(
                    id = raw.id,
                    state = gson.fromJson(raw.stateJson, raw.type.definition.stateClass.java)
                )
            }
        }
        
        override fun set(value: List<PlaylistItem>) {
            // Auto-update system metadata
            val currentHeader = getPlaylistHeader(id)
            val updatedSystemMetadata = PlaylistSystemMetadata(
                size = value.size,
                lastModified = System.currentTimeMillis()
            )
            val updatedHeader = currentHeader.copy(systemMetadata = updatedSystemMetadata)
            indexState?.set { list -> list.map { if (it.id == id) updatedHeader else it } }
            
            // Serialize and save to disk
            val rawItems = value.map { item ->
                PlaylistItemRaw(
                    id = item.id,
                    type = PrintableRegistry.getTypeFor(item.state),
                    stateJson = gson.toJson(item.state)
                )
            }
            rawState.set(rawItems)
        }
    }
}

interface IPersistentState<T> {
    fun get(): T
    fun set(value: T)
    
    fun set(updater: (T) -> T) {
        set(updater(get()))
    }
}

class JsonListDiskState<T>(
    private val file: File,
    elementClass: Class<T>
) : IPersistentState<List<T>> {
    private val gson = Gson()
    private val listType = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, elementClass).type
    
    override fun get(): List<T> {
        if (!file.exists()) return emptyList()
        return gson.fromJson(file.readText(), listType) ?: emptyList()
    }
    
    override fun set(value: List<T>) {
        file.writeText(gson.toJson(value))
    }
}

class ObservablePersistentState<T>(
    private val disk: IPersistentState<T>,
    private val debounceDuration: Duration = 500.milliseconds
) : IPersistentState<T> {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    private val _stateFlow = MutableStateFlow(disk.get())
    val stateFlow: StateFlow<T> = _stateFlow.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun open() {
        if (job != null) return
        job = scope.launch {
            if (debounceDuration > 0.milliseconds) {
                _stateFlow.debounce(debounceDuration).collect { settledItems ->
                    disk.set(settledItems)
                }
            } else {
                _stateFlow.collect { items ->
                    disk.set(items)
                }
            }
        }
    }

    override fun get() = _stateFlow.value

    override fun set(value: T) {
        _stateFlow.value = value
    }

    fun close() {
        if (job != null) {
            job?.cancel()
            job = null
            // Flush any pending debounced changes to disk immediately!
            disk.set(_stateFlow.value)
        }
    }
}

@Composable
fun <T> rememberObservablePersistentState(
    persistentState: IPersistentState<T>,
    debounceDuration: Duration = 500.milliseconds
): ObservablePersistentState<T> {
    val editor = remember(persistentState) {
        ObservablePersistentState(persistentState, debounceDuration).apply { open() }
    }
    DisposableEffect(editor) {
        onDispose { editor.close() }
    }
    return editor
}
