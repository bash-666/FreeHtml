package com.freehtml.app.util

import com.freehtml.app.data.model.DocumentType

/**
 * Builds the HTML that is handed to the preview WebView. HTML and converted DOCX content are used
 * directly; other text formats get a small readable wrapper so the preview is useful too.
 */
fun previewHtmlFor(type: DocumentType?, content: String): String = when (type) {
    DocumentType.HTML, DocumentType.DOCX -> content.ifBlank { emptyDocument() }
    DocumentType.CSS -> cssDocument(content)
    DocumentType.JS -> javascriptDocument(content)
    DocumentType.JSON, DocumentType.TXT -> textDocument(content)
    DocumentType.PDF, null -> emptyDocument()
}

private fun emptyDocument(): String = """
    <!DOCTYPE html>
    <html lang="es">
    <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
    :root { color-scheme: light; }
    body {
        margin: 0;
        padding: 32px 20px;
        background: #ffffff;
        color: #1f2328;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }
    p { line-height: 1.55; }
    </style>
    </head>
    <body>
    <p>Sin contenido para previsualizar.</p>
    </body>
    </html>
""".trimIndent()

private fun cssDocument(css: String): String {
    if (css.isBlank()) return emptyDocument()
    return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
        :root { color-scheme: light; }
        * { box-sizing: border-box; }
        body {
            margin: 0;
            padding: 24px 18px 40px;
            background: #f6f8fa;
            color: #1f2328;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
            line-height: 1.55;
        }
        h1 { margin-top: 0; font-size: 1.5rem; }
        .preview-card {
            margin-top: 20px;
            padding: 18px;
            border: 1px solid #d0d7de;
            border-radius: 12px;
            background: #ffffff;
        }
        .preview-row { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 12px; }
        button, .preview-button {
            padding: 9px 14px;
            border: 1px solid #0969da;
            border-radius: 8px;
            background: #0969da;
            color: #ffffff;
            font: inherit;
        }
        $css
        </style>
        </head>
        <body>
        <h1>Vista previa CSS</h1>
        <p>Así se aplican tus estilos a un documento de ejemplo.</p>
        <div class="preview-card">
            <h2>Tarjeta de ejemplo</h2>
            <p>Un párrafo con <strong>texto en negrita</strong> y <em>texto en cursiva</em>.</p>
            <div class="preview-row">
                <button type="button">Botón</button>
                <span class="preview-button">Enlace</span>
            </div>
        </div>
        </body>
        </html>
    """.trimIndent()
}

private fun javascriptDocument(script: String): String {
    if (script.isBlank()) return emptyDocument()
    return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
        :root { color-scheme: light; }
        * { box-sizing: border-box; }
        body {
            margin: 0;
            padding: 24px 18px 40px;
            background: #f6f8fa;
            color: #1f2328;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
            line-height: 1.55;
        }
        h1 { margin-top: 0; font-size: 1.5rem; }
        #preview-output {
            margin-top: 16px;
            padding: 16px;
            border: 1px solid #d0d7de;
            border-radius: 12px;
            background: #ffffff;
        }
        </style>
        </head>
        <body>
        <h1>Vista previa JavaScript</h1>
        <div id="preview-output">
            <p>Este es el documento de ejemplo. Los cambios del script se reflejarán aquí.</p>
        </div>
        <script>
        $script
        </script>
        </body>
        </html>
    """.trimIndent()
}

private fun textDocument(text: String): String {
    if (text.isBlank()) return emptyDocument()
    return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
        :root { color-scheme: light; }
        * { box-sizing: border-box; }
        body {
            margin: 0;
            padding: 18px;
            background: #ffffff;
            color: #1f2328;
        }
        pre {
            margin: 0;
            white-space: pre-wrap;
            overflow-wrap: anywhere;
            font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
            font-size: 14px;
            line-height: 1.6;
        }
        </style>
        </head>
        <body>
        <pre>${escapeHtml(text)}</pre>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeHtml(value: String): String = buildString(value.length) {
    value.forEach { character ->
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(character)
        }
    }
}
