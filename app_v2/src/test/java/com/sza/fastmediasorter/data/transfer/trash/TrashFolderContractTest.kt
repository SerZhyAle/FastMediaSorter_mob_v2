package com.sza.fastmediasorter.data.transfer.trash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TrashFolderContract].
 * Cover round-trip between path builder and parsers for both canonical and legacy layouts.
 */
class TrashFolderContractTest {

    @Test
    fun `buildSnapshotPath returns parent slash dot-trash slash timestamp`() {
        val path = TrashFolderContract.buildSnapshotPath("/x/y", 1700000000000L)
        assertEquals("/x/y/.trash/1700000000000", path)
    }

    @Test
    fun `parseSnapshotTimestamp returns the long for numeric snapshot dir name`() {
        val parsed = TrashFolderContract.parseSnapshotTimestamp("1700000000000")
        assertEquals(1700000000000L, parsed)
    }

    @Test
    fun `parseSnapshotTimestamp returns null for non-numeric snapshot dir name`() {
        assertNull(TrashFolderContract.parseSnapshotTimestamp("garbage"))
    }

    @Test
    fun `isContainerDir matches canonical name and rejects legacy`() {
        assertTrue(TrashFolderContract.isContainerDir(".trash"))
        assertFalse(TrashFolderContract.isContainerDir(".trash_123"))
        assertFalse(TrashFolderContract.isContainerDir("trash"))
    }

    @Test
    fun `isLegacyContainerDir matches legacy prefix and rejects canonical`() {
        assertTrue(TrashFolderContract.isLegacyContainerDir(".trash_123"))
        assertTrue(TrashFolderContract.isLegacyContainerDir(".trash_"))
        assertFalse(TrashFolderContract.isLegacyContainerDir(".trash"))
        assertFalse(TrashFolderContract.isLegacyContainerDir("trash_123"))
    }

    @Test
    fun `parseLegacyTimestamp returns long for numeric suffix and null otherwise`() {
        assertEquals(1700000000000L, TrashFolderContract.parseLegacyTimestamp(".trash_1700000000000"))
        assertNull(TrashFolderContract.parseLegacyTimestamp(".trash_x"))
        assertNull(TrashFolderContract.parseLegacyTimestamp(".trash"))
    }

    @Test
    fun `buildContainerPath returns parent slash dot-trash`() {
        val path = TrashFolderContract.buildContainerPath("/x/y")
        assertEquals("/x/y/.trash", path)
    }
}
