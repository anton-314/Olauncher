package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AnkiCardHtmlTest {

    @Test
    fun `sound tags of both formats are removed`() {
        assertEquals(
            "Hund  dog ",
            AnkiCardHtml.stripSoundTags("Hund [sound:hund.mp3] dog [anki:play:a:0]")
        )
    }

    @Test
    fun `text without sound tags is unchanged`() {
        val html = """<b>\(x^2\)</b> [not a tag] <img src="a.png">"""
        assertEquals(html, AnkiCardHtml.stripSoundTags(html))
    }

    @Test
    fun `page holds the card, its styling and the night mode classes`() {
        val page = AnkiCardHtml.build("<b>Frage</b>", ".card { font-size: 20px; }", "#ffffff", nightMode = true)
        assertTrue(page.contains("<b>Frage</b>"))
        assertTrue(page.contains(".card { font-size: 20px; }"))
        assertTrue(page.contains("""<body class="card nightMode night_mode">"""))
        assertTrue(page.contains("color: #ffffff"))
        assertTrue(page.contains("${AnkiCardHtml.KATEX_PATH}katex.min.js"))
    }

    @Test
    fun `day mode body has only the card class`() {
        val page = AnkiCardHtml.build("x", "", "#000000", nightMode = false)
        assertTrue(page.contains("""<body class="card">"""))
        assertFalse(page.contains("nightMode"))
    }

    @Test
    fun `note type css cannot close its style element`() {
        val page = AnkiCardHtml.build("x", "a{}</style><script>alert(1)</script>", "#000", nightMode = false)
        assertFalse(page.contains("</style><script>alert(1)"))
    }

    @Test
    fun `media files resolve to direct children of the media folder only`() {
        val dir = Files.createTempDirectory("media").toFile()
        try {
            assertEquals(File(dir, "paste-1.jpg"), AnkiCardHtml.mediaFile(dir, "/paste-1.jpg"))
            assertEquals(File(dir, "latex-ab12.svg"), AnkiCardHtml.mediaFile(dir, "latex-ab12.svg"))
            assertEquals(File(dir, "with space.png"), AnkiCardHtml.mediaFile(dir, "/with space.png"))
            assertNull(AnkiCardHtml.mediaFile(dir, "/../secret.txt"))
            assertNull(AnkiCardHtml.mediaFile(dir, "/sub/file.png"))
            assertNull(AnkiCardHtml.mediaFile(dir, "/.."))
            assertNull(AnkiCardHtml.mediaFile(dir, "/"))
            assertNull(AnkiCardHtml.mediaFile(dir, "..\\x.png"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
