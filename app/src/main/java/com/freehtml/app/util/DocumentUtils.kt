package com.freehtml.app.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.freehtml.app.data.model.DocumentType

fun ContentResolver.queryDisplayName(uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return runCatching {
        query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()
}

fun Context.queryDisplayName(uri: Uri): String? = contentResolver.queryDisplayName(uri)

fun Context.persistReadPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun Context.persistReadWritePermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

fun ContentResolver.writeInitialContent(uri: Uri, type: DocumentType) {
    openOutputStream(uri, "w")?.use { stream ->
        stream.write(type.starterContent().toByteArray(Charsets.UTF_8))
    } ?: error("No output stream for $uri")
}

fun ensureExtension(fileName: String, type: DocumentType): String {
    val trimmed = fileName.trim().ifBlank { "nuevo" }
    val base = trimmed.substringBeforeLast('.', trimmed).ifBlank { "nuevo" }
    return "$base.${type.extension}"
}

val supportedMimeTypes: Array<String> = arrayOf(
    "text/html",
    "application/xhtml+xml",
    "text/plain",
    "application/json",
    "text/css",
    "application/javascript",
    "text/javascript",
    "application/x-javascript",
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)
