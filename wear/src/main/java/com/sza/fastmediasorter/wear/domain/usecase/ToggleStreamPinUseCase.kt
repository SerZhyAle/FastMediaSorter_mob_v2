package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.repository.WearStreamPinsRepository
import javax.inject.Inject

/**
 * S2497: toggling a stream pin and synchronizing with the phone are one action.
 *
 * Persists the pin change locally and triggers an immediate delta delivery to the phone.
 */
class ToggleStreamPinUseCase @Inject constructor(
    private val pinsRepository: WearStreamPinsRepository,
    private val sendStreamPinsDelta: SendStreamPinsDeltaUseCase
) {

    /** Toggles the pin state for [urlOrIdentity] and pushes the change to the phone. Returns the new state. */
    suspend fun toggle(urlOrIdentity: String): Boolean {
        val newState = pinsRepository.togglePin(urlOrIdentity)
        sendStreamPinsDelta()
        return newState
    }

    /** Sets the pin state for [urlOrIdentity] based on [wasPinned] and pushes to phone. Returns the new state. */
    suspend fun toggle(urlOrIdentity: String, wasPinned: Boolean): Boolean {
        val newState = !wasPinned
        pinsRepository.setPin(urlOrIdentity, newState)
        sendStreamPinsDelta()
        return newState
    }

    /** Checks whether the stream is pinned either locally on watch or from phone. */
    fun isPinned(urlOrIdentity: String): Boolean =
        pinsRepository.isPinned(urlOrIdentity)
}
