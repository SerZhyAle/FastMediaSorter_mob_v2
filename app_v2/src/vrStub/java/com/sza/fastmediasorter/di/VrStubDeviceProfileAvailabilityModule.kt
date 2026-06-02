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
 * Non-VR flavors (standard / lite / photos / legacy mount `src/vrStub/java`): the VR headset profile
 * is hidden from the picker.
 */
@Module
@InstallIn(SingletonComponent::class)
object VrStubDeviceProfileAvailabilityModule {

    @Provides
    @Singleton
    fun provideDeviceProfileAvailability(): DeviceProfileAvailability =
        object : DeviceProfileAvailability {
            override val selectableProfiles: List<DeviceProfileType> =
                DeviceProfileUi.displayOrder.filter { it != DeviceProfileType.VR_HEADSET }
        }
}
