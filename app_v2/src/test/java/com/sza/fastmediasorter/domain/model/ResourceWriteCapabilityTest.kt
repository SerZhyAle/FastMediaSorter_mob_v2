package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1019: the shared write-policy resolver. Network resources are writable when not read-only
 * (the connectivity-only probe must not hide actions the user enabled); LOCAL/CLOUD honour the
 * probed `isWritable`; streams never writable; `isReadOnly` always wins.
 */
class ResourceWriteCapabilityTest {

    private fun resource(
        type: ResourceType,
        isReadOnly: Boolean = false,
        isWritable: Boolean = false,
    ) = MediaResource(name = "r", path = "p", type = type, isReadOnly = isReadOnly, isWritable = isWritable)

    @Test
    fun `network resource is writable when not read-only regardless of the isWritable probe`() {
        // The reported bug: QR-imported SFTP resource with the connectivity probe still false.
        assertTrue(resource(ResourceType.SFTP, isReadOnly = false, isWritable = false).allowsWriteOperations())
        assertTrue(resource(ResourceType.SMB, isReadOnly = false, isWritable = false).allowsWriteOperations())
        assertTrue(resource(ResourceType.FTP, isReadOnly = false, isWritable = false).allowsWriteOperations())
    }

    @Test
    fun `read-only always blocks writes`() {
        assertFalse(resource(ResourceType.SFTP, isReadOnly = true, isWritable = true).allowsWriteOperations())
        assertFalse(resource(ResourceType.LOCAL, isReadOnly = true, isWritable = true).allowsWriteOperations())
        assertFalse(resource(ResourceType.CLOUD, isReadOnly = true, isWritable = true).allowsWriteOperations())
    }

    @Test
    fun `local and cloud honour the isWritable probe`() {
        assertTrue(resource(ResourceType.LOCAL, isWritable = true).allowsWriteOperations())
        assertFalse(resource(ResourceType.LOCAL, isWritable = false).allowsWriteOperations())
        assertTrue(resource(ResourceType.CLOUD, isWritable = true).allowsWriteOperations())
        assertFalse(resource(ResourceType.CLOUD, isWritable = false).allowsWriteOperations())
    }

    @Test
    fun `streams are never writable`() {
        assertFalse(resource(ResourceType.HTTP_STREAM, isWritable = true).allowsWriteOperations())
        assertFalse(resource(ResourceType.RTSP_STREAM, isWritable = true).allowsWriteOperations())
    }
}
