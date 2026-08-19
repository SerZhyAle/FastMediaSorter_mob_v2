package com.sza.fastmediasorter.wear.data.repository

import com.sza.fastmediasorter.wear.domain.model.WearParsedCatalogEntry
import javax.inject.Inject

/**
 * S1708: Pure RFC-4180 parser for the curated stream catalog `streams.csv` on Wear OS.
 * Columns are matched by header name (not position) so the catalog file may reorder columns
 * or add new ones without breaking the import.
 */
class WearStreamCatalogCsvParser @Inject constructor() {

    fun parse(text: String): List<WearParsedCatalogEntry> {
        val rows = tokenize(text)
        if (rows.isEmpty()) return emptyList()

        val header = rows.first()
        val columnIndex = HashMap<String, Int>(header.size)
        header.forEachIndexed { index, name ->
            columnIndex[name.trim().lowercase()] = index
        }

        fun cell(fields: List<String>, name: String): String {
            val idx = columnIndex[name] ?: return ""
            return fields.getOrNull(idx)?.trim() ?: ""
        }

        val result = ArrayList<WearParsedCatalogEntry>(rows.size - 1)
        for (i in 1 until rows.size) {
            val fields = rows[i]
            val url = cell(fields, "url")
            val name = cell(fields, "name")
            if (url.isBlank() || name.isBlank()) continue
            result += WearParsedCatalogEntry(
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
                confidence = cell(fields, "confidence"),
                faviconIndex = cell(fields, "favicon_index").toIntOrNull()?.takeIf { it >= 0 },
                access = cell(fields, "access")
            )
        }
        return result
    }

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

        if (sawAnyContent || field.isNotEmpty() || fields.isNotEmpty()) {
            endRecord()
        }
        return records
    }

    private fun String.toBooleanFlag(): Boolean = trim().equals("true", ignoreCase = true)
}
