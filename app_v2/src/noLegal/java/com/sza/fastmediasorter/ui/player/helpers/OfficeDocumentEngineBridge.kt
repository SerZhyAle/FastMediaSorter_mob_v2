package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.data.common.OfficeDocumentFamily
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import timber.log.Timber
import java.io.File
import java.util.zip.ZipFile

/**
 * noLegal engine bridge that normalizes Office documents into read-only HTML (S0301 Phase 03).
 *
 * Phase-1 default engine per strategic spec §5.1.1 / §6.2: a JVM/WebView hybrid. OOXML and
 * ODF files are ZIP+XML containers, so they are parsed with the JDK ZIP reader and the
 * already-bundled jsoup XML parser - no heavy native Office core and no extra heavy
 * dependency, which keeps the noLegal slice inside the §6.6 medium budget and on a permissive
 * license (§6.3). RTF is reduced with a small control-word stripper.
 *
 * Quality is explicitly preview-level (owner accepted in §6.1): text, paragraph, and table
 * structure are preserved; pixel-perfect layout is not promised. Legacy binary formats
 * (.doc / .xls / .ppt) are out of the phase-1 default budget and return null so the caller
 * falls back to the external-open path.
 *
 * The bridge is engine-only: it performs no UI work and holds no Android view state.
 */
class OfficeDocumentEngineBridge {

    /** Best-effort render. Returns sanitized HTML, or null when the family/format is unsupported. */
    fun renderToHtml(file: File, family: OfficeDocumentFamily): String? {
        val ext = file.extension.lowercase()
        return try {
            val body = when (ext) {
                "docx" -> renderWordOoxml(file)
                "odt" -> renderOdfText(file)
                "rtf" -> renderRtf(file)
                "xlsx" -> renderSpreadsheetOoxml(file)
                "ods" -> renderOdfSpreadsheet(file)
                "pptx" -> renderPresentationOoxml(file)
                "odp" -> renderOdfPresentation(file)
                // Legacy binary formats are out of the phase-1 default budget.
                else -> null
            }
            body?.let { wrapDocument(file.name, it) }
        } catch (e: Exception) {
            Timber.w(e, "OfficeDocumentEngineBridge: failed to render ${file.name}")
            null
        }
    }

    // ── Word family ──────────────────────────────────────────────────────────

    private fun renderWordOoxml(file: File): String? {
        val xml = readZipEntry(file, "word/document.xml") ?: return null
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val sb = StringBuilder()
        for (p in doc.getElementsByTag("w:p")) {
            val text = p.getElementsByTag("w:t").joinToString("") { it.text() }
            if (text.isBlank()) {
                sb.append("<p>&nbsp;</p>")
            } else {
                sb.append("<p>").append(escape(text)).append("</p>")
            }
        }
        return sb.takeIf { it.isNotEmpty() }?.toString()
    }

    private fun renderOdfText(file: File): String? {
        val xml = readZipEntry(file, "content.xml") ?: return null
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val sb = StringBuilder()
        for (node in doc.getElementsByTag("text:h") + doc.getElementsByTag("text:p")) {
            val text = node.text()
            if (text.isNotBlank()) sb.append("<p>").append(escape(text)).append("</p>")
        }
        return sb.takeIf { it.isNotEmpty() }?.toString()
    }

    private fun renderRtf(file: File): String {
        val raw = file.readText(Charsets.ISO_8859_1)
        val plain = stripRtf(raw)
        return plain.split('\n').joinToString("") { line ->
            if (line.isBlank()) "<p>&nbsp;</p>" else "<p>${escape(line)}</p>"
        }
    }

    // ── Spreadsheet family ───────────────────────────────────────────────────

    private fun renderSpreadsheetOoxml(file: File): String? {
        ZipFile(file).use { zip ->
            val sharedStrings = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
                val doc = Jsoup.parse(zip.getInputStream(entry), "UTF-8", "", Parser.xmlParser())
                doc.getElementsByTag("si").map { si -> si.getElementsByTag("t").joinToString("") { it.text() } }
            } ?: emptyList()

            val sheets = zip.entries().asSequence()
                .filter { it.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
                .sortedBy { it.name }
                .toList()
            if (sheets.isEmpty()) return null

            val sb = StringBuilder()
            for ((index, entry) in sheets.withIndex()) {
                val doc = Jsoup.parse(zip.getInputStream(entry), "UTF-8", "", Parser.xmlParser())
                sb.append("<h3>").append(escape("Sheet ${index + 1}")).append("</h3>")
                sb.append("<table>")
                for (row in doc.getElementsByTag("row")) {
                    sb.append("<tr>")
                    for (cell in row.getElementsByTag("c")) {
                        val value = resolveSpreadsheetCell(cell, sharedStrings)
                        sb.append("<td>").append(escape(value)).append("</td>")
                    }
                    sb.append("</tr>")
                }
                sb.append("</table>")
            }
            return sb.toString()
        }
    }

