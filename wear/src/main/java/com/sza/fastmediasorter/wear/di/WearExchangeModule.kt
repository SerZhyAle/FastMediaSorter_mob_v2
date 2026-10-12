package com.sza.fastmediasorter.wear.di

import android.os.Build
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.data.exchange.EncryptedWearExchangeEntryStore
import com.sza.fastmediasorter.wear.data.exchange.TlsWearExchangeConnector
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeConnector
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeDeviceProfile
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntryStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** S4158: binds the watch's exchange entries - the TLS connector, the encrypted store, the device profile. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WearExchangeModule {

    @Binds
    abstract fun bindConnector(impl: TlsWearExchangeConnector): WearExchangeConnector

    @Binds
    abstract fun bindEntryStore(impl: EncryptedWearExchangeEntryStore): WearExchangeEntryStore
}

/** S4158: how the watch names itself in `enroll`; read from the build and the device, never from settings. */
@Module
@InstallIn(SingletonComponent::class)
object WearExchangeProfileModule {

    @Provides
    fun provideDeviceProfile(): WearExchangeDeviceProfile =
        WearExchangeDeviceProfile(deviceName = Build.MODEL, productVersion = BuildConfig.VERSION_NAME)
}
