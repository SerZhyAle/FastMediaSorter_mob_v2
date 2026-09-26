package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearClockStylePayload
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.launcher.LauncherSettings
import com.sza.fastmediasorter.domain.repository.ClockDialStyleSource
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S3557: publishes the launcher clock dial and wallpaper style to the watch face on every change.
 *
 * Shaped after [PushWearStreamPinsUseCase], with one difference: it does not wait for a connected
 * watch. The style is state carried by a Data Item, which the Data Layer delivers on reconnect, so a
 * gesture made while the watch is away must still be put rather than dropped.
 */
class PushWearClockStyleUseCase @Inject constructor(
    private val wearableRepository: WearableDataLayerRepository,
    private val gson: Gson,
    private val clockDialStyleSource: ClockDialStyleSource,
    private val settingsRepository: SettingsRepository,
) {

    /** Gated on [SettingsRepository] enableWearCompanion; re-enabling it republishes the current style. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAndPush(scope: CoroutineScope): Job = scope.launch {
        settingsRepository.getSettings()
            .map { it.enableWearCompanion }
            .distinctUntilChanged()
            .flatMapLatest { enabled ->
                if (enabled) {
                    observeStyle()
                } else {
                    emptyFlow()
                }
            }
            // The collector lives for the whole process; a throw escaping it would reach the default
            // uncaught-exception handler, a process-wide surface this feature must not take down.
            .catch { Timber.w(it, "Wear clock style: style stream failed") }
            .collectLatest { style ->
                push(style).onFailure { Timber.w(it, "Wear clock style not pushed") }
            }
    }

    // Debounced because a colour or tuning slider writes on every step, and each put is a Data Layer
    // sync; only the value the owner settles on is worth sending.
    @OptIn(FlowPreview::class)
    private fun observeStyle(): Flow<WearClockStylePayload> = combine(
        clockDialStyleSource.observe(),
        settingsRepository.getSettings().map { it.launcher.toWallpaperStyle() }.distinctUntilChanged(),
    ) { dial, wallpaper ->
        wallpaper.copy(
            secondsVisible = dial.secondsVisible,
            dialColor = dial.dialColor,
            dialTypeface = dial.typefaceName,
        )
    }
        .debounce(DEBOUNCE_MS)
        .distinctUntilChanged()

    private suspend fun push(style: WearClockStylePayload): Result<Unit> = runCatching {
        val sentAt = System.currentTimeMillis()
        val payloadBytes = gson.toJson(style.copy(sentAt = sentAt)).toByteArray(Charsets.UTF_8)
        val envelope = WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_CLOCK_STYLE,
            sentAt = sentAt,
            data = payloadBytes,
        )
        wearableRepository.putEnvelopeDataItem(WearDataLayerPaths.CLOCK_STYLE, envelope)
    }

    private fun LauncherSettings.toWallpaperStyle() = WearClockStylePayload(
        animationPalette = animationPalette,
        wallpaperIntensity = wallpaperIntensity,
        wallpaperAnimationSpeed = wallpaperAnimationSpeed,
        wallpaperParticleDensity = wallpaperParticleDensity,
    )

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
