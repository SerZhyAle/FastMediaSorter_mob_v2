package com.sza.fastmediasorter.di

import android.content.Context
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.FileOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.FtpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SftpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SmbOperationStrategy
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Singleton

/**
 * Hilt module that provides a [Map]<[String], [FileOperationStrategy]> for directory-level
 * operations (delete, rename, copy, move) in [com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler].
 *
 * Keys match the protocol prefix identifiers used by [UnifiedFileOperationHandler.getStrategy]:
 * "local", "smb", "sftp", "ftp", "cloud".
 */
@Module
@InstallIn(SingletonComponent::class)
object DirectoryStrategyModule {

    @Provides
    @Singleton
    @IntoMap
    @StringKey("local")
    fun provideLocalDirectoryStrategy(
        @ApplicationContext context: Context
    ): FileOperationStrategy = LocalOperationStrategy(context)

    @Provides
    @Singleton
    @IntoMap
    @StringKey("smb")
    fun provideSmbDirectoryStrategy(
        @ApplicationContext context: Context,
        smbClient: SmbClient,
        credentialsRepository: NetworkCredentialsRepository
    ): FileOperationStrategy = SmbOperationStrategy(context, smbClient, credentialsRepository)

    @Provides
    @Singleton
    @IntoMap
    @StringKey("sftp")
    fun provideSftpDirectoryStrategy(
        @ApplicationContext context: Context,
        sftpClient: SftpClient,
        credentialsRepository: NetworkCredentialsRepository
    ): FileOperationStrategy = SftpOperationStrategy(context, sftpClient, credentialsRepository)

    @Provides
    @Singleton
    @IntoMap
    @StringKey("ftp")
    fun provideFtpDirectoryStrategy(
        @ApplicationContext context: Context,
        ftpClient: FtpClient,
        credentialsRepository: NetworkCredentialsRepository
    ): FileOperationStrategy = FtpOperationStrategy(context, ftpClient, credentialsRepository)

    @Provides
    @Singleton
    @IntoMap
    @StringKey("cloud")
    fun provideCloudDirectoryStrategy(
        @ApplicationContext context: Context,
        googleDriveClient: GoogleDriveRestClient,
        dropboxClient: DropboxClient,
        oneDriveClient: OneDriveRestClient
    ): FileOperationStrategy = CloudOperationStrategy(context, googleDriveClient, dropboxClient, oneDriveClient)
}
