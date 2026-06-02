package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CommandPanelLayoutPlanner.planLayout] single-promote logic.
 *
 * Uses CAST (barCapable=true) as a representative bar-capable command and
 * SLEEP_TIMER (barCapable=false) as a representative overflow-only command.
 *
 * Width is set so CAST fits on bar but we force it to overflow by reducing
 * availableWidthPx below the threshold, using a helper that puts given commands
 * directly into overflow (availableWidthPx = 0, overflowButtonSizePx = 0).
 *
 * Simpler approach: call planLayout() with widths tuned so target commands land
 * in overflow, and verify promote/no-promote outcome.
 */
class CommandPanelLayoutPlannerTest {

    private lateinit var planner: CommandPanelLayoutPlanner

    private val buttonSizePx = 100
    private val overflowButtonSizePx = 60
    // Fits exactly one command once overflow width is reserved.
    private val oneBarSlotWidth = buttonSizePx + overflowButtonSizePx
    // Reserve room for the overflow button but no bar-command slots.
    private val overflowOnlyWidth = overflowButtonSizePx

    @Before
    fun setUp() {
        planner = CommandPanelLayoutPlanner()
    }

    // ------------------------------------------------------------------
    // Helper: build a one-command list with the given command
    // ------------------------------------------------------------------

    private fun planWith(
        commands: List<CommandPanelLayoutPlanner.PlayerCommand>,
        widthPx: Int = overflowOnlyWidth
    ) = planner.planLayout(
        activeCommands = commands,
        availableWidthPx = widthPx,
        buttonSizePx = buttonSizePx,
        overflowButtonSizePx = overflowButtonSizePx
    )

    // ------------------------------------------------------------------
    // 1. Single barCapable in overflow → promote, hide overflow button
    // ------------------------------------------------------------------

    @Test
    fun `single barCapable in overflow is promoted to bar and overflow button hidden`() {
        // Width only allows one visible bar slot after reserving overflow,
        // so the second bar-capable command becomes the lone overflow item.
        val result = planWith(
            listOf(
                CommandPanelLayoutPlanner.PlayerCommand.CAST,
                CommandPanelLayoutPlanner.PlayerCommand.LYRICS
            ),
            widthPx = oneBarSlotWidth
        )

        assertTrue(
            "Higher-priority command should stay on bar",
            CommandPanelLayoutPlanner.PlayerCommand.CAST in result.barCommands
        )
        assertTrue(
            "Lone overflow bar-capable command should be promoted to bar",
            CommandPanelLayoutPlanner.PlayerCommand.LYRICS in result.barCommands
        )
        assertTrue("overflowCommands should be empty", result.overflowCommands.isEmpty())
        assertFalse("showOverflowButton should be false", result.showOverflowButton)
    }

    // ------------------------------------------------------------------
    // 2. Single overflow-only command → overflow button stays
    // ------------------------------------------------------------------

    @Test
    fun `single overflow-only command keeps overflow button visible`() {
        // SLEEP_TIMER is barCapable=false - must NOT be promoted.
        val result = planWith(listOf(CommandPanelLayoutPlanner.PlayerCommand.SLEEP_TIMER))

        assertTrue(
            "Overflow-only command should remain in overflow",
            CommandPanelLayoutPlanner.PlayerCommand.SLEEP_TIMER in result.overflowCommands
        )
        assertTrue("showOverflowButton should be true", result.showOverflowButton)
    }

    // ------------------------------------------------------------------
    // 3. Two barCapable commands in overflow → no promote, button stays
    // ------------------------------------------------------------------

    @Test
    fun `two barCapable commands in overflow are not promoted`() {
        val result = planWith(
            listOf(
                CommandPanelLayoutPlanner.PlayerCommand.CAST,
                CommandPanelLayoutPlanner.PlayerCommand.LYRICS
            )
        )

        // Both should be in overflow (or at most one on bar due to narrowWidth logic),
        // but showOverflowButton must be true since not all fit.
        assertTrue("showOverflowButton should be true", result.showOverflowButton)
    }

    // ------------------------------------------------------------------
    // 4. No overflow commands → overflow button hidden (baseline)
    // ------------------------------------------------------------------

    @Test
    fun `no active commands returns empty layout without overflow button`() {
        val result = planner.planLayout(
            activeCommands = emptyList(),
            availableWidthPx = 500,
            buttonSizePx = buttonSizePx,
            overflowButtonSizePx = overflowButtonSizePx
        )

        assertTrue("barCommands should be empty", result.barCommands.isEmpty())
        assertTrue("overflowCommands should be empty", result.overflowCommands.isEmpty())
        assertFalse("showOverflowButton should be false", result.showOverflowButton)
    }

