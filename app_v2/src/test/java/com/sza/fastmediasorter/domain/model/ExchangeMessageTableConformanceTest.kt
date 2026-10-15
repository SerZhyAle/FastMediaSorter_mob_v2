package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Holds this program's strings to the table of contract DEVICE-EXCHANGE section 25 key by key and language by
 * language: the contract produces no machine-readable copy, so each product checks its own words (section 25,
 * conformance paragraph). Skipped when the contracts catalogue is not on this machine.
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
            .filter { it.kind != ExchangeMessage.Kind.PRODUCT && it != ExchangeMessage.REFUSED }
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

    private fun keyOf(message: ExchangeMessage): String {
        val base = "exchange_failure_" + message.code.replace('-', '_')
        val variant = when (message) {
            ExchangeMessage.BAD_CREDENTIALS_ONE_TIME_CODE -> "_one_time_code"
            ExchangeMessage.LIMIT_REACHED_CLIPBOARD -> "_clipboard"
            ExchangeMessage.UNREACHABLE_SERVER -> "_server"
            ExchangeMessage.UNREACHABLE_DEVICE -> "_device"
            else -> ""
        }
        return base + variant
    }

    private fun messageKey(firstCell: String): String {
        val match = checkNotNull(CODE_CELL.matchEntire(firstCell)) { "not a code cell: $firstCell" }
        val code = match.groupValues[1].replace('-', '_')
        val suffix = when (val qualifier = match.groupValues[2].trim()) {
            "" -> ""
            "with a one-time code" -> "_one_time_code"
            "on a clipboard send" -> "_clipboard"
            "to a server" -> "_server"
            "to a device" -> "_device"
            else -> error("unknown qualifier: $qualifier")
        }
        return "exchange_failure_$code$suffix"
    }

    private fun stripTicks(cell: String): String = cell.trim('`')

    private fun messageRows(): List<Row> = tableRows().filter { CODE_CELL.matches(it.first) && it.second in KINDS }

    private fun stepRows(): List<Row> = tableRows().filter { it.first.toIntOrNull() != null }

    private fun tableRows(): List<Row> = contractSection()
        .filter { it.startsWith("| ") && !it.startsWith("| ---") }
        .map { line -> line.trim().removePrefix("|").removeSuffix("|").split(" | ").map { it.trim() } }
        .filter { it.size == COLUMNS }
        .map(::Row)

    private fun contractSection(): List<String> {
        val root = System.getenv("FMS_CONTRACTS_ROOT")
        assumeTrue("FMS_CONTRACTS_ROOT is not set", !root.isNullOrBlank())
        val file = File(root, "device-exchange/README.md")
        assumeTrue("the contract file is not there", file.isFile)
        val lines = file.readLines(Charsets.UTF_8)
        val start = lines.indexOfFirst { it.startsWith("## 25.") }
        val end = lines.indexOfFirst { it.startsWith("## 26.") }
        assumeTrue("section 25 is not in the contract", start in 0 until end)
        // Section 27 item DR adds four rows to the same table; section 26 item DD is a standalone product string.
        val sealedStart = lines.indexOfFirst { it.startsWith("## 27.") }
        val sealedEnd = lines.indexOfFirst { it.startsWith("## Document log") }
        val sealed = if (sealedStart in 0 until sealedEnd) lines.subList(sealedStart, sealedEnd) else emptyList()
        return lines.subList(start, end) + sealed
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
        val CODE_CELL = Regex("""`([a-z0-9-]+)`(.*)""")
        val KINDS = setOf("wire", "cast", "local", "state", "result", "wire, cast")
        val ESCAPE = Regex("""\\(.)""")
    }
}
