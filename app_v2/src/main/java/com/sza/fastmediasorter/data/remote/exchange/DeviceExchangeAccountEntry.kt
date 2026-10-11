package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonObject
import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import com.sza.fastmediasorter.core.security.SecretMasker.REDACTED
import java.io.IOException
import java.util.Locale

/**
 * One account on one exchange server as this device holds it (contract DEVICE-EXCHANGE 5.2 and amendment
 * D). Every stream the client opens belongs to exactly one entry, so a device may hold several - two logins
 * on one server each with its own [deviceId] (S4156) - without any stream knowing about the others.
 */
data class DeviceExchangeAccountEntry(
    val host: String,
    val port: Int,
    val login: String,
    val deviceId: String,
    /** The leaf fingerprint the user accepted for a server the platform does not trust; null otherwise. */
    val pinnedCertificate: String?,
) {

    /** Host lower-cased, port and login compared case-insensitively (5.1); the device token is kept under it. */
    val identity: String
        get() = host.lowercase(Locale.ROOT) + ":" + port + "/" + login.lowercase(Locale.ROOT)

    // DIAGNOSTIC-REPORT section 10: host, login and pin are private; the generated form would print all three.
    override fun toString(): String =
        "DeviceExchangeAccountEntry(host=$REDACTED, port=$port, login=$REDACTED, deviceId=$deviceId, " +
            "pinnedCertificate=${pinnedCertificate?.let { REDACTED }})"

    companion object {
        private val LOGIN = Regex("[A-Za-z0-9._-]{3,64}")

        /** The login form of 5.1: 3 to 64 characters of `[a-z0-9._-]`, compared case-insensitively. */
        fun isValidLogin(login: String?): Boolean = login != null && LOGIN.matches(login)
    }
}

/** What proves the account at enrollment (5.2, 5.3); neither value is ever stored or logged. */
sealed interface DeviceExchangeCredential {

    class Password(val value: String) : DeviceExchangeCredential {
        init {
            PrivateDiagnosticValues.register(value)
        }

        override fun toString(): String = "Password(***)"
    }

    class PairingCode(val value: String) : DeviceExchangeCredential {
        init {
            PrivateDiagnosticValues.register(value)
        }

        override fun toString(): String = "PairingCode(***)"
    }
}

/** How this device describes itself in `enroll` (7.2); `receiver` is the 6.1 object, present with that role. */
data class DeviceExchangeEnrollment(
    val deviceName: String,
    val productVersion: String,
    val roles: List<String>,
    val receiver: JsonObject? = null,
)

/** The members of `welcome`, which `enrolled` carries as well (7.2). */
data class DeviceExchangeWelcome(
    val account: String?,
    val publicEndpoint: String?,
    val keepaliveSeconds: Int?,
    val features: List<String>,
    val serverVersion: String?,
) {

    // The account is the login and the endpoint an address the sink must know (DIAGNOSTIC-REPORT section 10).
    init {
        PrivateDiagnosticValues.register(account, publicEndpoint)
    }

    override fun toString(): String =
        "DeviceExchangeWelcome(account=${account?.let { REDACTED }}, " +
            "publicEndpoint=${publicEndpoint?.let { REDACTED }}, keepaliveSeconds=$keepaliveSeconds, " +
            "features=$features, serverVersion=$serverVersion)"

    companion object {
        fun from(envelope: DeviceExchangeEnvelope): DeviceExchangeWelcome = DeviceExchangeWelcome(
            account = envelope.string(KEY_ACCOUNT),
            publicEndpoint = envelope.string(KEY_PUBLIC_ENDPOINT),
            keepaliveSeconds = envelope.int(KEY_KEEPALIVE_SECONDS),
            features = envelope.members.get(KEY_FEATURES)?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asString }
                .orEmpty(),
            serverVersion = envelope.string(KEY_SERVER_VERSION),
        )

        private const val KEY_ACCOUNT = "account"
        private const val KEY_PUBLIC_ENDPOINT = "publicEndpoint"
        private const val KEY_KEEPALIVE_SECONDS = "keepaliveSeconds"
        private const val KEY_FEATURES = "features"
        private const val KEY_SERVER_VERSION = "serverVersion"
    }
}

/** How an enrollment ended; only [Enrolled] stored a token. */
sealed interface DeviceExchangeEnrollResult {

    data class Enrolled(val welcome: DeviceExchangeWelcome) : DeviceExchangeEnrollResult

    /** [reason] is a 7.10 reason, or [REASON_TOKEN_NOT_STORED] when the secret store refused the token. */
    data class Refused(val reason: String) : DeviceExchangeEnrollResult

    /** The user has to see and accept [presented] before the entry may pin it (amendment D). */
    data class CertificateUntrusted(val presented: String) : DeviceExchangeEnrollResult {
        override fun toString(): String = "CertificateUntrusted(presented=$REDACTED)"
    }

    data class CertificateChanged(val pinned: String, val presented: String) : DeviceExchangeEnrollResult {
        override fun toString(): String = "CertificateChanged(pinned=$REDACTED, presented=$REDACTED)"
    }

    data class Unreachable(val cause: IOException) : DeviceExchangeEnrollResult

    companion object {
        const val REASON_TOKEN_NOT_STORED = "token-not-stored"
    }
}

/** The server answered `refused`; [reason] is a 7.10 reason or `unknown`. */
class DeviceExchangeRefusedException(val reason: String) : IOException("exchange server refused: $reason")

/** The entry holds no device token: it was never enrolled, or its token was revoked and forgotten. */
class DeviceExchangeNotEnrolledException : IOException("no device token for this exchange account entry")
