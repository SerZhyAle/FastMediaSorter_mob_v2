package com.sza.fastmediasorter.domain.usecase.link

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkExtractionRegistryTest {

    @Test
    fun `orders site before generic strategies`() = runTest {
        val registry = LinkExtractionRegistry(
            setOf(
                FakeStrategy("html"),
                FakeStrategy("site"),
                FakeStrategy("direct"),
                FakeStrategy("custom"),
            ),
        )

        assertEquals(listOf("site", "direct", "html", "custom"), registry.ordered().map { it.id })
    }

    private class FakeStrategy(
        override val id: String,
    ) : UrlExtractionStrategy {
        override suspend fun probe(url: String): ProbeResult = ProbeResult.NotApplicable

        override suspend fun open(
            url: String,
            onProgress: (bytesRead: Long, total: Long?) -> Unit,
        ): OpenResult = OpenResult.NotFound("unused")
    }
}