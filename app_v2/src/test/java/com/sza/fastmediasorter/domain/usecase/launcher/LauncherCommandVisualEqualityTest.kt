package com.sza.fastmediasorter.domain.usecase.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S2889: [LauncherCommandVisual] is deliberately not a data class, so its `equals`, `hashCode` and
 * `withLabel` are written by hand and a field appended past any of the three is invisible.
 *
 * That invisibility is not theoretical - the desktop's whole redraw path bottoms out here: the visual's
 * equality decides `LauncherCellUi` equality, which decides StateFlow conflation, which decides the
 * binder's early return. A field missing from `equals` therefore shows as a cell that keeps its stale
 * appearance, with nothing failing anywhere.
 *
 * The whole field list is pinned rather than only the accent, so this stops being the last time anyone
 * checks. `iconDrawable` is excluded on purpose: it is the one field the class documents as outside
 * equality, because Drawable inherits identity equality.
 */
class LauncherCommandVisualEqualityTest {

    private fun base() = LauncherCommandVisual(
        label = "Calculator",
        iconRes = 1,
        iconKey = "key",
        monogramSeed = "seed",
        spokenLabel = "spoken",
        accentRes = 2,
    )

    @Test
    fun `a differing accent makes two visuals unequal`() {
        val other = LauncherCommandVisual(
            label = "Calculator",
            iconRes = 1,
            iconKey = "key",
            monogramSeed = "seed",
            spokenLabel = "spoken",
            accentRes = 3,
        )

        assertNotEquals(base(), other)
        assertNotEquals(base().hashCode(), other.hashCode())
    }

    @Test
    fun `a differing label makes two visuals unequal`() {
        assertNotEquals(base(), base().withLabel("Other"))
    }

    @Test
    fun `a differing icon resource makes two visuals unequal`() {
        val other = LauncherCommandVisual(
            label = "Calculator",
            iconRes = 9,
            iconKey = "key",
            monogramSeed = "seed",
            spokenLabel = "spoken",
            accentRes = 2,
        )

        assertNotEquals(base(), other)
    }

    @Test
    fun `a differing icon key makes two visuals unequal`() {
        val other = LauncherCommandVisual(
            label = "Calculator",
            iconRes = 1,
            iconKey = "other",
            monogramSeed = "seed",
            spokenLabel = "spoken",
            accentRes = 2,
        )

        assertNotEquals(base(), other)
    }

    @Test
    fun `a differing monogram seed makes two visuals unequal`() {
        val other = LauncherCommandVisual(
            label = "Calculator",
            iconRes = 1,
            iconKey = "key",
            monogramSeed = "other",
            spokenLabel = "spoken",
            accentRes = 2,
        )

        assertNotEquals(base(), other)
    }

    @Test
    fun `a differing spoken label makes two visuals unequal`() {
        val other = LauncherCommandVisual(
            label = "Calculator",
            iconRes = 1,
            iconKey = "key",
            monogramSeed = "seed",
            spokenLabel = "other",
            accentRes = 2,
        )

        assertNotEquals(base(), other)
    }

    @Test
    fun `identical visuals stay equal so conflation still works`() {
        assertEquals(base(), base())
        assertEquals(base().hashCode(), base().hashCode())
    }

    @Test
    fun `a renamed cell keeps every field but the label`() {
        val renamed = base().withLabel("Renamed")

        assertEquals("Renamed", renamed.label)
        assertEquals(base().iconRes, renamed.iconRes)
        assertEquals(base().iconKey, renamed.iconKey)
        assertEquals(base().monogramSeed, renamed.monogramSeed)
        assertEquals(base().spokenLabel, renamed.spokenLabel)
        assertEquals(base().accentRes, renamed.accentRes)
    }
}
