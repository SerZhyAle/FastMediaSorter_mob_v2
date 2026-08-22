package com.sza.fastmediasorter.wear.di

import android.content.Context
import com.sza.fastmediasorter.wear.data.network.ftp.FtpDataSource
import com.sza.fastmediasorter.wear.data.network.sftp.SftpDataSource
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.data.thumbnail.EmbeddedPreviewReader
import com.sza.fastmediasorter.wear.data.thumbnail.WearThumbnailRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearThumbnailRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ThumbnailModule {

    /** Singleton because the cache is the point: a per-screen instance would re-read every scroll. */
    @Provides
    @Singleton
    fun provideWearThumbnailRepository(
        @ApplicationContext context: Context,
        networkSourceRepository: NetworkSourceRepository,
        smbDataSource: SmbDataSource,
        ftpDataSource: FtpDataSource,
        sftpDataSource: SftpDataSource,
        previewReader: EmbeddedPreviewReader
    ): WearThumbnailRepository = WearThumbnailRepositoryImpl(
        context,
        networkSourceRepository,
        smbDataSource,
        ftpDataSource,
        sftpDataSource,
        previewReader
    )
}
