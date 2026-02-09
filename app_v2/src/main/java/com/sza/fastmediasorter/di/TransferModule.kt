package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.transfer.FileAccess
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import com.sza.fastmediasorter.data.transfer.access.LocalFileAccess
import com.sza.fastmediasorter.data.transfer.access.SmbFileAccess
import com.sza.fastmediasorter.data.transfer.strategies.LocalToSmbStrategy
import com.sza.fastmediasorter.data.transfer.strategies.SmbToLocalStrategy
import com.sza.fastmediasorter.data.transfer.strategies.SmbToSmbStrategy
import com.sza.fastmediasorter.data.transfer.strategies.LocalToSftpStrategy
import com.sza.fastmediasorter.data.transfer.strategies.SftpToLocalStrategy
import com.sza.fastmediasorter.data.transfer.strategies.SftpToSftpStrategy
import com.sza.fastmediasorter.data.transfer.strategies.LocalToFtpStrategy
import com.sza.fastmediasorter.data.transfer.strategies.FtpToLocalStrategy
import com.sza.fastmediasorter.data.transfer.strategies.FtpToFtpStrategy
import com.sza.fastmediasorter.data.transfer.access.SftpFileAccess
import com.sza.fastmediasorter.data.transfer.access.FtpFileAccess
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class TransferModule {

    @Binds
    @IntoSet
    abstract fun bindLocalToSmbStrategy(impl: LocalToSmbStrategy): TransferStrategy

    @Binds
    @IntoSet
    abstract fun bindSmbToLocalStrategy(impl: SmbToLocalStrategy): TransferStrategy

    @Binds
    @IntoSet
    abstract fun bindSmbToSmbStrategy(impl: SmbToSmbStrategy): TransferStrategy

    @Binds
    @IntoSet
    abstract fun bindLocalFileAccess(impl: LocalFileAccess): FileAccess

    @Binds
    @IntoSet
    abstract fun bindSmbFileAccess(impl: SmbFileAccess): FileAccess

    @Binds
    @IntoSet
    abstract fun bindLocalToSftpStrategy(impl: LocalToSftpStrategy): TransferStrategy

    @Binds
    @IntoSet
    abstract fun bindSftpToLocalStrategy(impl: SftpToLocalStrategy): TransferStrategy

    @Binds
    @IntoSet
    abstract fun bindSftpToSftpStrategy(impl: SftpToSftpStrategy): TransferStrategy

    @Binds
    @IntoSet
    abstract fun bindSftpFileAccess(impl: SftpFileAccess): FileAccess

    @Binds
    @IntoSet
    abstract fun bindLocalToFtpStrategy(impl: LocalToFtpStrategy): TransferStrategy

    @Binds
    @IntoSet
    abstract fun bindFtpToLocalStrategy(impl: FtpToLocalStrategy): TransferStrategy

    @Binds
    @IntoSet
    abstract fun bindFtpToFtpStrategy(impl: FtpToFtpStrategy): TransferStrategy

    @Binds
    @IntoSet
    abstract fun bindFtpFileAccess(impl: FtpFileAccess): FileAccess
}
