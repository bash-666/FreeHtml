package com.freehtml.app

import com.freehtml.app.data.model.LanguageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageModeTest {

    @Test
    fun systemHasNoForcedLocaleTag() {
        assertNull(LanguageMode.SYSTEM.localeTag())
    }

    @Test
    fun eachLanguageMapsToItsTag() {
        assertEquals("es", LanguageMode.SPANISH.localeTag())
        assertEquals("en", LanguageMode.ENGLISH.localeTag())
        assertEquals("fr", LanguageMode.FRENCH.localeTag())
        assertEquals("pt", LanguageMode.PORTUGUESE.localeTag())
    }

    @Test
    fun unknownStorageValuesFallBackToSystem() {
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromStorage(null))
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromStorage("XX"))
        assertEquals(LanguageMode.FRENCH, LanguageMode.fromStorage("FRENCH"))
    }
}
