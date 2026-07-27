package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.domain.usecase.streams.StreamTrackPreferenceUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S1144 (ADR-9): exposes [StreamTrackPreferenceUseCase] to `PlaybackControlDialogFragment`, which is a
 * plain `DialogFragment` rather than an `@AndroidEntryPoint` and already resolves its other Hilt
 * dependency this way (see `MediaCapabilitiesEntryPoint`). Making the dialog an entry point instead
 * would pull the whole fragment into the Hilt graph for one use case.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface StreamTrackPreferenceEntryPoint {
    fun streamTrackPreferenceUseCase(): StreamTrackPreferenceUseCase
}
