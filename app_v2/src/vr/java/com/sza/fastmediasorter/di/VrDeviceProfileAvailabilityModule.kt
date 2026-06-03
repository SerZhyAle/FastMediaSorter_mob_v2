package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.ui.common.DeviceProfileUi
import com.sza.fastmediasorter.ui.profile.DeviceProfileAvailability
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * VR-capable flavors (`vr`, and `noLegal` which mounts `src/vr/java`): every profile is selectable,
 * including VR headset.
 */
@Module
@InstallIn(SingletonComponent::class)
object VrDeviceProfileAvailabilityModule {

    @Provides
    @Singleton
    fun provideDeviceProfileAvailability(): DeviceProfileAvailability =
        object : DeviceProfileAvailability {
            override val selectableProfiles: List<DeviceProfileType> = DeviceProfileUi.displayOrder
        }
}
