package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.diagnostics.NoLegalWearInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.WearSystemInfoContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Binds the noLegal report contributor into the multibound set `WearSystemInfoModule` declares in
 * `src/main`. Compiled only into the noLegal flavor, on the pattern of `app_v2`'s
 * `NoLegalExtendedDiagnosticsModule`.
 *
 * This is why the set in `src/main` is declared with `@Multibinds` rather than assembled from
 * `@IntoSet` bindings alone: in the `standard` flavor this file does not exist, nothing occupies the
 * extended slot, and the set has to stay injectable while empty of it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalWearInfoModule {

    @Binds
    @IntoSet
    abstract fun bindNoLegalWearInfoContributor(
        impl: NoLegalWearInfoContributor
    ): WearSystemInfoContributor
}
