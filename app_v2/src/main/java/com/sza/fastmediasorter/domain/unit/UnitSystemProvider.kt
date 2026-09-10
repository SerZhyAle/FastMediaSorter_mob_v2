package com.sza.fastmediasorter.domain.unit

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.usecase.ObserveUnitSystemUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2795: the current measurement system, cheap to read and observable.
 *
 * `SettingsRepository` already exposes the value; what is missing is a read a list row or a ticking
 * clock can make on every frame without touching storage. That is a caching lifetime rather than a
 * repository concern, so it lives here - widening the repository would hand a cached field to every
 * one of its consumers when only the format seam needs one.
 */
@Singleton
class UnitSystemProvider @Inject constructor(
    observeUnitSystemUseCase: ObserveUnitSystemUseCase,
    @ApplicationScope scope: CoroutineScope,
) {

    /** For surfaces that must redraw when the setting flips while they are on screen. */
    val current: StateFlow<UnitSystem> = observeUnitSystemUseCase()
        .onEach {
            Timber.d("S2795: unit system published to every surface = %s", it)
        }
        .stateIn(scope, SharingStarted.Eagerly, UnitSystem.DEFAULT)

    /** For a one-shot render - a widget update, an adapter bind, a dialog being built. */
    val value: UnitSystem
        get() = current.value
}
