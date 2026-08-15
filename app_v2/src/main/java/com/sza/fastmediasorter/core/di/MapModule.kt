package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.data.location.PlatformDeviceLocationSource
import com.sza.fastmediasorter.data.map.HttpMapsShortLinkResolver
import com.sza.fastmediasorter.data.map.MAP_TILE_CLIENT
import com.sza.fastmediasorter.data.map.MapRepositoryImpl
import com.sza.fastmediasorter.data.map.OsmMapTileProvider
import com.sza.fastmediasorter.data.map.PlatformMapPlaceLabelProvider
import com.sza.fastmediasorter.data.map.TileUserAgent
import com.sza.fastmediasorter.domain.location.DeviceLocationSource
import com.sza.fastmediasorter.domain.map.MapPlaceLabelProvider
import com.sza.fastmediasorter.domain.map.MapTileProvider
import com.sza.fastmediasorter.domain.map.MapsShortLinkResolver
import com.sza.fastmediasorter.domain.repository.MapRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/** S1175: the map source, its cache, and the position behind it. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MapModule {

    @Binds
    @Singleton
    abstract fun bindDeviceLocationSource(impl: PlatformDeviceLocationSource): DeviceLocationSource

    @Binds
    @Singleton
    abstract fun bindMapTileProvider(impl: OsmMapTileProvider): MapTileProvider

    @Binds
    @Singleton
    abstract fun bindMapPlaceLabelProvider(impl: PlatformMapPlaceLabelProvider): MapPlaceLabelProvider

    @Binds
    @Singleton
    abstract fun bindMapRepository(impl: MapRepositoryImpl): MapRepository

    @Binds
    @Singleton
    abstract fun bindMapsShortLinkResolver(impl: HttpMapsShortLinkResolver): MapsShortLinkResolver
}

/**
 * A dedicated client rather than the shared one from [AppModule]: the tile source blocks by
 * User-Agent, and pinning that header on the shared client would rewrite every other request the app
 * makes. Same reasoning as the `linkDownload` client in `LinkDownloadModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
object MapTileClientModule {

    @Provides
    @Singleton
    @Named(MAP_TILE_CLIENT)
    fun provideTileClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(TileUserAgentInterceptor)
        .build()

    private object TileUserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", TileUserAgent.value())
                .build(),
        )
    }

    private const val TIMEOUT_SECONDS = 15L
}
