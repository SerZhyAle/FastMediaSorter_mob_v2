package com.sza.fastmediasorter.domain.usecase.intake

import com.sza.fastmediasorter.data.companion.CompanionConfigParser
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringCodec
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringLookalike
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringResult
import com.sza.fastmediasorter.domain.model.IntakeTextDispatch
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import javax.inject.Inject

/**
 * S4155: the single classification point for intake text (ADR-1). Stateless and Android-free, so every
 * surface - UI, exported receivers, tests - shares one decision instead of one branch per screen.
 */
class DispatchIntakeTextUseCase @Inject constructor() {

    fun dispatch(text: String): IntakeTextDispatch {
        val trimmed = text.trim()
        val codecResult = ExchangeConnectionStringCodec.decode(trimmed)
        return when (codecResult) {
            is ExchangeConnectionStringResult.Read ->
                IntakeTextDispatch.ExchangeString(codecResult.value)
            is ExchangeConnectionStringResult.NewerMajor ->
                IntakeTextDispatch.NewerExchangeMajor(codecResult.major)
            is ExchangeConnectionStringResult.Refused ->
                dispatchNonExchange(trimmed, codecResult.looks)
        }
    }

    private fun dispatchNonExchange(
        trimmed: String,
        lookalike: ExchangeConnectionStringLookalike?,
    ): IntakeTextDispatch {
        val shareCode = SftpPairingPayload.decode(trimmed)
        return when {
            shareCode != null -> IntakeTextDispatch.ShareCode(shareCode)
            trimmed.startsWith(CompanionConfigParser.COMPRESSED_PREFIX) -> IntakeTextDispatch.CompanionConfig
            else -> IntakeTextDispatch.Unrecognized(lookalike)
        }
    }
}
