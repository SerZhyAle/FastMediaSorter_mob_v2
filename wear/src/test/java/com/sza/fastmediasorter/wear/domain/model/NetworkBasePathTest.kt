package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkBasePathTest {

    @Test
    fun `smb url collapses to the share root`() {
        val result = NetworkBasePath.normalize(
            rawPath = "smb://192.168.1.100/common",
            type = NetworkSourceType.SMB,
            shareName = "common"
        )

        assertEquals("", result)
    }

    @Test
    fun `smb url keeps the folders below the share`() {
        val result = NetworkBasePath.normalize(
            rawPath = "smb://192.168.1.100/common/photos/2026",
            type = NetworkSourceType.SMB,
            shareName = "common"
        )

        assertEquals("photos/2026", result)
    }

    @Test
    fun `smb share name is matched case insensitively`() {
        val result = NetworkBasePath.normalize(
            rawPath = "smb://server/Common/photos",
            type = NetworkSourceType.SMB,
            shareName = "common"
        )

        assertEquals("photos", result)
    }

    @Test
    fun `smb path that is already share relative is unchanged`() {
        val result = NetworkBasePath.normalize(
            rawPath = "photos/2026",
            type = NetworkSourceType.SMB,
            shareName = "common"
        )

        assertEquals("photos/2026", result)
    }

    @Test
    fun `sftp url drops scheme host and port`() {
        val result = NetworkBasePath.normalize(
            rawPath = "sftp://192.168.1.112:2222/media/photos",
            type = NetworkSourceType.SFTP,
            shareName = null
        )

        assertEquals("/media/photos", result)
    }

    @Test
    fun `ftp url without a path is the server root`() {
        val result = NetworkBasePath.normalize(
            rawPath = "ftp://193.178.50.43:21",
            type = NetworkSourceType.FTP,
            shareName = null
        )

        assertEquals("/", result)
    }

    @Test
    fun `watch created source keeps its root`() {
        val result = NetworkBasePath.normalize(
            rawPath = "/",
            type = NetworkSourceType.FTP,
            shareName = null
        )

        assertEquals("/", result)
    }

    @Test
    fun `normalising twice changes nothing`() {
        val once = NetworkBasePath.normalize(
            rawPath = "smb://192.168.1.100/common/photos",
            type = NetworkSourceType.SMB,
            shareName = "common"
        )
        val twice = NetworkBasePath.normalize(once, NetworkSourceType.SMB, "common")

        assertEquals(once, twice)
    }
}
