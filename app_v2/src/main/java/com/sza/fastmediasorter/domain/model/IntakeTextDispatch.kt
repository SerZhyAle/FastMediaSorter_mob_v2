package com.sza.fastmediasorter.domain.model

/**
 * S4155: what one piece of intake text is. Every intake surface - paste, `.fmsexch` file, QR scanner,
 * system share - classifies through `DispatchIntakeTextUseCase` before branching, so a new prefix lands
 * in one place instead of per-screen checks drifting apart (strategic ADR-1).
 *
 * [Unrecognized.lookalike] carries what a refused text resembled, so the caller can say so in its one
 * message. The broadcast descriptor is a future kind owned by S4160 and will be added here.
 */
sealed interface IntakeTextDispatch {

    data class ExchangeString(val value: ExchangeConnectionString) : IntakeTextDispatch

    /** `FMSEXCH2:` and up - the user needs a newer app; nothing was read. */
    data class NewerExchangeMajor(val major: Int) : IntakeTextDispatch

    data class ShareCode(val payload: SftpPairingPayload) : IntakeTextDispatch

    object CompanionConfig : IntakeTextDispatch

    data class Unrecognized(val lookalike: ExchangeConnectionStringLookalike? = null) : IntakeTextDispatch
}
