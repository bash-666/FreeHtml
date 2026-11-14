package com.freehtml.app.ui.editor

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freehtml.app.data.model.DocumentType
import com.freehtml.app.util.DocxToHtml
import com.freehtml.app.util.languageForFileName
import com.freehtml.app.util.previewHtmlFor
import com.freehtml.app.util.queryDisplayName
import com.freehtml.app.util.resolveLocalResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditorError {
    UNSUPPORTED,
    READ_FAILED,
}

enum class EditorMessage {
    SAVED,
    SAVE_ERROR,
}

data class EditorUiState(
    val fileName: String = "",
    val type: DocumentType? = null,
    val content: String = "",
    val savedContent: String = "",
    val hasPendingChanges: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: EditorError? = null,
    val message: EditorMessage? = null,
) {
    val isEditable: Boolean get() = type?.isEditable == true
    val isDirty: Boolean get() = hasPendingChanges || content != savedContent
    val language: String
        get() = if (type == DocumentType.DOCX) "html" else languageForFileName(fileName)
}

/** A file served to the preview WebView from a user-granted folder. */
data class PreviewAsset(
    val mimeType: String,
    val bytes: ByteArray,
)

class EditorViewModel(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val preferences: com.freehtml.app.data.preferences.AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _previewHtml = MutableStateFlow<String?>(null)
    val previewHtml: StateFlow<String?> = _previewHtml.asStateFlow()

    private val _needsFolderAccess = MutableStateFlow(false)
    val needsFolderAccess: StateFlow<Boolean> = _needsFolderAccess.asStateFlow()

    private val _assetResolver = MutableStateFlow<((String) -> PreviewAsset?)?>(null)
    val assetResolver: StateFlow<((String) -> PreviewAsset?)?> = _assetResolver.asStateFlow()

    private var pendingSyncJob: Job? = null
    private var previewJob: Job? = null
    private var contentRevision = 0L
    private var folderAccessDismissed = false
    private var incognito = false
    private var grantedTreeUri: Uri? = null
    private var cachedFolderAccess: Map<String, String>? = null

    init {
        loadFile()
    }

    fun loadFile() {
        android.util.Log.d("FREEHTML-PREP", "loadFile() start")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = contentResolver.queryDisplayName(uri)
                        ?: uri.lastPathSegment
                        ?: "archivo"
                    val type = DocumentType.fromFileName(fileName)
                        ?: DocumentType.fromMimeType(contentResolver.getType(uri))

                    when (type) {
                        null -> LoadResult.Failure(fileName, null, EditorError.UNSUPPORTED)
                        DocumentType.PDF -> {
                            // The PDF is opened directly by the viewer from its content URI.
                            LoadResult.Success(fileName, type, content = "")
                        }

                        else -> {
                            val content = runCatching {
                                contentResolver.openInputStream(uri)?.use { stream ->
                                    if (type == DocumentType.DOCX) {
                                        DocxToHtml.convert(stream)
                                    } else {
                                        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                                    }
                                }
                            }.getOrNull()

                            if (content == null) {
                                LoadResult.Failure(fileName, type, EditorError.READ_FAILED)
                            } else {
                                LoadResult.Success(fileName, type, content)
                            }
                        }
                    }
                }.getOrElse {
                    LoadResult.Failure(null, null, EditorError.READ_FAILED)
                }
            }

            pendingSyncJob?.cancel()
            previewJob?.cancel()
            _previewHtml.value = null
            contentRevision = 0L

            _uiState.value = when (result) {
                is LoadResult.Success -> EditorUiState(
                    fileName = result.fileName,
                    type = result.type,
                    content = result.content,
                    savedContent = result.content,
                    isLoading = false,
                )

                is LoadResult.Failure -> EditorUiState(
                    fileName = result.fileName.orEmpty(),
                    type = result.type,
                    isLoading = false,
                    error = result.error,
                )
            }
        }
    }

    /**
     * Keeps the ViewModel in sync without rebuilding the whole editor on every keystroke.
     * [immediate] is used before changing tabs, saving or leaving the editor.
     */
    fun onContentChange(content: String, immediate: Boolean = false) {
        val state = _uiState.value
        if (!state.isEditable) return

        contentRevision += 1
        pendingSyncJob?.cancel()
        val hasPendingChanges = content != state.savedContent

        if (immediate) {
            _uiState.update { current ->
                current.copy(
                    content = content,
                    hasPendingChanges = hasPendingChanges,
                )
            }
            return
        }

        if (state.hasPendingChanges != hasPendingChanges) {
            _uiState.update { it.copy(hasPendingChanges = hasPendingChanges) }
        }

        // For very large files, don't keep the whole screen recomposing while typing:
        // the content is synchronized immediately when leaving the Edit tab, saving or exiting.
        if (content.length > LARGE_FILE_SYNC_THRESHOLD) return

        pendingSyncJob = viewModelScope.launch {
            delay(CONTENT_SYNC_DELAY_MS)
            _uiState.update { it.copy(content = content) }
        }
    }

    fun setIncognito(enabled: Boolean) {
        incognito = enabled
    }

    fun save(content: String) {
        val state = _uiState.value
        if (incognito) return
        if (!state.isEditable || state.isSaving) return
        if (content == state.savedContent && !state.hasPendingChanges) return

        pendingSyncJob?.cancel()
        val revisionAtSave = contentRevision
        val contentToSave = content

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val output = runCatching {
                        contentResolver.openOutputStream(uri, "wt")
                    }.getOrNull()
                        ?: contentResolver.openOutputStream(uri, "w")
                        ?: error("No output stream for $uri")

                    output.use { stream ->
                        stream.write(contentToSave.toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
            }

            _uiState.update { current ->
                if (!saved) {
                    current.copy(
                        isSaving = false,
                        message = EditorMessage.SAVE_ERROR,
                    )
                } else if (contentRevision == revisionAtSave) {
                    current.copy(
                        content = contentToSave,
                        savedContent = contentToSave,
                        hasPendingChanges = false,
                        isSaving = false,
                        message = EditorMessage.SAVED,
                    )
                } else {
                    // The user kept typing while the file was being written.
                    current.copy(
                        savedContent = contentToSave,
                        hasPendingChanges = true,
                        isSaving = false,
                        message = EditorMessage.SAVED,
                    )
                }
            }
        }
    }

    /**
     * Builds the final preview HTML for [content]. When the document loads local files and
     * the user has granted access to its folder, those files are served to the WebView
     * directly; otherwise the resources found next to the document are inlined and the UI
     * is asked to request folder access.
     */
    fun preparePreview(content: String) {
        android.util.Log.d("FREEHTML-PREP", "preparePreview() entry")
        val type = _uiState.value.type ?: return
        previewJob?.cancel()
        _previewHtml.value = null
        previewJob = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            if (grantedTreeUri == null) {
                val storedTree = (cachedFolderAccess
                    ?: preferences.folderAccess.first().also { cachedFolderAccess = it })
                    .get(uri.toString())
                    ?.let { runCatching { it.toUri() }.getOrNull() }
                if (storedTree != null) {
                    grantedTreeUri = storedTree
                    buildAssetResolver(storedTree)
                }
            }

            android.util.Log.d(
                "FREEHTML-PREP",
                "folderAccess check: ${System.currentTimeMillis() - t0}ms",
            )
            val requiresLocalFiles = type == DocumentType.HTML &&
                com.freehtml.app.util.needsLocalFiles(content)

            if (requiresLocalFiles && grantedTreeUri == null && !folderAccessDismissed) {
                _needsFolderAccess.value = true
            } else {
                _needsFolderAccess.value = false
            }

            val html = withContext(Dispatchers.Default) {
                val resolved = if (type == DocumentType.HTML && grantedTreeUri == null) {
                    runCatching {
                        resolveLocalResources(contentResolver, uri, content)
                    }.getOrDefault(content)
                } else {
                    content
                }
                previewHtmlFor(type, resolved)
            }
            android.util.Log.d(
                "FREEHTML-PREP",
                "done: ${System.currentTimeMillis() - t0}ms needsLocalFiles=$requiresLocalFiles",
            )
            _previewHtml.value = html
        }
    }

    private suspend fun buildAssetResolver(treeUri: Uri) {
        val files = withContext(Dispatchers.IO) {
            runCatching {
                com.freehtml.app.util.listTreeFiles(contentResolver, treeUri)
            }.getOrDefault(emptyMap())
        }
        if (files.isEmpty()) {
            _assetResolver.value = null
            return
        }
        _assetResolver.value = resolver@{ path ->
            val fileUri = files[path] ?: return@resolver null
            val bytes = com.freehtml.app.util.readBytes(contentResolver, fileUri)
                ?: return@resolver null
            val mimeType = com.freehtml.app.util.mimeTypeForExtension(
                path.substringAfterLast('.', ""),
            ) ?: "application/octet-stream"
            PreviewAsset(mimeType = mimeType, bytes = bytes)
        }
    }

    /** User dismissed the folder-access dialog for this file. */
    fun dismissFolderAccessPrompt() {
        folderAccessDismissed = true
        _needsFolderAccess.value = false
    }

    /** User granted access to the folder that contains this document. */
    fun onFolderGranted(treeUri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        grantedTreeUri = treeUri
        _needsFolderAccess.value = false
        viewModelScope.launch {
            preferences.setFolderAccess(uri.toString(), treeUri.toString())
            // Rebuild the resolver and refresh the preview so the page can load its files.
            buildAssetResolver(treeUri)
        }
        preparePreview(_uiState.value.content)
    }


    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private sealed interface LoadResult {
        data class Success(
            val fileName: String,
            val type: DocumentType,
            val content: String,
        ) : LoadResult

        data class Failure(
            val fileName: String?,
            val type: DocumentType?,
            val error: EditorError,
        ) : LoadResult
    }
}

private const val CONTENT_SYNC_DELAY_MS = 300L
private const val LARGE_FILE_SYNC_THRESHOLD = 100_000
