package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * S4237: holds this program's words for the terms of contract DEVICE-EXCHANGE item CW. The message
 * table of items CY and CZ is held by [ExchangeMessageTableConformanceTest]; this class holds the dictionary
 * around it - the clipboard operations, the link with a password, the Drive channel and the word "cast".
 * The table checks are skipped when the contracts catalogue is not on this machine.
 */
class ExchangeTermsConformanceTest {

    private val locales = listOf("values", "values-ru", "values-uk")

    @Test
    fun `terms of the dictionary are held word for word by the keys that stand for them`() {
        val rows = termRows()
        val mismatches = TERM_KEYS.flatMap { (term, keys) ->
            val row = checkNotNull(rows[term]) { "term '$term' is not a row of item CW" }
            keys.flatMap { key ->
                locales.indices.mapNotNull { locale ->
                    val actual = resources(locales[locale], EXCHANGE_FILE)[key]
                    val expected = row[locale]
                    expected.takeUnless { it.equals(actual, ignoreCase = true) }
                        ?.let { "$key [${locales[locale]}]: '$actual' != '$it'" }
                }
            }
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun `the Drive channel is never called the Russian or Ukrainian name of the storage`() {
        val offenders = listOf("values-ru", "values-uk").flatMap { directory ->
            EXCHANGE_FILES.flatMap { file ->
                resources(directory, file).filterValues { it.contains(STORAGE_NAME_CYRILLIC) }
                    .keys.map { "$directory/$file: $it" }
            }
        }
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun `a cast of the English strings is a cast in Russian and Ukrainian`() {
        val english = resources("values", EXCHANGE_FILE)
        val offenders = listOf("values-ru", "values-uk").flatMap { directory ->
            val localized = resources(directory, EXCHANGE_FILE)
            english.filterValues { CAST_WORD.containsMatchIn(it) && !it.contains(PLATFORM_CASTING) }
                .keys.filter { key -> !localized[key].orEmpty().contains("cast", ignoreCase = true) }
                .map { key -> "$directory: $key" }
        }
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** Rows of item CW, keyed by the term cell, holding the English, Russian and Ukrainian wording. */
    private fun termRows(): Map<String, List<String>> {
        val root = System.getenv("FMS_CONTRACTS_ROOT")
        assumeTrue("FMS_CONTRACTS_ROOT is not set", !root.isNullOrBlank())
        val file = File(root, "device-exchange/README.md")
        assumeTrue("the contract file is not there", file.isFile)
        val lines = file.readLines(Charsets.UTF_8)
        // Found by item letter, never by section number: the consolidation of 0.30 moved sections 14 to 27, and a
        // present contract without the item fails rather than skips, so the dictionary never stops being guarded.
        val start = lines.indexOfFirst { ITEM_HEADING.find(it)?.groupValues?.get(1) == TERMS_ITEM }
        check(start >= 0) { "item $TERMS_ITEM is not in the contract" }
        val next = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("**") || lines[it].startsWith("#") }
        return lines.subList(start, next ?: lines.size)
            .filter { it.startsWith("| ") && !it.startsWith("| ---") }
            .map { line -> line.trim().removePrefix("|").removeSuffix("|").split(" | ").map { it.trim() } }
            .filter { it.size == TERM_COLUMNS }
            .associate { cells -> cells[0] to cells.drop(1) }
    }

    private fun resources(directory: String, fileName: String): Map<String, String> {
        val file = File("src/main/res/$directory/$fileName")
        assertTrue("missing ${file.path}", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to unescape(node.textContent)
        }
    }

    // Android resource escaping: a backslash makes the next character literal, `\n` is a newline.
    private fun unescape(raw: String): String = ESCAPE.replace(raw) { match ->
        if (match.groupValues[1] == "n") "\n" else match.groupValues[1]
    }

    private companion object {
        const val TERM_COLUMNS = 4
        const val EXCHANGE_FILE = "strings_exchange.xml"
        val EXCHANGE_FILES = listOf(EXCHANGE_FILE, "strings_sources.xml")
        const val STORAGE_NAME_CYRILLIC = "Google Диск"
        const val PLATFORM_CASTING = "Google Cast"
        val CAST_WORD = Regex("""\bcasts?\b""", RegexOption.IGNORE_CASE)
        val ESCAPE = Regex("""\\(.)""")
        val ITEM_HEADING = Regex("""^\*\*([A-Z]{1,2})(?: \[[^\]]*])? - """)
        const val TERMS_ITEM = "CW"

        /** Item CW term cell -> the string keys that must read exactly as the term does. */
        val TERM_KEYS = mapOf(
            "send the clipboard" to listOf("exchange_clipboard_send_label"),
            "get the clipboard" to listOf("exchange_clipboard_get_label"),
            "clipboard on the server" to listOf("exchange_clipboard_channel_name"),
            "clipboard received" to listOf("exchange_clipboard_clip_label"),
            "public link with a password" to listOf("exchange_outward_create_link", "exchange_outward_item_link"),
        )
    }
}
