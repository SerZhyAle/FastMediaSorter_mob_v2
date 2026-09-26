package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.data.preferences.WearClockStyleStore
import com.sza.fastmediasorter.wear.data.preferences.WearPreferencesRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.WearClockStyleRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S2655: the settings binding, in its own module rather than in `WearAppModule`.
 *
 * The seven themed sections and the composite that delegates to them all carry `@Inject`
 * constructors, so Hilt needs only the interface-to-implementation binding and `@Binds` states it
 * without a body. It moved out of `WearAppModule` because that object stood at detekt's
 * `TooManyFunctions` threshold of 40 - the same ceiling this ticket exists to clear, one file over.
 */
@Module
@InstallIn(SingletonComponent::class)
interface WearPreferencesModule {

    @Binds
    @Singleton
    fun bindWearPreferencesRepository(impl: WearPreferencesRepositoryImpl): WearPreferencesRepository

    @Binds
    @Singleton
    fun bindWearClockStyleRepository(impl: WearClockStyleStore): WearClockStyleRepository
}
