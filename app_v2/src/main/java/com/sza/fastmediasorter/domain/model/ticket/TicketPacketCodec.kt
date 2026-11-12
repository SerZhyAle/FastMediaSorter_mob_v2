package com.sza.fastmediasorter.domain.model.ticket

import org.json.JSONArray
import org.json.JSONObject

/**
 * S4554: the byte form of the INBOX-PACKET v1 files.
 *
 * The manifest is written by hand rather than through `org.json`: the canon writer is PowerShell 7
 * `ConvertTo-Json` (2-space indent, `": "`, LF, a final newline, Newtonsoft's default escaping), and
 * Android's `JSONStringer` escapes `/` and indents differently, so `"text/markdown"` alone would
 * break the byte-for-byte replay of the conformance vector. The readers are lenient: an unknown
 * member is ignored, a malformed file reads as null.
 */
object TicketPacketCodec {

    private const val INDENT = "  "

    /** The exact bytes of `manifest.json` for [manifest], members in the format's order. */
    fun manifestToJson(manifest: TicketPacketManifest): String {
        val members = mutableListOf<Pair<String, Any?>>(
            "schemaVersion" to TicketPacketManifest.SCHEMA_VERSION,
            "payloadKind" to TicketPacketManifest.PAYLOAD_KIND,
            "packetId" to manifest.packetId,
            "createdAtEpochMs" to manifest.createdAtEpochMs,
            "senderDeviceName" to manifest.senderDeviceName
        )
        manifest.senderDeviceId?.let { members += "senderDeviceId" to it }
        members += "targetDeviceName" to manifest.targetDeviceName
        members += "status" to manifest.status
        members += "fileNames" to manifest.fileNames
        members += "totalSizeBytes" to manifest.totalSizeBytes
        members += "ttlSeconds" to manifest.ttlSeconds
        members += "files" to manifest.files.map(::fileMembers)
        members += "project" to manifest.project
        if (manifest.projects.isNotEmpty()) members += "projects" to manifest.projects
        manifest.text?.let { members += "text" to it }
        return StringBuilder().also { writeObject(it, members, 0) }.append('\n').toString()
    }

    /** `projects.json`, or null when it is absent, unparsable or lists nothing usable. */
    fun projectsFromJson(raw: String): List<TicketProject>? = runCatching {
        val array = JSONObject(raw).optJSONArray("projects") ?: JSONArray()
        (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val key = entry.optString("key")
            if (TicketPacketManifest.isValidProjectKey(key)) {
                TicketProject(key, entry.optString("name").ifBlank { key })
            } else {
                null
            }
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun resultFromJson(raw: String): TicketPacketResult? = runCatching {
        val json = JSONObject(raw)
        val array = json.optJSONArray("tickets") ?: JSONArray()
        TicketPacketResult(
            tickets = (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) },
            verdict = json.optString("verdict").takeIf(String::isNotBlank)
        )
    }.getOrNull()

    fun refusalFromJson(raw: String): TicketPacketRefusal? = runCatching {
        val json = JSONObject(raw)
        TicketPacketRefusal(
            error = json.getString("error"),
            detail = if (json.isNull("detail")) null else json.optString("detail").takeIf(String::isNotBlank)
        )
    }.getOrNull()

    private fun fileMembers(file: TicketPacketFile): JsonMembers = JsonMembers(
        buildList {
            add("name" to file.name)
            add("size" to file.size)
            add("sha256" to file.sha256)
            add("mimeType" to file.mimeType)
            file.role?.let { add("role" to it.wire) }
        }
    )

    private fun writeObject(out: StringBuilder, members: List<Pair<String, Any?>>, depth: Int) {
        if (members.isEmpty()) {
            out.append("{}")
            return
        }
        out.append("{\n")
        members.forEachIndexed { index, (key, value) ->
            indent(out, depth + 1)
            writeString(out, key)
            out.append(": ")
            writeValue(out, value, depth + 1)
            if (index < members.lastIndex) out.append(',')
            out.append('\n')
        }
        indent(out, depth)
        out.append('}')
    }

    private fun writeArray(out: StringBuilder, items: List<*>, depth: Int) {
        if (items.isEmpty()) {
            out.append("[]")
            return
        }
        out.append("[\n")
        items.forEachIndexed { index, item ->
            indent(out, depth + 1)
            writeValue(out, item, depth + 1)
            if (index < items.lastIndex) out.append(',')
            out.append('\n')
        }
        indent(out, depth)
        out.append(']')
    }

    private fun writeValue(out: StringBuilder, value: Any?, depth: Int) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Int, is Long -> out.append(value.toString())
            is JsonMembers -> writeObject(out, value.members, depth)
            is List<*> -> writeArray(out, value, depth)
            else -> writeString(out, value.toString())
        }
    }

    private class JsonMembers(val members: List<Pair<String, Any?>>)

    // Newtonsoft's default StringEscapeHandling: quote, backslash, controls, U+0085, U+2028, U+2029.
    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (ch < ' ' || ch in ESCAPED_SEPARATORS) {
                    out.append("\\u").append(Integer.toHexString(ch.code).padStart(UNICODE_ESCAPE_DIGITS, '0'))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
    }

    private fun indent(out: StringBuilder, depth: Int) {
        repeat(depth) { out.append(INDENT) }
    }

    private const val UNICODE_ESCAPE_DIGITS = 4
    private const val ESCAPED_SEPARATORS = "\u0085  "
}
