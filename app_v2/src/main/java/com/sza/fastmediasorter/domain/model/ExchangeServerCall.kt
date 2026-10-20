package com.sza.fastmediasorter.domain.model

/** How one request to an entry's server ended, for the "My devices" screen. */
sealed interface ExchangeServerCall<out T> {

    data class Done<T>(val value: T) : ExchangeServerCall<T>

    /** [reason] is a contract 7.10 reason, or `not-enrolled` when the entry holds no token. */
    data class Refused(val reason: String) : ExchangeServerCall<Nothing>

    /** The server was not reached or answered nothing; [failure] says which of the local classes it was. */
    data class Unreachable(val failure: ExchangeLocalFailure) : ExchangeServerCall<Nothing>
}
