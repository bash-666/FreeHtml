package com.freehtml.app.data.model

/**
 * User-selected theme. Dark remains the default to preserve FreeHtml's design.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: DARK
    }
}
