package app.olauncher.helper

import java.io.File

/**
 * Builds the page for an Anki card. The page is loaded with [BASE_URL] as its origin; the WebView
 * serves KaTeX from the app's assets under [KATEX_PATH] and every other path of that host from
 * the AnkiDroid media folder, and blocks all other hosts.
 */
object AnkiCardHtml {

    const val HOST = "olauncher.anki"
    const val BASE_URL = "https://$HOST/"
    const val KATEX_PATH = "/_olauncher/katex/"

    // Audio is not played; AnkiDroid hands sound as [sound:file], newer renders as [anki:play:q:0]
    private val soundTags = Regex("""\[sound:[^\]]*]|\[anki:play:[^\]]*]""")

    fun stripSoundTags(html: String): String = soundTags.replace(html, "")

    /**
     * [textColor] is a CSS color for the card text. The note type's own background is dropped so the
     * card sits on the pause screen; AnkiDroid's night mode classes let note types restyle for dark.
     */
    fun build(content: String, css: String, textColor: String, nightMode: Boolean): String {
        val bodyClass = if (nightMode) "card nightMode night_mode" else "card"
        return """
            <!doctype html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <link rel="stylesheet" href="${KATEX_PATH}katex.min.css">
            <style>${css.replace("</style", "<\\/style")}</style>
            <style>
            html, body, .card { background: transparent !important; }
            body { margin: 0; padding: 16px 24px; color: $textColor; }
            .card { color: $textColor !important; }
            img { max-width: 100%; height: auto; }
            </style>
            </head>
            <body class="$bodyClass">
            ${stripSoundTags(content)}
            <script src="${KATEX_PATH}katex.min.js"></script>
            <script src="${KATEX_PATH}auto-render.min.js"></script>
            <script>
            renderMathInElement(document.body, {
                delimiters: [
                    { left: "\\[", right: "\\]", display: true },
                    { left: "\\(", right: "\\)", display: false }
                ],
                throwOnError: false
            });
            </script>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * The media file for a decoded request [path] such as "/image.png", or null if the path leaves
     * [mediaDirectory]. Anki keeps media in one flat folder, so only direct children are served.
     */
    fun mediaFile(mediaDirectory: File, path: String): File? {
        val name = path.removePrefix("/")
        if (name.isEmpty() || name.contains('/') || name.contains('\\') || name == "." || name == "..")
            return null
        val file = File(mediaDirectory, name)
        return if (file.canonicalFile.parentFile == mediaDirectory.canonicalFile) file else null
    }
}
