package com.freehtml.app.data.model

/**
 * User-selected interface language. [SYSTEM] follows the device language.
 */
enum class LanguageMode {
    SYSTEM,
    SPANISH,
    ENGLISH,
    FRENCH,
    PORTUGUESE,
    ;

    /** BCP-47 tag to apply, or null to keep the system default. */
    fun localeTag(): String? = when (this) {
        SYSTEM -> null
        SPANISH -> "es"
        ENGLISH -> "en"
        FRENCH -> "fr"
        PORTUGUESE -> "pt"
    }

    companion object {
        fun fromStorage(value: String?): LanguageMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
