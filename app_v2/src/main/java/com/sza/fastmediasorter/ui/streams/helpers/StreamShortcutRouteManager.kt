package com.sza.fastmediasorter.ui.streams.helpers

import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.network.NetworkContextAnalyzer
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.streams.GetStreamSourceByUrlUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S1471: answers the one question a stream shortcut asks before anything is drawn - can this URL play
 * without a screen, or must the Streams screen open instead.
 *
 * It exists so the trampoline that asks holds no domain-layer collaborator of its own: Rule 3 forbids
 * that, and the `activity-logic` gate enforces it mechanically.
 */
class StreamShortcutRouteManager @Inject constructor(
    private val getStreamSourceByUrl: GetStreamSourceByUrlUseCase,
    private val settingsRepository: SettingsRepository,
    private val capabilityAvailability: CapabilityAvailability,
    private val networkContextAnalyzer: NetworkContextAnalyzer,
) {

    /**
     * The channel to play with no screen, or null when the caller must fall back to the Streams screen.
     * An unknown URL answers null rather than failing: a shortcut can outlive the channel's row, and
     * that screen already owns the "cannot play this" messaging.
     */
    suspend fun headlessSource(url: String): StreamSourceEntity? {
        val source = getStreamSourceByUrl(url) ?: return null
        val qualifies = StreamHeadlessPlayManager.qualifiesForHeadlessPlay(
            source = source,
            backgroundAudioEnabled = isBackgroundAudioEnabled(),
            hasNetwork = networkContextAnalyzer.hasAnyNetwork(),
        )
        return if (qualifies) source else null
    }

    /** Mirrors StreamsActivity.isBackgroundAudioEnabled: the user's switch AND the build's capability. */
    private suspend fun isBackgroundAudioEnabled(): Boolean =
        settingsRepository.getSettings().first().enablePersistentAudioPlayback &&
            capabilityAvailability.isPersistentAudioPlaybackAvailable()
}
