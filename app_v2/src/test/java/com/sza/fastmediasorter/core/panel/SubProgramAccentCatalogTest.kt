package com.sza.fastmediasorter.core.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The catalog is keyed by string, so the compiler cannot demand exhaustiveness the way it does for a
 * `when` over an enum. These assertions are that guarantee: a sub-program added without an accent
 * fails here rather than shipping as the one grey row in a coloured list.
 */
class SubProgramAccentCatalogTest {

    @Test
    fun `every route has an accent`() {
        val missing = InternalRouteCatalog.all()
            .map { it.key }
            .filter { SubProgramAccentCatalog.accentFor(it) == null }

        assertEquals("routes with no accent: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `the palette holds exactly the declared number of tones`() {
        val distinct = InternalRouteCatalog.all()
            .mapNotNull { SubProgramAccentCatalog.accentFor(it.key) }
            .distinct()

        assertEquals(SubProgramAccentCatalog.PALETTE_SIZE, distinct.size)
    }

    @Test
    fun `palette size is pinned so widening it is deliberate`() {
        assertEquals(8, SubProgramAccentCatalog.PALETTE_SIZE)
    }

    @Test
    fun `an unknown key has no accent`() {
        assertNull(SubProgramAccentCatalog.accentFor("not_a_route"))
    }

    @Test
    fun `every route with an accent also has an on-dark tone`() {
        val missing = InternalRouteCatalog.all()
            .map { it.key }
            .filter { SubProgramAccentCatalog.accentFor(it) != null }
            .filter { SubProgramAccentCatalog.accentOnDarkFor(it) == null }

        assertEquals("routes with no on-dark tone: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `the on-dark set is as wide as the palette`() {
        val distinct = InternalRouteCatalog.all()
            .mapNotNull { SubProgramAccentCatalog.accentOnDarkFor(it.key) }
            .distinct()

        assertEquals(SubProgramAccentCatalog.PALETTE_SIZE, distinct.size)
    }

    @Test
    fun `an on-dark tone is never the theme-following one`() {
        val shared = InternalRouteCatalog.all()
            .map { it.key }
            .filter { SubProgramAccentCatalog.accentFor(it) == SubProgramAccentCatalog.accentOnDarkFor(it) }

        assertEquals("routes whose two tones are the same resource: $shared", emptyList<String>(), shared)
    }

    @Test
    fun `an unknown key has no on-dark tone`() {
        assertNull(SubProgramAccentCatalog.accentOnDarkFor("not_a_route"))
    }
}
