package com.sza.fastmediasorter.data.transfer

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TempFileNamingStrategy.
 * Tests temporary file naming logic for atomic transfers.
 */
class TempFileNamingStrategyTest {
    
    @Test
    fun `getTempPath - simple filename`() {
        val original = "photo.jpg"
        val temp = TempFileNamingStrategy.getTempPath(original)
        assertEquals("photo.jpg.temp_copy", temp)
    }
    
    @Test
    fun `getTempPath - filename with multiple dots`() {
        val original = "backup.2024.tar.gz"
        val temp = TempFileNamingStrategy.getTempPath(original)
        assertEquals("backup.2024.tar.gz.temp_copy", temp)
    }
    
    @Test
    fun `getTempPath - filename without extension`() {
        val original = "README"
        val temp = TempFileNamingStrategy.getTempPath(original)
        assertEquals("README.temp_copy", temp)
    }
    
    @Test
    fun `getTempPath - full local path`() {
        val original = "/storage/emulated/0/DCIM/photo.jpg"
        val temp = TempFileNamingStrategy.getTempPath(original)
        assertEquals("/storage/emulated/0/DCIM/photo.jpg.temp_copy", temp)
    }
    
    @Test
    fun `getTempPath - SMB path`() {
        val original = "smb://192.168.1.100/share/videos/movie.mp4"
        val temp = TempFileNamingStrategy.getTempPath(original)
        assertEquals("smb://192.168.1.100/share/videos/movie.mp4.temp_copy", temp)
    }
    
    @Test
    fun `getTempPath - FTP path`() {
        val original = "ftp://user@host/path/file.txt"
        val temp = TempFileNamingStrategy.getTempPath(original)
        assertEquals("ftp://user@host/path/file.txt.temp_copy", temp)
    }
    
    @Test
    fun `getTempPath - SFTP path`() {
        val original = "sftp://user@host:22/home/user/document.pdf"
        val temp = TempFileNamingStrategy.getTempPath(original)
        assertEquals("sftp://user@host:22/home/user/document.pdf.temp_copy", temp)
    }
    
    @Test
    fun `getTempPath - empty path`() {
        val original = ""
        val temp = TempFileNamingStrategy.getTempPath(original)
        assertEquals(".temp_copy", temp)
    }
    
    @Test
    fun `getOriginalPath - temp file`() {
        val temp = "photo.jpg.temp_copy"
        val original = TempFileNamingStrategy.getOriginalPath(temp)
        assertEquals("photo.jpg", original)
    }
    
    @Test
    fun `getOriginalPath - not temp file`() {
        val normal = "photo.jpg"
        val result = TempFileNamingStrategy.getOriginalPath(normal)
        assertEquals("photo.jpg", result)
    }
    
    @Test
    fun `getOriginalPath - full path temp file`() {
        val temp = "/storage/emulated/0/photo.jpg.temp_copy"
        val original = TempFileNamingStrategy.getOriginalPath(temp)
        assertEquals("/storage/emulated/0/photo.jpg", original)
    }
    
    @Test
    fun `getOriginalPath - multiple dots temp file`() {
        val temp = "archive.2024.tar.gz.temp_copy"
        val original = TempFileNamingStrategy.getOriginalPath(temp)
        assertEquals("archive.2024.tar.gz", original)
    }
    
    @Test
    fun `isTempFile - true for temp file`() {
        assertTrue(TempFileNamingStrategy.isTempFile("photo.jpg.temp_copy"))
        assertTrue(TempFileNamingStrategy.isTempFile("/path/to/file.txt.temp_copy"))
        assertTrue(TempFileNamingStrategy.isTempFile("smb://server/file.doc.temp_copy"))
    }
    
    @Test
    fun `isTempFile - false for normal file`() {
        assertFalse(TempFileNamingStrategy.isTempFile("photo.jpg"))
        assertFalse(TempFileNamingStrategy.isTempFile("/path/to/file.txt"))
        assertFalse(TempFileNamingStrategy.isTempFile("smb://server/file.doc"))
    }
    
