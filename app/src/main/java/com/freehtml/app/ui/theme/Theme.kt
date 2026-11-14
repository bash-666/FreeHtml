package com.freehtml.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = OnTealDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = Teal80,
    onSecondary = OnTealDark,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = OnTealContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = SurfaceVariantDark,
    error = ErrorDark,
)

private val LightColorScheme = lightColorScheme(
    primary = TealLight,
    onPrimary = OnTealLight,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = TealLight,
    onSecondary = OnTealLight,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = OnTealContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = SurfaceVariantLight,
)

@Composable
fun FreeHtmlTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FreeHtmlTypography,
        content = content,
    )
}
