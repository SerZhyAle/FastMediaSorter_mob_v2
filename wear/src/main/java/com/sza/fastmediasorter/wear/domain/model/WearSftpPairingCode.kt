package com.sza.fastmediasorter.wear.domain.model

import java.net.URLDecoder

/**
 * S4228: the reading half of the phone's SFTP pairing code, which a resource's `access.descriptor` carries
 * (contract DEVICE-EXCHANGE 6.2): `FMSSFTP1:h=<host>,<host>&p=<port>&u=<user>&pw=<password>&fp=<fingerprint>`,
 * each value URL-encoded, `pw` absent in key-login mode. `FMSSFTP2` adds the exchange and Drive keys, which the
 * watch does not need - it tunnels through its own entry - so they are ignored rather than judged.
 *
 * The phone's `SftpPairingPayload` is the writer; the two modules share no code, so the form is the contract.
 */
data class WearSftpPairingCode(
    val hosts: List<String>,
    val port: Int,
    val username: String,
    val password: String?,
    val hostKeyFingerprint: String,
) {

    // The code carries the share's password by design.
    override fun toString(): String = "WearSftpPairingCode(hosts=${hosts.size}, port=$port, password=***)"

    companion object {
        private const val PREFIX = "FMSSFTP1:"
        private const val PREFIX_V2 = "FMSSFTP2:"
        private const val FIELD_SEPARATOR = "&"
        private const val HOST_SEPARATOR = ","
        private const val KEY_HOSTS = "h"
        private const val KEY_PORT = "p"
        private const val KEY_USER = "u"
        private const val KEY_PASSWORD = "pw"
        private const val KEY_FINGERPRINT = "fp"
        private const val FINGERPRINT_PREFIX = "SHA256:"
        private const val MAX_PORT = 65_535

        /** Null for anything that is not a complete, well-formed pairing code. */
        fun decode(raw: String): WearSftpPairingCode? {
            val trimmed = raw.trim()
            val prefix = listOf(PREFIX, PREFIX_V2).firstOrNull(trimmed::startsWith) ?: return null
            val fields = trimmed.removePrefix(prefix).split(FIELD_SEPARATOR).mapNotNull { field ->
                val separator = field.indexOf('=')
                if (separator <= 0) null else field.substring(0, separator) to field.substring(separator + 1)
            }.toMap()
            val hosts = fields[KEY_HOSTS].orEmpty().split(HOST_SEPARATOR).map { it.urlDecodedOrNull() }
            val password = fields[KEY_PASSWORD]?.urlDecodedOrNull()
            // A malformed percent sequence means the code was damaged or is not ours.
            val damaged = null in hosts || (fields[KEY_PASSWORD] != null && password == null)
            val code = WearSftpPairingCode(
                hosts = hosts.filterNotNull().filter(String::isNotBlank),
                port = fields[KEY_PORT]?.toIntOrNull() ?: 0,
                username = fields[KEY_USER]?.urlDecodedOrNull().orEmpty(),
                password = password,
                hostKeyFingerprint = fields[KEY_FINGERPRINT]?.urlDecodedOrNull().orEmpty(),
            )
            return code.takeIf { !damaged && it.isComplete() }
        }

        private fun WearSftpPairingCode.isComplete(): Boolean =
            hosts.isNotEmpty() && port in 1..MAX_PORT && username.isNotBlank() &&
                hostKeyFingerprint.startsWith(FINGERPRINT_PREFIX)

        private fun String.urlDecodedOrNull(): String? =
            runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }.getOrNull()
    }
}
