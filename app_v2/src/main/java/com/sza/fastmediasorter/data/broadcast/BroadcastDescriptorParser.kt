package com.sza.fastmediasorter.data.broadcast

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BroadcastDescriptorParser @Inject constructor() {

    private val gson = Gson()

    fun parse(bytes: ByteArray): BroadcastDescriptorDto? = parse(String(bytes, Charsets.UTF_8))

    fun parse(raw: String): BroadcastDescriptorDto? =
        (parseDetailed(raw) as? ParseOutcome.Valid)?.dto

    /**
     * Separates a descriptor of a higher schema from plain garbage, so the caller shows the canonical
     * `format-unsupported` refusal (LIVE-BROADCAST 1.1) rather than blame the payload.
     */
    fun parseDetailed(raw: String): ParseOutcome {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_DESCRIPTOR_SIZE) return ParseOutcome.Malformed
        val json = decodeRawPayload(trimmed, allowLink = true)
        return json?.let { parseAndValidateJson(it) } ?: ParseOutcome.Malformed
    }

    // allowLink is false on the second hop, so a crafted chain of nested `payload=` values cannot recurse.
    @Suppress("TooGenericExceptionCaught")
    private fun decodeRawPayload(trimmed: String, allowLink: Boolean): String? {
        return try {
            when {
                trimmed.startsWith("{") -> trimmed
                trimmed.startsWith(COMPRESSED_PREFIX) -> inflate(trimmed.removePrefix(COMPRESSED_PREFIX))
                allowLink && trimmed.contains(PAYLOAD_KEY) ->
                    extractPayload(trimmed)?.let { decodeRawPayload(it.trim(), allowLink = false) }

                else -> null
            }
        } catch (e: Exception) {
            Timber.d(e, "BroadcastDescriptorParser: failed to decode raw payload")
            null
        }
    }

    // A '%' means the link was URL-encoded, and only then is the value decoded: a bare '+' of a raw
    // base64 payload must stay a '+' and not become a space.
    private fun extractPayload(input: String): String? {
        val value = PAYLOAD_PARAMETER.find(input)?.groupValues?.get(1) ?: return null
        return if (value.contains("%")) URLDecoder.decode(value, Charsets.UTF_8.name()) else value
    }

    private fun parseAndValidateJson(json: String): ParseOutcome {
        val root = readRootObject(json) ?: return ParseOutcome.Malformed
        // The version is judged before the shape: a descriptor of a higher schema may reshape a member,
        // and it must still answer the canonical `format-unsupported`, never "malformed" (LIVE-BROADCAST 1.1).
        return when {
            declaredVersion(root) > SUPPORTED_SCHEMA_VERSION -> ParseOutcome.UnsupportedVersion
            !hasValidShape(root) -> ParseOutcome.Malformed
            else -> readDto(json)
        }
    }

    private fun readRootObject(json: String): JsonObject? {
        if (exceedsNestingLimit(json)) return null
        return try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: JsonParseException) {
            Timber.d(e, "BroadcastDescriptorParser: failed to read the JSON structure")
            null
        }
    }

    // Gson 2.10 reads nested containers recursively, so a bounded depth is what keeps a hostile
    // document from ending the process with a StackOverflowError that no catch block sees.
    private fun exceedsNestingLimit(json: String): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (ch in json) {
            when {
                escaped -> escaped = false
                inString -> {
                    escaped = ch == '\\'
                    inString = escaped || ch != '"'
                }

                ch == '"' -> inString = true
                ch == '{' || ch == '[' -> depth++
                ch == '}' || ch == ']' -> depth--
            }
            if (depth > MAX_NESTING_DEPTH) return true
        }
        return false
    }

    private fun declaredVersion(root: JsonObject): Double =
        root.get("schemaVersion")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asDouble
            ?: 0.0

    // The members every shipped producer writes on every descriptor (LIVE-BROADCAST 3.1 and item M). A
    // malformed `endpoints` entry is deliberately not judged here: item W of 1.7 skips that entry and
    // keeps the descriptor.
    private fun hasValidShape(root: JsonObject): Boolean =
        root.hasText("url") && root.hasText("mode") && isValidAuth(root.get("auth"))

    private fun isValidAuth(auth: JsonElement?): Boolean {
        if (auth == null || auth.isJsonNull) return true
        return auth.isJsonObject && auth.asJsonObject.hasText("scheme") && auth.asJsonObject.hasText("user")
    }

    private fun JsonObject.hasText(key: String): Boolean =
        get(key)?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() } == true

    private fun readDto(json: String): ParseOutcome {
        val dto = try {
            gson.fromJson(json, BroadcastDescriptorDto::class.java)
        } catch (e: JsonSyntaxException) {
            Timber.d(e, "BroadcastDescriptorParser: failed to parse JSON")
            null
        }
        return when {
            dto == null -> ParseOutcome.Malformed
            dto.schemaVersion > SUPPORTED_SCHEMA_VERSION -> ParseOutcome.UnsupportedVersion
            dto.schemaVersion >= 1 && dto.url.isNotBlank() && dto.mode.isNotBlank() -> {
                ParseOutcome.Valid(dto)
            }

            else -> ParseOutcome.Malformed
        }
    }

    sealed interface ParseOutcome {
        data class Valid(val dto: BroadcastDescriptorDto) : ParseOutcome
        data object UnsupportedVersion : ParseOutcome
        data object Malformed : ParseOutcome
    }

    private fun inflate(base64Gzip: String): String {
        val compressed = Base64.getDecoder().decode(base64Gzip)
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { stream -> readBounded(stream) }
    }

    // The compressed form is a few KiB while its inflated size is attacker-chosen, so the cap is on the
    // inflated bytes; the IllegalArgumentException is caught by decodeRawPayload and reads as malformed.
    private fun readBounded(stream: InputStream): String {
        val sink = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        var total = 0
        var read = stream.read(chunk)
        while (read > 0) {
            total += read
            require(total <= MAX_DESCRIPTOR_SIZE) { "inflated descriptor exceeds the size cap" }
            sink.write(chunk, 0, read)
            read = stream.read(chunk)
        }
        return sink.toString(Charsets.UTF_8.name())
    }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val COMPRESSED_PREFIX = "FMSBCAST1:"

        /** Ceiling on the text handed in and on the size it inflates to; the file read keeps its own 64 KiB (4.2). */
        const val MAX_DESCRIPTOR_SIZE = 1024 * 1024

        private const val MAX_NESTING_DEPTH = 32
        private const val READ_CHUNK_BYTES = 4096
        private const val PAYLOAD_KEY = "payload="
        private val PAYLOAD_PARAMETER = Regex("""[?&#]payload=([^&#;]+)""")
    }
}
