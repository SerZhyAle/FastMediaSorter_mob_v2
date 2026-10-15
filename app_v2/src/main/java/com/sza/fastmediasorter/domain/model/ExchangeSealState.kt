package com.sza.fastmediasorter.domain.model

/** Why sealed content that came through the server is not usable here (DEVICE-EXCHANGE 27 items DP and DR). */
enum class ExchangeSealIssue(val message: ExchangeMessage) {
    /** The box did not open: not addressed here, an untrusted sender, a failed tag, a moved context. */
    SEALED_FAILED(ExchangeMessage.SEALED_FAILED),

    /** The sender sealed nothing - it has no key - so its clear content through the server is never used. */
    E2E_MISSING(ExchangeMessage.E2E_MISSING),

    /** A grant's box came from another key than its first one (27 item DJ). */
    KEY_CHANGED(ExchangeMessage.KEY_CHANGED),
}

/** How another device of an account entry stands for sealed content (DEVICE-EXCHANGE 27 items DG and DR). */
enum class ExchangeSealState {
    /** It receives sealed content. */
    TRUSTED,

    /** `e2e-missing`: it declared no capability or no key, so nothing goes to it through the server. */
    E2E_MISSING,

    /** `key-changed`: its key is not the one first seen, so nothing goes to it until it is removed and re-added. */
    KEY_CHANGED,
    ;

    companion object {
        /**
         * The table row a device's line shows instead of its plain details, or null for none. The own device speaks
         * for the server too: a server that answered without `sealed` carries nothing (27 item DP).
         */
        fun noticeOf(state: ExchangeSealState?, own: Boolean, entry: DeviceExchangeAccountEntry): ExchangeMessage? =
            when {
                own && entry.features != null && !entry.serverSeals -> ExchangeMessage.SERVER_NO_E2E
                state == E2E_MISSING -> ExchangeMessage.E2E_MISSING
                state == KEY_CHANGED -> ExchangeMessage.KEY_CHANGED
                else -> null
            }
    }
}
