package com.freehtml.app

import com.freehtml.app.data.model.DocumentType
import com.freehtml.app.util.previewHtmlFor
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewHtmlTest {

    @Test
    fun keepsHtmlPreviewContentUnchanged() {
        val html = "<!DOCTYPE html><html><body><h1>Hola</h1></body></html>"

        assertTrue(previewHtmlFor(DocumentType.HTML, html).contains(html))
    }

    @Test
    fun escapesPlainTextAndJson() {
        val text = previewHtmlFor(DocumentType.TXT, "<b>no es html</b>")

        assertTrue(text.contains("&lt;b&gt;no es html&lt;/b&gt;"))
        assertTrue(!text.contains("<b>no es html</b>"))
    }

    @Test
    fun wrapsCssAndJavascriptInPreviewDocuments() {
        val css = previewHtmlFor(DocumentType.CSS, "h1 { color: red; }")
        val js = previewHtmlFor(DocumentType.JS, "document.body.dataset.ready = '1';")

        assertTrue(css.contains("h1 { color: red; }"))
        assertTrue(css.contains("Vista previa CSS"))
        assertTrue(js.contains("document.body.dataset.ready = '1';"))
        assertTrue(js.contains("Vista previa JavaScript"))
    }

    @Test
    fun showsAnEmptyStateForBlankOrPdfContent() {
        assertTrue(previewHtmlFor(DocumentType.HTML, "").contains("Sin contenido"))
        assertTrue(previewHtmlFor(DocumentType.PDF, "ignored").contains("Sin contenido"))
        assertTrue(previewHtmlFor(null, "ignored").contains("Sin contenido"))
    }
}
