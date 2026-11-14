package com.freehtml.app.ui.editor

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freehtml.app.R
import com.freehtml.app.data.model.DocumentType

/**
 * Body of the editor: tab row, contextual action bar below the tabs and the
 * content for the active tab. Kept apart from [EditorScreen] so it recomposes
 * independently of the Scaffold chrome.
 */
@Composable
internal fun EditorContent(
    uri: String,
    uiState: EditorUiState,
    selectedTab: Int,
    visibleTabs: List<EditorTab>,
    selectedTabIndex: Int,
    editorText: EditorTextState,
    previewWebView: WebView?,
    previewHtml: String?,
    assetResolver: ((String) -> PreviewAsset?)?,
    previewReloadKey: Int,
    zoom: Float,
    searchActive: Boolean,
    searchQuery: String,
    findResults: List<IntRange>,
    findIndex: Int,
    viewJumpLine: Int,
    viewJumpTick: Int,
    onSearchQueryChange: (String) -> Unit,
    onSearchPrev: () -> Unit,
    onSearchNext: () -> Unit,
    onSearchClose: () -> Unit,
    onTabSelected: (EditorTab) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onShareFile: () -> Unit,
    onSave: () -> Unit,
    onInspect: () -> Unit,
    onReload: () -> Unit,
    onFullscreen: () -> Unit,
    onContentChange: (String) -> Unit,
    onLoadFile: () -> Unit,
    saveAllowed: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (!uiState.isLoading && uiState.error == null) {
            if (uiState.type == DocumentType.PDF) {
                // PDFs have no tabs; offer only the file share action.
                EditorActionBar(
                    tabOrdinal = -1,
                    pdfMode = true,
                    isEditable = false,
                    isDirty = false,
                    isSaving = false,
                    onCopy = onCopy,
                    onShare = onShareFile,
                    onSave = onSave,
                    onInspect = onInspect,
                    onReload = onReload,
                    onFullscreen = onFullscreen,
                )
            } else if (visibleTabs.isNotEmpty()) {
                PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                visibleTabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab.ordinal,
                        onClick = { onTabSelected(tab) },
                        text = { Text(text = stringResource(tab.labelRes)) },
                    )
                }
            }

            EditorActionBar(
                tabOrdinal = selectedTab,
                isEditable = uiState.isEditable && saveAllowed,
                isDirty = uiState.isDirty,
                isSaving = uiState.isSaving,
                onCopy = onCopy,
                onShare = onShare,
                onSave = onSave,
                onInspect = onInspect,
                onReload = onReload,
                onFullscreen = onFullscreen,
            )

            FileInfoStrip(
                text = if (selectedTab == EditorTab.EDIT.ordinal) {
                    editorText.value.text
                } else {
                    uiState.content
                },
            )

            if (searchActive) {
                SearchBar(
                    query = searchQuery,
                    resultCount = findResults.size,
                    currentIndex = findIndex,
                    onQueryChange = onSearchQueryChange,
                    onPrev = onSearchPrev,
                    onNext = onSearchNext,
                    onClose = onSearchClose,
                )
            }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val documentType = uiState.type
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null -> {
                    EditorErrorPane(
                        error = uiState.error,
                        onRetry = onLoadFile,
                    )
                }

                documentType == DocumentType.PDF -> {
                    PdfPreview(
                        uri = uri,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                selectedTab == EditorTab.VIEW.ordinal -> {
                    CodeViewer(
                        code = uiState.content,
                        language = uiState.language,
                        zoom = zoom,
                        searchHighlightRange = if (searchActive) {
                            findResults.getOrNull(findIndex)
                        } else {
                            null
                        },
                        jumpLine = viewJumpLine,
                        jumpTick = viewJumpTick,
                    )
                }

                selectedTab == EditorTab.EDIT.ordinal && uiState.isEditable -> {
                    CodeEditor(
                        editorText = editorText,
                        onValueChange = onContentChange,
                        language = uiState.language,
                        zoom = zoom,
                    )
                }

                selectedTab == EditorTab.PREVIEW.ordinal &&
                    documentType != null &&
                    previewWebView != null -> {
                    if (previewHtml == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        WebViewPreview(
                            webView = previewWebView,
                            html = previewHtml,
                            javaScriptEnabled = documentType == DocumentType.HTML ||
                                documentType == DocumentType.JS,
                            modifier = Modifier.fillMaxSize(),
                            forceReloadKey = previewReloadKey,
                            assetResolver = assetResolver,
                        )
                    }
                }

                else -> {
                    CodeViewer(
                        code = uiState.content,
                        language = uiState.language,
                        zoom = zoom,
                        searchHighlightRange = null,
                        jumpLine = -1,
                        jumpTick = 0,
                    )
                }
            }
        }
    }
}


