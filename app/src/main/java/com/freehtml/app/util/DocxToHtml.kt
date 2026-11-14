package com.freehtml.app.util

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Converts a Word document into a small, self-contained HTML page.
 *
 * Supports the formatting that matters for a readable preview: headings, bullet and
 * numbered lists, alignment, hyperlinks, inline images (embedded as data URIs) and
 * basic run formatting (bold, italic, underline, strike, colour and size).
 * Headers/footers, footnotes and other complex Word features are intentionally skipped.
 */
object DocxToHtml {

    private const val WORD_NS =
        "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val RELATIONSHIP_NS =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val DRAWING_NS =
        "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val VML_NS =
        "urn:schemas-microsoft-com:vml"

    /** Largest image embedded into the HTML. */
    private const val MAX_MEDIA_ENTRY_BYTES = 3_000_000

    /** Budget for all images combined, to keep the generated HTML manageable. */
    private const val MAX_MEDIA_TOTAL_BYTES = 8_000_000

    fun convert(input: InputStream): String {
        val entries = readEntries(input)
        val documentXml = entries["word/document.xml"]
            ?: throw IOException("El archivo no contiene word/document.xml")
        val document = parse(documentXml)
        val relationships = RelationshipTable.from(entries["word/_rels/document.xml.rels"])
        val numbering = NumberingInfo.from(entries["word/numbering.xml"])
        val media = MediaTable.from(entries)

        val renderer = Renderer(relationships, numbering, media)
        val body = StringBuilder()
        document.documentElement?.let { root ->
            val bodyElement = firstChildElement(root, "body") ?: root
            renderer.renderChildren(bodyElement, body)
        }
        renderer.closeAllLists(body)

        val visibleBody = if (body.isBlank()) {
            "<p>Documento sin texto visible.</p>"
        } else {
            body.toString()
        }
        return htmlShell(visibleBody)
    }

