package com.sza.fastmediasorter.domain.usecase.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LinkExtractionRegistryTest {

    private class FakeStrategy(override val id: String) : UrlExtractionStrategy {
        override suspend fun probe(url: String): ProbeResult = ProbeResult.NotApplicable
        override suspend fun open(
            url: String,
            onProgress: (bytesRead: Long, total: Long?) -> Unit
        ): OpenResult = OpenResult.NotFound("unused")
    }

    @Test
    fun `ordered sorts by canonical order`() {
        val registry = LinkExtractionRegistry(
            setOf(FakeStrategy("direct"), FakeStrategy("vimeo"), FakeStrategy("artstation"))
        )
        assertEquals(
            listOf("artstation", "vimeo", "direct"),
            registry.ordered().map { it.id }
        )
    }

    @Test
    fun `unknown ids sort to the end`() {
        val registry = LinkExtractionRegistry(
            setOf(FakeStrategy("zzz-unknown"), FakeStrategy("direct"))
        )
        assertEquals(
            listOf("direct", "zzz-unknown"),
            registry.ordered().map { it.id }
        )
    }

    @Test
    fun `duplicate ids throw on construction`() {
        // Distinct instances with the same id - a Set keeps both, registry must reject.
        assertThrows(IllegalArgumentException::class.java) {
            LinkExtractionRegistry(
                setOf(FakeStrategy("direct"), object : UrlExtractionStrategy {
                    override val id: String = "direct"
                    override suspend fun probe(url: String) = ProbeResult.NotApplicable
                    override suspend fun open(
                        url: String,
                        onProgress: (Long, Long?) -> Unit
                    ) = OpenResult.NotFound("x")
                })
            )
        }
    }
}
