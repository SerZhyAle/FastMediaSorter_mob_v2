package com.sza.fastmediasorter.wear.domain.files

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

/** S4230: a name clash on a resource gets another name by the watch's own repeat-name rule, never a replacement. */
class WearRemoteNameChoiceTest {

    @Test
    fun `a free name is returned unchanged`() {
        val result = WearFileNameConflictResolver.resolveAmong(setOf("b.jpg"), "a.jpg", NOW, ZONE)

        assertEquals(Pair("a.jpg", false), result)
    }

    @Test
    fun `a taken name gets the seconds suffix`() {
        val result = WearFileNameConflictResolver.resolveAmong(setOf("a.jpg"), "a.jpg", NOW, ZONE)

        assertEquals(Pair("a-$SECONDS.jpg", true), result)
    }

    @Test
    fun `a taken suffixed name walks to the first free number`() {
        val taken = setOf("a.jpg", "a-$SECONDS.jpg", "a-$SECONDS-2.jpg")

        val result = WearFileNameConflictResolver.resolveAmong(taken, "a.jpg", NOW, ZONE)

        assertEquals(Pair("a-$SECONDS-3.jpg", true), result)
    }

    @Test
    fun `a name differing only by case counts as taken`() {
        val result = WearFileNameConflictResolver.resolveAmong(setOf("A.JPG"), "a.jpg", NOW, ZONE)

        assertEquals(Pair("a-$SECONDS.jpg", true), result)
    }

    @Test
    fun `an extensionless name takes the suffix at the end`() {
        val result = WearFileNameConflictResolver.resolveAmong(setOf("note"), "note", NOW, ZONE)

        assertEquals(Pair("note-$SECONDS", true), result)
    }

    private companion object {
        const val NOW = 42_000L
        const val SECONDS = "42"
        val ZONE: ZoneOffset = ZoneOffset.UTC
    }
}
