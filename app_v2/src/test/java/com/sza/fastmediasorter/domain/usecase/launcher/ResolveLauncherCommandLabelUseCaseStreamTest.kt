package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.data.util.StreamChannelIdentity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1832: covers the stream branch of [ResolveLauncherCommandLabelUseCase], which had no test at all -
 * which is why a desktop cell could quietly stop finding its channel for as long as it did.
 *
 * Against a real repository rather than a mock on purpose: the identity-first, id-second resolution
 * lives in [StreamSourceRepository], so a mocked repository would assert only that this use case calls
 * something, and the prune-and-return cycle these cells must survive cannot be staged at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResolveLauncherCommandLabelUseCaseStreamTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val repo get() = StreamSourceRepository(
        dbRule.db,
        dbRule.db.streamSourceDao(),
        dbRule.db.streamQualityMemoryDao(),
        dbRule.db.streamUserStateDao(),
    )

    private val useCase get() = ResolveLauncherCommandLabelUseCase(
        context = RuntimeEnvironment.getApplication(),
        resourceRepository = mockk(relaxed = true),
        streamSourceRepository = repo,
        scheduledOperationRepository = mockk(relaxed = true),
        resourceIconProvider = mockk(relaxed = true),
        appShortcutDataSource = mockk(relaxed = true),
        liveContactDataSource = mockk(relaxed = true),
        faviconAtlasStore = mockk(relaxed = true),
    )

    @Test
    fun `a cell addressed by identity resolves to the channel`() = runTest {
        repo.mergeCatalog(listOf(channel(FIRST_ROW_ID)))

        val visual = useCase(LauncherCellCommand.Stream(StreamChannelIdentity.of(CHANNEL_URL)))

        assertEquals(TITLE, visual?.label)
    }

    @Test
    fun `a cell still carrying a row id resolves through the fallback`() = runTest {
        repo.mergeCatalog(listOf(channel(FIRST_ROW_ID)))

        val visual = useCase(LauncherCellCommand.Stream(FIRST_ROW_ID))

        assertEquals(TITLE, visual?.label)
    }

    @Test
    fun `a cell whose channel is gone resolves to nothing rather than throwing`() = runTest {
        val visual = useCase(LauncherCellCommand.Stream(StreamChannelIdentity.of(CHANNEL_URL)))

        assertNull(visual)
    }

    @Test
    fun `a channel pruned and republished under a new row id still resolves from the same cell`() =
        runTest {
            repo.mergeCatalog(listOf(channel(FIRST_ROW_ID)))
            val cell = LauncherCellCommand.Stream(StreamChannelIdentity.of(CHANNEL_URL))

            // The publisher drops the channel from the bank, then puts it back. A catalog import mints
            // a fresh UUID for every row, so the row id the cell was written with is now nobody's.
            repo.mergeCatalog(emptyList())
            assertNull("the prune must really have removed the row", useCase(cell))
            repo.mergeCatalog(listOf(channel(SECOND_ROW_ID)))

            assertEquals(TITLE, useCase(cell)?.label)
        }

    private fun channel(rowId: String) = StreamSourceEntity(
        id = rowId,
        url = CHANNEL_URL,
        title = TITLE,
        mediaKind = "AUDIO",
        sourceOrigin = "CATALOG",
        sortIndex = 0,
        addedAt = 1L,
    )

    private companion object {
        const val CHANNEL_URL = "https://example.org/one.mp3"
        const val TITLE = "Channel One"
        const val FIRST_ROW_ID = "row-id-before-the-prune"
        const val SECOND_ROW_ID = "row-id-after-the-return"
    }
}
