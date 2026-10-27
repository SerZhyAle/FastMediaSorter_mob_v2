package com.sza.fastmediasorter.domain.usecase.broadcast

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastLinkChoice
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordMode
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordRules
import com.sza.fastmediasorter.domain.repository.BroadcastLinkPasswordStore
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * S4405: remembers the person's link protection and applies it to the live broadcast at once. The previous password
 * stops opening the link the way "New password" revokes one (LIVE-BROADCAST item R); the announcer sends the new
 * verifiers with `broadcast-update` on its own.
 */
class SetBroadcastLinkPasswordUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val linkPasswordStore: BroadcastLinkPasswordStore,
    private val credentials: BroadcastCredentialRegistry,
) {

    sealed interface Result {
        /** [livePassword] is the link password now in force, null for an open link or when nothing is live. */
        class Applied(val livePassword: String?) : Result

        data class Invalid(val verdict: BroadcastLinkPasswordRules.Verdict) : Result

        /** The secret store could not encrypt the password; nothing was changed. */
        data object StoreFailed : Result
    }

    /**
     * The mode the dialog opens on: the choice protecting the live link while a broadcast is announced, otherwise the
     * remembered one. They differ when a broadcast started under a prepared link (S4247), which takes that link's
     * choice - the remembered mode would then name a protection the live link does not have.
     */
    suspend fun currentMode(): BroadcastLinkPasswordMode {
        val live = credentials.linkChoice?.mode
        Timber.d("S4440: link password dialog mode live=$live")
        return live ?: settingsRepository.getSettings().first().broadcast.linkPasswordMode
    }

    suspend operator fun invoke(mode: BroadcastLinkPasswordMode, password: String?): Result {
        Timber.d("S4405: apply link password mode=$mode")
        val choice = when (mode) {
            BroadcastLinkPasswordMode.CUSTOM -> {
                val text = password.orEmpty()
                storeCustom(text)?.let { refusal -> return refusal }
                BroadcastLinkChoice.Custom(text)
            }
            BroadcastLinkPasswordMode.NONE -> {
                linkPasswordStore.clear()
                BroadcastLinkChoice.Open
            }
            BroadcastLinkPasswordMode.AUTO -> {
                linkPasswordStore.clear()
                BroadcastLinkChoice.Generated
            }
        }
        settingsRepository.updateSettings { settings ->
            settings.copy(broadcast = settings.broadcast.copy(linkPasswordMode = mode))
        }
        return Result.Applied(credentials.applyLink(choice)?.password)
    }

    // The refusal, or null once the password is stored - before the mode is written, so CUSTOM never outlives it.
    private suspend fun storeCustom(password: String): Result? {
        val verdict = BroadcastLinkPasswordRules.validate(password)
        return when {
            verdict != BroadcastLinkPasswordRules.Verdict.OK -> Result.Invalid(verdict)
            !linkPasswordStore.put(password) -> Result.StoreFailed
            else -> null
        }
    }
}
