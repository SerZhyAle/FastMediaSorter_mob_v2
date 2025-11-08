package com.sza.fastmediasorter_v2.di

import android.content.Context
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.util.DebugLogger
import com.sza.fastmediasorter_v2.data.network.SmbClient
import com.sza.fastmediasorter_v2.data.network.coil.NetworkFileFetcher
import com.sza.fastmediasorter_v2.data.remote.sftp.SftpClient
import com.sza.fastmediasorter_v2.domain.repository.NetworkCredentialsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        smbClient: SmbClient,
        sftpClient: SftpClient,
        credentialsRepository: NetworkCredentialsRepository
    ): ImageLoader {
        // Configure dispatcher for parallel network requests
        val dispatcher = Dispatcher().apply {
            maxRequests = 12 // Increase from default 64 to limit parallel SMB connections
            maxRequestsPerHost = 4 // Limit per host to avoid overwhelming SMB server
        }
        
        return ImageLoader.Builder(context)
            .components {
                // Add video frame decoder for local video thumbnails
                add(VideoFrameDecoder.Factory())
                
                // Add network file fetcher for SMB/SFTP thumbnails
                add(NetworkFileFetcher.Factory(smbClient, sftpClient, credentialsRepository))
            }
            .crossfade(true)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED) // Enable disk cache
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED) // Enable memory cache
            .build()
    }
}
