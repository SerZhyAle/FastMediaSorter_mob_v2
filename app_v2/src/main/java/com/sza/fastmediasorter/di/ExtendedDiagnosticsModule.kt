package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.systeminfo.ExtendedDiagnosticsContributor
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the multibound set of [ExtendedDiagnosticsContributor]s so it is injectable even when no
 * flavor contributes one (every non-noLegal flavor injects an empty set). The real contributor is
 * bound `@IntoSet` from `src/noLegal/java`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExtendedDiagnosticsModule {

    @Multibinds
    abstract fun extendedDiagnosticsContributors(): Set<@JvmSuppressWildcards ExtendedDiagnosticsContributor>
}
