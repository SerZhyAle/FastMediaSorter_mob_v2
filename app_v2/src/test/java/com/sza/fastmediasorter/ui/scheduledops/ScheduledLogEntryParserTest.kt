package com.sza.fastmediasorter.ui.scheduledops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Line shapes come verbatim from [com.sza.fastmediasorter.domain.usecase.ExecuteScheduledOperationUseCase]
 * `logOp`: `ts | OP | src -> dst | message` (the sides join on a literal arrow). ADR-4 - the parser is
 * the whole history feature, so a regression here is a regression in what the user reads.
 */
class ScheduledLogEntryParserTest {

    @Test
    fun parsesSuccessLine() {
        val entries = ScheduledLogEntryParser.parse(
            "2026-09-21 10:00 | COPY | Photos → Backup | OK (12 files)"
        )

        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("2026-09-21 10:00", entry.timestamp)
        assertEquals("COPY", entry.operation)
        assertEquals("Photos", entry.source)
        assertEquals("Backup", entry.target)
        assertEquals("OK (12 files)", entry.message)
        assertFalse(entry.isError)
    }

    @Test
    fun parsesErrorLine() {
        val entries = ScheduledLogEntryParser.parse(
            "2026-09-21 10:01 | MOVE | Camera → Archive | ERROR: target unreachable"
        )

        val entry = entries.first()
        assertTrue(entry.isError)
        assertEquals("target unreachable", entry.message.removePrefix("ERROR: "))
    }

    @Test
    fun parsesDeleteLineWithoutTarget() {
        val entries = ScheduledLogEntryParser.parse(
            "2026-09-21 10:02 | DELETE | Downloads → - | OK (3 files)"
        )

        val entry = entries.first()
        assertEquals("DELETE", entry.operation)
        assertEquals("-", entry.target)
        assertFalse(entry.isError)
    }

    @Test
    fun keepsMalformedLineAsRaw() {
        val garbage = "someone edited this file by hand"
        val entries = ScheduledLogEntryParser.parse(garbage)

        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals(garbage, entry.raw)
        assertNull(entry.timestamp)
        assertNull(entry.operation)
        assertFalse(entry.isError)
    }

    @Test
    fun keepsLineWithUnknownOperationAsRaw() {
        val entries = ScheduledLogEntryParser.parse(
            "2026-09-21 10:03 | SYNC | Photos → Backup | OK (1 files)"
        )

        assertEquals("2026-09-21 10:03 | SYNC | Photos → Backup | OK (1 files)", entries.first().raw)
        assertNull(entries.first().operation)
    }

    @Test
    fun emptyFileYieldsNoEntries() {
        assertTrue(ScheduledLogEntryParser.parse("").isEmpty())
        assertTrue(ScheduledLogEntryParser.parse("\n\n").isEmpty())
    }

    @Test
    fun truncatedTailIsShownRatherThanDropped() {
        val entries = ScheduledLogEntryParser.parse(
            "2026-09-21 10:00 | COPY | Photos → Backup | OK (12 files)\n2026-09-21 10:0"
        )

        assertEquals(2, entries.size)
        assertEquals("2026-09-21 10:0", entries[1].raw)
        assertTrue(entries[0].timestamp != null)
    }

    @Test
    fun preservesFileOrder() {
        val entries = ScheduledLogEntryParser.parse(
            "2026-09-21 09:00 | COPY | A → B | OK (1 files)\n" +
                "2026-09-21 09:01 | DELETE | C → - | OK (2 files)"
        )

        assertEquals("09:00", entries[0].timestamp?.takeLast(5))
        assertEquals("09:01", entries[1].timestamp?.takeLast(5))
    }
}
