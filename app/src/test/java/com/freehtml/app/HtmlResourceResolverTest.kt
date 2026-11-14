package com.freehtml.app

import com.freehtml.app.util.collectLocalReferences
import com.freehtml.app.util.inlineLocalResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlResourceResolverTest {

    @Test
    fun inlinesLinkedCssAndExternalScript() {
        val html = """
            <html><head>
            <link rel="stylesheet" href="style.css">
            <script src="app.js"></script>
            </head><body>Hola</body></html>
        """.trimIndent()

        val inlined = inlineLocalResources(
            html,
            mapOf(
                "style.css" to "h1 { color: red; }",
                "app.js" to "console.log('ok');",
            ),
        )

        assertTrue(inlined.contains("<style>\nh1 { color: red; }\n</style>"))
        assertTrue(inlined.contains("<script>\nconsole.log('ok');\n</script>"))
        assertTrue(!inlined.contains("href=\"style.css\""))
        assertTrue(!inlined.contains("src=\"app.js\""))
    }

    @Test
    fun expandsCssImports() {
        val html = "<style>@import \"base.css\";</style>"
        val inlined = inlineLocalResources(
            html,
            mapOf("base.css" to "body { margin: 0; }"),
        )

        assertTrue(inlined.contains("body { margin: 0; }"))
        assertTrue(!inlined.contains("@import"))
    }

    @Test
    fun leavesRemoteAndUnknownReferencesUntouched() {
        val html = """
            <link rel="stylesheet" href="https://example.com/a.css">
            <link rel="stylesheet" href="missing.css">
            <script src="https://cdn.example.com/b.js"></script>
        """.trimIndent()

        val inlined = inlineLocalResources(html, emptyMap())

        assertEquals(html, inlined)
    }

    @Test
    fun collectsOnlyLocalReferences() {
        val html = """
            <link rel="stylesheet" href="style.css">
            <link rel="stylesheet" href="https://example.com/a.css">
            <script src="./app.js"></script>
            <script src="data:text/javascript,console.log(1)"></script>
        """.trimIndent()

        assertEquals(setOf("style.css", "./app.js"), collectLocalReferences(html))
    }
}
