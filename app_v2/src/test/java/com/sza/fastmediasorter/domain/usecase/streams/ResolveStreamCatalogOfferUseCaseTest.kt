package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.domain.delivery.PublishedStreamCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S4459: the offer rule decides whether the Streams banner appears, so every row of it is pinned. The
 * rows that matter most are the quiet ones: an unreachable mirror, an already-installed list and an
 * already-closed offer must all read as "do not offer", because the ticket exists to stop offering for
 * nothing.
 */
class ResolveStreamCatalogOfferUseCaseTest {

    private fun published(stamp: String, lastModified: Long? = null) = PublishedStreamCatalog(stamp, lastModified)

    private fun decide(
        published: PublishedStreamCatalog?,
        installed: String? = null,
        dismissed: String? = null,
        lastRefreshAt: Long = 0L,
    ) = ResolveStreamCatalogOfferUseCase.decide(published, installed, dismissed, lastRefreshAt)

    @Test
    fun `an unreachable mirror offers nothing`() {
        assertNull(decide(published = null))
        assertNull(decide(published = null, installed = "a", lastRefreshAt = 1L))
    }

    @Test
    fun `the installed list is not offered again`() {
        assertNull(decide(published("a"), installed = "a", lastRefreshAt = 1L))
    }

    @Test
    fun `a closed offer is not shown again for the same list`() {
        assertNull(decide(published("b"), installed = "a", dismissed = "b", lastRefreshAt = 1L))
    }

    @Test
    fun `a list newer than the installed one is offered`() {
        assertEquals("b", decide(published("b"), installed = "a", lastRefreshAt = 1L))
    }

    @Test
    fun `a list newer than the one whose offer was closed is offered again`() {
        assertEquals("c", decide(published("c"), installed = "a", dismissed = "b", lastRefreshAt = 1L))
    }

    @Test
    fun `an install that never imported is offered the published list`() {
        assertEquals("a", decide(published("a")))
    }

    @Test
    fun `a never-imported install that closed the offer is left alone`() {
        assertNull(decide(published("a"), dismissed = "a"))
    }

    @Test
    fun `an install from before stamps is not offered a file older than its last import`() {
        assertNull(decide(published("a", lastModified = 1_000L), lastRefreshAt = 2_000L))
    }

    @Test
    fun `an install from before stamps is offered a file published after its last import`() {
        assertEquals("a", decide(published("a", lastModified = 3_000L), lastRefreshAt = 2_000L))
    }

    @Test
    fun `an install from before stamps cannot judge a file without a modification time`() {
        assertNull(decide(published("a", lastModified = null), lastRefreshAt = 2_000L))
    }

    @Test
    fun `stampOf prefers the entity tag`() {
        assertEquals("\"0x1\"", PublishedStreamCatalog.stampOf("\"0x1\"", "Tue, 20 Oct 2026 13:55:10 GMT"))
    }

    @Test
    fun `stampOf falls back to the modification time with a prefix`() {
        assertEquals(
            "lm:Tue, 20 Oct 2026 13:55:10 GMT",
            PublishedStreamCatalog.stampOf(null, " Tue, 20 Oct 2026 13:55:10 GMT "),
        )
        assertEquals("lm:x", PublishedStreamCatalog.stampOf("  ", "x"))
    }

    @Test
    fun `stampOf is null when neither header is usable`() {
        assertNull(PublishedStreamCatalog.stampOf(null, null))
        assertNull(PublishedStreamCatalog.stampOf(" ", ""))
    }
}
