package com.freehtml.app

import com.freehtml.app.ui.editor.findMatches
import org.junit.Assert.assertEquals
import org.junit.Test

class FindMatchesTest {

    @Test
    fun findsAllCaseInsensitiveMatches() {
        val matches = findMatches("Hola html MUNDO Html", "html")

        assertEquals(2, matches.size)
        assertEquals(5 until 9, matches[0])
        assertEquals(16 until 20, matches[1])
    }

    @Test
    fun returnsEmptyForBlankQueryOrNoMatches() {
        assertEquals(emptyList<IntRange>(), findMatches("contenido", ""))
        assertEquals(emptyList<IntRange>(), findMatches("contenido", "xyz"))
    }
}
