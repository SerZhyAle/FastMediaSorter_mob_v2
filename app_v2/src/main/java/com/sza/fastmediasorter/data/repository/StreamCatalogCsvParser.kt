package com.sza.fastmediasorter.data.repository

import javax.inject.Inject

/**
 * S0570: pure RFC-4180 parser for the curated stream catalog `streams.csv`. No Android, no IO -
 * just `String -> List<ParsedCatalogEntry>`. Columns are matched by header name (not position) so
 * the catalog file may reorder columns or add new ones without breaking the import.
 */
class StreamCatalogCsvParser @Inject constructor() {

    fun parse(text: String): List<ParsedCatalogEntry> {
        val rows = tokenize(text)
        if (rows.isEmpty()) return emptyList()

        // Header row -> name->index map (lower-cased, trimmed). Unknown extra columns are tolerated.
        val header = rows.first()
        val columnIndex = HashMap<String, Int>(header.size)
        header.forEachIndexed { index, name ->
            columnIndex[name.trim().lowercase()] = index
        }

        fun cell(fields: List<String>, name: String): String {
            val idx = columnIndex[name] ?: return ""
            return fields.getOrNull(idx)?.trim() ?: ""
        }

        val result = ArrayList<ParsedCatalogEntry>(rows.size - 1)
        for (i in 1 until rows.size) {
            val fields = rows[i]
            val url = cell(fields, "url")
            val name = cell(fields, "name")
            // A catalog row is only meaningful with both a target url and a display name.
            if (url.isBlank() || name.isBlank()) continue
            result += ParsedCatalogEntry(
                category = cell(fields, "category"),
                topic = cell(fields, "topic"),
                name = name,
                url = url,
                mediaKind = cell(fields, "media_kind"),
                protocol = cell(fields, "protocol"),
                format = cell(fields, "format"),
                bitrate = cell(fields, "bitrate"),
                isLive = cell(fields, "is_live").toBooleanFlag(),
                https = cell(fields, "https").toBooleanFlag(),
                language = cell(fields, "language"),
                country = cell(fields, "country"),
                homepage = cell(fields, "homepage"),
                sourceKind = cell(fields, "source_kind"),
                licenseNote = cell(fields, "license_note"),
                notes = cell(fields, "notes"),
                confidence = cell(fields, "confidence")
            )
        }
        return result
    }

    /**
     * RFC-4180 field splitter. Handles `"`-quoted fields containing commas, escaped `""`, and
     * embedded newlines; tolerates CRLF and a trailing newline. Returns one list of raw fields per
     * record (header included as the first record).
     */
    private fun tokenize(text: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        var fields = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        var sawAnyContent = false

        fun endField() {
            fields.add(field.toString())
            field.setLength(0)
        }

        fun endRecord() {
            endField()
            records.add(fields)
            fields = ArrayList()
            sawAnyContent = false
        }

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when (c) {
                    '"' -> {
                        // A doubled quote inside a quoted field is an escaped literal quote.
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    }
                    else -> field.append(c)
                }
                c == '"' -> {
                    inQuotes = true
                    sawAnyContent = true
                }
                c == ',' -> {
                    endField()
                    sawAnyContent = true
                }
                c == '\r' -> {
                    // Swallow CR; the following LF (CRLF) terminates the record once.
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    endRecord()
                }
                c == '\n' -> endRecord()
                else -> {
                    field.append(c)
                    sawAnyContent = true
                }
            }
            i++
        }

        // Flush the final record only if the trailing line had any content (ignore trailing newline).
        if (sawAnyContent || field.isNotEmpty() || fields.isNotEmpty()) {
            endRecord()
        }
        return records
    }

    private fun String.toBooleanFlag(): Boolean = trim().equals("true", ignoreCase = true)
}

/**
 * S0570: one curated catalog row. All fields are `String` except [isLive] / [https]. Missing
 * optional columns decode to an empty string (or `false` for the booleans).
 */
data class ParsedCatalogEntry(
    val category: String,
    val topic: String,
    val name: String,
    val url: String,
    val mediaKind: String,
    val protocol: String,
    val format: String,
    val bitrate: String,
    val isLive: Boolean,
    val https: Boolean,
    val language: String,
    val country: String,
    val homepage: String,
    val sourceKind: String,
    val licenseNote: String,
    val notes: String,
    val confidence: String
)
