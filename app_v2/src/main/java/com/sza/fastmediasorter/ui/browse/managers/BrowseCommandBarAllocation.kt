package com.sza.fastmediasorter.ui.browse.managers

/**
 * Pure, Android-free allocation core for the Browse top command bar (S0374).
 *
 * Partitions command buttons into a visible set (kept on the bar) and an overflow set
 * (moved into the "⋮" menu) by priority and measured width. Extracted as a pure function
 * so the partition logic is unit-testable without a View/Context.
 */

/**
 * One candidate command on the top bar.
 *
 * @param viewId the button's view id (stable handle back to the layout).
 * @param measuredWidthPx the button's measured width including horizontal margins.
 * @param priority ascending = higher priority; a higher-priority command overflows last.
 */
data class CommandSlot(
    val viewId: Int,
    val measuredWidthPx: Int,
    val priority: Int
)

/** Result of [allocateCommandBar]: ids that stay visible vs ids pushed to overflow. */
data class CommandBarAllocation(
    val visibleIds: List<Int>,
    val overflowIds: List<Int>
)

/**
 * Decide which command buttons fit on the bar and which overflow into the "⋮" menu.
 *
 * @param slots candidate commands (any order); only feature-eligible buttons should be passed.
 * @param availableWidthPx the bar container's inner content width (width minus horizontal padding).
 * @param reservedWidthPx width the bar spends on views that are not candidates: the always-present
 *   overflow anchor (btnResourceOps), plus btnPath while it is visible.
 *
 * Uses a priority cut, not best-fit packing: the visible set is always a stable prefix of the
 * priority ranking, so buttons do not pop in and out unpredictably across recomputes.
 */
fun allocateCommandBar(
    slots: List<CommandSlot>,
    availableWidthPx: Int,
    reservedWidthPx: Int
): CommandBarAllocation {
    if (slots.isEmpty()) {
        return CommandBarAllocation(emptyList(), emptyList())
    }

    val visibleIds = mutableSetOf<Int>()

    // No room even for the reserved anchor: every command overflows.
    if (availableWidthPx > reservedWidthPx) {
        val budget = availableWidthPx - reservedWidthPx
        var running = 0
        // Stable sort by priority; the first slot that does not fit ends the visible prefix.
        for (slot in slots.sortedBy { it.priority }) {
            val next = running + slot.measuredWidthPx
            if (next <= budget) {
                running = next
                visibleIds.add(slot.viewId)
            } else {
                break
            }
        }
    }

    // Preserve the caller's original (XML) order within each bucket.
    val visible = slots.filter { it.viewId in visibleIds }.map { it.viewId }
    val overflow = slots.filter { it.viewId !in visibleIds }.map { it.viewId }
    return CommandBarAllocation(visible, overflow)
}
