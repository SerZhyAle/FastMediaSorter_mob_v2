package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.domain.systeminfo.AppInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.DeviceInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.HealthInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.MemoryInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.PhoneLinkContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.RadioCapabilityContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.SensorsInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.StorageInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.WearSystemInfoContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

/**
 * Declares the multibound set of report contributors and binds the ones every flavor carries.
 *
 * `@Multibinds` rather than a plain set of `@IntoSet` bindings, so the set stays injectable in a build
 * where nothing occupies the extended slot - which is the `standard` flavor, where
 * `wear/src/noLegal/`'s contributor is not compiled in at all.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearSystemInfoModule {

    @Multibinds
    abstract fun systemInfoContributors(): Set<@JvmSuppressWildcards WearSystemInfoContributor>

    @Binds
    @IntoSet
    abstract fun bindDeviceInfoContributor(impl: DeviceInfoContributor): WearSystemInfoContributor

    @Binds
    @IntoSet
    abstract fun bindAppInfoContributor(impl: AppInfoContributor): WearSystemInfoContributor

    @Binds
    @IntoSet
    abstract fun bindHealthInfoContributor(impl: HealthInfoContributor): WearSystemInfoContributor

    @Binds
    @IntoSet
    abstract fun bindSensorsInfoContributor(impl: SensorsInfoContributor): WearSystemInfoContributor

    @Binds
    @IntoSet
    abstract fun bindRadioCapabilityContributor(
        impl: RadioCapabilityContributor
    ): WearSystemInfoContributor

    @Binds
    @IntoSet
    abstract fun bindMemoryInfoContributor(impl: MemoryInfoContributor): WearSystemInfoContributor

    @Binds
    @IntoSet
    abstract fun bindStorageInfoContributor(impl: StorageInfoContributor): WearSystemInfoContributor

    @Binds
    @IntoSet
    abstract fun bindPhoneLinkContributor(impl: PhoneLinkContributor): WearSystemInfoContributor
}
