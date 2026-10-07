package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpShareIdTest {

    @Test
    fun `generated ids are valid and distinct`() {
        val ids = List(50) { SftpShareId.generate() }
        ids.forEach { assertTrue(it, SftpShareId.isValid(it)) }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `only the unpadded 22-character form of 16 bytes is accepted`() {
        assertTrue(SftpShareId.isValid("q3Vb7YtK0xP2mN9sLfR4wA"))
        assertFalse(SftpShareId.isValid(null))
        assertFalse(SftpShareId.isValid("q3Vb7YtK0xP2mN9sLfR4wA=="))
        assertFalse(SftpShareId.isValid("q3Vb7YtK0xP2mN9sLfR4w"))
        assertFalse(SftpShareId.isValid("q3Vb7YtK0xP2mN9sLfR4w+"))
        // Same 16 bytes as the valid id but with the spare bits set - a second spelling.
        assertFalse(SftpShareId.isValid("q3Vb7YtK0xP2mN9sLfR4wB"))
    }
}