    /**
     * Reads the entries needed to build the preview: the document itself, its
     * relationships, numbering definitions and media files (within budget).
     */
    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        var mediaBytes = 0
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "word/document.xml") {
                    entries[name] = zip.readBytes()
                } else if (name == "word/_rels/document.xml.rels" || name == "word/numbering.xml") {
                    if (entry.size == -1L || entry.size < 1_000_000) {
                        entries[name] = zip.readBytes()
                    }
                } else if (name.startsWith("word/media/") && mediaBytes < MAX_MEDIA_TOTAL_BYTES) {
                    val bytes = zip.readBytes()
                    if (bytes.size <= MAX_MEDIA_ENTRY_BYTES &&
                        mediaBytes + bytes.size <= MAX_MEDIA_TOTAL_BYTES
                    ) {
                        entries[name] = bytes
                        mediaBytes += bytes.size
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun parse(xml: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            setFeatureQuietly("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureQuietly("http://xml.org/sax/features/external-general-entities", false)
            setFeatureQuietly("http://xml.org/sax/features/external-parameter-entities", false)
            setFeatureQuietly(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false,
            )
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
    }

    private fun DocumentBuilderFactory.setFeatureQuietly(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun firstChildElement(parent: Element?, localName: String): Element? {
        if (parent == null) return null
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && child.localName == localName) {
                return child
            }
        }
        return null
    }

    private fun firstDescendantElement(parent: Element, localName: String): Element? {
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) {
                if (child.localName == localName) return child
                firstDescendantElement(child, localName)?.let { return it }
            }
        }
        return null
    }

    /** Relationship targets of the document part, keyed by relationship id. */
    private class RelationshipTable private constructor(
        private val targets: Map<String, String>,
    ) {
        fun targetFor(id: String): String? = targets[id]

        companion object {
            private const val RELS_NS =
                "http://schemas.openxmlformats.org/package/2006/relationships"

            fun from(xml: ByteArray?): RelationshipTable {
                if (xml == null) return empty()
                val targets = mutableMapOf<String, String>()
                runCatching {
                    val root = parseRels(xml).documentElement
                    val children = root.childNodes
                    for (index in 0 until children.length) {
                        val child = children.item(index)
                        if (child is Element && child.nodeName == "Relationship") {
                            val id = child.getAttribute("Id")
                            val target = child.getAttribute("Target")
                            if (id.isNotBlank() && target.isNotBlank()) {
                                targets[id] = resolveTarget(target)
                            }
                        }
                    }
                }
                return RelationshipTable(targets)
            }

            private fun empty() = RelationshipTable(emptyMap())

            private fun parseRels(xml: ByteArray): Document {
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = false
                    isExpandEntityReferences = false
                    setFeatureQuietly(
                        "http://apache.org/xml/features/disallow-doctype-decl",
                        true,
                    )
                    setFeatureQuietly(
                        "http://xml.org/sax/features/external-general-entities",
                        false,
                    )
                    setFeatureQuietly(
                        "http://xml.org/sax/features/external-parameter-entities",
                        false,
                    )
                    setFeatureQuietly(
                        "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                        false,
                    )
                }
                return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            }

            /** Resolves a relationship target against the "word/" package part. */
            private fun resolveTarget(target: String): String {
                // Absolute URLs (external hyperlinks) are not package paths.
                if (target.startsWith("/") ||
                    target.contains("://") ||
                    target.startsWith("mailto:")
                ) {
                    return target
                }
                val segments = ArrayDeque<String>()
                segments.addLast("word")
                for (segment in target.split('/')) {
                    when (segment) {
                        "", "." -> Unit
                        ".." -> if (segments.size > 1) segments.removeLast()
                        else -> segments.addLast(segment)
                    }
                }
                return segments.joinToString("/")
            }
        }
    }

    /** Maps (numId, ilvl) to the numbering format used by a list paragraph. */
    private class NumberingInfo private constructor(
        private val abstractFormats: Map<String, Map<Int, String>>,
        private val numToAbstract: Map<String, String>,
    ) {
        fun formatFor(numId: String, level: Int): String? {
            val levels = abstractFormats[numToAbstract[numId]] ?: return null
            return levels[level] ?: levels[0]
        }

        companion object {
            fun from(xml: ByteArray?): NumberingInfo? {
                if (xml == null) return null
                val abstractFormats = mutableMapOf<String, Map<Int, String>>()
                val numToAbstract = mutableMapOf<String, String>()
                runCatching {
                    val document = parse(xml)
                    document.documentElement?.let { root ->
                        val children = root.childNodes
                        for (index in 0 until children.length) {
                            val child = children.item(index)
                            if (child !is Element) continue
                            when (child.localName) {
                                "num" -> {
                                    val numId = child.getAttributeNS(WORD_NS, "numId")
                                    val abstractId = firstChildElement(child, "abstractNumId")
                                        ?.getAttributeNS(WORD_NS, "val")
                                    if (numId.isNotBlank() && !abstractId.isNullOrBlank()) {
                                        numToAbstract[numId] = abstractId
                                    }
                                }
                                "abstractNum" -> {
                                    val abstractId = child.getAttributeNS(WORD_NS, "abstractNumId")
                                    val levels = mutableMapOf<Int, String>()
                                    val levelNodes = child.childNodes
                                    for (levelIndex in 0 until levelNodes.length) {
                                        val levelNode = levelNodes.item(levelIndex)
                                        if (levelNode is Element && levelNode.localName == "lvl") {
                                            val level = levelNode
                                                .getAttributeNS(WORD_NS, "ilvl")
                                                .toIntOrNull() ?: continue
                                            val format = firstChildElement(levelNode, "numFmt")
                                                ?.getAttributeNS(WORD_NS, "val")
                                            if (!format.isNullOrBlank()) {
                                                levels[level] = format
                                            }
                                        }
                                    }
                                    if (abstractId.isNotBlank()) {
                                        abstractFormats[abstractId] = levels
                                    }
                                }
                            }
                        }
                    }
                }
                return NumberingInfo(abstractFormats, numToAbstract)
            }

            private fun parse(xml: ByteArray): Document {
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    isExpandEntityReferences = false
                    setFeatureQuietly(
                        "http://apache.org/xml/features/disallow-doctype-decl",
                        true,
                    )
                    setFeatureQuietly(
                        "http://xml.org/sax/features/external-general-entities",
                        false,
                    )
                    setFeatureQuietly(
                        "http://xml.org/sax/features/external-parameter-entities",
                        false,
                    )
                    setFeatureQuietly(
                        "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                        false,
                    )
                }
                return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            }
        }
    }

    /** Media files of the package, keyed by package path. */
    private class MediaTable private constructor(
        private val entries: Map<String, ByteArray>,
    ) {
        fun dataUriFor(path: String): String? {
            val bytes = entries[path] ?: return null
            val mime = mimeFor(path) ?: return null
            return "data:$mime;base64," +
                Base64.getEncoder().encodeToString(bytes)
        }

        private fun mimeFor(path: String): String? = when {
            path.endsWith(".png", ignoreCase = true) -> "image/png"
            path.endsWith(".jpg", ignoreCase = true) ||
                path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            path.endsWith(".gif", ignoreCase = true) -> "image/gif"
            path.endsWith(".bmp", ignoreCase = true) -> "image/bmp"
            path.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> null // EMF/WMF/TIFF cannot be rendered by a WebView.
        }

        companion object {
            fun from(entries: Map<String, ByteArray>) =
                MediaTable(entries.filterKeys { it.startsWith("word/media/") })
        }
    }

    /** List nesting frames kept while rendering consecutive list paragraphs. */
    private class ListFrame(val level: Int, val tag: String)

    private class Renderer(
        private val relationships: RelationshipTable,
        private val numbering: NumberingInfo?,
        private val media: MediaTable,
    ) {
        private val listStack = ArrayDeque<ListFrame>()

        fun renderChildren(parent: Node, out: StringBuilder) {
            val children = parent.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is Element) {
                    renderElement(child, out)
                }
            }
        }

        private fun renderElement(element: Element, out: StringBuilder) {
            when (element.localName) {
                "p" -> renderParagraph(element, out)
                "tbl" -> {
                    closeAllLists(out)
                    renderBlock("table", element, out)
                }
                "tr" -> renderBlock("tr", element, out)
                "tc" -> renderBlock("td", element, out)
                "hyperlink" -> renderHyperlink(element, out)
                "drawing", "pict" -> renderImage(element, out)
                "r" -> renderRun(element, out)
                "t" -> out.append(escapeHtml(element.textContent.orEmpty()))
                "tab" -> out.append("<span class=\"tab\"></span>")
                "br" -> out.append("<br />")
                else -> renderChildren(element, out)
            }
        }

        private fun renderBlock(tag: String, element: Element, out: StringBuilder) {
            out.append('<').append(tag).append('>')
            renderChildren(element, out)
            out.append("</").append(tag).append('>')
        }

        private fun renderParagraph(element: Element, out: StringBuilder) {
            val properties = firstChildElement(element, "pPr")
            val styleId = properties
                ?.let { firstChildElement(it, "pStyle") }
                ?.getAttributeNS(WORD_NS, "val")
                .orEmpty()
            val alignment = properties
                ?.let { firstChildElement(it, "jc") }
                ?.getAttributeNS(WORD_NS, "val")
                .orEmpty()
            val numberingProperties = properties?.let { firstChildElement(it, "numPr") }

            val headingLevel = headingLevelFor(styleId)
            if (numberingProperties != null && headingLevel == null) {
                renderListParagraph(element, numberingProperties, alignment, out)
                return
            }
            closeAllLists(out)

            val tag = if (headingLevel != null) "h$headingLevel" else "p"
            out.append('<').append(tag)
            alignmentCss(alignment)?.let { out.append(" style=\"").append(it).append('"') }
            out.append('>')
            renderChildren(element, out)
            out.append("</").append(tag).append('>')
        }

        private fun renderListParagraph(
            element: Element,
            numberingProperties: Element,
            alignment: String,
            out: StringBuilder,
        ) {
            val numId = firstChildElement(numberingProperties, "numId")
                ?.getAttributeNS(WORD_NS, "val")
                .orEmpty()
            val level = firstChildElement(numberingProperties, "ilvl")
                ?.getAttributeNS(WORD_NS, "val")
                ?.toIntOrNull() ?: 0
            val format = numbering?.formatFor(numId, level)
            val tag = if (isOrderedFormat(format)) "ol" else "ul"

            while (listStack.isNotEmpty() && listStack.last().level > level) {
                closeOneList(out)
            }
            val top = listStack.lastOrNull()
            when {
                top == null || level > top.level -> {
                    out.append('<').append(tag).append("><li")
                    alignmentCss(alignment)?.let { out.append(" style=\"").append(it).append('"') }
                    out.append('>')
                    listStack.addLast(ListFrame(level, tag))
                }
                top.level == level && top.tag == tag -> {
                    out.append("</li><li")
                    alignmentCss(alignment)?.let { out.append(" style=\"").append(it).append('"') }
                    out.append('>')
                }
                else -> {
                    closeOneList(out)
                    out.append('<').append(tag).append("><li")
                    alignmentCss(alignment)?.let { out.append(" style=\"").append(it).append('"') }
                    out.append('>')
                    listStack.addLast(ListFrame(level, tag))
                }
            }
            renderChildren(element, out)
        }

        /** Closes the deepest open list frame (`</li></ul|ol>`). */
        private fun closeOneList(out: StringBuilder) {
            val frame = listStack.removeLastOrNull() ?: return
            out.append("</li></").append(frame.tag).append('>')
        }

        fun closeAllLists(out: StringBuilder) {
            while (listStack.isNotEmpty()) {
                closeOneList(out)
            }
        }

        private fun isOrderedFormat(format: String?): Boolean {
            if (format == null) return false
            val value = format.lowercase()
            return value.contains("decimal") ||
                value.contains("roman") ||
                value.contains("letter")
        }

        private fun headingLevelFor(styleId: String): Int? {
            val match = Regex("(?i)heading\\s*([1-6])").find(styleId) ?: return null
            return match.groupValues[1].toIntOrNull()
        }

        private fun alignmentCss(alignment: String): String? = when (alignment.lowercase()) {
            "center" -> "text-align:center"
            "right", "end" -> "text-align:right"
            "both", "justify" -> "text-align:justify"
            else -> null
        }

        private fun renderHyperlink(element: Element, out: StringBuilder) {
            val id = element.getAttributeNS(RELATIONSHIP_NS, "id")
            val target = relationships.targetFor(id)
            if (target != null && isShareableUrl(target)) {
                out.append("<a href=\"").append(escapeHtml(target)).append("\">")
                renderChildren(element, out)
                out.append("</a>")
            } else {
                renderChildren(element, out)
            }
        }

        private fun isShareableUrl(target: String): Boolean =
            target.startsWith("http://") ||
                target.startsWith("https://") ||
                target.startsWith("mailto:")

        private fun renderImage(element: Element, out: StringBuilder) {
            val relationshipId = findImageRelationshipId(element) ?: return
            val path = relationships.targetFor(relationshipId) ?: return
            val dataUri = media.dataUriFor(path) ?: return
            out.append("<img src=\"").append(dataUri).append("\" alt=\"\" />")
        }

        private fun findImageRelationshipId(element: Element): String? {
            // DrawingML (modern w:drawing) ...
            firstDescendantElement(element, "blip")
                ?.getAttributeNS(RELATIONSHIP_NS, "embed")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            // ... and legacy VML (w:pict).
            firstDescendantElement(element, "imagedata")
                ?.getAttributeNS(RELATIONSHIP_NS, "id")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            return null
        }

        private fun renderRun(element: Element, out: StringBuilder) {
            val properties = firstChildElement(element, "rPr")
            val bold = isToggleOn(firstChildElement(properties, "b"))
            val italic = isToggleOn(firstChildElement(properties, "i"))
            val underline = isToggleOn(firstChildElement(properties, "u"))
            val strike = isToggleOn(firstChildElement(properties, "strike"))
            val color = properties
                ?.let { parent -> firstChildElement(parent, "color") }
                ?.getAttributeNS(WORD_NS, "val")
                ?.takeIf { it.length == 6 && it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }
            val size = properties
                ?.let { parent -> firstChildElement(parent, "sz") }
                ?.getAttributeNS(WORD_NS, "val")
                ?.toFloatOrNull()
                ?.takeIf { it > 0f }
                ?.coerceAtLeast(12f)

            if (bold) out.append("<strong>")
            if (italic) out.append("<em>")
            if (underline) out.append("<u>")
            if (strike) out.append("<s>")
            if (color != null || size != null) {
                out.append("<span style=\"")
                color?.let { out.append("color:#").append(it.lowercase()) }
                if (color != null && size != null) out.append(';')
                size?.let { points ->
                    val half = points / 2f
                    val text = if (half == half.toInt().toFloat()) {
                        half.toInt().toString()
                    } else {
                        half.toString()
                    }
                    out.append("font-size:").append(text).append("pt")
                }
                out.append("\">")
            }

            renderChildren(element, out)

            if (color != null || size != null) out.append("</span>")
            if (strike) out.append("</s>")
            if (underline) out.append("</u>")
            if (italic) out.append("</em>")
            if (bold) out.append("</strong>")
        }

        private fun isToggleOn(element: Element?): Boolean {
            if (element == null) return false
            val value = element.getAttributeNS(WORD_NS, "val").lowercase()
            // OOXML uses values such as "single" or "double" for underline, and "none"/"0" for off.
            return value.isBlank() || value !in setOf("0", "false", "off", "none")
        }
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

    private fun htmlShell(body: String): String = """
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
            background: #ffffff;
            color: #1f2328;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
            font-size: 16px;
            line-height: 1.55;
            overflow-wrap: anywhere;
        }
        p { margin: 0 0 12px; }
        p:empty::after { content: "\00a0"; }
        h1, h2, h3, h4, h5, h6 { margin: 20px 0 10px; line-height: 1.25; }
        h1 { font-size: 1.6em; }
        h2 { font-size: 1.4em; }
        h3 { font-size: 1.2em; }
        h4, h5, h6 { font-size: 1.05em; }
        ul, ol { margin: 0 0 12px; padding-left: 28px; }
        li { margin: 4px 0; }
        a { color: #0b57d0; }
        img { max-width: 100%; height: auto; }
        table { border-collapse: collapse; width: 100%; margin: 12px 0; }
        td, th { border: 1px solid #d0d7de; padding: 6px 8px; vertical-align: top; }
        .tab { display: inline-block; width: 2em; }
        </style>
        </head>
        <body>
        $body
        </body>
        </html>
    """.trimIndent()
}
