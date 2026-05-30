package com.sza.fastmediasorter.data.network.datasource

import android.content.Context
import androidx.media3.datasource.DataSource
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SmbDataSourceFactory] - confirms it constructs an [SmbDataSource]. The
 * open()/read() watchdog paths require a live SMB socket + Android Uri/permission, so are out of
 * scope; construction only stores the collaborators and is JVM-safe. Collaborators are mocked.
 */
class SmbDataSourceFactoryTest {

    @Test
    fun `createDataSource builds an SmbDataSource instance`() {
        val factory = SmbDataSourceFactory(
            smbClient = mockk<SmbClient>(relaxed = true),
            connectionInfo = SmbConnectionInfo(server = "srv", shareName = "share"),
            context = mockk<Context>(relaxed = true)
        )
        val created: DataSource = factory.createDataSource()
        assertTrue(created is SmbDataSource)
    }
}
