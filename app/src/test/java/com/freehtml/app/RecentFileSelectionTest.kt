package com.freehtml.app

import com.freehtml.app.data.model.DocumentType
import com.freehtml.app.data.model.RecentFile
import com.freehtml.app.data.model.withoutUris
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFileSelectionTest {

    private val files = listOf(
        RecentFile("content://one", "uno.html", DocumentType.HTML, openedAt = 1),
        RecentFile("content://two", "dos.txt", DocumentType.TXT, openedAt = 2),
        RecentFile("content://three", "tres.css", DocumentType.CSS, openedAt = 3),
    )

    @Test
    fun removesOnlyTheSelectedUris() {
        val remaining = files.withoutUris(setOf("content://one", "content://three"))

        assertEquals(listOf("content://two"), remaining.map { it.uri })
    }

    @Test
    fun keepsTheListUntouchedWhenNothingIsSelected() {
        assertEquals(files, files.withoutUris(emptySet()))
    }
}
