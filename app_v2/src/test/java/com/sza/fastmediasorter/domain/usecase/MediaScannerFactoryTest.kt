package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.cloud.CloudMediaScanner
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.data.network.SmbMediaScanner
import com.sza.fastmediasorter.data.remote.ftp.FtpMediaScanner
import com.sza.fastmediasorter.data.remote.sftp.SftpMediaScanner
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.scanner.WearWatchMediaScanner
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test

class MediaScannerFactoryTest {

    private val local = mockk<LocalMediaScanner>()
    private val smb = mockk<SmbMediaScanner>()
    private val sftp = mockk<SftpMediaScanner>()
    private val ftp = mockk<FtpMediaScanner>()
    private val cloud = mockk<CloudMediaScanner>()
    private val wearWatch = mockk<WearWatchMediaScanner>()

    private val factory = MediaScannerFactory(local, smb, sftp, ftp, cloud, wearWatch)

    @Test
    fun `getScanner returns the scanner matching the resource type`() {
        assertSame(local, factory.getScanner(ResourceType.LOCAL))
        assertSame(smb, factory.getScanner(ResourceType.SMB))
        assertSame(sftp, factory.getScanner(ResourceType.SFTP))
        assertSame(ftp, factory.getScanner(ResourceType.FTP))
        assertSame(cloud, factory.getScanner(ResourceType.CLOUD))
        assertSame(wearWatch, factory.getScanner(ResourceType.WEAR_WATCH))
    }
}
