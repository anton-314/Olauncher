package app.olauncher.helper

import java.text.Normalizer

/** Matches app labels against the drawer's search text, ignoring case, accents and separators. */
object AppSearch {

    private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separatorsRegex = Regex("[-_+,.`'\\s\\p{Z}]")

    fun matches(label: String, query: CharSequence): Boolean {
        if (label.contains(query.trim(), true)) return true
        val normalizedQuery = normalize(query)
        return normalizedQuery.isNotEmpty() && normalize(label).contains(normalizedQuery, true)
    }

    /**
     * The part of [label] that [query] matched, to highlight it, or null if it doesn't match.
     * A match that ignored accents or separators covers the label characters it came from,
     * e.g. "Google Maps" for "lema" gives "le Ma".
     */
    fun matchRange(label: String, query: CharSequence): IntRange? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val plain = label.indexOf(trimmed.toString(), ignoreCase = true)
        if (plain >= 0) return plain until plain + trimmed.length

        val normalizedQuery = normalize(trimmed)
        if (normalizedQuery.isEmpty()) return null
        // The label normalized one character at a time, and for each normalized character the
        // index of the label character it came from
        val normalized = StringBuilder()
        val origins = ArrayList<Int>(label.length)
        var i = 0
        while (i < label.length) {
            val next = label.offsetByCodePoints(i, 1)
            val piece = normalize(label.substring(i, next))
            normalized.append(piece)
            repeat(piece.length) { origins.add(i) }
            i = next
        }
        val start = normalized.indexOf(normalizedQuery, ignoreCase = true)
        if (start < 0) return null
        val last = origins[start + normalizedQuery.length - 1]
        return origins[start] until label.offsetByCodePoints(last, 1)
    }

    private fun normalize(text: CharSequence): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
            .replace(separatorsRegex, "")
}
