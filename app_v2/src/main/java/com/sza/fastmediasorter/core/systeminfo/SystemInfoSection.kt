package com.sza.fastmediasorter.core.systeminfo

/**
 * One labelled group of the System info summary: a [title] header and a list of
 * `label to value` [fields]. Pure data, no Android dependencies.
 */
data class SystemInfoSection(
    val title: String,
    val fields: List<Pair<String, String>>,
)

/**
 * Render a list of [SystemInfoSection] into the grouped text format used by the
 * System info dialog: a title line, then `  label: value` per field, with a blank
 * line between sections. Empty sections (no fields) are skipped.
 */
fun List<SystemInfoSection>.renderSystemInfo(): String =
    filter { it.fields.isNotEmpty() }.joinToString(separator = "\n\n") { section ->
        buildString {
            append(section.title)
            section.fields.forEach { (label, value) ->
                append("\n  ").append(label).append(": ").append(value)
            }
        }
    }
