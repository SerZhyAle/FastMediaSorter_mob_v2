package com.sza.fastmediasorter.ui.settings.search

/**
 * One row of the generated settings manifest (`docs/settings/settings-manifest.json`).
 *
 * `key` is the view's `android:id` resource entry name - the stable join key shared with
 * the annotations sidecar and the reference renderer (S0440). Titles are resolved per locale
 * from the same scan the app ships (`LayoutSettingsSearchSource`).
 */
data class SettingsManifestEntry(
    val key: String,
    val sectionId: String,
    val destination: String,
    val layout: String,
    val kind: String,
    val titleEn: String,
    val titleRu: String,
    val titleUk: String
)

/**
 * Serializes manifest entries to deterministic JSON: entries sorted by `sectionId` then `key`,
 * fixed key order per object, 2-space indent. Hand-rolled rather than `org.json` because
 * `JSONObject` does not guarantee key order, which would make the committed file's byte-diffs
 * meaningless for the drift gate.
 */
object SettingsManifestSerializer {

    fun serialize(entries: List<SettingsManifestEntry>): String {
        val sorted = entries.sortedWith(compareBy({ it.sectionId }, { it.key }))
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"entries\": [\n")
        sorted.forEachIndexed { index, e ->
            sb.append("    {\n")
            sb.append("      \"key\": ").append(quote(e.key)).append(",\n")
            sb.append("      \"sectionId\": ").append(quote(e.sectionId)).append(",\n")
            sb.append("      \"destination\": ").append(quote(e.destination)).append(",\n")
            sb.append("      \"layout\": ").append(quote(e.layout)).append(",\n")
            sb.append("      \"kind\": ").append(quote(e.kind)).append(",\n")
            sb.append("      \"titleEn\": ").append(quote(e.titleEn)).append(",\n")
            sb.append("      \"titleRu\": ").append(quote(e.titleRu)).append(",\n")
            sb.append("      \"titleUk\": ").append(quote(e.titleUk)).append("\n")
            sb.append("    }")
            sb.append(if (index < sorted.lastIndex) ",\n" else "\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
