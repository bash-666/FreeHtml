package com.freehtml.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.freehtml.app.data.model.RecentFile
import com.freehtml.app.data.preferences.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val recentFiles: List<RecentFile> = emptyList(),
)

class HomeViewModel(private val preferences: AppPreferences) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = preferences.recentFiles
        .map { recentFiles -> HomeUiState(recentFiles = recentFiles) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun onFileOpened(file: RecentFile) {
        viewModelScope.launch {
            preferences.addRecentFile(file)
        }
    }

    fun clearRecentFiles() {
        viewModelScope.launch {
            preferences.clearRecentFiles()
        }
    }

    fun removeRecentFiles(uris: Set<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            preferences.removeRecentFiles(uris)
        }
    }

    companion object {
        fun factory(context: Context) = viewModelFactory {
            initializer {
                HomeViewModel(AppPreferences(context.applicationContext))
            }
        }
    }
}
