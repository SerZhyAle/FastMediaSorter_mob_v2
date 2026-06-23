package com.sza.fastmediasorter.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsSnapshotTest {

    @Test
    fun `sorted files count includes both copied and moved files`() {
        val snapshot = StatsSnapshot(
            baseline = StatsBaselineSnapshot(),
            counters = mapOf(
                StatsKey.FILES_COPIED to 4L,
                StatsKey.FILES_MOVED to 3L,
                StatsKey.FILES_DELETED to 9L,
            ),
            byType = emptyMap(),
        )

        assertEquals(7L, snapshot.sortedFilesCount())
    }
}
