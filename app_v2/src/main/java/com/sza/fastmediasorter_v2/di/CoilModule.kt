package com.sza.fastmediasorter_v2.di

import android.content.Context
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.sza.fastmediasorter_v2.data.network.SmbClient
import com.sza.fastmediasorter_v2.data.network.coil.NetworkFileFetcher
import com.sza.fastmediasorter_v2.data.remote.sftp.SftpClient
import com.sza.fastmediasorter_v2.domain.repository.NetworkCredentialsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
        return ImageLoader.Builder(context)
            .components {
                // Add video frame decoder for local video thumbnails
                add(VideoFrameDecoder.Factory())
                
                // Add network file fetcher for SMB/SFTP thumbnails
                add(NetworkFileFetcher.Factory(smbClient, sftpClient, credentialsRepository))
            }
            .crossfade(true)
            .build()
    }
}
