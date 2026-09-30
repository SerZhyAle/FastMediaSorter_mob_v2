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

    private fun declaredBlocks(): Map<String, IntRange> =
        NotificationIds::class.java.declaredFields
            .filter { it.type == IntRange::class.java }
            .associate { field ->
                field.isAccessible = true
                field.name to field.get(NotificationIds) as IntRange
            }

    @Test
    fun `id blocks do not overlap each other`() {
        val blocks = declaredBlocks().entries.toList()
        val overlaps = blocks.flatMapIndexed { index, a ->
            blocks.drop(index + 1)
                .filter { b -> a.value.first <= b.value.last && b.value.first <= a.value.last }
                .map { b -> "${a.key}/${b.key}" }
        }

        assertTrue("Notification id blocks overlap: $overlaps", overlaps.isEmpty())
    }

    @Test
    fun `no single id falls inside a block`() {
        val blocks = declaredBlocks()
        val intruders = declaredIds().flatMap { (name, id) ->
            blocks.filterValues { id in it }.keys.map { block -> "$name in $block" }
        }

        assertTrue("A single id sits inside a derived-id block: $intruders", intruders.isEmpty())
    }

    @Test
    fun `no block reaches the save-fallback block`() {
        val intruders = declaredBlocks().filterValues { it.last >= NotificationIds.SAVE_FALLBACK_BASE }

        assertTrue("A block reaches SAVE_FALLBACK_BASE: $intruders", intruders.isEmpty())
    }

    @Test
    fun `slotIn stays inside its block for any key`() {
        val block = NotificationIds.BROWSE_TRANSFER_RESULTS
        val keys = listOf(Int.MIN_VALUE, -1, 0, 1, 99, 100, Int.MAX_VALUE)

        assertTrue(keys.all { NotificationIds.slotIn(block, it) in block })
    }

    @Test
    fun `registry declares the worker blocks`() {
        assertTrue("Reflection found no IntRange blocks - registry shape changed", declaredBlocks().size >= 4)
    }

    @Test
    fun `registry is not empty`() {
        assertTrue("Reflection found no int constants - registry shape changed", declaredIds().size >= 10)
    }
}
