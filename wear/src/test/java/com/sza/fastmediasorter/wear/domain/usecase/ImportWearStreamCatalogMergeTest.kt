package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1799 ADR-1: catalog import replaces catalog rows only - phone-origin rows survive the refresh,
 * except when the fresh catalog itself carries the same url (the catalog row supersedes then).
 */
class ImportWearStreamCatalogMergeTest {

    private fun channel(
        url: String,
        origin: String? = null,
        name: String = url
    ) = WearStreamChannel(
        id = url,
        name = name,
        url = url,
        mediaKind = "AUDIO",
        origin = origin
    )

    @Test
    fun `phone-origin row absent from catalog is preserved`() {
        val catalog = listOf(channel("https://a"), channel("https://b"))
        val stored = listOf(
            channel("https://a"),
            channel("https://manual", origin = WearStreamChannel.ORIGIN_PHONE)
        )
        val merged = ImportWearStreamCatalogUseCase.mergePreservingPhoneRows(catalog, stored)
        assertEquals(3, merged.size)
        assertTrue(merged.any { it.url == "https://manual" })
    }

    @Test
    fun `catalog row supersedes a phone-origin row with the same url`() {
        val catalog = listOf(channel("https://a", name = "Catalog A"))
        val stored = listOf(
            channel("https://a", origin = WearStreamChannel.ORIGIN_PHONE, name = "Manual A")
        )
        val merged = ImportWearStreamCatalogUseCase.mergePreservingPhoneRows(catalog, stored)
        assertEquals(1, merged.size)
        assertEquals("Catalog A", merged.single().name)
    }

    @Test
    fun `stored catalog rows are replaced, not preserved`() {
        val catalog = listOf(channel("https://new"))
        val stored = listOf(channel("https://old"), channel("https://older"))
        val merged = ImportWearStreamCatalogUseCase.mergePreservingPhoneRows(catalog, stored)
        assertEquals(listOf("https://new"), merged.map { it.url })
    }

    @Test
    fun `empty store keeps catalog untouched`() {
        val catalog = listOf(channel("https://a"))
        val merged = ImportWearStreamCatalogUseCase.mergePreservingPhoneRows(catalog, emptyList())
        assertEquals(catalog, merged)
    }
}
