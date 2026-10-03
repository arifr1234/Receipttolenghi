package com.arifr1234.receipttolenghi.ui.modes

import androidx.compose.runtime.compositionLocalOf

interface PlaylistNavigation {
    fun openViewer(playlistId: String)
}

val LocalPlaylistNavigation = compositionLocalOf<PlaylistNavigation> {
    object : PlaylistNavigation {
        override fun openViewer(playlistId: String) {}
    }
}
