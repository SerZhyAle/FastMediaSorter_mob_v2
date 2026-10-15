package com.sza.fastmediasorter.wear.domain.model

/**
 * The rows of the shared table of connection messages (contract DEVICE-EXCHANGE 25 item CZ) that the watch can
 * meet. The watch shows the row's words and one fixed line and never the advice of a step (item DA); [code] is
 * what goes to the log. A row's words are resolved in the UI layer, so this stays a plain model.
 */
enum class WearExchangeMessage(val code: String) {
    BAD_CREDENTIALS("bad-credentials"),
    DEVICE_REVOKED("device-revoked"),
    SIGNED_OUT("signed-out"),
    VERSION_UNSUPPORTED("version-unsupported"),
    TLS_REQUIRED("tls-required"),
    ID_COLLISION("id-collision"),
    CAPACITY("capacity"),
    PORT_UNAVAILABLE("port-unavailable"),
    UNAVAILABLE("unavailable"),
    RATE_LIMITED("rate-limited"),
    PROTECTION_UNSUPPORTED("protection-unsupported"),
    LIMIT_REACHED("limit-reached"),
    LIMIT_REACHED_CLIPBOARD("limit-reached"),
    NOT_PERMITTED("not-permitted"),
    UNREACHABLE_SERVER("unreachable"),
    UNREACHABLE_DEVICE("unreachable"),
    UNANSWERED("unanswered"),
    TLS_FAILED("tls-failed"),
    CERTIFICATE_CHANGED("certificate-changed"),
    SERVER_OFFLINE("server-offline"),
    RESOURCE_LOCKED("resource-locked"),

    /** Three rows of 27 item DR: nothing sealed goes here, the server cannot carry it, or a box did not open. */
    E2E_MISSING("e2e-missing"),
    SERVER_NO_E2E("server-no-e2e"),
    SEALED_FAILED("sealed-failed"),

    /** An unknown `refused.reason` (7.10): the generic refusal. */
    REFUSED("refused");

    companion object {
        private const val REASON_NOT_ENROLLED = "not-enrolled"

        private val BY_WIRE_CODE: Map<String, WearExchangeMessage> = mapOf(
            "bad-credentials" to BAD_CREDENTIALS,
            "device-revoked" to DEVICE_REVOKED,
            "signed-out" to SIGNED_OUT,
            "version-unsupported" to VERSION_UNSUPPORTED,
            "tls-required" to TLS_REQUIRED,
            "id-collision" to ID_COLLISION,
            "capacity" to CAPACITY,
            "port-unavailable" to PORT_UNAVAILABLE,
            "unavailable" to UNAVAILABLE,
            "rate-limited" to RATE_LIMITED,
            "protection-unsupported" to PROTECTION_UNSUPPORTED,
            "limit-reached" to LIMIT_REACHED,
            "not-permitted" to NOT_PERMITTED,
        )

        /**
         * The row of a `refused.reason`: [clipboardSend] picks the clipboard text of `limit-reached`, a token that
         * was never enrolled reads as a removed device, and an unknown or null reason is the generic refusal.
         */
        fun fromWire(reason: String?, clipboardSend: Boolean = false): WearExchangeMessage {
            val message = if (reason == REASON_NOT_ENROLLED) DEVICE_REVOKED else BY_WIRE_CODE[reason]
            return when {
                message == LIMIT_REACHED && clipboardSend -> LIMIT_REACHED_CLIPBOARD
                message != null -> message
                else -> REFUSED
            }
        }
    }
}
