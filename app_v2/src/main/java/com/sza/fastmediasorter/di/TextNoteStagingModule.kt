package com.sza.fastmediasorter.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Marker module for the text-note staging subsystem (S0189).
 *
 * [com.sza.fastmediasorter.data.local.TextNoteStagingDirectory] and
 * [com.sza.fastmediasorter.data.local.TextNoteStagingRegistry] are both
 * `@Singleton @Inject constructor` types — Hilt resolves them automatically
 * without explicit `@Provides`. This module exists as a named anchor for
 * future overrides (e.g., test doubles or alternative staging paths).
 */
@Module
@InstallIn(SingletonComponent::class)
object TextNoteStagingModule
