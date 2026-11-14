package com.freehtml.app.data.preferences

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.freehtml.app.data.model.LanguageMode
import com.freehtml.app.data.model.RecentFile
import com.freehtml.app.data.model.ThemeMode
import com.freehtml.app.data.model.withoutUris
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "freehtml_preferences")

class AppPreferences(context: Context) {
    private val context = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val recentFilesSerializer = ListSerializer(RecentFile.serializer())

    private val preferences: Flow<Preferences> = context.dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    val themeMode: Flow<ThemeMode> = preferences.map { prefs ->
        ThemeMode.fromStorage(prefs[Keys.THEME_MODE])
    }

    val recentFiles: Flow<List<RecentFile>> = preferences.map { prefs ->
        decodeRecentFiles(prefs[Keys.RECENT_FILES])
    }

    /** Granted folder (tree URI) per document URI, used to load site resources in the preview. */
    val folderAccess: Flow<Map<String, String>> = preferences.map { prefs ->
        decodeStringMap(prefs[Keys.FOLDER_ACCESS])
    }

    /** Editor code zoom (1.0 = 100%), persisted across sessions. */
    val editorZoom: Flow<Float> = preferences.map { prefs ->
        prefs[Keys.EDITOR_ZOOM]?.coerceIn(0.5f, 3.0f) ?: 1.0f
    }

    /** Incognito mode: no history entries and no writes to disk. */
    val incognito: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.INCOGNITO] ?: false
    }

    /** Interface language mode. */
    val languageMode: Flow<LanguageMode> = preferences.map { prefs ->
        LanguageMode.fromStorage(prefs[Keys.LANGUAGE_MODE])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun addRecentFile(file: RecentFile) {
        context.dataStore.edit { prefs ->
            val current = decodeRecentFiles(prefs[Keys.RECENT_FILES])
            val updated = (
                listOf(file.copy(openedAt = System.currentTimeMillis())) +
                    current.filterNot { it.uri == file.uri }
                ).take(MAX_RECENT_FILES)
            prefs[Keys.RECENT_FILES] = json.encodeToString(recentFilesSerializer, updated)
        }
    }

    suspend fun clearRecentFiles() {
        var files = emptyList<RecentFile>()
        context.dataStore.edit { prefs ->
            files = decodeRecentFiles(prefs[Keys.RECENT_FILES])
            prefs.remove(Keys.RECENT_FILES)
        }
        files.forEach(::releasePermission)
    }

    suspend fun removeRecentFiles(uris: Set<String>) {
        if (uris.isEmpty()) return

        var removed = emptyList<RecentFile>()
        context.dataStore.edit { prefs ->
            val current = decodeRecentFiles(prefs[Keys.RECENT_FILES])
            removed = current.filter { it.uri in uris }
            val updated = current.withoutUris(uris)
            if (updated.isEmpty()) {
                prefs.remove(Keys.RECENT_FILES)
            } else {
                prefs[Keys.RECENT_FILES] = json.encodeToString(recentFilesSerializer, updated)
            }
        }
        removed.forEach(::releasePermission)
    }

    suspend fun setIncognito(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.INCOGNITO] = value
        }
    }

    suspend fun setLanguageMode(mode: LanguageMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE_MODE] = mode.name
        }
        // Mirror synchronously so attachBaseContext can apply the locale before
        // the activity (and its resources) are created.
        writeSyncedLanguageMode(mode)
    }

    private fun writeSyncedLanguageMode(mode: LanguageMode) {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SYNC_KEY_LANGUAGE_MODE, mode.name)
            .apply()
    }

    suspend fun setEditorZoom(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EDITOR_ZOOM] = value.coerceIn(0.5f, 3.0f)
        }
    }

    suspend fun setFolderAccess(documentUri: String, treeUri: String) {
        context.dataStore.edit { prefs ->
            val current = decodeStringMap(prefs[Keys.FOLDER_ACCESS]).toMutableMap()
            current[documentUri] = treeUri
            prefs[Keys.FOLDER_ACCESS] = encodeStringMap(current)
        }
    }

    private fun decodeStringMap(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString(StringMapSerializer, value)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun encodeStringMap(value: Map<String, String>): String =
        json.encodeToString(StringMapSerializer, value)

    private fun decodeRecentFiles(value: String?): List<RecentFile> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(recentFilesSerializer, value)
        }.getOrDefault(emptyList())
    }

    private fun releasePermission(file: RecentFile) {
        val flags = if (file.type.isEditable) {
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        } else {
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        runCatching {
            context.contentResolver.releasePersistableUriPermission(file.uri.toUri(), flags)
        }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val RECENT_FILES = stringPreferencesKey("recent_files")
        val FOLDER_ACCESS = stringPreferencesKey("folder_access")
        val EDITOR_ZOOM = floatPreferencesKey("editor_zoom")
        val INCOGNITO = booleanPreferencesKey("incognito")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
    }

    companion object {
        private const val MAX_RECENT_FILES = 50
        private const val SYNC_PREFS_NAME = "freehtml_sync"
        private const val SYNC_KEY_LANGUAGE_MODE = "language_mode"

        /** Synchronous read of the mirrored language mode, safe for attachBaseContext. */
        fun readSyncedLanguageMode(context: Context): String? =
            context.applicationContext.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(SYNC_KEY_LANGUAGE_MODE, null)
        val StringMapSerializer: kotlinx.serialization.KSerializer<Map<String, String>> =
            kotlinx.serialization.builtins.MapSerializer(
                String.serializer(),
                String.serializer(),
            )
    }
}
