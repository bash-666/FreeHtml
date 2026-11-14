package com.freehtml.app

import com.freehtml.app.util.DocxToHtml
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxToHtmlTest {

    @Test
    fun convertsParagraphsRunsAndTablesToHtml() {
        val docx = docx(
            """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:r><w:t>Hola </w:t></w:r>
                  <w:r><w:rPr><w:b/></w:rPr><w:t>mundo</w:t></w:r>
                </w:p>
                <w:p>
                  <w:r><w:rPr><w:i/><w:u w:val="single"/></w:rPr><w:t>énfasis</w:t></w:r>
                </w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Celda</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
            """.trimIndent(),
        )

        val html = DocxToHtml.convert(ByteArrayInputStream(docx))

        assertTrue(html.contains("<p>Hola <strong>mundo</strong></p>"))
        assertTrue(html.contains("<p><em><u>énfasis</u></em></p>"))
        assertTrue(html.contains("<table><tr><td><p>Celda</p></td></tr></table>"))
    }

    @Test
    fun escapesTextFromTheDocument() {
        val docx = docx(
            """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>&lt;script&gt;alert(1)&lt;/script&gt;</w:t></w:r></w:p>
              </w:body>
            </w:document>
            """.trimIndent(),
        )

        val html = DocxToHtml.convert(ByteArrayInputStream(docx))

        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(!html.contains("<script>alert(1)</script>"))
    }

    @Test(expected = IOException::class)
    fun rejectsArchivesWithoutDocumentXml() {
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("word/styles.xml"))
                zip.write("<styles/>".toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }

        DocxToHtml.convert(ByteArrayInputStream(bytes))
    }

    @Test
    fun rendersHeadingsListsAndAlignment() {
        val docx = docx(
            "word/document.xml" to """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Título</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="Heading3"/></w:pPr><w:r><w:t>Sección</w:t></w:r></w:p>
                <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>Centrado</w:t></w:r></w:p>
                <w:p><w:pPr><w:numPr><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>Uno</w:t></w:r></w:p>
                <w:p><w:pPr><w:numPr><w:numId w:val="1"/><w:ilvl w:val="1"/></w:numPr></w:pPr><w:r><w:t>Sub</w:t></w:r></w:p>
                <w:p><w:pPr><w:numPr><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>Dos</w:t></w:r></w:p>
                <w:p><w:pPr><w:numPr><w:numId w:val="2"/></w:numPr></w:pPr><w:r><w:t>Primero</w:t></w:r></w:p>
              </w:body>
            </w:document>
            """.trimIndent(),
            "word/numbering.xml" to """
            <w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:abstractNum w:abstractNumId="0">
                <w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/></w:lvl>
                <w:lvl w:ilvl="1"><w:numFmt w:val="bullet"/></w:lvl>
              </w:abstractNum>
              <w:abstractNum w:abstractNumId="1">
                <w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/></w:lvl>
              </w:abstractNum>
              <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
              <w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
            </w:numbering>
            """.trimIndent(),
        )

        val html = DocxToHtml.convert(ByteArrayInputStream(docx))

        assertTrue(html.contains("<h1>Título</h1>"))
        assertTrue(html.contains("<h3>Sección</h3>"))
        assertTrue(html.contains("<p style=\"text-align:center\">Centrado</p>"))
        assertTrue(html.contains("<ul><li>Uno<ul><li>Sub</li></ul></li><li>Dos</li></ul>"))
        assertTrue(html.contains("<ol><li>Primero</li></ol>"))
    }

    @Test
    fun rendersHyperlinksAndImages() {
        val imageBytes = byteArrayOf(
            0x89.toByte(),
            'P'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
        )
        val docx = docx(
            "word/document.xml" to """
            <w:document
              xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
              xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
              xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
              <w:body>
                <w:p><w:hyperlink r:id="rId1"><w:r><w:t>Enlace</w:t></w:r></w:hyperlink></w:p>
                <w:p><w:r><w:drawing><a:blip r:embed="rId2"/></w:drawing></w:r></w:p>
                <w:p><w:r><w:rPr><w:strike/><w:color w:val="FF0000"/><w:sz w:val="28"/></w:rPr><w:t>Raro</w:t></w:r></w:p>
              </w:body>
            </w:document>
            """.trimIndent(),
            "word/_rels/document.xml.rels" to """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Target="https://example.com" TargetMode="External"/>
              <Relationship Id="rId2" Target="media/image1.png"/>
            </Relationships>
            """.trimIndent(),
            "word/media/image1.png" to imageBytes,
        )

        val html = DocxToHtml.convert(ByteArrayInputStream(docx))

        assertTrue(html.contains("<a href=\"https://example.com\">Enlace</a>"))
        assertTrue(html.contains("<img src=\"data:image/png;base64,"))
        assertTrue(html.contains("<s><span style=\"color:#ff0000;font-size:14pt\">Raro</span></s>"))
    }

    private fun docx(documentXml: String): ByteArray =
        docx("word/document.xml" to documentXml)

    private fun docx(vararg entries: Pair<String, Any>): ByteArray {
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, content) ->
                    val bytes = when (content) {
                        is String -> content.toByteArray(Charsets.UTF_8)
                        is ByteArray -> content
                        else -> error("Unsupported entry content")
                    }
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }
}
