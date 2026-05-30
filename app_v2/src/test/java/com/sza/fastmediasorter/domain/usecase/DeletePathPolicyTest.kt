package com.sza.fastmediasorter.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletePathPolicyTest {

    @Test
    fun `plain local path allows soft delete`() {
        assertTrue(DeletePathPolicy.canUseSoftDelete("/storage/emulated/0/DCIM/a.jpg"))
    }

    @Test
    fun `content uri is rejected`() {
        assertFalse(DeletePathPolicy.canUseSoftDelete("content://media/external/images/1"))
        assertFalse(DeletePathPolicy.canUseSoftDelete("content:/media/external"))
    }

    @Test
    fun `network and cloud schemes are rejected`() {
        assertFalse(DeletePathPolicy.canUseSoftDelete("smb://host/share/a.jpg"))
        assertFalse(DeletePathPolicy.canUseSoftDelete("sftp://host/a.jpg"))
        assertFalse(DeletePathPolicy.canUseSoftDelete("ftp://host/a.jpg"))
        assertFalse(DeletePathPolicy.canUseSoftDelete("cloud://drive/a.jpg"))
    }

    @Test
    fun `protected android media path is rejected`() {
        assertFalse(
            DeletePathPolicy.canUseSoftDelete("/storage/emulated/0/Android/media/com.x/a.jpg")
        )
    }

    @Test
    fun `backslashes are normalised before matching`() {
        assertFalse(DeletePathPolicy.canUseSoftDelete("\\Android\\media\\com.x\\a.jpg"))
    }

    @Test
    fun `list overload is true only when every path qualifies`() {
        assertTrue(
            DeletePathPolicy.canUseSoftDelete(listOf("/a/b.jpg", "/c/d.png"))
        )
        assertFalse(
            DeletePathPolicy.canUseSoftDelete(listOf("/a/b.jpg", "smb://h/x.jpg"))
        )
    }

    @Test
    fun `empty list is vacuously true`() {
        assertTrue(DeletePathPolicy.canUseSoftDelete(emptyList()))
    }
}
