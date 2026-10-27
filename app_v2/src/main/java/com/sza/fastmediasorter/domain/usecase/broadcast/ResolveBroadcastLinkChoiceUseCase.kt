package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastLinkChoice
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordMode
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordRules
import com.sza.fastmediasorter.domain.repository.BroadcastLinkPasswordStore
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * S4405: the link protection the next announcement starts with, from the remembered mode. A restored backup carries
 * CUSTOM without the password, and a stored password the rule no longer accepts is no password either - both fall
 * back to a generated one, never to an open link.
 */
class ResolveBroadcastLinkChoiceUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val linkPasswordStore: BroadcastLinkPasswordStore,
) {
    /** S4440: the setting as the person left it, which differs from the live link when a prepared link started. */
    suspend fun rememberedMode(): BroadcastLinkPasswordMode =
        settingsRepository.getSettings().first().broadcast.linkPasswordMode

    suspend operator fun invoke(): BroadcastLinkChoice {
        val mode = rememberedMode()
        Timber.d("S4405: resolve link choice for announcement mode=$mode")
        return when (mode) {
            BroadcastLinkPasswordMode.NONE -> BroadcastLinkChoice.Open
            BroadcastLinkPasswordMode.CUSTOM -> linkPasswordStore.get()
                ?.takeIf { BroadcastLinkPasswordRules.validate(it) == BroadcastLinkPasswordRules.Verdict.OK }
                ?.let(BroadcastLinkChoice::Custom)
                ?: BroadcastLinkChoice.Generated
            BroadcastLinkPasswordMode.AUTO -> BroadcastLinkChoice.Generated
        }
    }
}
