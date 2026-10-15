package com.sza.fastmediasorter.domain.model

/**
 * One row of the shared table of connection messages (contract DEVICE-EXCHANGE 25 item CZ): the code the row is
 * known by in the table and in the log, where it comes from, and the step of the check ladder that says what to
 * try. A row whose [step] is null carries its own advice. The words live in string resources keyed by the row.
 *
 * [kind] is [Kind.PRODUCT] for a word of this program that is not a table row.
 */
enum class ExchangeMessage(val code: String, val kind: Kind, val step: ExchangeCheckStep?) {
    BAD_CREDENTIALS("bad-credentials", Kind.WIRE, ExchangeCheckStep.CREDENTIALS),
    BAD_CREDENTIALS_ONE_TIME_CODE("bad-credentials", Kind.WIRE, ExchangeCheckStep.CREDENTIALS),
    DEVICE_REVOKED("device-revoked", Kind.WIRE, ExchangeCheckStep.DEVICE_REMOVED),
    SIGNED_OUT("signed-out", Kind.WIRE, ExchangeCheckStep.CREDENTIALS),
    VERSION_UNSUPPORTED("version-unsupported", Kind.WIRE, ExchangeCheckStep.OPERATOR),
    TLS_REQUIRED("tls-required", Kind.WIRE, ExchangeCheckStep.OPERATOR),
    ID_COLLISION("id-collision", Kind.WIRE, null),
    CAPACITY("capacity", Kind.WIRE, ExchangeCheckStep.OPERATOR),
    PORT_UNAVAILABLE("port-unavailable", Kind.WIRE, ExchangeCheckStep.OPERATOR),

    /** A `refused.reason` and also a reason of a `cast-result`; the table row is the same. */
    UNAVAILABLE("unavailable", Kind.WIRE, ExchangeCheckStep.PC_ASLEEP),
    RATE_LIMITED("rate-limited", Kind.WIRE, ExchangeCheckStep.SIGN_IN_RATE),
    PROTECTION_UNSUPPORTED("protection-unsupported", Kind.WIRE, null),
    INVITATION_INVALID("invitation-invalid", Kind.WIRE, ExchangeCheckStep.INVITATION),
    LOGIN_TAKEN("login-taken", Kind.WIRE, null),
    PASSWORD_POLICY("password-policy", Kind.WIRE, null),
    LIMIT_REACHED("limit-reached", Kind.WIRE, ExchangeCheckStep.OPERATOR),
    LIMIT_REACHED_CLIPBOARD("limit-reached", Kind.WIRE, null),
    NOT_PERMITTED("not-permitted", Kind.WIRE, null),
    DECLINED("declined", Kind.CAST, null),
    TIMEOUT("timeout", Kind.CAST, null),
    UNSUPPORTED("unsupported", Kind.CAST, null),
    UNREACHABLE_SERVER("unreachable", Kind.LOCAL, ExchangeCheckStep.PC_ASLEEP),
    UNREACHABLE_DEVICE("unreachable", Kind.LOCAL, ExchangeCheckStep.PC_ASLEEP),
    UNANSWERED("unanswered", Kind.LOCAL, ExchangeCheckStep.OPERATOR),
    TLS_FAILED("tls-failed", Kind.LOCAL, ExchangeCheckStep.DEVICE_CLOCK),
    CERTIFICATE_CHANGED("certificate-changed", Kind.LOCAL, ExchangeCheckStep.CERTIFICATE),
    EMPTY_CREDENTIAL("empty-credential", Kind.LOCAL, null),
    SERVER_OFFLINE("server-offline", Kind.STATE, ExchangeCheckStep.PC_ASLEEP),
    RESOURCE_LOCKED("resource-locked", Kind.STATE, null),
    CLIPBOARD_EMPTY("clipboard-empty", Kind.RESULT, null),

    /** The four rows of 27 item DR: why sealed content does not go somewhere, or did not open here. */
    E2E_MISSING("e2e-missing", Kind.STATE, null),
    KEY_CHANGED("key-changed", Kind.STATE, null),
    SERVER_NO_E2E("server-no-e2e", Kind.STATE, ExchangeCheckStep.OPERATOR),
    SEALED_FAILED("sealed-failed", Kind.LOCAL, null),

    /** An unknown `refused.reason` (7.10): the generic refusal, sent to whoever runs the server. */
    REFUSED("refused", Kind.WIRE, ExchangeCheckStep.OPERATOR),

    /** This device's secret store refused the token it was given; not a table row. */
    TOKEN_NOT_STORED("token-not-stored", Kind.PRODUCT, null);

    /** Where the row comes from: a wire refusal, a cast result, a local class, a state or a plain result. */
    enum class Kind { WIRE, CAST, LOCAL, STATE, RESULT, PRODUCT }

    /** A failure with no reason of its own on the wire is walked along the ladder (item CY), not shown at once. */
    val walked: Boolean get() = code in WALKED_CODES

    companion object {
        const val REASON_NOT_ENROLLED = "not-enrolled"
        const val REASON_TOKEN_NOT_STORED = "token-not-stored"
        private const val REASON_BAD_CREDENTIALS = "bad-credentials"
        private const val REASON_LIMIT_REACHED = "limit-reached"

        private val WALKED_CODES = setOf("unreachable", "unanswered", "tls-failed")

        /**
         * The row for a `refused.reason`. [oneTimeCode] picks the "login or one-time code" text of
         * `bad-credentials`, [clipboardSend] the clipboard text of `limit-reached`. An unknown reason is
         * [REFUSED]; `not-enrolled` (the entry holds no token) reads as a removed device.
         */
        fun ofWire(reason: String, oneTimeCode: Boolean = false, clipboardSend: Boolean = false): ExchangeMessage =
            when (reason) {
                REASON_NOT_ENROLLED -> DEVICE_REVOKED
                REASON_TOKEN_NOT_STORED -> TOKEN_NOT_STORED
                REASON_BAD_CREDENTIALS -> if (oneTimeCode) BAD_CREDENTIALS_ONE_TIME_CODE else BAD_CREDENTIALS
                REASON_LIMIT_REACHED -> if (clipboardSend) LIMIT_REACHED_CLIPBOARD else LIMIT_REACHED
                else -> entries.firstOrNull { it.kind == Kind.WIRE && it.code == reason } ?: REFUSED
            }

        /** The row for a reason of a `cast-result` (14.5 item R); anything else is the generic refusal. */
        fun ofCast(reason: String?): ExchangeMessage = when (reason) {
            UNAVAILABLE.code -> UNAVAILABLE
            DECLINED.code -> DECLINED
            TIMEOUT.code -> TIMEOUT
            UNSUPPORTED.code -> UNSUPPORTED
            else -> REFUSED
        }
    }
}
