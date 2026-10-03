package com.arifr1234.receipttolenghi.ui.modes.general

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.google.gson.Gson
import java.io.File

internal object PlaylistShareManager {
    const val FILE_EXTENSION = ".json"
    private val gson: Gson = PlaylistManager.gson

    data class ExternalFile(
        val relativePath: String,
        val base64Data: String
    )

    data class ExportPlaylist(
        val header: PlaylistHeader,
        val items: List<PlaylistItemRaw>
    )

    data class ExportPackage(
        val playlists: List<ExportPlaylist>,
        val externalFiles: List<ExternalFile>
    )

    fun getExternalFilesDir(context: Context): File {
        val dir = File(context.filesDir, "external_files")
        dir.mkdirs()
        return dir
    }

    fun exportPlaylist(context: Context, playlistId: String): Uri {
        val header = PlaylistManager.getPlaylistHeader(playlistId)
        val items = PlaylistManager.DiskPlaylist(playlistId).get()
        
        val externalFilesDir = getExternalFilesDir(context)

        val externalFiles = items
            .flatMap { item -> PrintableRegistry.getTypedDefinitionFor(item.state).getExternalFiles(item.state) }
            .distinct()
            .map { relativePath ->
                val file = File(externalFilesDir, relativePath)
                if (!file.exists()) {
                    throw IllegalStateException("Missing external file: $relativePath")
                }
                val bytes = file.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                ExternalFile(relativePath, base64)
            }

        val rawItems = items.map { item ->
            PlaylistItemRaw(
                id = item.id,
                type = PrintableRegistry.getTypeFor(item.state),
                stateJson = gson.toJson(item.state)
            )
        }
        
        val pkg = ExportPackage(
            playlists = listOf(ExportPlaylist(header, rawItems)),
            externalFiles = externalFiles
        )

        val json = gson.toJson(pkg)

        val sharedDir = File(context.cacheDir, "shared")
        sharedDir.mkdirs()
        
        sharedDir.listFiles()?.forEach { it.delete() }
        
        val safeName = header.userMetadata.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val exportFile = File(sharedDir, "${safeName}$FILE_EXTENSION")
        exportFile.writeText(json)

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
    }

    fun importPlaylists(context: Context, fileUri: Uri) {
        val json = context.contentResolver.openInputStream(fileUri)?.use { it.reader().readText() }
            ?: throw IllegalStateException("Could not read shared file")
            
        val pkg = gson.fromJson(json, ExportPackage::class.java)
        val externalFilesDir = getExternalFilesDir(context)
        
        pkg.externalFiles.forEach { extFile ->
            val resolvedFile = File(externalFilesDir, extFile.relativePath)
            if (!resolvedFile.canonicalPath.startsWith(externalFilesDir.canonicalPath)) {
                throw SecurityException("Invalid relative path in shared package: ${extFile.relativePath}")
            }
            
            val bytes = Base64.decode(extFile.base64Data, Base64.NO_WRAP)
            resolvedFile.writeBytes(bytes)
        }
        
        pkg.playlists.forEach { importedPlaylist ->
            val id = importedPlaylist.header.id
            
            val inflatedItems = importedPlaylist.items.map { raw ->
                PlaylistItem(
                    id = raw.id,
                    state = gson.fromJson(raw.stateJson, raw.type.definition.stateClass.java)
                )
            }
            
            PlaylistManager.writePlaylistHeader(importedPlaylist.header)
            PlaylistManager.DiskPlaylist(id).set(inflatedItems)
        }
    }
}
