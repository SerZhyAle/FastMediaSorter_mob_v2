package com.sza.fastmediasorter.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareTargetRegistryTest {

    private fun target(id: String) = ShareTarget(
        id = id,
        titleRes = 0,
        defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
        availability = ShareTargetAvailability.ALWAYS,
    )

    @Test
    fun `byId returns registered target`() {
        val registry = ShareTargetRegistry(setOf(target("telegram"), target("email")))

        assertEquals("telegram", registry.byId("telegram")?.id)
        assertEquals("email", registry.byId("email")?.id)
    }

    @Test
    fun `byId returns null for unknown id`() {
        val registry = ShareTargetRegistry(setOf(target("telegram")))

        assertNull(registry.byId("whatsapp"))
    }

    @Test
    fun `all returns targets in canonical display order, not by id`() {
        // telegram precedes email in DISPLAY_ORDER although it sorts after alphabetically.
        val registry = ShareTargetRegistry(setOf(target("email"), target("telegram")))

        assertEquals(listOf("telegram", "email"), registry.all().map { it.id })
    }

    @Test
    fun `all keeps system_share last as the catch-all`() {
        val registry = ShareTargetRegistry(
            setOf(
                target("system_share"),
                target("tiktok"),
                target("instagram"),
                target("messenger"),
                target("viber"),
                target("whatsapp"),
                target("telegram"),
            ),
        )

        assertEquals(
            listOf(
                "telegram",
                "whatsapp",
                "viber",
                "messenger",
                "instagram",
                "tiktok",
                "system_share",
            ),
            registry.all().map { it.id },
        )
    }

    @Test
    fun `all places an unranked id just before system_share`() {
        val registry = ShareTargetRegistry(
            setOf(target("system_share"), target("unranked_xyz"), target("telegram")),
        )

        assertEquals(
            listOf("telegram", "unranked_xyz", "system_share"),
            registry.all().map { it.id },
        )
    }

    @Test
    fun `all is empty when no targets registered`() {
        val registry = ShareTargetRegistry(emptySet())

        assertEquals(emptyList<String>(), registry.all().map { it.id })
    }
}
