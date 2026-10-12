package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

/**
 * S4158: one exchange account the watch belongs to, created from a phone entry's "Connect watch" action. Each
 * holds its own [deviceId] (contract DEVICE-EXCHANGE 5.2 and item AD), so the watch is a device of its own in
 * every account it joined; the device token lives in the secret store under [identity], never here.
 */
// Stored as JSON: the names are pinned so a minified build reads what an earlier one wrote.
data class WearExchangeEntry(
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int,
    @SerializedName("login") val login: String,
    @SerializedName("deviceId") val deviceId: String,
    /** The phone entry's trust decision: the accepted leaf, or null where the platform trusts the server. */
    @SerializedName("pinnedCertificate") val pinnedCertificate: String?,
    /** The watch broadcasts audio through this entry (S4157 on the phone); on exactly one entry while any exist. */
    @SerializedName("outgoing") val outgoing: Boolean = false,
) {

    /** Host lower-cased, port and login compared case-insensitively (5.1); the phone keys its entries alike. */
    val identity: String
        get() = host.lowercase(Locale.ROOT) + ":" + port + "/" + login.lowercase(Locale.ROOT)

    // DIAGNOSTIC-REPORT section 10: host, login and pin are private.
    override fun toString(): String =
        "WearExchangeEntry(port=$port, deviceId=$deviceId, pinned=${pinnedCertificate != null}, outgoing=$outgoing)"

    companion object {
        private const val DEVICE_ID_BYTES = 16
        private val random = SecureRandom()

        /** 16 random bytes in the share-id form of contract section 2: base64url without padding. */
        fun newDeviceId(): String {
            val bytes = ByteArray(DEVICE_ID_BYTES).also(random::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

/** How one `enroll` ended; only [Enrolled] carries a token, and only the repository stores it. */
sealed interface WearExchangeEnrollResult {

    class Enrolled(val token: String) : WearExchangeEnrollResult {
        override fun toString(): String = "Enrolled(token=***)"
    }

    /** [reason] is a 7.10 reason, `unknown`, or [REASON_TOKEN_NOT_STORED] when the secret store refused. */
    data class Refused(val reason: String) : WearExchangeEnrollResult

    data object CertificateUntrusted : WearExchangeEnrollResult

    data object CertificateChanged : WearExchangeEnrollResult

    data class Unreachable(val cause: IOException) : WearExchangeEnrollResult

    companion object {
        const val REASON_TOKEN_NOT_STORED = "token-not-stored"
        const val REASON_UNKNOWN = "unknown"
    }
}

/** How removing an entry ended (item AB); both forget the token and the entry. */
enum class WearExchangeRemoval {
    REVOKED,

    /** The server was not reached: the watch stays in the account's device list until another device revokes it. */
    FORGOTTEN_LOCALLY,
}
