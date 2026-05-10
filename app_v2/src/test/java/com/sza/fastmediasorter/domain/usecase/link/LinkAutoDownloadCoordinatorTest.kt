package com.sza.fastmediasorter.domain.usecase.link

import com.sza.fastmediasorter.data.link.LinkDownloadWriter
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.link.streaming.StreamingPipeline
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class LinkAutoDownloadCoordinatorTest {

    @Test
    fun `continues to next strategy when first applicable strategy returns NotFound`() = runTest {
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.getSettings() } returns flowOf(AppSettings())

        val registry = mockk<LinkExtractionRegistry>()
        val writer = mockk<LinkDownloadWriter>()
        val streamingPipeline = mockk<StreamingPipeline>(relaxed = true)

        var htmlOpened = false
        var dynamicOpened = false

        val htmlStrategy = FakeStrategy(id = "html") {
            htmlOpened = true
            OpenResult.NotFound("no_media_in_html")
        }
        val dynamicStrategy = FakeStrategy(id = "dynamic") {
            dynamicOpened = true
            OpenResult.Stream(
                body = ByteArrayInputStream("ok".toByteArray()),
                contentLength = 2L,
                mime = "video/mp4",
                fileName = "video.mp4",
                close = {},
            )
        }

        every { registry.ordered() } returns listOf(htmlStrategy, dynamicStrategy)
        coEvery {
            writer.writeFromStream(
                stream = any(),
                mime = "video/mp4",
                suggestedFileName = "video.mp4",
                resourceId = null,
                onBytesCopied = any(),
            )
        } returns LinkDownloadWriter.WriteResult.Saved(
            resourceLabel = "Target",
            fileName = "video.mp4",
            destinationUri = null,
        )

        val coordinator = LinkAutoDownloadCoordinator(
            settingsRepository = settingsRepository,
            registry = registry,
            writer = writer,
            streamingPipeline = streamingPipeline,
        )

        val result = coordinator.handle(
            url = "https://example.com/watch",
            callbacks = object : LinkAutoDownloadCoordinator.Callbacks {
                override fun onProgress(state: LinkAutoDownloadCoordinator.ProgressState) = Unit
            },
        )

        assertEquals(
            LinkAutoDownloadCoordinator.Result.Saved(
                resourceLabel = "Target",
                fileName = "video.mp4",
                mime = "video/mp4",
                openInPlayerUri = null,
            ),
            result,
        )
        assertTrue("html strategy should be attempted first", htmlOpened)
        assertTrue("dynamic fallback strategy should run after html miss", dynamicOpened)
    }

    private class FakeStrategy(
        override val id: String,
        private val openResult: suspend () -> OpenResult,
    ) : UrlExtractionStrategy {
        override suspend fun probe(url: String): ProbeResult = ProbeResult.Applicable(
            tentativeMime = null,
            tentativeSizeBytes = null,
        )

        override suspend fun open(
            url: String,
            onProgress: (bytesRead: Long, total: Long?) -> Unit,
        ): OpenResult = openResult()
    }
}