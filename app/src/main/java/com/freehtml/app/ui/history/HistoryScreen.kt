package com.freehtml.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freehtml.app.R
import com.freehtml.app.data.model.RecentFile
import com.freehtml.app.ui.components.EmptyStateCard
import com.freehtml.app.ui.components.RecentFileCard

private val SelectedUrisSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

@Composable
fun HistoryScreen(
    recentFiles: List<RecentFile>,
    onRecentFileClick: (RecentFile) -> Unit,
    onDeleteRecentFiles: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedUris by rememberSaveable(stateSaver = SelectedUrisSaver) {
        mutableStateOf(emptySet())
    }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val allUris = remember(recentFiles) {
        recentFiles.mapTo(mutableSetOf()) { it.uri }
    }
    val allSelected = recentFiles.isNotEmpty() && selectedUris.containsAll(allUris)

    fun exitSelection() {
        selectionMode = false
        selectedUris = emptySet()
    }

    LaunchedEffect(recentFiles) {
        val validUris = selectedUris.intersect(allUris)
        if (validUris != selectedUris) {
            selectedUris = validUris
        }
        if (recentFiles.isEmpty() && selectionMode) {
            exitSelection()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (recentFiles.isNotEmpty()) {
                TextButton(
                    onClick = {
                        if (selectionMode) {
                            exitSelection()
                        } else {
                            selectionMode = true
                        }
                    },
                ) {
                    Text(
                        text = stringResource(
                            if (selectionMode) {
                                R.string.history_cancel_selection
                            } else {
                                R.string.history_select
                            },
                        ),
                    )
                }
            }
        }

        if (selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = {
                        selectedUris = if (allSelected) emptySet() else allUris
                    },
                ) {
                    Text(
                        text = stringResource(
                            if (allSelected) {
                                R.string.history_deselect_all
                            } else {
                                R.string.history_select_all
                            },
                        ),
                    )
                }
                Button(
                    onClick = { showDeleteDialog = true },
                    enabled = selectedUris.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(
                            R.string.history_delete_selected,
                            selectedUris.size,
                        ),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        if (recentFiles.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Rounded.History,
                title = stringResource(R.string.history_empty),
                message = stringResource(R.string.history_empty_hint),
            )
        } else {
            recentFiles.forEach { file ->
                val selected = file.uri in selectedUris
                RecentFileCard(
                    file = file,
                    onClick = {
                        if (selectionMode) {
                            selectedUris = if (selected) {
                                selectedUris - file.uri
                            } else {
                                selectedUris + file.uri
                            }
                        } else {
                            onRecentFileClick(file)
                        }
                    },
                    selectionMode = selectionMode,
                    selected = selected,
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                )
            },
            title = {
                Text(text = stringResource(R.string.history_delete_title))
            },
            text = {
                Text(
                    text = pluralStringResource(
                        R.plurals.history_delete_message,
                        selectedUris.size,
                        selectedUris.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = selectedUris
                        showDeleteDialog = false
                        exitSelection()
                        onDeleteRecentFiles(toDelete)
                    },
                ) {
                    Text(text = stringResource(R.string.history_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}
