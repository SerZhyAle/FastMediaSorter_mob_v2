package com.sza.fastmediasorter.core.notification

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1292: two notification ids collided in production (0x4054 shared by the overlay host and the
 * screen recorder, 4201 shared by three workers). Reflection over the registry keeps this test
 * honest without a hand-maintained list that could drift from the constants.
 */
class NotificationIdsTest {

    private fun declaredIds(): Map<String, Int> =
        NotificationIds::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { field ->
                field.isAccessible = true
                field.name to field.getInt(NotificationIds)
            }

    @Test
    fun `every notification id is declared exactly once`() {
        val duplicates = declaredIds().entries
            .groupBy { it.value }
            .filterValues { it.size > 1 }
            .mapValues { (_, entries) -> entries.map { it.key } }

        assertTrue(
            "Notification ids must be unique - Android replaces same-id notifications: $duplicates",
            duplicates.isEmpty()
        )
    }

    @Test
    fun `no id intrudes into the save-fallback block`() {
        val base = NotificationIds.SAVE_FALLBACK_BASE
        val intruders = declaredIds()
            .filterKeys { it != "SAVE_FALLBACK_BASE" }
            .filterValues { it >= base }

        assertTrue(
            "SaveFallbackNotifier derives per-file ids from $base upwards: $intruders",
            intruders.isEmpty()
        )
    }

    @Test
    fun `registry is not empty`() {
        assertTrue("Reflection found no int constants - registry shape changed", declaredIds().size >= 10)
    }
}