    private fun resolveSpreadsheetCell(cell: org.jsoup.nodes.Element, sharedStrings: List<String>): String {
        val type = cell.attr("t")
        return when (type) {
            "s" -> {
                val idx = cell.getElementsByTag("v").firstOrNull()?.text()?.toIntOrNull()
                idx?.let { sharedStrings.getOrNull(it) }.orEmpty()
            }
            "inlineStr" -> cell.getElementsByTag("t").joinToString("") { it.text() }
            else -> cell.getElementsByTag("v").firstOrNull()?.text().orEmpty()
        }
    }

    private fun renderOdfSpreadsheet(file: File): String? {
        val xml = readZipEntry(file, "content.xml") ?: return null
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val tables = doc.getElementsByTag("table:table")
        if (tables.isEmpty()) return null
        val sb = StringBuilder()
        for ((index, table) in tables.withIndex()) {
            sb.append("<h3>").append(escape("Sheet ${index + 1}")).append("</h3>")
            sb.append("<table>")
            for (row in table.getElementsByTag("table:table-row")) {
                sb.append("<tr>")
                for (cell in row.getElementsByTag("table:table-cell")) {
                    sb.append("<td>").append(escape(cell.text())).append("</td>")
                }
                sb.append("</tr>")
            }
            sb.append("</table>")
        }
        return sb.toString()
    }

    // ── Presentation family ──────────────────────────────────────────────────

    private fun renderPresentationOoxml(file: File): String? {
        ZipFile(file).use { zip ->
            val slides = zip.entries().asSequence()
                .filter { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
                .sortedBy { naturalSlideIndex(it.name) }
                .toList()
            if (slides.isEmpty()) return null

            val sb = StringBuilder()
            for ((index, entry) in slides.withIndex()) {
                val doc = Jsoup.parse(zip.getInputStream(entry), "UTF-8", "", Parser.xmlParser())
                sb.append("<section class=\"slide\">")
                sb.append("<h3>").append(escape("Slide ${index + 1}")).append("</h3>")
                for (t in doc.getElementsByTag("a:p")) {
                    val text = t.getElementsByTag("a:t").joinToString("") { it.text() }
                    if (text.isNotBlank()) sb.append("<p>").append(escape(text)).append("</p>")
                }
                sb.append("</section>")
            }
            return sb.toString()
        }
    }

    private fun renderOdfPresentation(file: File): String? {
        val xml = readZipEntry(file, "content.xml") ?: return null
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val pages = doc.getElementsByTag("draw:page")
        if (pages.isEmpty()) return null
        val sb = StringBuilder()
        for ((index, page) in pages.withIndex()) {
            sb.append("<section class=\"slide\">")
            sb.append("<h3>").append(escape("Slide ${index + 1}")).append("</h3>")
            for (p in page.getElementsByTag("text:p")) {
                if (p.text().isNotBlank()) sb.append("<p>").append(escape(p.text())).append("</p>")
            }
            sb.append("</section>")
        }
        return sb.toString()
    }

    // ── Helpers ────────────────────────────────────────────────────────────--

    private fun naturalSlideIndex(name: String): Int =
        Regex("slide(\\d+)\\.xml").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun readZipEntry(file: File, entryName: String): String? =
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(entryName) ?: return null
            zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

    /** Minimal RTF control-word stripper - good enough for a phase-1 text preview. */
    private fun stripRtf(rtf: String): String {
        val sb = StringBuilder()
        var i = 0
        var depth = 0
        while (i < rtf.length) {
            val c = rtf[i]
            when {
                c == '\\' && i + 1 < rtf.length && rtf[i + 1] == 'p' && rtf.startsWith("\\par", i) -> {
                    sb.append('\n'); i += 4
                }
                c == '\\' && i + 1 < rtf.length && (rtf[i + 1].isLetter()) -> {
                    // Skip a control word and its optional numeric argument.
                    i++
                    while (i < rtf.length && rtf[i].isLetter()) i++
                    if (i < rtf.length && (rtf[i] == '-' || rtf[i].isDigit())) {
                        if (rtf[i] == '-') i++
                        while (i < rtf.length && rtf[i].isDigit()) i++
                    }
                    if (i < rtf.length && rtf[i] == ' ') i++
                }
                c == '\\' && i + 1 < rtf.length -> { sb.append(rtf[i + 1]); i += 2 }
                c == '{' -> { depth++; i++ }
                c == '}' -> { depth--; i++ }
                c == '\n' || c == '\r' -> i++
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun wrapDocument(title: String, body: String): String = """
        <!DOCTYPE html>
        <html><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: serif; padding: 16px; line-height: 1.5; color: #1a1a1a; background: #ffffff; }
          h3 { margin: 18px 0 8px; font-size: 1.05em; color: #444; }
          p { margin: 0 0 8px; }
          table { border-collapse: collapse; margin: 4px 0 16px; width: 100%; }
          td { border: 1px solid #ccc; padding: 4px 8px; font-size: 0.9em; vertical-align: top; }
          section.slide { border: 1px solid #ddd; border-radius: 6px; padding: 12px; margin: 0 0 16px; }
        </style>
        </head><body>${escape(title).let { "<!-- $it -->" }}$body</body></html>
    """.trimIndent()
}
