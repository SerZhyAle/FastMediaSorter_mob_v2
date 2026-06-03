package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.systeminfo.ExtendedDiagnosticsContributor
import com.sza.fastmediasorter.diagnostics.NoLegalExtendedDiagnosticsContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Binds the noLegal extended-diagnostics contributor into the multibound set declared by
 * `ExtendedDiagnosticsModule` in `src/main`. Compiled only into the noLegal flavor.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalExtendedDiagnosticsModule {

    @Binds
    @IntoSet
    abstract fun bindExtendedDiagnosticsContributor(
        impl: NoLegalExtendedDiagnosticsContributor,
    ): ExtendedDiagnosticsContributor
}
