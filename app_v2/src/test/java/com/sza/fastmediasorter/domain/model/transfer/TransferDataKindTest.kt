package com.sza.fastmediasorter.domain.model.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferDataKindTest {

    @Test
    fun `drive file names are distinct`() {
        val names = TransferDataKind.entries.map { it.driveFileName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `no drive file name collides with the backup history namespace`() {
        TransferDataKind.entries.forEach { kind ->
            assertFalse(
                "${kind.name} would overwrite a dated backup snapshot",
                kind.driveFileName.startsWith(TransferDataKind.BACKUP_HISTORY_PREFIX)
            )
        }
    }

    @Test
    fun `drive file names are lowercase ascii`() {
        val allowed = Regex("^[a-z0-9_.]+$")
        TransferDataKind.entries.forEach { kind ->
            assertTrue(
                "${kind.name} carries a character that is not stable across devices and locales",
                allowed.matches(kind.driveFileName)
            )
        }
    }

    @Test
    fun `every kind declares a positive format version`() {
        TransferDataKind.entries.forEach { kind ->
            assertTrue("${kind.name} has no format version", kind.formatVersion > 0)
        }
    }
}
