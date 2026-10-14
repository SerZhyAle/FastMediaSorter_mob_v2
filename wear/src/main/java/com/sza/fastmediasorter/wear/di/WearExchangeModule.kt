package com.sza.fastmediasorter.wear.di

import android.os.Build
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.data.exchange.EncryptedWearExchangeEntryStore
import com.sza.fastmediasorter.wear.data.exchange.EncryptedWearResourcePinStore
import com.sza.fastmediasorter.wear.data.exchange.SocketWearLanEndpointProbe
import com.sza.fastmediasorter.wear.data.exchange.TlsWearExchangeConnector
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeConnector
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeDeviceProfile
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntryStore
import com.sza.fastmediasorter.wear.data.exchange.WearLanEndpointProbe
import com.sza.fastmediasorter.wear.data.exchange.WearResourcePinStore
import com.sza.fastmediasorter.wear.data.exchange.WearServerClipboardRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.WearAccountBroadcastRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.WearAccountBroadcastRepository
import com.sza.fastmediasorter.wear.domain.repository.WearServerClipboardRepository
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

    // S4228: the PINs of protected exchange folders, in the same encrypted file.
    @Binds
    abstract fun bindResourcePinStore(impl: EncryptedWearResourcePinStore): WearResourcePinStore

    // S4229: the account's broadcast list and the probe that tells a reachable LAN endpoint from a far one.
    @Binds
    abstract fun bindLanEndpointProbe(impl: SocketWearLanEndpointProbe): WearLanEndpointProbe

    @Binds
    abstract fun bindAccountBroadcastRepository(
        impl: WearAccountBroadcastRepositoryImpl,
    ): WearAccountBroadcastRepository

    // S4227: the clipboard on the exchange server, over the watchs own Wi-Fi or LTE.
    @Binds
    abstract fun bindServerClipboardRepository(
        impl: WearServerClipboardRepositoryImpl,
    ): WearServerClipboardRepository
}

/** S4158: how the watch names itself in `enroll`; read from the build and the device, never from settings. */
@Module
@InstallIn(SingletonComponent::class)
object WearExchangeProfileModule {

    @Provides
    fun provideDeviceProfile(): WearExchangeDeviceProfile =
        WearExchangeDeviceProfile(deviceName = Build.MODEL, productVersion = BuildConfig.VERSION_NAME)
}
