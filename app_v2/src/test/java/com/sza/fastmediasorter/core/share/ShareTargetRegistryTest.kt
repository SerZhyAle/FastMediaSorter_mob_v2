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
    fun `all returns every registered target sorted by id`() {
        val registry = ShareTargetRegistry(setOf(target("telegram"), target("email")))

        assertEquals(listOf("email", "telegram"), registry.all().map { it.id })
    }

    @Test
    fun `all is empty when no targets registered`() {
        val registry = ShareTargetRegistry(emptySet())

        assertEquals(emptyList<String>(), registry.all().map { it.id })
    }
}
