package com.sza.fastmediasorter.domain.model

/** How handing one exchange entry to the watch ended (S4158). */
sealed interface WatchExchangeHandoverOutcome {

    /** The watch enrolled into the entry's account and keeps its own token. */
    data object Enrolled : WatchExchangeHandoverOutcome

    /** The watch answered with a refusal: a watch-side reason or the server's 7.10 reason. */
    data class WatchRefused(val reason: String) : WatchExchangeHandoverOutcome

    /** The code reached a watch and no answer came back in time. */
    data object WatchDidNotAnswer : WatchExchangeHandoverOutcome

    /** The entry's server refused to issue a code, with its 7.10 reason. */
    data class CodeRefused(val reason: String) : WatchExchangeHandoverOutcome

    /** The entry's server could not be reached, or its token or certificate was refused. */
    data object ServerUnreachable : WatchExchangeHandoverOutcome

    data object NoConnectedWatch : WatchExchangeHandoverOutcome

    /** The owner turned the Wear Companion off; nothing was asked of the server. */
    data object CompanionOff : WatchExchangeHandoverOutcome
}
