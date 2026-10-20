package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.preferences.WearOpenInLastUsedStore
import com.sza.fastmediasorter.wear.domain.model.WearOpenInAction
import com.sza.fastmediasorter.wear.domain.model.WearOpenInCandidate
import com.sza.fastmediasorter.wear.domain.model.WearOpenInCandidates
import com.sza.fastmediasorter.wear.domain.model.WearOpenInKind
import com.sza.fastmediasorter.wear.domain.model.WearOpenInLaunchResult
import com.sza.fastmediasorter.wear.domain.model.WearOpenInObject
import com.sza.fastmediasorter.wear.domain.model.WearOpenInRole
import com.sza.fastmediasorter.wear.domain.model.WearOpenInTarget
import com.sza.fastmediasorter.wear.domain.repository.WearOpenInRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** S4381: the choice is remembered only when the application really started, and per kind of object. */
class LaunchWearOpenInUseCaseTest {

    private val repository = mockk<WearOpenInRepository>()
    private val store = mockk<WearOpenInLastUsedStore>(relaxed = true)
    private val text = WearOpenInObject.Text("hello")
    private val target = WearOpenInTarget(
        packageName = "pkg.chat",
        activityName = "pkg.chat.Main",
        label = "Chat",
        action = WearOpenInAction.SEND_TEXT,
        role = WearOpenInRole.OTHER,
    )

    @Test
    fun `a launched application is remembered for the kind of the object`() {
        coEvery { repository.launch(target, text) } returns WearOpenInLaunchResult.Launched

        val result = runBlocking { LaunchWearOpenInUseCase(repository, store)(target, text) }

        assertEquals(WearOpenInLaunchResult.Launched, result)
        coVerify(exactly = 1) { store.remember(WearOpenInKind.TEXT, "pkg.chat") }
    }

    @Test
    fun `an application that vanished or refused is not remembered`() {
        listOf(WearOpenInLaunchResult.NotFound, WearOpenInLaunchResult.Refused).forEach { outcome ->
            coEvery { repository.launch(target, text) } returns outcome

            val result = runBlocking { LaunchWearOpenInUseCase(repository, store)(target, text) }

            assertEquals(outcome, result)
        }
        coVerify(exactly = 0) { store.remember(any(), any()) }
    }

    @Test
    fun `the list uses the last choice of the objects own kind`() {
        val candidates = listOf(
            WearOpenInCandidate("pkg.chat", "pkg.chat.Main", "Chat", WearOpenInAction.SEND_TEXT),
            WearOpenInCandidate("pkg.notes", "pkg.notes.Main", "Notes", WearOpenInAction.SEND_TEXT),
        )
        coEvery { repository.candidatesFor(text) } returns WearOpenInCandidates(candidates, null, null)
        coEvery { store.lastUsed(WearOpenInKind.TEXT) } returns "pkg.notes"

        val rows = runBlocking { ListWearOpenInTargetsUseCase(repository, store)(text) }

        assertEquals(listOf("pkg.notes", "pkg.chat"), rows.map { it.packageName })
        assertEquals(WearOpenInRole.LAST_USED, rows.first().role)
    }
}
