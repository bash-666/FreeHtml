package com.freehtml.app.data.model

import kotlinx.serialization.Serializable

/**
 * File formats supported by FreeHtml.
 *
 * The extension is the source of truth for local files. MIME types are used
 * when creating documents through the Storage Access Framework.
 */
@Serializable
enum class DocumentType(
    val extension: String,
    val mimeType: String,
    val isEditable: Boolean,
    val hasPreview: Boolean,
) {
    HTML(
        extension = "html",
        mimeType = "text/html",
        isEditable = true,
        hasPreview = true,
    ),
    TXT(
        extension = "txt",
        mimeType = "text/plain",
        isEditable = true,
        hasPreview = false,
    ),
    JSON(
        extension = "json",
        mimeType = "application/json",
        isEditable = true,
        hasPreview = false,
    ),
    CSS(
        extension = "css",
        mimeType = "text/css",
        isEditable = true,
        hasPreview = false,
    ),
    JS(
        extension = "js",
        mimeType = "application/javascript",
        isEditable = true,
        hasPreview = false,
    ),
    PDF(
        extension = "pdf",
        mimeType = "application/pdf",
        isEditable = false,
        hasPreview = false,
    ),
    DOCX(
        extension = "docx",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        isEditable = false,
        hasPreview = false,
    ),
    ;

    val displayName: String
        get() = extension.uppercase()

    val syntaxLanguage: String
        get() = when (this) {
            HTML -> "html"
            TXT -> "plaintext"
            JSON -> "json"
            CSS -> "css"
            JS -> "javascript"
            PDF, DOCX -> "plaintext"
        }

    fun starterContent(): String = when (this) {
        HTML -> """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Nuevo documento</title>
            </head>
            <body>
                <h1>Hola, FreeHtml</h1>
            </body>
            </html>
        """.trimIndent() + "\n"

        CSS -> "/* Nuevo estilo */\n"
        JS -> "// Nuevo script\n"
        JSON -> "{\n  \n}\n"
        TXT, PDF, DOCX -> ""
    }

    companion object {
        val editableTypes: List<DocumentType> = entries.filter { it.isEditable }

        fun fromExtension(extension: String?): DocumentType? =
            extension
                ?.lowercase()
                ?.removePrefix(".")
                ?.let { ext -> entries.firstOrNull { it.extension == ext } }

        fun fromFileName(fileName: String?): DocumentType? =
            fromExtension(fileName?.substringAfterLast('.', missingDelimiterValue = ""))

        fun fromMimeType(mimeType: String?): DocumentType? {
            if (mimeType == null) return null
            return entries.firstOrNull { it.mimeType.equals(mimeType, ignoreCase = true) }
                ?: when (mimeType.lowercase()) {
                    "text/javascript", "application/x-javascript" -> JS
                    "application/xhtml+xml" -> HTML
                    else -> null
                }
        }
    }
}
