package com.sza.fastmediasorter.data.companion

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0421: parses the companion export payload (QR string or `.fmscfg` file content)
 * into a validated [CompanionConfigDto].
 *
 * Two transport variants per the contract (companion `docs/CONFIG_FORMAT.md`):
 * plain JSON (starts with `{`) or `FMSCFG1:` + base64(gzip(json)).
 */
@Singleton
class CompanionConfigParser @Inject constructor() {

    private val gson = Gson()

    /** Parses raw file bytes (UTF-8; either transport variant). */
    fun parse(bytes: ByteArray): CompanionConfigDto = parse(String(bytes, Charsets.UTF_8))

    fun parse(payload: String): CompanionConfigDto {
        val json = decodeTransport(payload.trim())
        val dto = try {
            gson.fromJson(json, CompanionConfigDto::class.java)
        } catch (e: JsonSyntaxException) {
            throw CompanionConfigException(
                CompanionConfigException.Reason.MALFORMED,
                "Config is not valid JSON: ${e.message}"
            )
        } ?: throw CompanionConfigException(CompanionConfigException.Reason.MALFORMED, "Config is empty")
        validate(dto)
        return dto
    }

    private fun decodeTransport(payload: String): String = when {
        payload.startsWith("{") -> payload
        payload.startsWith(COMPRESSED_PREFIX) -> inflate(payload.removePrefix(COMPRESSED_PREFIX))
        else -> throw CompanionConfigException(
            CompanionConfigException.Reason.MALFORMED,
            "Unknown payload format (expected JSON or $COMPRESSED_PREFIX prefix)"
        )
    }

    private fun inflate(base64Gzip: String): String = try {
        val compressed = Base64.getDecoder().decode(base64Gzip)
        GZIPInputStream(ByteArrayInputStream(compressed)).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        throw CompanionConfigException(
            CompanionConfigException.Reason.MALFORMED,
            "Compressed payload undecodable: ${e.message}"
        )
    }

    private fun validate(dto: CompanionConfigDto) {
        if (dto.schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            throw CompanionConfigException(
                CompanionConfigException.Reason.UNSUPPORTED_VERSION,
                "Config schemaVersion ${dto.schemaVersion} is newer than supported $SUPPORTED_SCHEMA_VERSION"
            )
        }
        if (dto.schemaVersion < 1) {
            invalid("schemaVersion missing or invalid")
        }
        if (dto.protocol != PROTOCOL_SFTP) {
            invalid("unsupported protocol '${dto.protocol}'")
        }
        val paths = dto.accessPaths.orEmpty()
        if (paths.isEmpty()) invalid("accessPaths is empty")
        paths.forEach { path ->
            if (path.host.isNullOrBlank() || path.port == null || path.port !in 1..MAX_TCP_PORT) {
                invalid("accessPath has invalid host/port: ${path.host}:${path.port}")
            }
        }
        if (dto.username.isNullOrBlank()) invalid("username missing")
        // S0984: empty password + empty fingerprint are valid Android-side (passwordless share
        // where the recipient types the password at import, and no-pin TOFU on first connect).
        // The frozen cross-repo contract still always sends both, so no schemaVersion bump.
        if (dto.roots.orEmpty().isEmpty()) invalid("roots is empty")
        dto.roots.orEmpty().forEach { root ->
            if (root.virtualPath.isNullOrBlank() || !root.virtualPath.startsWith("/")) {
                invalid("root virtualPath invalid: '${root.virtualPath}'")
            }
        }
    }

    private fun invalid(detail: String): Nothing =
        throw CompanionConfigException(CompanionConfigException.Reason.INVALID_CONTENT, detail)

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val COMPRESSED_PREFIX = "FMSCFG1:"
        const val PROTOCOL_SFTP = "sftp"
        const val FILE_EXTENSION = ".fmscfg"
        private const val MAX_TCP_PORT = 65535
    }
}
