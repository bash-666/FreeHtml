package com.freehtml.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.freehtml.app.BuildConfig
import com.freehtml.app.R
import com.freehtml.app.data.model.LanguageMode
import com.freehtml.app.data.model.ThemeMode

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    incognito: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
    editorZoom: Float,
    onEditorZoomChange: (Float) -> Unit,
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
    recentFileCount: Int,
    onClearHistory: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val linkError = stringResource(R.string.settings_link_error)
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        item {
            SettingsSectionHeader(text = stringResource(R.string.settings_section_general))
        }
        item {
            HistoryCard(
                recentFileCount = recentFileCount,
                onClearRequest = { showClearDialog = true },
            )
        }
        item {
            IncognitoCard(
                incognito = incognito,
                onIncognitoChange = onIncognitoChange,
            )
        }
        item {
            LanguageCard(
                languageMode = languageMode,
                onLanguageModeChange = onLanguageModeChange,
            )
        }

        item {
            SettingsSectionHeader(text = stringResource(R.string.settings_section_appearance))
        }
        item {
            ThemeCard(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
        item {
            ZoomDefaultCard(
                editorZoom = editorZoom,
                onEditorZoomChange = onEditorZoomChange,
            )
        }

        item {
            SettingsSectionHeader(text = stringResource(R.string.settings_section_about))
        }
        item {
            AboutCard(
                onOpenGithub = {
                    openUrl(context, GITHUB_URL) {
                        onShowMessage(linkError)
                    }
                },
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                )
            },
            title = {
                Text(text = stringResource(R.string.settings_clear_history_title))
            },
            text = {
                Text(text = stringResource(R.string.settings_clear_history_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                    },
                ) {
                    Text(text = stringResource(R.string.settings_clear_history_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ThemeCard(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsIcon(
                    icon = if (themeMode == ThemeMode.LIGHT) {
                        Icons.Rounded.LightMode
                    } else {
                        Icons.Rounded.DarkMode
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_theme_force_light),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_theme_current,
                            themeModeLabel(themeMode),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = themeMode == ThemeMode.LIGHT,
                    onCheckedChange = { checked ->
                        onThemeModeChange(
                            if (checked) ThemeMode.LIGHT else ThemeMode.DARK,
                        )
                    },
                )
            }

            Text(
                text = stringResource(R.string.settings_theme_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            TextButton(
                onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                enabled = themeMode != ThemeMode.SYSTEM,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = stringResource(R.string.settings_theme_use_system))
            }
        }
    }
}

@Composable
private fun HistoryCard(
    recentFileCount: Int,
    onClearRequest: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIcon(icon = Icons.Rounded.History)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_recent_history),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (recentFileCount == 0) {
                        stringResource(R.string.settings_no_recent_files)
                    } else {
                        pluralStringResource(
                            R.plurals.settings_recent_files_count,
                            recentFileCount,
                            recentFileCount,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onClearRequest,
                enabled = recentFileCount > 0,
            ) {
                Text(text = stringResource(R.string.settings_clear_history))
            }
        }
    }
}

@Composable
private fun AboutCard(onOpenGithub: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Code,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(R.string.settings_open_source_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.settings_open_source_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onOpenGithub,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.settings_github))
            }
            Text(
                text = stringResource(
                    R.string.settings_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                ),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun themeModeLabel(themeMode: ThemeMode): String = when (themeMode) {
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
}

private fun openUrl(context: Context, url: String, onError: () -> Unit) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }.onFailure {
        onError()
    }
}

// TODO: Sustituir por la URL pública real del repositorio cuando esté disponible.
private const val GITHUB_URL = "https://github.com/bash-666/FreeHtml"

@Composable
private fun IncognitoCard(
    incognito: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIcon(icon = Icons.Rounded.VisibilityOff)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_incognito_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_incognito_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = incognito,
                onCheckedChange = onIncognitoChange,
            )
        }
    }
}

@Composable
private fun LanguageCard(
    languageMode: LanguageMode,
    onLanguageModeChange: (LanguageMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsIcon(icon = Icons.Rounded.Language)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_language_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = languageModeLabel(languageMode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = true }) {
                    Text(text = stringResource(R.string.settings_change))
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                LanguageMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(text = languageModeLabel(mode)) },
                        onClick = {
                            expanded = false
                            onLanguageModeChange(mode)
                        },
                        trailingIcon = if (mode == languageMode) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

private val ZOOM_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)

@Composable
private fun ZoomDefaultCard(
    editorZoom: Float,
    onEditorZoomChange: (Float) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsIcon(icon = Icons.Rounded.ZoomIn)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_default_zoom_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_default_zoom_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = true }) {
                    Text(text = "${(editorZoom * 100).toInt()}%")
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                ZOOM_PRESETS.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(text = "${(preset * 100).toInt()}%") },
                        onClick = {
                            expanded = false
                            onEditorZoomChange(preset)
                        },
                        trailingIcon = if (preset == editorZoom) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun languageModeLabel(mode: LanguageMode): String = when (mode) {
    LanguageMode.SYSTEM -> stringResource(R.string.settings_language_system)
    LanguageMode.SPANISH -> stringResource(R.string.settings_language_spanish)
    LanguageMode.ENGLISH -> stringResource(R.string.settings_language_english)
    LanguageMode.FRENCH -> stringResource(R.string.settings_language_french)
    LanguageMode.PORTUGUESE -> stringResource(R.string.settings_language_portuguese)
}
