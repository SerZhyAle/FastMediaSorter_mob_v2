package com.sza.fastmediasorter.domain.usecase.launcher

import android.net.Uri
import com.sza.fastmediasorter.data.launcher.ContactSnapshotDataSource
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactChannel
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactTarget
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S2240: unit tests for PickContactShortcutUseCase outcomes. */
@Suppress("FunctionNaming")
class PickContactShortcutUseCaseTest {

    private val contactsDataSource: ContactSnapshotDataSource = mockk()
    private val useCase = PickContactShortcutUseCase(contactsDataSource)
    private val dummyUri: Uri = mockk()

    @Test
    fun `profile action returns ready outcome when target exists`() = runBlocking {
        val target = LauncherContactTarget(
            action = LauncherContactAction.PROFILE,
            lookupKey = "lookup_123",
            displayName = "Alice",
        )
        coEvery { contactsDataSource.readProfile(dummyUri) } returns target

        val outcome = useCase(LauncherContactAction.PROFILE, dummyUri)

        assertTrue(outcome is PickContactShortcutUseCase.Outcome.Ready)
        assertEquals(target, (outcome as PickContactShortcutUseCase.Outcome.Ready).target)
    }

    @Test
    fun `message action with single channel returns ready outcome`() = runBlocking {
        val channelTarget = LauncherContactTarget(
            action = LauncherContactAction.MESSAGE,
            lookupKey = "lookup_123",
            messageDataId = 42L,
            messagePackage = "org.telegram.messenger",
            displayName = "Alice",
        )
        val channel = LauncherContactChannel(channelTarget, "Telegram")
        coEvery { contactsDataSource.readMessageChannels(dummyUri) } returns listOf(channel)

        val outcome = useCase(LauncherContactAction.MESSAGE, dummyUri)

        assertTrue(outcome is PickContactShortcutUseCase.Outcome.Ready)
        assertEquals(channelTarget, (outcome as PickContactShortcutUseCase.Outcome.Ready).target)
    }

    @Test
    fun `message action with no channels returns unavailable outcome`() = runBlocking {
        coEvery { contactsDataSource.readMessageChannels(dummyUri) } returns emptyList()

        val outcome = useCase(LauncherContactAction.MESSAGE, dummyUri)

        assertEquals(PickContactShortcutUseCase.Outcome.Unavailable, outcome)
    }
}
