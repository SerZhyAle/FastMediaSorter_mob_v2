package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.bodysensor.HealthServicesBodySensorDataSource
import com.sza.fastmediasorter.wear.bodysensor.SamsungRawPpgDataSource
import com.sza.fastmediasorter.wear.domain.bodysensor.WearBodySensorDataSource
import com.sza.fastmediasorter.wear.domain.bodysensor.WearPpgDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the `noLegal` answer for [WearBodySensorDataSource]. Mirrors `StandardBodySensorModule` in the
 * other flavor source set; exactly one of the two is ever compiled, so a missing declaration here fails
 * the `noLegal` build rather than silently falling back to the withheld answer (S2457).
 *
 * Deliberately unscoped, for the reason `WearNetworkMonitorModule` records for the same choice: reopening
 * the diagnostic must measure afresh rather than replay a reading kept from the previous visit. The
 * component is where the binding lives, not a lifetime - the sensor session's lifetime belongs to the
 * cold flow in [WearBodySensorDataSource.measure].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalBodySensorModule {

    @Binds
    abstract fun bindWearBodySensorDataSource(
        impl: HealthServicesBodySensorDataSource
    ): WearBodySensorDataSource

    /** S3113: the raw pulse-wave source behind the blood-pressure estimate, unscoped for the same reason. */
    @Binds
    abstract fun bindWearPpgDataSource(
        impl: SamsungRawPpgDataSource
    ): WearPpgDataSource
}
