package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.testing.createMediaFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ScheduledFileConditionsTest {

    @Test
    fun emptyConditionsMatchEverything() {
        val conditions = ScheduledFileConditions()
        assertTrue(conditions.isEmpty)
        assertTrue(conditions.matches(createMediaFile(name = "a.bin", size = 0L), NOW))
    }

    @Test
    fun maskMatchesAnyPatternCaseInsensitively() {
        val conditions = ScheduledFileConditions(nameMask = "*.JPG; IMG_????.png ;")
        assertTrue(conditions.matches(createMediaFile(name = "holiday.jpg"), NOW))
        assertTrue(conditions.matches(createMediaFile(name = "img_0001.PNG"), NOW))
        assertFalse(conditions.matches(createMediaFile(name = "img_01.png"), NOW))
        assertFalse(conditions.matches(createMediaFile(name = "clip.mp4"), NOW))
    }

    @Test
    fun maskTreatsRegexCharactersLiterally() {
        val conditions = ScheduledFileConditions(nameMask = "a+b(1).txt")
        assertTrue(conditions.matches(createMediaFile(name = "a+b(1).txt"), NOW))
        assertFalse(conditions.matches(createMediaFile(name = "aab1.txt"), NOW))
    }

    @Test
    fun blankMaskIsNoCondition() {
        val conditions = ScheduledFileConditions(nameMask = " ; ")
        assertTrue(conditions.isEmpty)
        assertTrue(conditions.matches(createMediaFile(name = "x.y"), NOW))
    }

    @Test
    fun olderThanKeepsOnlyOldFiles() {
        val conditions = ScheduledFileConditions(minAgeHours = DAY_HOURS)
        assertTrue(conditions.matches(fileAgedHours(DAY_HOURS + 1), NOW))
        assertFalse(conditions.matches(fileAgedHours(DAY_HOURS - 1), NOW))
    }

    @Test
    fun youngerThanKeepsOnlyRecentFiles() {
        val conditions = ScheduledFileConditions(maxAgeHours = DAY_HOURS)
        assertTrue(conditions.matches(fileAgedHours(1), NOW))
        assertFalse(conditions.matches(fileAgedHours(DAY_HOURS + 1), NOW))
    }

    @Test
    fun sizeBoundsAreExclusive() {
        val conditions = ScheduledFileConditions(minSizeBytes = MB, maxSizeBytes = MB * 10)
        assertTrue(conditions.matches(createMediaFile(size = MB * 2), NOW))
        assertFalse(conditions.matches(createMediaFile(size = MB), NOW))
        assertFalse(conditions.matches(createMediaFile(size = MB * 10), NOW))
    }

    @Test
    fun contradictoryBoundsAreDetected() {
        assertTrue(ScheduledFileConditions(minAgeHours = DAY_HOURS, maxAgeHours = 1).hasContradictoryBounds)
        assertTrue(ScheduledFileConditions(minSizeBytes = MB, maxSizeBytes = MB).hasContradictoryBounds)
        assertFalse(ScheduledFileConditions(minAgeHours = 1, maxAgeHours = DAY_HOURS).hasContradictoryBounds)
        assertFalse(ScheduledFileConditions(maxSizeBytes = MB).hasContradictoryBounds)
    }

    private fun fileAgedHours(hours: Int) =
        createMediaFile(createdDate = NOW - TimeUnit.HOURS.toMillis(hours.toLong()))

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val DAY_HOURS = 24
        const val MB = 1_048_576L
    }
}
