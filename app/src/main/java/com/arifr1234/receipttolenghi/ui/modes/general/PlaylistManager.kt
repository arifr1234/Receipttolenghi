package com.arifr1234.receipttolenghi.ui.modes.general

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

import com.arifr1234.receipttolenghi.utils.AppErrorHandler

private data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val type: PrintableType,
    val stateJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ParsedHistoryItem(
    val id: String,
    val state: PrintableState
)

object PlaylistManager {
    private const val PREFS_NAME = "receipttolenghi_history"
    private const val KEY_HISTORY_LIST = "history_list"
    
    private var prefs: SharedPreferences? = null
    private val gson = Gson()
    
    private val _history = MutableStateFlow<List<ParsedHistoryItem>>(emptyList())
    val history: StateFlow<List<ParsedHistoryItem>> = _history.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadHistory()
    }

    private fun loadHistory() {
        val json = prefs?.getString(KEY_HISTORY_LIST, null) ?: return
        val rawItems = runCatching { 
            gson.fromJson(json, Array<HistoryItem>::class.java).toList() 
        }.getOrDefault(emptyList())

        _history.value = rawItems.mapNotNull { raw ->
            runCatching {
                val state = gson.fromJson(raw.stateJson, raw.type.definition.stateClass.java)
                ParsedHistoryItem(raw.id, state)
            }.onFailure {
                // We use AppErrorHandler directly just to log to the system/snackbar without crashing the whole map
                AppErrorHandler.handleError(it, "Failed to parse history item")
            }.getOrNull()
        }
    }

    private fun saveHistory(items: List<ParsedHistoryItem>) {
        _history.value = items
        val rawItems = items.map { 
            HistoryItem(it.id, PrintableRegistry.getTypeFor(it.state), gson.toJson(it.state)) 
        }
        prefs?.edit()?.putString(KEY_HISTORY_LIST, gson.toJson(rawItems))?.apply()
    }

    fun addStateToHistory(state: PrintableState) {
        val newItem = ParsedHistoryItem(
            id = UUID.randomUUID().toString(),
            state = state
        )
        // Add to top of list
        val updatedList = listOf(newItem) + _history.value
        saveHistory(updatedList)
    }
    
    fun clearHistory() {
        saveHistory(emptyList())
    }
}
