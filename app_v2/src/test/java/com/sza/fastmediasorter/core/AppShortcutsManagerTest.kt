package com.sza.fastmediasorter.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AppShortcutsManagerTest {

    @Test
    fun `dynamic slots are the platform limit minus the declared static shortcuts`() {
        assertEquals(13, DynamicShortcutBudget.dynamicSlots(platformMax = 15, staticCount = 2))
    }

    @Test
    fun `a limit lowered below the static count leaves no dynamic slot instead of a negative one`() {
        assertEquals(0, DynamicShortcutBudget.dynamicSlots(platformMax = 1, staticCount = 2))
    }

    @Test
    fun `journal order wins and fallback only fills the remaining places`() {
        val ranked = DynamicShortcutBudget.rankWithinBudget(
            journal = listOf("route_calculator", "resource_7"),
            fallback = listOf("resource_3", "resource_9"),
            slots = 3,
        ) { it }

        assertEquals(listOf("route_calculator", "resource_7", "resource_3"), ranked)
    }

    @Test
    fun `a resource present in both sources is published once at its journal rank`() {
        val ranked = DynamicShortcutBudget.rankWithinBudget(
            journal = listOf("resource_7", "route_stopwatch"),
            fallback = listOf("resource_7", "resource_3"),
            slots = 5,
        ) { it }

        assertEquals(listOf("resource_7", "route_stopwatch", "resource_3"), ranked)
    }

    @Test
    fun `nothing beyond the budget is built`() {
        val ranked = DynamicShortcutBudget.rankWithinBudget(
            journal = listOf("a", "b", "c"),
            fallback = listOf("d"),
            slots = 0,
        ) { it }

        assertEquals(emptyList<String>(), ranked)
    }
}