/** Small gray strip with file stats shown above the code. */
@Composable
private fun FileInfoStrip(text: String) {
    val stats = remember(text) { fileStatsFor(text) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(
                R.string.editor_info_strip,
                stats.lines,
                stats.weight,
                stats.elements,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Expandable in-code search bar. */
@Composable
private fun SearchBar(
    query: String,
    resultCount: Int,
    currentIndex: Int,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(text = stringResource(R.string.editor_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
        )
        Text(
            text = if (resultCount == 0) {
                "0/0"
            } else {
                "${currentIndex + 1}/$resultCount"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onPrev, enabled = resultCount > 0) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
            )
        }
        IconButton(onClick = onNext, enabled = resultCount > 0) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
            )
        }
    }
}

data class FileStats(
    val lines: Int,
    val weight: String,
    val elements: Int,
)

internal fun fileStatsFor(text: String): FileStats {
    val bytes = text.encodeToByteArray().size
    val weight = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    }
    val lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
    val elements = HTML_TAG_REGEX.findAll(text).count()
    return FileStats(lines = lines, weight = weight, elements = elements)
}

private val HTML_TAG_REGEX = Regex("<[a-zA-Z][^>]*>")

@Composable
internal fun EditorActionBar(
    tabOrdinal: Int,
    isEditable: Boolean,
    isDirty: Boolean,
    isSaving: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onInspect: () -> Unit,
    onReload: () -> Unit,
    onFullscreen: () -> Unit,
    pdfMode: Boolean = false,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // In narrow (portrait) screens the labels overflow, so fall back to icon-only buttons.
        val compact = maxWidth < COMPACT_ACTIONS_WIDTH
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp),
        ) {
            if (pdfMode) {
                EditorActionButton(
                    icon = Icons.Rounded.Share,
                    labelRes = R.string.editor_action_share,
                    compact = compact,
                    onClick = onShare,
                )
            } else if (tabOrdinal == EditorTab.PREVIEW.ordinal) {
                EditorActionButton(
                    icon = Icons.Rounded.Code,
                    labelRes = R.string.editor_action_inspect,
                    compact = compact,
                    onClick = onInspect,
                )
                EditorActionButton(
                    icon = Icons.Rounded.Refresh,
                    labelRes = R.string.editor_action_reload,
                    compact = compact,
                    onClick = onReload,
                )
                EditorActionButton(
                    icon = Icons.Rounded.Share,
                    labelRes = R.string.editor_action_share,
                    compact = compact,
                    onClick = onShare,
                )
                EditorActionButton(
                    icon = Icons.Rounded.Fullscreen,
                    labelRes = R.string.editor_action_fullscreen,
                    shortLabelRes = R.string.editor_action_fullscreen_short,
                    compact = compact,
                    onClick = onFullscreen,
                )
            } else {
                EditorActionButton(
                    icon = Icons.Rounded.ContentCopy,
                    labelRes = R.string.editor_action_copy,
                    compact = compact,
                    onClick = onCopy,
                )
                EditorActionButton(
                    icon = Icons.Rounded.Share,
                    labelRes = R.string.editor_action_share,
                    compact = compact,
                    onClick = onShare,
                )
                EditorActionButton(
                    icon = Icons.Rounded.Save,
                    labelRes = R.string.editor_save,
                    compact = compact,
                    onClick = onSave,
                    enabled = isEditable && isDirty && !isSaving,
                )
            }
        }
    }
}

private val COMPACT_ACTIONS_WIDTH = 420.dp

@Composable
private fun EditorActionButton(
    icon: ImageVector,
    labelRes: Int,
    compact: Boolean,
    onClick: () -> Unit,
    shortLabelRes: Int = labelRes,
    enabled: Boolean = true,
) {
    if (compact) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(labelRes),
                modifier = Modifier.size(20.dp),
            )
        }
        return
    }

    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp,
            vertical = 4.dp,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(shortLabelRes),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 6.dp),
            maxLines = 1,
        )
    }
}

/** Full-screen preview: hides every piece of chrome except a floating exit button. */
@Composable
internal fun FullscreenPreview(
    webView: WebView,
    html: String?,
    assetResolver: ((String) -> PreviewAsset?)?,
    javaScriptEnabled: Boolean,
    forceReloadKey: Int,
    onExit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (html == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            WebViewPreview(
                webView = webView,
                html = html,
                javaScriptEnabled = javaScriptEnabled,
                modifier = Modifier.fillMaxSize(),
                forceReloadKey = forceReloadKey,
                assetResolver = assetResolver,
            )
        }

        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.editor_exit_fullscreen),
            )
        }
    }
}
