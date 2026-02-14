package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.domain.model.ResourceType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFileOperationHandlerTest {

    // Helper to call private method normalizeNetworkPath
    private fun normalizeNetworkPath(handler: CloudFileOperationHandler, path: String): String {
        val method = CloudFileOperationHandler::class.java.getDeclaredMethod("normalizeNetworkPath", String::class.java)
        method.isAccessible = true
        return method.invoke(handler, path) as String
    }
    
    // Helper to call private method getResourceType
    private fun getResourceType(handler: CloudFileOperationHandler, path: String): ResourceType {
        val method = CloudFileOperationHandler::class.java.getDeclaredMethod("getResourceType", String::class.java)
        method.isAccessible = true
        return method.invoke(handler, path) as ResourceType
    }
    
    // Helper to call private method isNetworkPath
    private fun isNetworkPath(handler: CloudFileOperationHandler, path: String): Boolean {
        val method = CloudFileOperationHandler::class.java.getDeclaredMethod("isNetworkPath", String::class.java)
        method.isAccessible = true
        return method.invoke(handler, path) as Boolean
    }

    @Test
    fun `test path normalization removes leading slash from network paths`() {
        val handler = createHandler()
        
        // Assert leading slashes are stripped for supported protocols
        assertEquals("smb://server/share", normalizeNetworkPath(handler, "/smb://server/share"))
        assertEquals("sftp://server/path", normalizeNetworkPath(handler, "/sftp://server/path"))
        assertEquals("ftp://server/path", normalizeNetworkPath(handler, "/ftp://server/path"))
        
        // Assert clean paths remain unchanged
        assertEquals("smb://server/share", normalizeNetworkPath(handler, "smb://server/share"))
        assertEquals("sftp://server/path", normalizeNetworkPath(handler, "sftp://server/path"))
        
        // Assert non-network paths follow current normalization behavior
        assertEquals("local/path", normalizeNetworkPath(handler, "/local/path"))
        assertEquals("content://uri", normalizeNetworkPath(handler, "content://uri"))
        assertEquals("storage/emulated/0", normalizeNetworkPath(handler, "/storage/emulated/0"))
    }

    @Test
    fun `test path normalization fixes single slash protocols`() {
        val handler = createHandler()
        
        // Assert single slash protocols are fixed
        assertEquals("smb://server/share", normalizeNetworkPath(handler, "smb:/server/share"))
        assertEquals("sftp://server/path", normalizeNetworkPath(handler, "sftp:/server/path"))
        assertEquals("ftp://server/path", normalizeNetworkPath(handler, "ftp:/server/path"))
        
        // Assert mixed cases (leading slash + single slash)
        assertEquals("smb://server/share", normalizeNetworkPath(handler, "/smb:/server/share"))
    }


    @Test
    fun `test getResourceType identifies network paths correctly via normalization`() {
        val handler = createHandler()
        
        // Test with leading slash (simulating the bug condition)
        assertEquals(ResourceType.SMB, getResourceType(handler, "/smb://server/share"))
        assertEquals(ResourceType.SFTP, getResourceType(handler, "/sftp://server/path"))
        assertEquals(ResourceType.FTP, getResourceType(handler, "/ftp://server/path"))
        
        // Test with single slash protocol
        assertEquals(ResourceType.SMB, getResourceType(handler, "smb:/server/share"))
        assertEquals(ResourceType.SFTP, getResourceType(handler, "sftp:/server/path"))
        
        // Test standard paths
        assertEquals(ResourceType.SMB, getResourceType(handler, "smb://server/share"))
        assertEquals(ResourceType.CLOUD, getResourceType(handler, "cloud://gdrive/id"))
        assertEquals(ResourceType.LOCAL, getResourceType(handler, "/local/path"))
    }
    
    @Test
    fun `test isNetworkPath identifies network paths correctly via normalization`() {
        val handler = createHandler()
        
        assertTrue(isNetworkPath(handler, "/smb://server/share"))
        assertTrue(isNetworkPath(handler, "/sftp://server/path"))
        assertTrue(isNetworkPath(handler, "/ftp://server/path"))
        assertTrue(isNetworkPath(handler, "smb://server/share"))
        assertTrue(isNetworkPath(handler, "smb:/server/share"))
    }

    private fun createHandler(): CloudFileOperationHandler {
        // We only need a shell instance for testing these private methods, 
        // so we can mock everything.
        return CloudFileOperationHandler(
            context = mockk(),
            googleDriveClient = mockk(),
            dropboxClient = mockk(),
            oneDriveClient = mockk(),
            smbClient = mockk(),
            sftpClient = mockk(),
            ftpClient = mockk(),
            credentialsRepository = mockk(),
            cloudPathParser = mockk(),
            networkCredentialsResolver = mockk(),
            cloudAuthHelper = mockk()
        )
    }
}
