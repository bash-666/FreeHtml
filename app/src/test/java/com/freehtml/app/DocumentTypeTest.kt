package com.freehtml.app

import com.freehtml.app.data.model.DocumentType
import com.freehtml.app.util.ensureExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentTypeTest {
    @Test
    fun detectsSupportedExtensionsIgnoringCase() {
        assertEquals(DocumentType.HTML, DocumentType.fromFileName("index.HTML"))
        assertEquals(DocumentType.JSON, DocumentType.fromExtension(".json"))
        assertEquals(DocumentType.DOCX, DocumentType.fromFileName("reporte.docx"))
        assertNull(DocumentType.fromFileName("script.py"))
    }

    @Test
    fun detectsAlternativeJavascriptMimeTypes() {
        assertEquals(DocumentType.JS, DocumentType.fromMimeType("text/javascript"))
        assertEquals(DocumentType.HTML, DocumentType.fromMimeType("application/xhtml+xml"))
    }

    @Test
    fun addsOrReplacesTheExtension() {
        assertEquals("nuevo.html", ensureExtension("nuevo", DocumentType.HTML))
        assertEquals("pagina.css", ensureExtension("pagina.txt", DocumentType.CSS))
        assertEquals("nuevo.json", ensureExtension("   ", DocumentType.JSON))
    }
}
