package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.core.format.QuantityFormatter
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S2795: the format seam for surfaces outside the injection graph - home-screen widget providers and
 * their remote-views services.
 *
 * Resolve it inside the refresh path on every update. A widget cannot hold a settings subscription, so
 * a formatter cached in a field keeps rendering the previous system until the provider is recreated.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UnitSystemEntryPoint {

    fun unitSystemProvider(): UnitSystemProvider

    fun quantityFormatter(): QuantityFormatter
}
