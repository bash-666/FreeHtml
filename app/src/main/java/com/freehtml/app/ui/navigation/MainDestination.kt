package com.freehtml.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.freehtml.app.R

enum class MainDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(
        route = "home",
        labelRes = R.string.nav_home,
        icon = Icons.Rounded.Home,
    ),
    HISTORY(
        route = "history",
        labelRes = R.string.nav_history,
        icon = Icons.Rounded.History,
    ),
    SETTINGS(
        route = "settings",
        labelRes = R.string.nav_settings,
        icon = Icons.Rounded.Settings,
    ),
}
