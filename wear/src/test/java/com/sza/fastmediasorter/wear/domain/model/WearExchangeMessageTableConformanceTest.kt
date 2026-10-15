package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Holds the watch's strings to the message table of contract DEVICE-EXCHANGE items CZ and DR key by key and
 * language by language (section 11, conformance of items CV to DA). Skipped when the contracts catalogue is not on
 * this machine.
 */
class WearExchangeMessageTableConformanceTest {

    private val locales = listOf("values", "values-ru", "values-uk")

    // Table code cell -> the watch's string key; the clipboard variant and the two `unreachable` rows are named
    // by the qualifier of the first cell.
    private val keyByCell: Map<String, String> = mapOf(
        "`bad-credentials`" to "wear_exchange_reason_bad_credentials",
        "`device-revoked`" to "wear_exchange_reason_device_revoked",
        "`signed-out`" to "wear_exchange_reason_signed_out",
        "`version-unsupported`" to "wear_exchange_reason_version_unsupported",
        "`tls-required`" to "wear_exchange_reason_tls_required",
        "`id-collision`" to "wear_exchange_reason_id_collision",
        "`capacity`" to "wear_exchange_reason_capacity",
        "`port-unavailable`" to "wear_exchange_reason_port_unavailable",
        "`unavailable`" to "wear_exchange_reason_unavailable",
        "`rate-limited`" to "wear_exchange_reason_rate_limited",
        "`protection-unsupported`" to "wear_exchange_reason_protection_unsupported",
        "`limit-reached`" to "wear_exchange_reason_limit_reached",
        "`limit-reached` on a clipboard send" to "wear_exchange_reason_limit_clipboard",
        "`not-permitted`" to "wear_exchange_reason_not_permitted",
        "`unreachable` to a server" to "wear_exchange_reason_unreachable",
        "`unreachable` to a device" to "wear_exchange_reason_unreachable_device",
        "`unanswered`" to "wear_exchange_reason_unanswered",
        "`tls-failed`" to "wear_exchange_reason_tls_failed",
        "`certificate-changed`" to "wear_exchange_reason_certificate_changed",
        "`server-offline`" to "wear_exchange_reason_server_offline",
        "`resource-locked`" to "wear_exchange_reason_resource_locked",
        "`e2e-missing`" to "wear_exchange_reason_e2e_missing",
        "`server-no-e2e`" to "wear_exchange_reason_server_no_e2e",
        "`sealed-failed`" to "wear_exchange_reason_sealed_failed",
    )

    @Test
    fun `every row the watch can meet is held word for word in the three authored languages`() {
        val rows = messageRows()
        val mismatches = keyByCell.flatMap { (cell, key) ->
            val row = rows[cell] ?: return@flatMap listOf("$cell: no such row in the contract")
            locales.indices.mapNotNull { locale ->
                val actual = resources(locales[locale])[key]
                val expected = row[COL_TEXT_EN + locale]
                expected.takeIf { it != actual }?.let { "$key [${locales[locale]}]: '$actual' != '$it'" }
            }
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun `the fixed line of item DA reads as the contract says`() {
        val values = locales.map { resources(it)["wear_exchange_details_on_phone"] }
        assertEquals(
            listOf("See the details on the phone.", "Подробности - на телефоне.", "Подробиці - на телефоні."),
            values,
        )
    }

    private fun messageRows(): Map<String, List<String>> = contractSection()
        .filter { it.startsWith("| `") }
        .map { line -> line.trim().removePrefix("|").removeSuffix("|").split(" | ").map { it.trim() } }
        .filter { it.size == COLUMNS && it[COL_KIND] in KINDS }
        .associateBy { it[COL_CODE] }

    private fun contractSection(): List<String> {
        val root = System.getenv("FMS_CONTRACTS_ROOT")
        assumeTrue("FMS_CONTRACTS_ROOT is not set", !root.isNullOrBlank())
        val file = File(root, "device-exchange/README.md")
        assumeTrue("the contract file is not there", file.isFile)
        val lines = file.readLines(Charsets.UTF_8)
        // Found by item letter, never by section number: letters are permanent, the consolidation of 0.30 moved
        // every section from 14 to 27. Item DR adds rows to the table.
        return CONTRACT_ITEMS.flatMap { itemBlock(lines, it) }
    }

    // A present contract without the item fails rather than skips: a skip would stop guarding the table silently.
    private fun itemBlock(lines: List<String>, letter: String): List<String> {
        val start = lines.indexOfFirst { ITEM_HEADING.find(it)?.groupValues?.get(1) == letter }
        check(start >= 0) { "item $letter is not in the contract" }
        val next = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("**") || lines[it].startsWith("#") }
        return lines.subList(start, next ?: lines.size)
    }

    private fun resources(directory: String): Map<String, String> {
        val file = File("src/main/res/$directory/strings.xml")
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
        const val COLUMNS = 6
        const val COL_CODE = 0
        const val COL_KIND = 1
        const val COL_TEXT_EN = 3
        val KINDS = setOf("wire", "cast", "local", "state", "result", "wire, cast")
        val ESCAPE = Regex("""\\(.)""")
        val ITEM_HEADING = Regex("""^\*\*([A-Z]{1,2})(?: \[[^\]]*])? - """)
        val CONTRACT_ITEMS = listOf("CV", "CW", "CX", "CY", "CZ", "DA", "DR")
    }
}
