package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S2317: the spelling of the section-collapse preferences key.
 *
 * Worth a test because the formula carries a promise no compiler can see. The first screen keeps the
 * exact string its folds were stored under before the screen became part of a section's identity, so a
 * later tidy-up that gave every screen a uniform segment would silently reset every fold on the desktop
 * the user actually looks at.
 *
 * Plain JUnit: [LauncherSectionVisibilityRepositoryImpl.keyFor] is pure string arithmetic and reaches
 * neither a `Context` nor the preferences file.
 */
class LauncherSectionVisibilityKeyTest {

    @Test
    fun `first screen keeps the key its folds were stored under`() {
        val key = LauncherSectionVisibilityRepositoryImpl.keyFor(
            orientation = LauncherOrientation.PORTRAIT,
            screenIndex = 0,
            target = SECTION_WIDGETS,
        )

        assertEquals("launcher_desktop__PORTRAIT__$SECTION_WIDGETS", key)
    }

    @Test
    fun `a later screen holds its own state for the same target`() {
        val first = LauncherSectionVisibilityRepositoryImpl.keyFor(
            orientation = LauncherOrientation.PORTRAIT,
            screenIndex = 0,
            target = SECTION_WIDGETS,
        )
        val second = LauncherSectionVisibilityRepositoryImpl.keyFor(
            orientation = LauncherOrientation.PORTRAIT,
            screenIndex = 1,
            target = SECTION_WIDGETS,
        )

        assertNotEquals(first, second)
    }

    @Test
    fun `two later screens do not share one state`() {
        val second = LauncherSectionVisibilityRepositoryImpl.keyFor(
            orientation = LauncherOrientation.PORTRAIT,
            screenIndex = 1,
            target = SECTION_WIDGETS,
        )
        val third = LauncherSectionVisibilityRepositoryImpl.keyFor(
            orientation = LauncherOrientation.PORTRAIT,
            screenIndex = 2,
            target = SECTION_WIDGETS,
        )

        assertNotEquals(second, third)
    }

    @Test
    fun `the two orientations stay independent on every screen`() {
        val portrait = LauncherSectionVisibilityRepositoryImpl.keyFor(
            orientation = LauncherOrientation.PORTRAIT,
            screenIndex = 1,
            target = SECTION_WIDGETS,
        )
        val landscape = LauncherSectionVisibilityRepositoryImpl.keyFor(
            orientation = LauncherOrientation.LANDSCAPE,
            screenIndex = 1,
            target = SECTION_WIDGETS,
        )

        assertNotEquals(portrait, landscape)
    }

    private companion object {
        /** The literal `LauncherCellCommand.SECTION_WIDGETS` seeds - the target duplicated across screens. */
        const val SECTION_WIDGETS = "widgets"
    }
}
