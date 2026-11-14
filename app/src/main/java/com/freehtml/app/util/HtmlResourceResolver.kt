package com.freehtml.app.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Resolves local CSS/JavaScript references from an HTML document against the sibling files
 * stored next to the original file (through the Storage Access Framework) and inlines them.
 * This lets `<link href="style.css">`, `<script src="app.js">` and CSS `@import` work in the
 * preview even though the WebView itself has file access disabled.
 */

private val LINK_REGEX = Regex(
    """<link\b[^>]*\bhref\s*=\s*(?:"([^"]*)"|'([^']*)')[^>]*>""",
    RegexOption.IGNORE_CASE,
)

private val SCRIPT_SRC_REGEX = Regex(
    """<script\b[^>]*\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)')[^>]*>\s*</script>""",
    RegexOption.IGNORE_CASE,
)

private val CSS_IMPORT_REGEX = Regex(
    """@import\s+(?:url\(\s*)?(?:"([^"]*)"|'([^']*)'|([^\s;"')]+))\s*\)?\s*;""",
    RegexOption.IGNORE_CASE,
)

/** Returns the local (non-remote, non-absolute) references used by [html]. */
internal fun collectLocalReferences(html: String): Set<String> {
    val refs = mutableSetOf<String>()
    LINK_REGEX.findAll(html).forEach { match -> localRefOf(match)?.let(refs::add) }
    SCRIPT_SRC_REGEX.findAll(html).forEach { match -> localRefOf(match)?.let(refs::add) }
    CSS_IMPORT_REGEX.findAll(html).forEach { match -> localRefOf(match)?.let(refs::add) }
    return refs
}

/** True when [html] loads local files (CSS/JS, including ES module scripts). */
internal fun needsLocalFiles(html: String): Boolean = collectLocalReferences(html).isNotEmpty()

/** MIME type for a file extension, used when serving granted-folder files to the WebView. */
internal fun mimeTypeForExtension(extension: String): String? = when (extension.lowercase()) {
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js", "mjs" -> "text/javascript"
    "json", "map" -> "application/json"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "bmp" -> "image/bmp"
    "ico" -> "image/x-icon"
    "txt" -> "text/plain"
    "wasm" -> "application/wasm"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "ogg" -> "audio/ogg"
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    else -> null
}

private fun localRefOf(match: MatchResult): String? {
    val ref = match.groupValues
        .drop(1)
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?: return null
    if (ref.isBlank() || isRemoteOrAbsolute(ref)) return null
    return ref
}

private fun isRemoteOrAbsolute(ref: String): Boolean {
    val lower = ref.lowercase()
    return lower.startsWith("http://") ||
        lower.startsWith("https://") ||
        lower.startsWith("//") ||
        lower.startsWith("data:") ||
        lower.startsWith("blob:") ||
        lower.startsWith("#") ||
        lower.startsWith("/") ||
        lower.startsWith("file:")
}

/**
 * Pure replacement step: inlines every known local reference in [html] using [resources]
 * (keyed by the reference exactly as written in the document). Unknown or remote references
 * are left untouched.
 */
internal fun inlineLocalResources(
    html: String,
    resources: Map<String, String>,
): String {
    if (resources.isEmpty()) return html

    var result = LINK_REGEX.replace(html) { match ->
        val ref = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim()
        val css = ref?.let(resources::get)
        if (css == null) {
            match.value
        } else {
            "<style>\n" + css.replace("</style>", "<\\/style>") + "\n</style>"
        }
    }

    result = SCRIPT_SRC_REGEX.replace(result) { match ->
        val ref = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim()
        val js = ref?.let(resources::get)
        if (js == null) {
            match.value
        } else {
            "<script>\n" + js.replace("</script>", "<\\/script>") + "\n</script>"
        }
    }

    // Imports can be nested inside inlined stylesheets; expand them with a bounded loop.
    repeat(MAX_IMPORT_DEPTH) {
        var changed = false
        result = CSS_IMPORT_REGEX.replace(result) { match ->
            val ref = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.trim()
            val css = ref?.let(resources::get)
            if (css == null) {
                match.value
            } else {
                changed = true
                css.replace("</style>", "<\\/style>")
            }
        }
        if (!changed) return result
    }

    return result
}

/**
 * Reads the sibling files of [documentUri] that [html] references and returns [html] with
 * those resources inlined:
 * - CSS/JS files referenced from `<link>`, `<script src>` and CSS `@import` are inlined as
 *   `<style>`/`<script>` blocks.
 * - Image files referenced by relative path (in `src`, `url()` or JavaScript data strings)
 *   are replaced by `data:` URIs so `<img>` and CSS backgrounds work offline.
 * Any failure leaves the document unchanged.
 */
