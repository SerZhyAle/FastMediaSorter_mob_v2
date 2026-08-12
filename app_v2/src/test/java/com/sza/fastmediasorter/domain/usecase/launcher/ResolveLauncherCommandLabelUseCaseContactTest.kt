package com.sza.fastmediasorter.domain.usecase.launcher

import androidx.core.net.toUri
import com.sza.fastmediasorter.data.launcher.LiveContactDataSource
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactTarget
import com.sza.fastmediasorter.domain.model.launcher.LiveContactDetails
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.Base64

/**
 * S1206: covers the contact branch of [ResolveLauncherCommandLabelUseCase], where the address book is
 * asked first and the pin-time snapshot is the fallback.
 *
 * The fallback is what these tests exist for: on a device with a healthy address book every read
 * succeeds, so nothing distinguishes "the fallback works" from "the fallback was never taken".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResolveLauncherCommandLabelUseCaseContactTest {

    private val app = RuntimeEnvironment.getApplication()
    private val liveContacts = mockk<LiveContactDataSource>()
    private val useCase = ResolveLauncherCommandLabelUseCase(
        context = app,
        resourceRepository = mockk(relaxed = true),
        streamSourceRepository = mockk(relaxed = true),
        scheduledOperationRepository = mockk(relaxed = true),
        resourceIconProvider = mockk(relaxed = true),
        appShortcutDataSource = mockk(relaxed = true),
        liveContactDataSource = liveContacts,
    )

    @Test
    fun `live name wins over the name stored at pin time`() = runTest {
        coEvery { liveContacts.read(LOOKUP_KEY) } returns LiveContactDetails("Ivan Renamed", null)

        val visual = useCase(profileCommand(pinnedName = "Ivan Old"))

        assertEquals("Ivan Renamed", visual?.label)
    }

    @Test
    fun `no live answer keeps the stored name and the monogram`() = runTest {
        coEvery { liveContacts.read(LOOKUP_KEY) } returns null

        val visual = useCase(profileCommand(pinnedName = "Ivan Old"))

        assertEquals("Ivan Old", visual?.label)
        assertEquals(LOOKUP_KEY, visual?.monogramSeed)
        assertNull(visual?.iconDrawable)
    }

    @Test
    fun `a live photo replaces the monogram and carries a stable key`() = runTest {
        shadowOf(app.contentResolver)
            .registerInputStream(PHOTO_URI.toUri(), ByteArrayInputStream(PNG_1X1))
        coEvery { liveContacts.read(LOOKUP_KEY) } returns LiveContactDetails("Ivan", PHOTO_URI)

        val visual = useCase(profileCommand(pinnedName = "Ivan"))

        assertNotNull(visual?.iconDrawable)
        assertEquals("$LOOKUP_KEY#$PHOTO_URI", visual?.iconKey)
        assertNull(visual?.monogramSeed)
    }

    @Test
    fun `a target without a lookup key never reaches the address book`() = runTest {
        val dialCommand = LauncherCellCommand.Contact(
            LauncherContactTarget(
                action = LauncherContactAction.DIAL,
                phoneNumber = "+79990000000",
                displayName = "Ivan",
            ),
        )

        val visual = useCase(dialCommand)

        assertEquals("Ivan", visual?.label)
        coVerify(exactly = 0) { liveContacts.read(any()) }
    }

    private fun profileCommand(pinnedName: String) = LauncherCellCommand.Contact(
        LauncherContactTarget(
            action = LauncherContactAction.PROFILE,
            lookupKey = LOOKUP_KEY,
            displayName = pinnedName,
        ),
    )

    private companion object {
        const val LOOKUP_KEY = "lookup-42"
        const val PHOTO_URI = "content://com.android.contacts/contacts/42/photo"

        /** A real 1x1 PNG, so the decode under test is a decode rather than a shadow's guess. */
        val PNG_1X1: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
        )
    }
}