    @Test
    fun `isTempFile - false for partial match`() {
        assertFalse(TempFileNamingStrategy.isTempFile("temp_copy.jpg"))
        assertFalse(TempFileNamingStrategy.isTempFile("photo.temp_copy_backup.jpg"))
    }
    
    @Test
    fun `getFileName - simple path`() {
        assertEquals("photo.jpg", TempFileNamingStrategy.getFileName("/storage/photo.jpg"))
        assertEquals("video.mp4", TempFileNamingStrategy.getFileName("video.mp4"))
        assertEquals("file", TempFileNamingStrategy.getFileName("/path/to/file"))
    }
    
    @Test
    fun `getFileName - network path`() {
        assertEquals("file.txt", TempFileNamingStrategy.getFileName("smb://server/share/file.txt"))
        assertEquals("doc.pdf", TempFileNamingStrategy.getFileName("ftp://host/dir/doc.pdf"))
    }
    
    @Test
    fun `getFileName - no directory`() {
        assertEquals("photo.jpg", TempFileNamingStrategy.getFileName("photo.jpg"))
        assertEquals("README", TempFileNamingStrategy.getFileName("README"))
    }
    
    @Test
    fun `getDirectoryPath - local path`() {
        assertEquals("/storage/emulated/0", 
            TempFileNamingStrategy.getDirectoryPath("/storage/emulated/0/photo.jpg"))
        assertEquals("/path/to", 
            TempFileNamingStrategy.getDirectoryPath("/path/to/file.txt"))
    }
    
    @Test
    fun `getDirectoryPath - network path`() {
        assertEquals("smb://server/share", 
            TempFileNamingStrategy.getDirectoryPath("smb://server/share/file.txt"))
        assertEquals("ftp://host/path", 
            TempFileNamingStrategy.getDirectoryPath("ftp://host/path/doc.pdf"))
    }
    
    @Test
    fun `getDirectoryPath - no directory`() {
        assertEquals("", TempFileNamingStrategy.getDirectoryPath("photo.jpg"))
        assertEquals("", TempFileNamingStrategy.getDirectoryPath("README"))
    }
    
    @Test
    fun `getDirectoryPath - root directory`() {
        assertEquals("", TempFileNamingStrategy.getDirectoryPath("/photo.jpg"))
    }
    
    @Test
    fun `getTempPathInSameDir - local path`() {
        val original = "/storage/emulated/0/photo.jpg"
        val temp = TempFileNamingStrategy.getTempPathInSameDir(original)
        assertEquals("/storage/emulated/0/photo.jpg.temp_copy", temp)
    }
    
    @Test
    fun `getTempPathInSameDir - network path`() {
        val original = "smb://server/share/video.mp4"
        val temp = TempFileNamingStrategy.getTempPathInSameDir(original)
        assertEquals("smb://server/share/video.mp4.temp_copy", temp)
    }
    
    @Test
    fun `roundtrip - original to temp to original`() {
        val paths = listOf(
            "photo.jpg",
            "/storage/file.txt",
            "smb://server/share/doc.pdf",
            "archive.tar.gz",
            "README",
            "backup.2024.01.15.zip"
        )
        
        paths.forEach { original ->
            val temp = TempFileNamingStrategy.getTempPath(original)
            val restored = TempFileNamingStrategy.getOriginalPath(temp)
            assertEquals("Roundtrip failed for: $original", original, restored)
            assertTrue("isTempFile failed for: $temp", TempFileNamingStrategy.isTempFile(temp))
        }
    }
    
    @Test
    fun `edge case - path ending with slash`() {
        val original = "/storage/folder/"
        val temp = TempFileNamingStrategy.getTempPath(original)
        assertEquals("/storage/folder/.temp_copy", temp)
    }
    
    @Test
    fun `edge case - Windows-style path`() {
        val original = "C:\\Users\\Documents\\file.docx"
        val temp = TempFileNamingStrategy.getTempPath(original)
        assertEquals("C:\\Users\\Documents\\file.docx.temp_copy", temp)
        
        // Note: getFileName uses '/' so Windows paths won't work correctly
        // This is acceptable since app is Android-only
    }
}
