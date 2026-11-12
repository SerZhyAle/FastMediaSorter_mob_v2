package com.sza.fastmediasorter.domain.streams.facets

import java.util.Locale

/**
 * Folds a raw catalog cell into the closed value set of STREAM-BANK 2.3 (amendment O). The publisher's
 * module does the same on its side and both are held to one shared vector file, so a bank imported from
 * an older publish and a freshly rewritten one converge on the same ids.
 *
 * Language: names of existing languages, one per language, in first-seen order; a non-blank cell with no
 * recognized language reads `english`; a blank cell stays blank. Country: an assigned ISO 3166-1 alpha-2
 * code or blank. Neither returns text it did not recognize, which is what let junk reach every picker.
 *
 * A two- or three-letter code used as a name (ISO 639-2 bibliographic or terminological, or a named ISO 639-3
 * extension code) folds to the language it names, never to a code (item P.3). The vocabulary stays closed: a
 * collective code such as `mul` names no language and reads like any unrecognized word.
 */
object StreamFacetFolding {

    private const val FALLBACK_LANGUAGE = "english"
    private const val TWO_LETTER_CODE_LENGTH = 2
    private const val THREE_LETTER_CODE_LENGTH = 3
    private const val MAX_PHRASE_WORDS = 3

    private val letterOrDigit = Regex("[\\p{L}\\p{N}]")
    private val pieceDelimiters = Regex("[,;/|]")
    private val wordDelimiters = Regex("[\\s\\-.:()/_&+]+")
    private val edgeNoise = Regex("^[#\\s.\\-_:;,!?*\"'()]+|[\\s.\\-_:;,!?*\"'()]+$")

    fun foldLanguages(cell: String): String {
        val raw = cell.trim()
        if (!letterOrDigit.containsMatchIn(raw)) return ""
        val found = LinkedHashSet<String>()
        raw.split(pieceDelimiters).forEach { piece -> found.addAll(resolvePiece(piece)) }
        return if (found.isEmpty()) FALLBACK_LANGUAGE else found.joinToString(",")
    }

    fun foldCountry(cell: String): String {
        val raw = cell.trim()
        val assigned = StreamFacetVocabulary.assignedCountries
        val upper = raw.uppercase(Locale.ROOT)
        val code = when {
            raw.isEmpty() -> null
            upper in assigned -> upper
            else -> StreamFacetVocabulary.countryLookup[StreamFacetVocabulary.foldKey(raw)]
        }
        return code?.takeIf { it in assigned }.orEmpty()
    }

    /** A whole piece first (alias, name, two- or three-letter code), then its words, longest phrase first. */
    private fun resolvePiece(piece: String): List<String> {
        val key = StreamFacetVocabulary.foldKey(piece).replace(edgeNoise, "")
        val lookup = StreamFacetVocabulary.languageLookup
        val byCode = StreamFacetVocabulary.languageNamesByCode
        val byCode3 = StreamFacetVocabulary.languageNamesByThreeLetterCode
        return when {
            key.isEmpty() -> emptyList()
            lookup.containsKey(key) -> lookup.getValue(key).split(',')
            key.length == TWO_LETTER_CODE_LENGTH && byCode.containsKey(key) -> listOf(byCode.getValue(key))
            key.length == THREE_LETTER_CODE_LENGTH && byCode3.containsKey(key) -> listOf(byCode3.getValue(key))
            else -> resolveWords(key.split(wordDelimiters).filter(String::isNotEmpty))
        }
    }

    /** A word that names no language is dropped; the caller decides what an empty result means. */
    private fun resolveWords(words: List<String>): List<String> {
        val lookup = StreamFacetVocabulary.languageLookup
        val found = mutableListOf<String>()
        var index = 0
        while (index < words.size) {
            val length = longestKnownPhrase(words, index)
            if (length == 0) {
                index++
            } else {
                found.addAll(lookup.getValue(words.subList(index, index + length).joinToString(" ")).split(','))
                index += length
            }
        }
        return found
    }

    private fun longestKnownPhrase(words: List<String>, index: Int): Int {
        val lookup = StreamFacetVocabulary.languageLookup
        return (minOf(MAX_PHRASE_WORDS, words.size - index) downTo 1).firstOrNull { length ->
            lookup.containsKey(words.subList(index, index + length).joinToString(" "))
        } ?: 0
    }
}
