package com.freehtml.app

import com.freehtml.app.util.languageForFileName
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageUtilsTest {
    @Test
    fun mapsEditableExtensionsToHighlightJsLanguages() {
        assertEquals("html", languageForFileName("index.html"))
        assertEquals("css", languageForFileName("styles.CSS"))
        assertEquals("javascript", languageForFileName("app.js"))
        assertEquals("json", languageForFileName("data.json"))
        assertEquals("plaintext", languageForFileName("notas.txt"))
    }

    @Test
    fun fallsBackToPlainTextForUnknownOrMissingExtensions() {
        assertEquals("plaintext", languageForFileName("script.py"))
        assertEquals("plaintext", languageForFileName("README"))
    }
}
