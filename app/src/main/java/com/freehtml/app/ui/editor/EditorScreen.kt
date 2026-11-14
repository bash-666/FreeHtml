package com.freehtml.app.ui.editor

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.freehtml.app.R
import com.freehtml.app.data.model.DocumentType
import com.freehtml.app.ui.components.EmptyStateCard
import com.freehtml.app.util.previewHtmlFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.hossain.highlight.ui.ExperimentalHighlightApi
import dev.hossain.highlight.ui.HighlightThemeProvider
import dev.hossain.highlight.ui.SyntaxHighlightedCode
import dev.hossain.highlight.ui.SyntaxHighlightedTextEditor
import dev.hossain.highlight.ui.rememberTomorrowLightTheme
import dev.hossain.highlight.ui.rememberTomorrowNightTheme

enum class EditorTab(@StringRes val labelRes: Int) {
    VIEW(R.string.editor_tab_view),
    EDIT(R.string.editor_tab_edit),
    PREVIEW(R.string.editor_tab_preview),
}

private fun tabsFor(type: DocumentType?): List<EditorTab> = when (type) {
    DocumentType.PDF -> emptyList()
    DocumentType.DOCX -> listOf(EditorTab.VIEW, EditorTab.PREVIEW)
    else -> EditorTab.entries
}

class EditorTextState {
    var value by mutableStateOf(TextFieldValue(""))
    var highlightOverride by mutableStateOf<Boolean?>(null)
    /** Incremented when the file is (re)loaded; used to reset windowed editing. */
    var epoch by mutableStateOf(0)
}

