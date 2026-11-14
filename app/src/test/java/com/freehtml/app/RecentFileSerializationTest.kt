package com.freehtml.app

import com.freehtml.app.data.model.DocumentType
import com.freehtml.app.data.model.RecentFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFileSerializationTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun recentFileRoundTripsThroughJson() {
        val original = RecentFile(
            uri = "content://com.android.providers.downloads/document/42",
            displayName = "index.html",
            type = DocumentType.HTML,
            openedAt = 1_700_000_000_000,
        )

        val encoded = json.encodeToString(RecentFile.serializer(), original)
        val decoded = json.decodeFromString(RecentFile.serializer(), encoded)

        assertEquals(original, decoded)
    }
}
