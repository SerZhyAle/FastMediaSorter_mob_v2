package com.sza.fastmediasorter.core

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.programs.ProgramsSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S4545: the refresh is driven by the launchable route set moving, so these tests pin the three rules that keep it
 * from publishing too often - the starting state, an unrelated write, and a switch that really flips a route.
 */
class AppShortcutsRefreshCoordinatorTest {

    // Only the calculator switch matters to this fake: every other write is "unrelated" by construction.
    private val calculatorOnly: (String, AppSettings) -> Boolean = { routeKey, settings ->
        routeKey == InternalRouteCatalog.KEY_CALCULATOR && settings.enableCalculator
    }

    @Test
    fun `the first settings emission is the starting state and triggers nothing`() = runBlocking {
        val calculatorOn = AppSettings(programs = ProgramsSettings(enableCalculator = true))
        val changes = LaunchableRouteSet.changes(flowOf(calculatorOn), calculatorOnly).toList()

        assertEquals(emptyList<Set<String>>(), changes)
    }

    @Test
    fun `a write that leaves the launchable set unchanged triggers nothing`() = runBlocking {
        val base = AppSettings(programs = ProgramsSettings(enableCalculator = false))
        val changes = LaunchableRouteSet.changes(
            flowOf(
                base,
                base.withPrograms { copy(enableStopwatch = true) },
                base.withPrograms { copy(enableStopwatch = false) },
            ),
            calculatorOnly,
        ).toList()

        assertEquals(emptyList<Set<String>>(), changes)
    }

    @Test
    fun `switching a program on and off yields the two successive launchable sets`() = runBlocking {
        val off = AppSettings(programs = ProgramsSettings(enableCalculator = false))
        val changes = LaunchableRouteSet.changes(
            flowOf(off, off.withPrograms { copy(enableCalculator = true) }, off),
            calculatorOnly,
        ).toList()

        assertEquals(listOf(setOf(InternalRouteCatalog.KEY_CALCULATOR), emptySet()), changes)
    }
}
