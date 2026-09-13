package com.sza.fastmediasorter.data.broadcast

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import timber.log.Timber
import java.io.ByteArrayInputStream
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
     * Separates a descriptor written by a newer app version from plain garbage, so the caller can
     * tell the user to update rather than blame the payload.
     */
    fun parseDetailed(raw: String): ParseOutcome {
        val trimmed = raw.trim()
        val json = trimmed.takeIf { it.isNotEmpty() }?.let { decodeRawPayload(it) }
        return json?.let { parseAndValidateJson(it) } ?: ParseOutcome.Malformed
    }

    @Suppress("TooGenericExceptionCaught")
    private fun decodeRawPayload(trimmed: String): String? {
        return try {
            when {
                trimmed.startsWith("{") -> trimmed
                trimmed.startsWith(COMPRESSED_PREFIX) -> inflate(trimmed.removePrefix(COMPRESSED_PREFIX))
                else -> null
            }
        } catch (e: Exception) {
            Timber.d(e, "BroadcastDescriptorParser: failed to decode raw payload")
            null
        }
    }

    private fun parseAndValidateJson(json: String): ParseOutcome {
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
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val COMPRESSED_PREFIX = "FMSBCAST1:"
    }
}