    @Test
    fun `big buttons layout reserves one slot for overflow when commands exceed slot cap`() {
        val result = planner.planBigButtonsLayout(
            activeCommands = listOf(
                CommandPanelLayoutPlanner.PlayerCommand.DELETE,
                CommandPanelLayoutPlanner.PlayerCommand.FAVORITE,
                CommandPanelLayoutPlanner.PlayerCommand.SHARE,
                CommandPanelLayoutPlanner.PlayerCommand.INFO,
                CommandPanelLayoutPlanner.PlayerCommand.FULLSCREEN
            ),
            maxCommandSlots = 4
        )

        assertTrue("Only three commands may stay on bar when overflow also needs a slot", result.barCommands.size == 3)
        assertTrue("Overflow button should remain visible when one command spills", result.showOverflowButton)
        assertTrue("Spilled command should stay accessible in overflow", result.overflowCommands.contains(CommandPanelLayoutPlanner.PlayerCommand.INFO) || result.overflowCommands.contains(CommandPanelLayoutPlanner.PlayerCommand.FULLSCREEN))
    }

    @Test
    fun `file info yields direct slot to fullscreen in portrait and big buttons layouts`() {
        val commands = listOf(
            CommandPanelLayoutPlanner.PlayerCommand.DELETE,
            CommandPanelLayoutPlanner.PlayerCommand.FAVORITE,
            CommandPanelLayoutPlanner.PlayerCommand.SHARE,
            CommandPanelLayoutPlanner.PlayerCommand.INFO,
            CommandPanelLayoutPlanner.PlayerCommand.FULLSCREEN,
            CommandPanelLayoutPlanner.PlayerCommand.RANDOM
        )

        val portrait = planner.planLayout(
            activeCommands = commands,
            availableWidthPx = buttonSizePx * 4 + overflowButtonSizePx,
            buttonSizePx = buttonSizePx,
            overflowButtonSizePx = overflowButtonSizePx
        )
        val bigButtons = planner.planBigButtonsLayout(
            activeCommands = commands,
            maxCommandSlots = 5
        )

        val expectedDirect = listOf(
            CommandPanelLayoutPlanner.PlayerCommand.DELETE,
            CommandPanelLayoutPlanner.PlayerCommand.FAVORITE,
            CommandPanelLayoutPlanner.PlayerCommand.SHARE,
            CommandPanelLayoutPlanner.PlayerCommand.FULLSCREEN
        )

        assertTrue("Portrait direct commands should prefer fullscreen over file info", portrait.barCommands == expectedDirect)
        assertTrue("Portrait overflow should keep file info accessible", portrait.overflowCommands.contains(CommandPanelLayoutPlanner.PlayerCommand.INFO))
        assertTrue("Big buttons direct commands should prefer fullscreen over file info", bigButtons.barCommands == expectedDirect)
        assertTrue("Big buttons overflow should keep file info accessible", bigButtons.overflowCommands.contains(CommandPanelLayoutPlanner.PlayerCommand.INFO))
    }

    @Test
    fun `big buttons layout shows all bar capable commands when they fit exactly`() {
        val commands = listOf(
            CommandPanelLayoutPlanner.PlayerCommand.DELETE,
            CommandPanelLayoutPlanner.PlayerCommand.FAVORITE,
            CommandPanelLayoutPlanner.PlayerCommand.SHARE,
            CommandPanelLayoutPlanner.PlayerCommand.INFO
        )

        val result = planner.planBigButtonsLayout(
            activeCommands = commands,
            maxCommandSlots = 4
        )

        assertTrue("All commands should stay on bar when slot count matches", result.barCommands == commands)
        assertTrue("Overflow should be empty when everything fits", result.overflowCommands.isEmpty())
        assertFalse("Overflow button should stay hidden when everything fits", result.showOverflowButton)
    }

    @Test
    fun `big buttons layout keeps overflow only commands in overflow while using remaining bar slots`() {
        val result = planner.planBigButtonsLayout(
            activeCommands = listOf(
                CommandPanelLayoutPlanner.PlayerCommand.DELETE,
                CommandPanelLayoutPlanner.PlayerCommand.FAVORITE,
                CommandPanelLayoutPlanner.PlayerCommand.TRANSLATE_OFFICE
            ),
            maxCommandSlots = 3
        )

        assertTrue("Bar-capable commands should still use available bar slots", result.barCommands == listOf(
            CommandPanelLayoutPlanner.PlayerCommand.DELETE,
            CommandPanelLayoutPlanner.PlayerCommand.FAVORITE
        ))
        assertTrue("Overflow-only commands must remain in overflow", result.overflowCommands == listOf(CommandPanelLayoutPlanner.PlayerCommand.TRANSLATE_OFFICE))
        assertTrue("Overflow button should occupy the last slot when overflow-only items exist", result.showOverflowButton)
    }
}
