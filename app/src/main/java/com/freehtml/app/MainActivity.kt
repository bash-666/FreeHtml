package com.freehtml.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freehtml.app.data.model.LanguageMode
import com.freehtml.app.data.preferences.AppPreferences
import com.freehtml.app.ui.home.HomeScreen
import com.freehtml.app.ui.settings.SettingsViewModel
import com.freehtml.app.ui.theme.FreeHtmlTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val tag = AppPreferences.readSyncedLanguageMode(newBase)
            ?.let { LanguageMode.fromStorage(it).localeTag() }
        val context = if (tag != null) {
            val configuration = Configuration(newBase.resources.configuration)
            configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
            newBase.createConfigurationContext(configuration)
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val preferences = remember { AppPreferences(applicationContext) }
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(preferences),
            )
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val incognito by settingsViewModel.incognito.collectAsStateWithLifecycle()
            val editorZoom by settingsViewModel.editorZoom.collectAsStateWithLifecycle()
            val languageMode by settingsViewModel.languageMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val resources = LocalResources.current

            // Apply the selected interface language (device default for SYSTEM).
            LaunchedEffect(languageMode) {
                val desiredTag = languageMode.localeTag()
                    ?: resources.configuration.locales.get(0).toLanguageTag()
                val currentTag = Locale.getDefault().toLanguageTag()
                val needsChange = !(currentTag.equals(desiredTag, ignoreCase = true) ||
                    currentTag.startsWith(desiredTag, ignoreCase = true))
                if (needsChange) {
                    Locale.setDefault(Locale.forLanguageTag(desiredTag))
                    recreate()
                }
            }

            FreeHtmlTheme(darkTheme = themeMode.isDark(systemDark)) {
                HomeScreen(
                    themeMode = themeMode,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    incognito = incognito,
                    editorZoom = editorZoom,
                    languageMode = languageMode,
                    onIncognitoChange = settingsViewModel::setIncognito,
                    onEditorZoomChange = settingsViewModel::setEditorZoom,
                    onLanguageModeChange = settingsViewModel::setLanguageMode,
                )
            }
        }
    }
}
