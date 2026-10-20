package com.sza.fastmediasorter.domain.model

/**
 * The codes of a failed `admin-reply` (contract DEVICE-EXCHANGE 1.4 `REMOTE-ADMIN.md` `administration-reply-codes`).
 * They are not refusal reasons and have no row in item CZ; `capacity` here is not the refusal reason of that name.
 */
enum class ExchangeAdminReplyCode(val code: String) {
    BAD_REQUEST("bad-request"),
    NO_ACCOUNT("no-account"),
    EXISTS("exists"),
    BAD_LOGIN("bad-login"),
    WEAK_PASSWORD("weak-password"),
    NOT_FOUND("not-found"),
    CAPACITY("capacity"),
    BAD_KIND("bad-kind"),
    BAD_LIMIT("bad-limit"),
    INTERNAL("internal"),
    ;

    companion object {
        /** A code this program has no row for reads as [INTERNAL], as the contract says. */
        fun ofWire(code: String): ExchangeAdminReplyCode = entries.firstOrNull { it.code == code } ?: INTERNAL
    }
}
