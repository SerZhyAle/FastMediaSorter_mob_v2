package com.sza.fastmediasorter.domain.usecase.launcher

import android.net.Uri
import com.sza.fastmediasorter.data.launcher.ContactSnapshotDataSource
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactChannel
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactTarget
import com.sza.fastmediasorter.domain.model.launcher.LauncherMessengerApp
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
        coEvery { contactsDataSource.readMessageChannels(dummyUri, null) } returns listOf(channel)

        val outcome = useCase(LauncherContactAction.MESSAGE, dummyUri)

        assertTrue(outcome is PickContactShortcutUseCase.Outcome.Ready)
        assertEquals(channelTarget, (outcome as PickContactShortcutUseCase.Outcome.Ready).target)
    }

    @Test
    fun `message action with no channels returns unavailable outcome`() = runBlocking {
        coEvery { contactsDataSource.readMessageChannels(dummyUri, null) } returns emptyList()

        val outcome = useCase(LauncherContactAction.MESSAGE, dummyUri)

        assertEquals(PickContactShortcutUseCase.Outcome.Unavailable, outcome)
    }

    @Test
    fun `messenger-filtered message action with one channel returns ready outcome`() = runBlocking {
        val channel = messageChannel(dataId = 42L, label = "Telegram")
        coEvery { contactsDataSource.readMessageChannels(dummyUri, TELEGRAM) } returns listOf(channel)

        val outcome = useCase(LauncherContactAction.MESSAGE, dummyUri, TELEGRAM)

        assertTrue(outcome is PickContactShortcutUseCase.Outcome.Ready)
        assertEquals(channel.target, (outcome as PickContactShortcutUseCase.Outcome.Ready).target)
    }

    /**
     * Strategic §3.1: one messenger holding several accounts for the same person is still a choice, so
     * narrowing to that app must not silently pin whichever row came back first.
     */
    @Test
    fun `messenger-filtered message action with several accounts still asks which channel`() =
        runBlocking {
            val channels = listOf(
                messageChannel(dataId = 42L, label = "Telegram - personal"),
                messageChannel(dataId = 43L, label = "Telegram - work"),
            )
            coEvery { contactsDataSource.readMessageChannels(dummyUri, TELEGRAM) } returns channels

            val outcome = useCase(LauncherContactAction.MESSAGE, dummyUri, TELEGRAM)

            assertTrue(outcome is PickContactShortcutUseCase.Outcome.ChooseChannel)
            assertEquals(
                channels,
                (outcome as PickContactShortcutUseCase.Outcome.ChooseChannel).channels,
            )
        }

    /** The contact exists but carries nothing in the app the user chose - strategic §7 risk 1. */
    @Test
    fun `messenger-filtered message action with no channel in that app is unavailable`() = runBlocking {
        coEvery { contactsDataSource.readMessageChannels(dummyUri, TELEGRAM) } returns emptyList()

        val outcome = useCase(LauncherContactAction.MESSAGE, dummyUri, TELEGRAM)

        assertEquals(PickContactShortcutUseCase.Outcome.Unavailable, outcome)
    }

    @Test
    fun `installed messengers are read from the data source`() = runBlocking {
        val messengers = listOf(LauncherMessengerApp(TELEGRAM, "Telegram"))
        coEvery { contactsDataSource.readInstalledMessengers() } returns messengers

        assertEquals(messengers, useCase.installedMessengers())
    }

    private fun messageChannel(dataId: Long, label: String) = LauncherContactChannel(
        target = LauncherContactTarget(
            action = LauncherContactAction.MESSAGE,
            lookupKey = "lookup_123",
            messageDataId = dataId,
            messagePackage = TELEGRAM,
            displayName = "Alice",
        ),
        label = label,
    )

    private companion object {
        const val TELEGRAM = "org.telegram.messenger"
    }
}
