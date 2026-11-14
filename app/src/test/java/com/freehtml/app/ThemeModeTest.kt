package com.freehtml.app

import com.freehtml.app.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun missingPreferenceFallsBackToDark() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("unknown"))
    }

    @Test
    fun storedPreferenceIsRestored() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorage("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("SYSTEM"))
    }

    @Test
    fun resolvesDarkState() {
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }
}
