package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.Context
import com.sza.fastmediasorter.data.local.db.LauncherLaunchStatsDao
import com.sza.fastmediasorter.data.local.db.LauncherLaunchStatsEntity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * S3412: tests frequency-based sorting and recency tie-breaking in [QueryRecentLauncherCommandsUseCase].
 */
class QueryRecentLauncherCommandsUseCaseTest {

    private val journal: LauncherJournalRepository = mockk()
    private val pins: LauncherPinsRepository = mockk()
    private val statsDao: LauncherLaunchStatsDao = mockk()
    private val resolveVisual: ResolveLauncherCommandLabelUseCase = mockk()
    private val resolveRouteAvailability: ResolvePanelRouteAvailabilityUseCase = mockk()
    private val context: Context = mockk(relaxed = true)

    private lateinit var useCase: QueryRecentLauncherCommandsUseCase

    private val commandA = LauncherCellCommand.Feature("route_a")
    private val commandB = LauncherCellCommand.Feature("route_b")
    private val commandC = LauncherCellCommand.Feature("route_c")
    private val commandD = LauncherCellCommand.Feature("route_d")

    @Before
    fun setUp() {
        useCase = QueryRecentLauncherCommandsUseCase(
            journal = journal,
            pins = pins,
            statsDao = statsDao,
            resolveVisual = resolveVisual,
            resolveRouteAvailability = resolveRouteAvailability,
            context = context,
        )

        coEvery { resolveVisual(any()) } answers {
            val cmd = firstArg<LauncherCellCommand>()
            LauncherCommandVisual(
                label = cmd.encode(),
                iconRes = 0,
            )
        }
    }

    @Test
    fun `commands with higher launch count appear first`() = runBlocking {
        every { journal.recentCommands(any()) } returns flowOf(listOf(commandA, commandB, commandC))
        every { pins.observePins() } returns flowOf(emptyList())
        every { statsDao.observeAll() } returns flowOf(
            listOf(
                LauncherLaunchStatsEntity(commandA.encode(), launchCount = 2, lastLaunchedAt = 1000L),
                LauncherLaunchStatsEntity(commandB.encode(), launchCount = 10, lastLaunchedAt = 500L),
                LauncherLaunchStatsEntity(commandC.encode(), launchCount = 5, lastLaunchedAt = 2000L),
            )
        )

        val result = useCase(limit = 10).first()

        assertEquals(3, result.size)
        assertEquals(commandB, result[0].command) // count 10
        assertEquals(commandC, result[1].command) // count 5
        assertEquals(commandA, result[2].command) // count 2
    }

    @Test
    fun `commands with identical launch count are ordered by lastLaunchedAt descending`() = runBlocking {
        every { journal.recentCommands(any()) } returns flowOf(listOf(commandA, commandB, commandC))
        every { pins.observePins() } returns flowOf(emptyList())
        every { statsDao.observeAll() } returns flowOf(
            listOf(
                LauncherLaunchStatsEntity(commandA.encode(), launchCount = 5, lastLaunchedAt = 1000L),
                LauncherLaunchStatsEntity(commandB.encode(), launchCount = 5, lastLaunchedAt = 3000L),
                LauncherLaunchStatsEntity(commandC.encode(), launchCount = 5, lastLaunchedAt = 2000L),
            )
        )

        val result = useCase(limit = 10).first()

        assertEquals(3, result.size)
        assertEquals(commandB, result[0].command) // 3000L
        assertEquals(commandC, result[1].command) // 2000L
        assertEquals(commandA, result[2].command) // 1000L
    }

    @Test
    fun `pinned commands are excluded from recents`() = runBlocking {
        every { journal.recentCommands(any()) } returns flowOf(listOf(commandA, commandB, commandC))
        every { pins.observePins() } returns flowOf(listOf(0 to commandB))
        every { statsDao.observeAll() } returns flowOf(
            listOf(
                LauncherLaunchStatsEntity(commandA.encode(), launchCount = 5, lastLaunchedAt = 1000L),
                LauncherLaunchStatsEntity(commandB.encode(), launchCount = 10, lastLaunchedAt = 500L),
                LauncherLaunchStatsEntity(commandC.encode(), launchCount = 1, lastLaunchedAt = 2000L),
            )
        )

        val result = useCase(limit = 10).first()

        assertEquals(2, result.size)
        assertEquals(commandA, result[0].command)
        assertEquals(commandC, result[1].command)
    }

    @Test
    fun `commands absent from stats get count 0 and appear last`() = runBlocking {
        every { journal.recentCommands(any()) } returns flowOf(listOf(commandA, commandD))
        every { pins.observePins() } returns flowOf(emptyList())
        every { statsDao.observeAll() } returns flowOf(
            listOf(
                LauncherLaunchStatsEntity(commandA.encode(), launchCount = 3, lastLaunchedAt = 1000L),
            )
        )

        val result = useCase(limit = 10).first()

        assertEquals(2, result.size)
        assertEquals(commandA, result[0].command)
        assertEquals(commandD, result[1].command)
    }
}
