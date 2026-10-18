package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminSession

/** What one administration operation came to. */
sealed interface ExchangeAdminOutcome<out T> {
    data class Done<T>(val value: T) : ExchangeAdminOutcome<T>

    /** The server answered `ok:false`; the words are those of `administration-reply-codes`. */
    data class Failed(val code: ExchangeAdminReplyCode) : ExchangeAdminOutcome<Nothing>

    /** The session ended under the screen; only signing in again helps. */
    data object Lapsed : ExchangeAdminOutcome<Nothing>

    data class Unreachable(val diagnosis: ExchangeDiagnosis) : ExchangeAdminOutcome<Nothing>
}

/** What the sign-in came to; a [SignedIn] session belongs to the caller, who closes it. */
sealed interface ExchangeAdminSignIn {
    data class SignedIn(val session: DeviceExchangeAdminSession) : ExchangeAdminSignIn

    data class Refused(val diagnosis: ExchangeDiagnosis) : ExchangeAdminSignIn
}