private const val HIGHLIGHT_CHAR_LIMIT = 50_000
private const val VIEW_HIGHLIGHT_CHAR_LIMIT = 50_000
private const val WINDOW_EDIT_CHAR_THRESHOLD = 50_000
private const val EDIT_WINDOW_LINES = 300
private const val EDIT_WINDOW_MARGIN = 50
private const val LARGE_FILE_CHAR_THRESHOLD = 20_000
private const val HIGHLIGHT_DEBOUNCE_MS = 250L
private const val LARGE_FILE_HIGHLIGHT_DEBOUNCE_MS = 600L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHighlightApi::class)
@Composable
fun EditorScreen(
    uri: String,
    onBack: () -> Unit,
    incognito: Boolean,
    defaultZoom: Float,
) {
    val context = LocalContext.current
    val factory = remember(uri, context) {
        viewModelFactory {
            initializer {
                EditorViewModel(
                    contentResolver = context.applicationContext.contentResolver,
                    uri = uri.toUri(),
                    preferences = com.freehtml.app.data.preferences.AppPreferences(
                        context.applicationContext,
                    ),
                )
            }
        }
    }
    val viewModel: EditorViewModel = viewModel(key = uri, factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.editor_saved)
    val saveErrorMessage = stringResource(R.string.editor_save_error)
    val copiedMessage = stringResource(R.string.editor_copied)
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by rememberSaveable(uri) { mutableIntStateOf(EditorTab.VIEW.ordinal) }
    var isFullscreen by rememberSaveable(uri) { mutableStateOf(false) }
    var showInspectDialog by rememberSaveable(uri) { mutableStateOf(false) }
    var previewReloadKey by rememberSaveable(uri) { mutableIntStateOf(0) }
    val editorText = remember(uri) { EditorTextState() }
    val previewHtml by viewModel.previewHtml.collectAsStateWithLifecycle()
    val needsFolderAccess by viewModel.needsFolderAccess.collectAsStateWithLifecycle()
    val assetResolver by viewModel.assetResolver.collectAsStateWithLifecycle()

    var sessionZoom by rememberSaveable(uri) { mutableFloatStateOf(-1f) }
    val editorZoom = if (sessionZoom > 0f) sessionZoom else defaultZoom

    var searchActive by rememberSaveable(uri) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(uri) { mutableStateOf("") }
    var findResults by remember(uri) { mutableStateOf<List<IntRange>>(emptyList()) }
    var findIndex by rememberSaveable(uri) { mutableIntStateOf(0) }
    var viewJumpLine by remember(uri) { mutableIntStateOf(-1) }
    var viewJumpTick by remember(uri) { mutableIntStateOf(0) }

    val searchText = if (selectedTab == EditorTab.EDIT.ordinal) {
        editorText.value.text
    } else {
        uiState.content
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            findResults = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        findResults = withContext(Dispatchers.Default) {
            findMatches(searchText, searchQuery)
        }
    }

    fun jumpToMatch(index: Int) {
        val range = findResults.getOrNull(index) ?: return
        findIndex = index
        if (selectedTab == EditorTab.EDIT.ordinal) {
            editorText.value = editorText.value.copy(
                selection = TextRange(range.first, range.last + 1),
            )
        } else {
            viewJumpLine = lineOfOffset(searchText, range.first)
            viewJumpTick += 1
        }
    }

    LaunchedEffect(findResults) {
        if (searchActive && findResults.isNotEmpty()) {
            jumpToMatch(0)
        }
    }

    var zoomMenuExpanded by rememberSaveable(uri) { mutableStateOf(false) }

    fun setZoom(value: Float) {
        sessionZoom = value.coerceIn(0.5f, 3.0f)
    }

    LaunchedEffect(incognito) {
        viewModel.setIncognito(incognito)
    }
    val documentType = uiState.type

    val visibleTabs = tabsFor(uiState.type)
    val selectedTabIndex = visibleTabs
        .indexOfFirst { it.ordinal == selectedTab }
        .coerceAtLeast(0)

    LaunchedEffect(uiState.type) {
        if (visibleTabs.none { it.ordinal == selectedTab }) {
            selectedTab = visibleTabs.firstOrNull()?.ordinal ?: EditorTab.VIEW.ordinal
        }
    }

    LaunchedEffect(uiState.isLoading, uiState.error) {
        if (!uiState.isLoading && uiState.error == null) {
            editorText.value = TextFieldValue(uiState.content)
            editorText.epoch += 1
            editorText.highlightOverride = null
        }
    }

    // Keep the latest text in the ViewModel when the editor leaves composition (rotation/back).
    DisposableEffect(editorText) {
        onDispose {
            viewModel.onContentChange(editorText.value.text, immediate = true)
        }
    }

    val previewWebView = if (
        uiState.type != null &&
        uiState.type != DocumentType.PDF
    ) {
        rememberPreviewWebView()
    } else {
        null
    }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    LaunchedEffect(selectedTab) {
        if (
            selectedTab == EditorTab.PREVIEW.ordinal &&
            !uiState.isLoading &&
            uiState.error == null &&
            documentType != DocumentType.PDF
        ) {
            viewModel.preparePreview(editorText.value.text)
        }
    }

    DisposableEffect(isFullscreen) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (isFullscreen) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    val stableOnContentChange: (String) -> Unit = remember(viewModel) {
        { text: String -> viewModel.onContentChange(text) }
    }
    val shareCode: () -> Unit = {
        val text = if (selectedTab == EditorTab.EDIT.ordinal) {
            editorText.value.text
        } else {
            uiState.content
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    // Shares the PDF file itself (not its text) with reading permission granted to the target app.
    val shareFile: () -> Unit = {
        val shareUri = uri.toUri()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            clipData = ClipData.newRawUri("pdf", shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    val copyCode: () -> Unit = {
        val text = if (selectedTab == EditorTab.EDIT.ordinal) {
            editorText.value.text
        } else {
            uiState.content
        }
        clipboard.setText(AnnotatedString(text))
        coroutineScope.launch { snackbarHostState.showSnackbar(copiedMessage) }
    }

    val reloadPreview: () -> Unit = {
        previewReloadKey += 1
        viewModel.preparePreview(editorText.value.text)
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            previewReloadKey += 1
            viewModel.onFolderGranted(treeUri)
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            if (message == EditorMessage.SAVED) savedMessage else saveErrorMessage,
        )
        viewModel.clearMessage()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (
                !isFullscreen &&
                uiState.isEditable &&
                uiState.isDirty &&
                selectedTab == EditorTab.PREVIEW.ordinal
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.save(editorText.value.text) },
                    icon = {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                            )
                        }
                    },
                    text = { Text(text = stringResource(R.string.editor_save)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.fileName.ifBlank {
                                stringResource(R.string.editor_title)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        uiState.type?.let { type ->
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.onContentChange(
                                editorText.value.text,
                                immediate = true,
                            )
                            onBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                        )
                    }
                },
                actions = {
                    val showCodeActions = selectedTab == EditorTab.VIEW.ordinal ||
                        selectedTab == EditorTab.EDIT.ordinal
                    if (showCodeActions) {
                        IconButton(onClick = { searchActive = !searchActive }) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.editor_action_search),
                            )
                        }
                        Box {
                            IconButton(onClick = { zoomMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.editor_action_zoom),
                                )
                            }
                            DropdownMenu(
                                expanded = zoomMenuExpanded,
                                onDismissRequest = { zoomMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { setZoom(editorZoom - 0.1f) }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Remove,
                                                    contentDescription = null,
                                                )
                                            }
                                            Text(
                                                text = "${(editorZoom * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelLarge,
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                            )
                                            IconButton(onClick = { setZoom(editorZoom + 0.1f) }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Add,
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    },
                                    onClick = { },
                                    enabled = false,
                                )
                            }
                        }
                    }
                    if (uiState.isEditable && selectedTab == EditorTab.EDIT.ordinal) {
                        IconButton(
                            onClick = {
                                val currentlyActive = editorText.highlightOverride
                                    ?: (editorText.value.text.length <= HIGHLIGHT_CHAR_LIMIT)
                                editorText.highlightOverride = !currentlyActive
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Highlight,
                                contentDescription = stringResource(
                                    R.string.editor_toggle_highlight,
                                ),
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.save(editorText.value.text) },
                        enabled = uiState.isEditable && uiState.isDirty && !uiState.isSaving,
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = stringResource(R.string.editor_save),
                            )
                        }
                    }
                },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
    ) { innerPadding ->
        if (isFullscreen && previewWebView != null && documentType != null) {
            FullscreenPreview(
                webView = previewWebView,
                html = previewHtml,
                assetResolver = assetResolver,
                javaScriptEnabled = documentType == DocumentType.HTML ||
                    documentType == DocumentType.JS,
                forceReloadKey = previewReloadKey,
                onExit = { isFullscreen = false },
            )
        } else {
            EditorContent(
                uri = uri,
                uiState = uiState,
                selectedTab = selectedTab,
                visibleTabs = visibleTabs,
                selectedTabIndex = selectedTabIndex,
                editorText = editorText,
                previewWebView = previewWebView,
                previewHtml = previewHtml,
                assetResolver = assetResolver,
                previewReloadKey = previewReloadKey,
                zoom = editorZoom,
                searchActive = searchActive,
                searchQuery = searchQuery,
                findResults = findResults,
                findIndex = findIndex,
                viewJumpLine = viewJumpLine,
                viewJumpTick = viewJumpTick,
                onSearchQueryChange = { searchQuery = it },
                onSearchPrev = {
                    if (findResults.isNotEmpty()) {
                        jumpToMatch(
                            (findIndex - 1 + findResults.size) % findResults.size,
                        )
                    }
                },
                onSearchNext = {
                    if (findResults.isNotEmpty()) {
                        jumpToMatch((findIndex + 1) % findResults.size)
                    }
                },
                onSearchClose = { searchActive = false },
                onTabSelected = { tab ->
                    if (
                        selectedTab == EditorTab.EDIT.ordinal &&
                        tab.ordinal != EditorTab.EDIT.ordinal
                    ) {
                        viewModel.onContentChange(
                            editorText.value.text,
                            immediate = true,
                        )
                    }
                    selectedTab = tab.ordinal
                },
                onCopy = copyCode,
                onShare = shareCode,
                onShareFile = shareFile,
                onSave = { viewModel.save(editorText.value.text) },
                onInspect = { showInspectDialog = true },
                onReload = reloadPreview,
                onFullscreen = { isFullscreen = true },
                onContentChange = stableOnContentChange,
                onLoadFile = viewModel::loadFile,
                saveAllowed = !incognito,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    if (needsFolderAccess && !isFullscreen) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFolderAccessPrompt,
            title = {
                Text(text = stringResource(R.string.folder_access_title))
            },
            text = {
                Text(text = stringResource(R.string.folder_access_message))
            },
            confirmButton = {
                TextButton(onClick = { folderPicker.launch(null) }) {
                    Text(text = stringResource(R.string.folder_access_select))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissFolderAccessPrompt) {
                    Text(text = stringResource(R.string.folder_access_later))
                }
            },
        )
    }

    if (showInspectDialog) {
        val inspectHtml = previewHtml
            ?: previewHtmlFor(documentType, uiState.content)
        AlertDialog(
            onDismissRequest = { showInspectDialog = false },
            title = { Text(text = stringResource(R.string.editor_inspect_title)) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SelectionContainer {
                        Text(
                            text = inspectHtml,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInspectDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Creates the syntax-highlight engine only where it is actually used (View/Edit tabs),
 * so the Preview tab never pays for a second hidden WebView.
 */
@OptIn(ExperimentalHighlightApi::class)
@Composable
internal fun HighlightScope(
    content: @Composable () -> Unit,
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    HighlightThemeProvider(
        darkTheme = darkTheme,
        lightHighlightTheme = rememberTomorrowLightTheme(),
        darkHighlightTheme = rememberTomorrowNightTheme(),
    ) {
        content()
    }
}

@Composable
internal fun CodeViewer(
    code: String,
    language: String,
    zoom: Float,
    searchHighlightRange: IntRange?,
    jumpLine: Int,
    jumpTick: Int,
) {
    if (code.isBlank()) {
        EmptyPane(
            icon = Icons.Rounded.Description,
            title = stringResource(R.string.editor_empty_title),
            message = stringResource(R.string.editor_empty_message),
        )
        return
    }

    val scrollState = rememberScrollState()
    val baseStyle = MaterialTheme.typography.bodySmall
    val scaledStyle = baseStyle.copy(
        fontSize = (baseStyle.fontSize ?: 12.sp) * zoom,
        lineHeight = (baseStyle.lineHeight ?: 16.sp) * zoom,
    )

    val density = LocalDensity.current
    LaunchedEffect(jumpTick) {
        if (jumpLine < 0) return@LaunchedEffect
        val lineHeightPx = with(density) { scaledStyle.lineHeight.toPx() }
        val target = (jumpLine * lineHeightPx).toInt().coerceAtLeast(0)
        scrollState.scrollTo(target)
    }

    // Highlighting large files blocks the main thread (ANR). Above the threshold we
    // show plain monospace text instead of running the highlight engine.
    if (code.length > VIEW_HIGHLIGHT_CHAR_LIMIT) {
        val annotated = remember(code, searchHighlightRange) {
            if (searchHighlightRange != null &&
                searchHighlightRange.first >= 0 &&
                searchHighlightRange.last <= code.length
            ) {
                buildAnnotatedString {
                    append(code)
                    addStyle(
                        SpanStyle(
                            background = Color(0x66FFFF00),
                        ),
                        searchHighlightRange.first,
                        searchHighlightRange.last + 1,
                    )
                }
            } else {
                buildAnnotatedString { append(code) }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            SelectionContainer {
                Text(
                    text = annotated,
                    fontFamily = FontFamily.Monospace,
                    style = scaledStyle,
                )
            }
        }
        return
    }

    HighlightScope {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            SyntaxHighlightedCode(
                code = code,
                language = language,
                modifier = Modifier.fillMaxWidth(),
                style = dev.hossain.highlight.ui.CodeBlockStyle(
                    textStyle = scaledStyle.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                ),
                showLineNumbers = true,
            )
        }
    }
}

@OptIn(ExperimentalHighlightApi::class)
@Composable
internal fun CodeEditor(
    editorText: EditorTextState,
    onValueChange: (String) -> Unit,
    language: String,
    zoom: Float,
) {
    val value = editorText.value
    val highlightActive = editorText.highlightOverride
        ?: (value.text.length <= HIGHLIGHT_CHAR_LIMIT)
    val handleValueChange: (TextFieldValue) -> Unit = remember(editorText, onValueChange) {
        { newValue: TextFieldValue ->
            editorText.value = newValue
            onValueChange(newValue.text)
        }
    }

    if (!highlightActive) {
        if (value.text.length > WINDOW_EDIT_CHAR_THRESHOLD) {
            WindowedCodeEditor(
                editorText = editorText,
                onValueChange = onValueChange,
                zoom = zoom,
            )
        } else {
            PlainCodeEditor(
                value = value,
                onValueChange = handleValueChange,
                zoom = zoom,
            )
        }
        return
    }

    val shape = remember { RoundedCornerShape(16.dp) }
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val debounceMs = if (value.text.length > LARGE_FILE_CHAR_THRESHOLD) {
        LARGE_FILE_HIGHLIGHT_DEBOUNCE_MS
    } else {
        HIGHLIGHT_DEBOUNCE_MS
    }

    HighlightScope {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            SyntaxHighlightedTextEditor(
                value = value,
                onValueChange = handleValueChange,
                language = language,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                shape = shape,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = (MaterialTheme.typography.bodySmall.fontSize ?: 12.sp) * zoom,
                    lineHeight = (MaterialTheme.typography.bodySmall.lineHeight ?: 16.sp) * zoom,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                debounceMs = debounceMs,
                horizontalScrollState = horizontalScroll,
                verticalScrollState = verticalScroll,
            )
        }
    }
}

@Composable
private fun PlainCodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    zoom: Float,
) {
    // Plain editor: no card/background container, same look as the plain View text.
    // Lines are not soft-wrapped (horizontal scroll instead), which avoids the very
    // expensive line-breaking work on huge minified lines.
    val horizontalScroll = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScroll),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(NO_WRAP_WIDTH),
                textStyle = zoomedCodeStyle(zoom),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private val NO_WRAP_WIDTH = 10000.dp

/** Holder for the editable window (mutable fields; the visible value is snapshot state). */
private class EditWindow(
    startLine: Int,
    charStart: Int,
    charEnd: Int,
    value: TextFieldValue,
) {
    var startLine = startLine
    var charStart = charStart
    var charEnd = charEnd
    var value = value
}

/**
 * Editor for very large files: only a window of [EDIT_WINDOW_LINES] lines around the cursor
 * is editable, so typing never re-lays-out the whole document. When the cursor approaches a
 * window edge, the window shifts to keep it centered. Lines are not soft-wrapped.
 */
@Composable
private fun WindowedCodeEditor(
    editorText: EditorTextState,
    onValueChange: (String) -> Unit,
    zoom: Float,
) {
    val horizontalScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val fullText = editorText.value.text
    val initialCursor = editorText.value.selection.end.coerceIn(0, fullText.length)

    var window by remember(editorText, editorText.epoch) {
        val cursorLine = lineOfOffset(fullText, initialCursor)
        val startLine = (cursorLine - EDIT_WINDOW_LINES / 2).coerceAtLeast(0)
        val charStart = offsetOfLine(fullText, startLine)
        val charEnd = offsetOfLine(fullText, startLine + EDIT_WINDOW_LINES)
        val windowText = fullText.substring(charStart, charEnd)
        val localCursor = (initialCursor - charStart).coerceIn(0, windowText.length)
        mutableStateOf(
            EditWindow(
                startLine = startLine,
                charStart = charStart,
                charEnd = charEnd,
                value = TextFieldValue(windowText, TextRange(localCursor)),
            ),
        )
    }

    val handleWindowChange: (TextFieldValue) -> Unit =
        remember(editorText, window, onValueChange) {
            { newValue ->
                val current = editorText.value.text
                val oldWindowLength = window.charEnd - window.charStart
                val newFull = current.replaceRange(
                    window.charStart,
                    window.charEnd,
                    newValue.text,
                )
                val cursorGlobal = window.charStart +
                    newValue.selection.end.coerceIn(0, newValue.text.length)
                editorText.value = editorText.value.copy(
                    text = newFull,
                    selection = TextRange(cursorGlobal),
                )
                onValueChange(newFull)

                window.charEnd += newValue.text.length - oldWindowLength
                val cursorLineInWindow = lineOfOffset(
                    newValue.text,
                    newValue.selection.end.coerceIn(0, newValue.text.length),
                )
                val windowLineCount = lineOfOffset(newValue.text, newValue.text.length)
                if (
                    cursorLineInWindow < EDIT_WINDOW_MARGIN ||
                    cursorLineInWindow > windowLineCount - EDIT_WINDOW_MARGIN
                ) {
                    val targetLine = lineOfOffset(newFull, cursorGlobal)
                    val totalLines = lineOfOffset(newFull, newFull.length)
                    val newStartLine = (targetLine - EDIT_WINDOW_LINES / 2)
                        .coerceIn(0, maxOf(0, totalLines - EDIT_WINDOW_LINES))
                    val newCharStart = offsetOfLine(newFull, newStartLine)
                    val newCharEnd = offsetOfLine(newFull, newStartLine + EDIT_WINDOW_LINES)
                    val newWindowText = newFull.substring(newCharStart, newCharEnd)
                    val newLocalCursor = (cursorGlobal - newCharStart)
                        .coerceIn(0, newWindowText.length)
                    window = EditWindow(
                        startLine = newStartLine,
                        charStart = newCharStart,
                        charEnd = newCharEnd,
                        value = TextFieldValue(newWindowText, TextRange(newLocalCursor)),
                    )
                    scope.launch {
                        horizontalScroll.scrollTo(0)
                    }
                } else {
                    window.value = newValue
                }
            }
        }

    // External cursor moves (e.g. search navigation) shift the window to contain the cursor.
    LaunchedEffect(editorText.value.selection) {
        val cursor = editorText.value.selection.end
        val current = editorText.value.text
        if (cursor >= window.charStart && cursor <= window.charEnd) return@LaunchedEffect
        val targetLine = lineOfOffset(current, cursor)
        val totalLines = lineOfOffset(current, current.length)
        val newStartLine = (targetLine - EDIT_WINDOW_LINES / 2)
            .coerceIn(0, maxOf(0, totalLines - EDIT_WINDOW_LINES))
        val newCharStart = offsetOfLine(current, newStartLine)
        val newCharEnd = offsetOfLine(current, newStartLine + EDIT_WINDOW_LINES)
        val newWindowText = current.substring(newCharStart, newCharEnd)
        val newLocalCursor = (cursor - newCharStart).coerceIn(0, newWindowText.length)
        window = EditWindow(
            startLine = newStartLine,
            charStart = newCharStart,
            charEnd = newCharEnd,
            value = TextFieldValue(newWindowText, TextRange(newLocalCursor)),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScroll),
        ) {
            BasicTextField(
                value = window.value,
                onValueChange = handleWindowChange,
                modifier = Modifier.width(NO_WRAP_WIDTH),
                textStyle = zoomedCodeStyle(zoom),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun zoomedCodeStyle(zoom: Float): androidx.compose.ui.text.TextStyle {
    val base = MaterialTheme.typography.bodySmall
    return base.copy(
        fontSize = (base.fontSize ?: 12.sp) * zoom,
        lineHeight = (base.lineHeight ?: 16.sp) * zoom,
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = FontFamily.Monospace,
    )
}

internal fun findMatches(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val matches = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val index = text.indexOf(query, from, ignoreCase = true)
        if (index < 0) break
        matches += index until (index + query.length)
        from = index + query.length
    }
    return matches
}

private fun lineOfOffset(text: String, offset: Int): Int {
    var line = 0
    var index = 0
    val end = offset.coerceIn(0, text.length)
    while (index < end) {
        if (text[index] == '\n') line++
        index++
    }
    return line
}

private fun offsetOfLine(text: String, line: Int): Int {
    if (line <= 0) return 0
    var currentLine = 0
    var index = 0
    while (index < text.length) {
        if (text[index] == '\n') {
            currentLine++
            if (currentLine == line) return index + 1
        }
        index++
    }
    return text.length
}

@Composable
internal fun EditorErrorPane(
    error: EditorError,
    onRetry: () -> Unit,
) {
    val title = stringResource(R.string.editor_error_title)
    val message = stringResource(
        when (error) {
            EditorError.UNSUPPORTED -> R.string.editor_error_unsupported
            EditorError.READ_FAILED -> R.string.editor_error_read
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (error == EditorError.READ_FAILED) {
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(R.string.editor_retry),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyPane(
    icon: ImageVector,
    title: String,
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        EmptyStateCard(
            icon = icon,
            title = title,
            message = message,
        )
    }
}
