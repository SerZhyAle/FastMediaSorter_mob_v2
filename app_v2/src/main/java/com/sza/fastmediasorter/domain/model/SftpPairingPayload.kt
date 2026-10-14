package com.sza.fastmediasorter.domain.model

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * What the embedded SFTP server's pairing code carries: where to connect, how to log in, and which
 * host key to expect. The fingerprint is what makes the scan safe - the scanning device pins it, so a
 * different machine answering on the same address is refused instead of trusted on first use.
 *
 * Wire form: `FMSSFTP1:h=<host>,<host>&p=<port>&u=<user>&pw=<password>&fp=<fingerprint>`, each value
 * URL-encoded; `pw` is absent in key-login mode. A private key never appears in it.
 *
 * Contract ANYWHERE-ACCESS section 4.1 adds `FMSSFTP2:` - the v1 fields followed by `x` (exchange
 * server `host:port`), `sid` (share id) and `drv=1` (Drive rendezvous applies). A code with none of
 * them is still written as `FMSSFTP1`, byte for byte as before, because every reader already in the
 * field refuses the v2 prefix.
 */
data class SftpPairingPayload(
    val hosts: List<String>,
    val port: Int,
    val username: String,
    val password: String?,
    val hostKeyFingerprint: String,
    val exchangeEndpoint: String? = null,
    val shareId: String? = null,
    val driveChannel: Boolean = false,
) {

    val isAnywhere: Boolean get() = exchangeEndpoint != null || shareId != null || driveChannel

    fun encode(): String = buildList {
        add("$KEY_HOSTS=" + hosts.joinToString(HOST_SEPARATOR) { it.urlEncoded() })
        add("$KEY_PORT=$port")
        add("$KEY_USER=" + username.urlEncoded())
        password?.let { add("$KEY_PASSWORD=" + it.urlEncoded()) }
        add("$KEY_FINGERPRINT=" + hostKeyFingerprint.urlEncoded())
        exchangeEndpoint?.let { add("$KEY_EXCHANGE=" + it.urlEncoded()) }
        shareId?.let { add("$KEY_SHARE_ID=$it") }
        if (driveChannel) add("$KEY_DRIVE=$DRIVE_ON")
    }.joinToString(FIELD_SEPARATOR, prefix = if (isAnywhere) PREFIX_V2 else PREFIX)

    companion object {
        const val PREFIX = "FMSSFTP1:"
        const val PREFIX_V2 = "FMSSFTP2:"
        private const val FIELD_SEPARATOR = "&"
        private const val HOST_SEPARATOR = ","
        private const val KEY_HOSTS = "h"
        private const val KEY_PORT = "p"
        private const val KEY_USER = "u"
        private const val KEY_PASSWORD = "pw"
        private const val KEY_FINGERPRINT = "fp"
        private const val KEY_EXCHANGE = "x"
        private const val KEY_SHARE_ID = "sid"
        private const val KEY_DRIVE = "drv"
        private const val DRIVE_ON = "1"
        private const val FINGERPRINT_PREFIX = "SHA256:"
        private const val MAX_PORT = 65_535

        fun isPairingPayload(raw: String): Boolean =
            raw.trim().let { it.startsWith(PREFIX) || it.startsWith(PREFIX_V2) }

        /** Null for anything that is not a complete, well-formed pairing code. */
        fun decode(raw: String): SftpPairingPayload? {
            val trimmed = raw.trim()
            val prefix = listOf(PREFIX, PREFIX_V2).firstOrNull(trimmed::startsWith) ?: return null
            val fields = trimmed.removePrefix(prefix).split(FIELD_SEPARATOR).mapNotNull { field ->
                val separator = field.indexOf('=')
                if (separator <= 0) null else field.substring(0, separator) to field.substring(separator + 1)
            }.toMap()
            val direct = decodeDirect(fields)
            // The v1 reading never changes (contract section 8), so a v1 code ignores the v2 keys.
            return when {
                direct == null -> null
                prefix == PREFIX -> direct
                else -> decodeAnywhere(direct, fields)
            }
        }

        private fun decodeDirect(fields: Map<String, String>): SftpPairingPayload? {
            val hosts = fields[KEY_HOSTS].orEmpty().split(HOST_SEPARATOR).map { it.urlDecodedOrNull() }
            val password = fields[KEY_PASSWORD]?.urlDecodedOrNull()
            // A malformed percent sequence means the code was damaged or is not ours.
            val damaged = null in hosts || (fields[KEY_PASSWORD] != null && password == null)
            val payload = SftpPairingPayload(
                hosts = hosts.filterNotNull().filter(String::isNotBlank),
                port = fields[KEY_PORT]?.toIntOrNull() ?: 0,
                username = fields[KEY_USER]?.urlDecodedOrNull().orEmpty(),
                password = password,
                hostKeyFingerprint = fields[KEY_FINGERPRINT]?.urlDecodedOrNull().orEmpty(),
            )
            return payload.takeIf { !damaged && it.isComplete() }
        }

        // The exchange endpoint and the share id only make sense together: one without the other
        // would send a consumer to a server with nothing to ask for, or hold a capability for nowhere.
        private fun decodeAnywhere(direct: SftpPairingPayload, fields: Map<String, String>): SftpPairingPayload? {
            val rawExchange = fields[KEY_EXCHANGE]
            val exchange = rawExchange?.urlDecodedOrNull()
            val shareId = fields[KEY_SHARE_ID]
            val drive = fields[KEY_DRIVE]
            val valid = (rawExchange == null) == (shareId == null) &&
                (rawExchange == null || (exchange != null && isHostPort(exchange))) &&
                (shareId == null || SftpShareId.isValid(shareId)) &&
                (drive == null || drive == DRIVE_ON)
            return direct.copy(exchangeEndpoint = exchange, shareId = shareId, driveChannel = drive == DRIVE_ON)
                .takeIf { valid }
        }

        private fun SftpPairingPayload.isComplete(): Boolean =
            hosts.isNotEmpty() && port in 1..MAX_PORT && username.isNotBlank() &&
                hostKeyFingerprint.startsWith(FINGERPRINT_PREFIX)

        /** `host:port`, an IPv6 literal in brackets (`[2001:db8::1]:443`). */
        fun isHostPort(value: String): Boolean {
            val separator = value.lastIndexOf(':')
            val host = if (separator > 0) value.substring(0, separator) else ""
            val port = if (separator > 0) value.substring(separator + 1).toIntOrNull() else null
            val hostValid = if (host.startsWith("[")) {
                host.endsWith("]") && host.length > 2
            } else {
                ':' !in host && host.isNotBlank()
            }
            return hostValid && port != null && port in 1..MAX_PORT
        }

        private fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

        private fun String.urlDecodedOrNull(): String? =
            runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrNull()
    }
}
