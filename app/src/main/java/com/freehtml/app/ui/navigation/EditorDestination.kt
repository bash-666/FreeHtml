package com.freehtml.app.ui.navigation

import android.net.Uri

object EditorDestination {
    const val ARG_URI = "uri"
    const val ROUTE = "editor/{$ARG_URI}"

    fun createRoute(uri: String): String = "editor/${Uri.encode(uri)}"
}
