package com.sza.fastmediasorter.data.network.datasource

import android.content.Context
import androidx.media3.datasource.DataSource
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FtpDataSourceFactory] - confirms it constructs an [FtpDataSource]. The actual
 * open()/read() paths require a live FTP socket and Android permission checks, so are out of scope;
 * construction only stores the collaborators and is JVM-safe. Collaborators are mocked.
 */
class FtpDataSourceFactoryTest {

    @Test
    fun `createDataSource builds an FtpDataSource instance`() {
        val factory = FtpDataSourceFactory(
            ftpClient = mockk<FtpClient>(relaxed = true),
            host = "192.168.0.5",
            port = 21,
            username = "u",
            password = "p",
            context = mockk<Context>(relaxed = true)
        )
        val created: DataSource = factory.createDataSource()
        assertTrue(created is FtpDataSource)
    }
}
