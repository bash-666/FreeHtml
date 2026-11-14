package com.freehtml.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.freehtml.app.data.model.LanguageMode
import com.freehtml.app.data.model.ThemeMode
import com.freehtml.app.data.preferences.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val preferences: AppPreferences) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val incognito: StateFlow<Boolean> = preferences.incognito
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val editorZoom: StateFlow<Float> = preferences.editorZoom
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    val languageMode: StateFlow<LanguageMode> = preferences.languageMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, LanguageMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    fun setIncognito(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setIncognito(enabled)
        }
    }

    fun setEditorZoom(value: Float) {
        viewModelScope.launch {
            preferences.setEditorZoom(value)
        }
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch {
            preferences.setLanguageMode(mode)
        }
    }

    companion object {
        fun factory(preferences: AppPreferences) = viewModelFactory {
            initializer { SettingsViewModel(preferences) }
        }
    }
}
