package com.sza.fastmediasorter.core.share

import com.sza.fastmediasorter.domain.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTargetApplicabilityTest {

    private fun target(applicable: Set<MediaType>) = ShareTarget(
        id = "t",
        titleRes = 0,
        defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
        availability = ShareTargetAvailability.ALWAYS,
        applicableTypes = applicable,
    )

    @Test
    fun `empty applicableTypes applies to every media type`() {
        val any = target(emptySet())
        MediaType.entries.forEach { assertTrue(any.appliesTo(it)) }
    }

    @Test
    fun `non-empty applicableTypes applies only to its members`() {
        val imageOnly = target(setOf(MediaType.IMAGE, MediaType.GIF))
        assertTrue(imageOnly.appliesTo(MediaType.IMAGE))
        assertTrue(imageOnly.appliesTo(MediaType.GIF))
        assertFalse(imageOnly.appliesTo(MediaType.TEXT))
        assertFalse(imageOnly.appliesTo(MediaType.VIDEO))
    }
}
