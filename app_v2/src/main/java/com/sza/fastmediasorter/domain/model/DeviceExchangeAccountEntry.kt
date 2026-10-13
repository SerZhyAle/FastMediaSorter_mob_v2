package com.sza.fastmediasorter.domain.model

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
    /** Attach every new resource of this account without asking (item AD); off until the user turns it on. */
    val autoAttach: Boolean = false,
    /** The last `welcome.publicEndpoint`; offered as an address edit when it differs, never applied silently. */
    val publicEndpoint: String? = null,
    /** The device publishes and broadcasts through this entry; on at most one entry (item AC). */
    val outgoing: Boolean = false,
) {

    /** Host lower-cased, port and login compared case-insensitively (5.1); the device token is kept under it. */
    val identity: String
        get() = host.lowercase(Locale.ROOT) + ":" + port + "/" + login.lowercase(Locale.ROOT)

    // DIAGNOSTIC-REPORT section 10: host, login and pin are private; the generated form would print all three.
    override fun toString(): String =
        "DeviceExchangeAccountEntry(host=$REDACTED, port=$port, login=$REDACTED, deviceId=$deviceId, " +
            "pinnedCertificate=${pinnedCertificate?.let { REDACTED }}, autoAttach=$autoAttach, " +
            "publicEndpoint=${publicEndpoint?.let { REDACTED }}, outgoing=$outgoing)"

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
) {

    companion object {
        const val ROLE_RESOURCE_PRODUCER = "resource-producer"
        const val ROLE_RESOURCE_CONSUMER = "resource-consumer"
        const val ROLE_BROADCASTER = "broadcaster"
        const val ROLE_RECEIVER = "receiver"

        /**
         * Every role this product may take on an entry, declared at `enroll` ahead of use (item AC), so making the
         * entry the outgoing one later needs no second enrollment and no password.
         */
        fun declaredRoles(producer: Boolean, broadcaster: Boolean, receiver: Boolean = false): List<String> =
            listOfNotNull(
                ROLE_RESOURCE_CONSUMER,
                ROLE_RESOURCE_PRODUCER.takeIf { producer },
                ROLE_BROADCASTER.takeIf { broadcaster },
                ROLE_RECEIVER.takeIf { receiver },
            )
    }
}

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

/**
 * A one-time pairing code of 5.3 - single use, valid for minutes - that another device enrolls with instead of
 * the password. [expiresAt] is the server's text, carried on unread. Neither member is ever stored or logged.
 */
class DeviceExchangePairingCode(val code: String, val expiresAt: String?) {
    init {
        PrivateDiagnosticValues.register(code)
    }

    override fun toString(): String = "DeviceExchangePairingCode(code=$REDACTED, expiresAt=$expiresAt)"
}

/** How an in-place address edit ended (contract DEVICE-EXCHANGE item AA). */
sealed interface DeviceExchangeAddressChange {

    data class Changed(val entry: DeviceExchangeAccountEntry) : DeviceExchangeAddressChange

    /** Another entry already holds the new address with this login; one device holds one entry per identity. */
    data object IdentityTaken : DeviceExchangeAddressChange

    /** The secret store refused the token under the new identity; nothing was changed. */
    data object TokenNotMoved : DeviceExchangeAddressChange
}

/** How removing an entry ended (item AB); both forget the token and the entry and keep every resource. */
enum class DeviceExchangeRemoval {
    REVOKED,

    /** The server was not reached: the old device stays in the account's list until another device revokes it. */
    FORGOTTEN_LOCALLY,
}
