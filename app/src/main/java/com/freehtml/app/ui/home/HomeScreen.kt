package com.freehtml.app.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freehtml.app.R
import com.freehtml.app.data.model.DocumentType
import com.freehtml.app.data.model.RecentFile
import com.freehtml.app.data.model.LanguageMode
import com.freehtml.app.data.model.ThemeMode
import com.freehtml.app.ui.editor.EditorScreen
import com.freehtml.app.ui.history.HistoryScreen
import com.freehtml.app.ui.navigation.EditorDestination
import com.freehtml.app.ui.navigation.MainDestination
import com.freehtml.app.ui.settings.SettingsScreen
import com.freehtml.app.util.ensureExtension
import com.freehtml.app.util.persistReadPermission
import com.freehtml.app.util.persistReadWritePermission
import com.freehtml.app.util.queryDisplayName
import com.freehtml.app.util.supportedMimeTypes
import com.freehtml.app.util.writeInitialContent
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    incognito: Boolean,
    editorZoom: Float,
    languageMode: LanguageMode,
    onIncognitoChange: (Boolean) -> Unit,
    onEditorZoomChange: (Float) -> Unit,
    onLanguageModeChange: (LanguageMode) -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isEditorOpen = currentDestination?.route == EditorDestination.ROUTE
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(context),
    )
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewFileDialog by rememberSaveable { mutableStateOf(false) }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    fun openRecentFile(file: RecentFile) {
        navController.navigate(EditorDestination.createRoute(file.uri))
    }

    fun recordRecentFile(file: RecentFile) {
        if (!incognito) {
            homeViewModel.onFileOpened(file)
        }
    }

    fun resolveDocument(uri: Uri): Pair<String, DocumentType?> {
        val displayName = context.queryDisplayName(uri)
            ?: uri.lastPathSegment
            ?: "archivo"
        val type = DocumentType.fromFileName(displayName)
            ?: DocumentType.fromMimeType(context.contentResolver.getType(uri))
        return displayName to type
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val (displayName, type) = resolveDocument(uri)
            if (type == null) {
                showMessage(resources.getString(R.string.unsupported_file, displayName))
            } else {
                if (type.isEditable) {
                    context.persistReadWritePermission(uri)
                } else {
                    context.persistReadPermission(uri)
                }
                recordRecentFile(
                    RecentFile(
                        uri = uri.toString(),
                        displayName = displayName,
                        type = type,
                    ),
                )
                navController.navigate(EditorDestination.createRoute(uri.toString()))
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val (displayName, type) = resolveDocument(uri)
                if (type == null) {
                    showMessage(resources.getString(R.string.unsupported_file, displayName))
                } else {
                    runCatching {
                        context.persistReadWritePermission(uri)
                        context.contentResolver.writeInitialContent(uri, type)
                    }.onSuccess {
                        homeViewModel.onFileOpened(
                            RecentFile(
                                uri = uri.toString(),
                                displayName = displayName,
                                type = type,
                            ),
                        )
                        navController.navigate(EditorDestination.createRoute(uri.toString()))
                    }.onFailure {
                        showMessage(resources.getString(R.string.new_file_error))
                    }
                }
            }
        }
    }

    fun launchCreateDocument(type: DocumentType, fileName: String) {
        val safeName = ensureExtension(fileName, type)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = type.mimeType
            putExtra(Intent.EXTRA_TITLE, safeName)
        }
        createDocumentLauncher.launch(intent)
    }

    if (showNewFileDialog) {
        NewFileDialog(
            onDismiss = { showNewFileDialog = false },
            onCreate = { type, fileName ->
                showNewFileDialog = false
                launchCreateDocument(type, fileName)
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (isEditorOpen) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!isEditorOpen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    MainDestination.entries.forEach { destination ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == destination.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(text = stringResource(destination.labelRes))
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainDestination.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(MainDestination.HOME.route) {
                HomeContent(
                    recentFiles = uiState.recentFiles,
                    onLoadFile = { openDocumentLauncher.launch(supportedMimeTypes) },
                    onNewFile = { showNewFileDialog = true },
                    onRecentFileClick = ::openRecentFile,
                )
            }

            composable(MainDestination.HISTORY.route) {
                HistoryScreen(
                    recentFiles = uiState.recentFiles,
                    onRecentFileClick = ::openRecentFile,
                    onDeleteRecentFiles = { uris ->
                        homeViewModel.removeRecentFiles(uris)
                        showMessage(resources.getString(R.string.history_deleted))
                    },
                )
            }

            composable(MainDestination.SETTINGS.route) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    incognito = incognito,
                    onIncognitoChange = onIncognitoChange,
                    editorZoom = editorZoom,
                    onEditorZoomChange = onEditorZoomChange,
                    languageMode = languageMode,
                    onLanguageModeChange = onLanguageModeChange,
                    recentFileCount = uiState.recentFiles.size,
                    onClearHistory = {
                        homeViewModel.clearRecentFiles()
                        showMessage(resources.getString(R.string.history_cleared))
                    },
                    onShowMessage = { message -> showMessage(message) },
                )
            }

            composable(
                route = EditorDestination.ROUTE,
                arguments = listOf(
                    navArgument(EditorDestination.ARG_URI) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val uri = backStackEntry.arguments
                    ?.getString(EditorDestination.ARG_URI)
                    .orEmpty()
                EditorScreen(
                    uri = uri,
                    onBack = { navController.popBackStack() },
                    incognito = incognito,
                    defaultZoom = editorZoom,
                )
            }
        }
    }
}
