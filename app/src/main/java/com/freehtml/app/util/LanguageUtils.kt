package com.freehtml.app.util

/**
 * Maps a file name/extension to the language id used by Highlight.js.
 * HTML is handled by Highlight.js' XML language through its "html" alias.
 */
fun languageForFileName(fileName: String): String {
    val extension = fileName
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()

    return when (extension) {
        "html", "htm", "xhtml" -> "html"
        "css" -> "css"
        "js", "mjs", "cjs", "jsx" -> "javascript"
        "json" -> "json"
        "txt", "text", "md", "markdown" -> "plaintext"
        else -> "plaintext"
    }
}
