package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Holds this program's strings to the tables of contract DEVICE-EXCHANGE items CY, CZ and DR key by key and
 * language by language: the contract produces no machine-readable copy, so each product checks its own words
 * (section 11, conformance of items CV to DA). Skipped when the contracts catalogue is not on this machine.
 */
class ExchangeMessageTableConformanceTest {

    private class Row(val cells: List<String>) {
        val first: String get() = cells[COL_FIRST]
        val second: String get() = cells[COL_SECOND]
        val third: String get() = cells[COL_THIRD]
        fun text(locale: Int): String = cells[COL_TEXT_EN + locale]
    }

    private val locales = listOf("values", "values-ru", "values-uk")

    @Test
    fun `every message row is held word for word in the three authored languages`() {
        val rows = messageRows()
        val mismatches = rows.flatMap { row ->
            val key = messageKey(row.first)
            locales.indices.mapNotNull { locale ->
                val actual = resources(locales[locale])[key]
                row.text(locale).takeIf { it != actual }?.let { "$key [${locales[locale]}]: '$actual' != '$it'" }
            }
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun `every step is held word for word in the three authored languages`() {
        val mismatches = stepRows().flatMap { row ->
            val key = "exchange_step_" + stripTicks(row.second).replace('-', '_')
            locales.indices.mapNotNull { locale ->
                val actual = resources(locales[locale])[key]
                row.text(locale).takeIf { it != actual }?.let { "$key [${locales[locale]}]: '$actual' != '$it'" }
            }
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun `the catalogue matches the table in rows and in steps`() {
        val byKey = messageRows().associateBy { messageKey(it.first) }
        val tableKeys = ExchangeMessage.entries
            .filter { it.kind !in NOT_IN_README && it != ExchangeMessage.REFUSED }
            .associateBy(::keyOf)
        assertEquals(byKey.keys, tableKeys.keys)
        tableKeys.forEach { (key, message) ->
            val stepCell = stripTicks(byKey.getValue(key).third)
            assertEquals(key, stepCell.takeIf { it != NO_STEP }, message.step?.id)
        }
    }

    @Test
    fun `the ladder matches the step table in order and in where each step applies`() {
        val rows = stepRows()
        assertEquals(rows.map { stripTicks(it.second) }, ExchangeCheckStep.entries.map { it.id })
        rows.forEachIndexed { index, row ->
            assertEquals(row.third.uppercase(), ExchangeCheckStep.entries[index].applies.name)
        }
    }

    @Test
    fun `the generic refusal reads as the contract says for an unknown reason`() {
        val values = locales.map { resources(it)["exchange_failure_refused"] }
        assertEquals(
            listOf("The server refused this.", "Сервер отказал в этом.", "Сервер відмовив у цьому."),
            values,
        )
    }

    @Test
    fun `the admin-disabled row of the web binding is held word for word in the three authored languages`() {
        val lines = contractFile(WEB_BINDING).readLines(Charsets.UTF_8)
        val row = tableRows(lines).singleOrNull { it.first == "`${ExchangeMessage.ADMIN_DISABLED.code}`" }
        checkNotNull(row) { "the admin-disabled row is not in $WEB_BINDING" }
        val key = keyOf(ExchangeMessage.ADMIN_DISABLED)
        val actual = locales.map { resources(it)[key] }
        assertEquals(locales.indices.map(row::text), actual)
    }

    @Test
    fun `every administration reply code is held word for word and the codes match the enum`() {
        val lines = contractFile(REMOTE_ADMIN).readLines(Charsets.UTF_8)
        val start = lines.indexOfFirst { it.startsWith(REPLY_CODES_HEADING) }
        check(start >= 0) { "$REPLY_CODES_HEADING is not in $REMOTE_ADMIN" }
        val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        val rows = tableRows(lines.subList(start, end), REPLY_CODE_COLUMNS).filter { CODE_CELL.matches(it.first) }
        val codes = rows.map { stripTicks(it.first) }
        assertEquals(ExchangeAdminReplyCode.entries.map { it.code }.toSet(), codes.toSet())
        val mismatches = rows.flatMap { row ->
            val key = "exchange_admin_reply_" + stripTicks(row.first).replace('-', '_')
            locales.indices.mapNotNull { locale ->
                val expected = row.cells[REPLY_TEXT_EN + locale]
                val actual = resources(locales[locale])[key]
                expected.takeIf { it != actual }?.let { "$key [${locales[locale]}]: '$actual' != '$it'" }
            }
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    private fun keyOf(message: ExchangeMessage): String {
        val variant = when (message) {
            ExchangeMessage.BAD_CREDENTIALS_ONE_TIME_CODE -> "_one_time_code"
            ExchangeMessage.LIMIT_REACHED_CLIPBOARD -> "_clipboard"
            ExchangeMessage.UNREACHABLE_SERVER -> "_server"
            ExchangeMessage.UNREACHABLE_DEVICE -> "_device"
            else -> ""
        }
        return KEY_BY_CODE[message.code] ?: ("exchange_failure_" + message.code.replace('-', '_') + variant)
    }

    private fun messageKey(firstCell: String): String {
        val match = checkNotNull(CODE_CELL.matchEntire(firstCell)) { "not a code cell: $firstCell" }
        val code = match.groupValues[1]
        val suffix = when (val qualifier = match.groupValues[2].trim()) {
            "" -> ""
            "with a one-time code" -> "_one_time_code"
            "on a clipboard send" -> "_clipboard"
            "to a server" -> "_server"
            "to a device" -> "_device"
            else -> error("unknown qualifier: $qualifier")
        }
        return KEY_BY_CODE[code] ?: "exchange_failure_${code.replace('-', '_')}$suffix"
    }

    private fun stripTicks(cell: String): String = cell.trim('`')

    // A row the table already holds wins over the amendment's prose, so a later consolidation that adds the table row
    // changes nothing here.
    private fun messageRows(): List<Row> {
        val tabled = tableRows().filter { CODE_CELL.matches(it.first) && it.second in KINDS }
        val tabledKeys = tabled.map { messageKey(it.first) }.toSet()
        return tabled + amendmentRows().filter { messageKey(it.first) !in tabledKeys }
    }

    // A normative amendment may state a row in prose instead of in the table: the amendment of 2026-10-17 does so for
    // `format-unsupported` (the EN, RU and UK texts quoted on three lines, then its code, kind and step) and prevails
    // over the table, which never gained the row.
    private fun amendmentRows(): List<Row> {
        val lines = contractFile(README).readLines(Charsets.UTF_8)
        return lines.mapIndexedNotNull { index, line ->
            AMENDMENT_ROW.find(line)?.let { match ->
                val quoted = lines.subList(index - AMENDMENT_LANGUAGES.size, index).map {
                    checkNotNull(AMENDMENT_TEXT.matchEntire(it)) { "not a quoted text of an amendment row: $it" }
                }
                check(quoted.map { it.groupValues[1] } == AMENDMENT_LANGUAGES) { "texts of ${match.value} misordered" }
                val head = listOf("`${match.groupValues[1]}`", match.groupValues[2], match.groupValues[3])
                Row(head + quoted.map { it.groupValues[2] })
            }
        }
    }

    private fun stepRows(): List<Row> = tableRows().filter { it.first.toIntOrNull() != null }

    private fun tableRows(): List<Row> = tableRows(contractSection())

    // A binding document indents its table under a bullet, so a row is recognised after trimming.
    private fun tableRows(lines: List<String>, columns: Int = COLUMNS): List<Row> = lines
        .map(String::trim)
        .filter { it.startsWith("| ") && !it.startsWith("| ---") }
        .map { line -> line.removePrefix("|").removeSuffix("|").split(" | ").map { it.trim() } }
        .filter { it.size == columns }
        .map(::Row)

    private fun contractFile(name: String): File {
        val root = System.getenv("FMS_CONTRACTS_ROOT")
        assumeTrue("FMS_CONTRACTS_ROOT is not set", !root.isNullOrBlank())
        val file = File(root, name)
        assumeTrue("the contract file is not there", file.isFile)
        return file
    }

    private fun contractSection(): List<String> {
        val lines = contractFile(README).readLines(Charsets.UTF_8)
        // Found by item letter, never by section number: letters are permanent, the consolidation of 0.30 moved
        // every section from 14 to 27. Item DR adds four rows to the table; item DD is a standalone product string.
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
        val file = File("src/main/res/$directory/strings_exchange.xml")
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
        const val COL_FIRST = 0
        const val COL_SECOND = 1
        const val COL_THIRD = 2
        const val COL_TEXT_EN = 3
        const val NO_STEP = "-"
        const val REPLY_CODE_COLUMNS = 4
        const val REPLY_TEXT_EN = 1
        const val README = "device-exchange/README.md"
        const val WEB_BINDING = "device-exchange/WEB-BINDING.md"
        const val REMOTE_ADMIN = "device-exchange/REMOTE-ADMIN.md"
        const val REPLY_CODES_HEADING = "## administration-reply-codes"
        val NOT_IN_README = setOf(ExchangeMessage.Kind.PRODUCT, ExchangeMessage.Kind.BINDING)
        val CODE_CELL = Regex("""`([a-z0-9-]+)`(.*)""")
        val KINDS = setOf("wire", "cast", "local", "state", "result", "wire, cast")
        val ESCAPE = Regex("""\\(.)""")
        val AMENDMENT_ROW = Regex("""^Code `([a-z0-9-]+)`, kind (\w+), step `?([a-z0-9-]+)`?\.""")
        val AMENDMENT_TEXT = Regex("""^(EN|RU|UK) "(.*)"$""")
        val AMENDMENT_LANGUAGES = listOf("EN", "RU", "UK")

        // The later-major refusal kept the name of its intake string (S4353) instead of the exchange_failure_ prefix.
        val KEY_BY_CODE = mapOf("format-unsupported" to "exchange_format_unsupported")
        val ITEM_HEADING = Regex("""^\*\*([A-Z]{1,2})(?: \[[^\]]*])? - """)
        val CONTRACT_ITEMS = listOf("CV", "CW", "CX", "CY", "CZ", "DA", "DR")
    }
}
