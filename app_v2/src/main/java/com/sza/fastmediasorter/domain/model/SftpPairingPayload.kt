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
 */
data class SftpPairingPayload(
    val hosts: List<String>,
    val port: Int,
    val username: String,
    val password: String?,
    val hostKeyFingerprint: String,
) {

    fun encode(): String = buildList {
        add("$KEY_HOSTS=" + hosts.joinToString(HOST_SEPARATOR) { it.urlEncoded() })
        add("$KEY_PORT=$port")
        add("$KEY_USER=" + username.urlEncoded())
        password?.let { add("$KEY_PASSWORD=" + it.urlEncoded()) }
        add("$KEY_FINGERPRINT=" + hostKeyFingerprint.urlEncoded())
    }.joinToString(FIELD_SEPARATOR, prefix = PREFIX)

    companion object {
        const val PREFIX = "FMSSFTP1:"
        private const val FIELD_SEPARATOR = "&"
        private const val HOST_SEPARATOR = ","
        private const val KEY_HOSTS = "h"
        private const val KEY_PORT = "p"
        private const val KEY_USER = "u"
        private const val KEY_PASSWORD = "pw"
        private const val KEY_FINGERPRINT = "fp"
        private const val FINGERPRINT_PREFIX = "SHA256:"
        private const val MAX_PORT = 65_535

        fun isPairingPayload(raw: String): Boolean = raw.trim().startsWith(PREFIX)

        /** Null for anything that is not a complete, well-formed pairing code. */
        fun decode(raw: String): SftpPairingPayload? {
            if (!isPairingPayload(raw)) return null
            val fields = raw.trim().removePrefix(PREFIX).split(FIELD_SEPARATOR).mapNotNull { field ->
                val separator = field.indexOf('=')
                if (separator <= 0) null else field.substring(0, separator) to field.substring(separator + 1)
            }.toMap()
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

        private fun SftpPairingPayload.isComplete(): Boolean =
            hosts.isNotEmpty() && port in 1..MAX_PORT && username.isNotBlank() &&
                hostKeyFingerprint.startsWith(FINGERPRINT_PREFIX)

        private fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

        private fun String.urlDecodedOrNull(): String? =
            runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrNull()
    }
}