fun resolveLocalResources(
    contentResolver: ContentResolver,
    documentUri: Uri,
    html: String,
): String {
    val files = runCatching {
        listSiblingFiles(contentResolver, documentUri)
    }.getOrDefault(emptyMap())
    if (files.isEmpty()) return html

    val result = if (files.isEmpty()) {
        html
    } else {
        inlineImageReferences(html, files, contentResolver)
    }

    val refs = collectLocalReferences(result)
    if (refs.isEmpty()) return result

    val byName = files.mapKeys { it.key.substringAfterLast('/') }
    val resources = refs.mapNotNull { ref ->
        val text = files[ref]?.let { readText(contentResolver, it) }
            ?: byName[ref.substringAfterLast('/')]?.let { readText(contentResolver, it) }
            ?: return@mapNotNull null
        ref to text
    }.toMap()

    return inlineLocalResources(result, resources)
}

/**
 * Replaces occurrences of relative image paths (e.g. `"img/photo.png"`) anywhere in the
 * document — including inside JavaScript data strings — with base64 `data:` URIs.
 */
internal fun inlineImageReferences(
    html: String,
    files: Map<String, Uri>,
    contentResolver: ContentResolver,
): String {
    var result = html
    // Longest paths first so overlapping names cannot shadow each other.
    files.entries
        .filter { it.key.mimeTypeForPath() != null }
        .sortedByDescending { it.key.length }
        .forEach { (path, uri) ->
            if (!result.contains(path)) return@forEach
            val bytes = runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@forEach
            val dataUri = "data:${path.mimeTypeForPath()};base64," +
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            result = result.replace(path, dataUri)
        }
    return result
}

private fun String.mimeTypeForPath(): String? =
    mimeTypeForExtension(substringAfterLast('.', ""))

/**
 * Lists every file inside the granted folder [treeUri], keyed by its path relative to the
 * folder root (e.g. `"src/main.js"`). Used to serve site resources to the preview WebView.
 */
fun listTreeFiles(
    contentResolver: ContentResolver,
    treeUri: Uri,
): Map<String, Uri> {
    val files = mutableMapOf<String, Uri>()
    val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    collectFiles(
        contentResolver = contentResolver,
        treeUri = treeUri,
        parentDocumentId = rootDocumentId,
        destination = files,
        depth = 0,
    )
    return files
}

/** Reads file bytes, used by the WebView request interceptor on a background thread. */
fun readBytes(contentResolver: ContentResolver, uri: Uri): ByteArray? = runCatching {
    contentResolver.openInputStream(uri)?.use { it.readBytes() }
}.getOrNull()

private fun listSiblingFiles(
    contentResolver: ContentResolver,
    documentUri: Uri,
): Map<String, Uri> {
    val authority = documentUri.authority ?: return emptyMap()
    val treeUri = DocumentsContract.buildTreeDocumentUri(
        authority,
        DocumentsContract.getTreeDocumentId(documentUri),
    )
    val files = mutableMapOf<String, Uri>()
    collectFiles(
        contentResolver = contentResolver,
        treeUri = treeUri,
        parentDocumentId = DocumentsContract.getDocumentId(documentUri),
        destination = files,
        depth = 0,
    )
    return files
}

private fun collectFiles(
    contentResolver: ContentResolver,
    treeUri: Uri,
    parentDocumentId: String,
    destination: MutableMap<String, Uri>,
    depth: Int,
) {
    if (depth > MAX_LIST_DEPTH) return

    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        treeUri,
        parentDocumentId,
    )
    val subfolders = mutableListOf<Pair<String, String>>() // relative path -> document id
    contentResolver.query(
        childrenUri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val documentId = cursor.getString(0) ?: continue
            val displayName = cursor.getString(1) ?: continue
            val mimeType = cursor.getString(2)
            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                subfolders += displayName to documentId
            } else {
                destination[displayName] = childUri
            }
        }
    }

    subfolders.forEach { (folderName, folderId) ->
        val nested = mutableMapOf<String, Uri>()
        collectFiles(
            contentResolver = contentResolver,
            treeUri = treeUri,
            parentDocumentId = folderId,
            destination = nested,
            depth = depth + 1,
        )
        nested.forEach { (relativePath, childUri) ->
            destination["$folderName/$relativePath"] = childUri
        }
    }
}

private fun readText(contentResolver: ContentResolver, uri: Uri): String? = runCatching {
    contentResolver.openInputStream(uri)?.use { stream ->
        stream.bufferedReader(Charsets.UTF_8).readText()
    }
}.getOrNull()

private const val MAX_IMPORT_DEPTH = 8
private const val MAX_LIST_DEPTH = 5
