package com.sza.fastmediasorter.core.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2889: refuses a drawing site that names no tone source.
 *
 * The enumeration is of SITES, one level finer than [SubProgramSurface], because ADR-2 puts the choice of
 * tone with the site rather than with the surface constant - one constant, `PROGRAMS_MENU`, covers both the
 * dropdown menu on the theme's surface and the panel strip on an opaque dark one, and those two take
 * different halves of the palette. A test over the enum alone therefore cannot ask the question this ticket
 * exists to answer.
 *
 * It is a unit test rather than a `scripts/quality/assert-*.ps1` for the reason CLAUDE.md Rule 33 gives: the
 * subject is a Kotlin-level fact, and a PowerShell gate would have to parse Kotlin to say anything, so it
 * would end up checking spelling. Deliberately in its own file rather than inside
 * `SubProgramCatalogCompletenessTest`, whose seeded-cell case is red on an unrelated finding (S2890) - a new
 * gate whose verdict cannot be read past someone else's failure is not a gate.
 */
class SubProgramAccentSurfaceCoverageTest {

    /** Where the tone a site paints with comes from. */
    private enum class ToneSource {
        /** `SubProgramAccentCatalog.accentFor` - the site's background follows the theme. */
        THEME_FOLLOWING,

        /** `SubProgramAccentCatalog.accentOnDarkFor` - the site's background is dark whatever the theme. */
        ON_DARK,

        /** The site deliberately paints no sub-program tone, for the reason it carries. */
        EXCLUDED,
    }

    private data class DrawingSite(
        val name: String,
        val surface: SubProgramSurface,
        val source: ToneSource,
        val note: String,
    )

    @Test
    fun `every drawing site names where its tone comes from`() {
        val undeclared = SITES.filter { it.note.isBlank() }.map { it.name }

        assertEquals("drawing sites with no stated reason: $undeclared", emptyList<String>(), undeclared)
    }

    @Test
    fun `every surface constant is drawn by at least one site`() {
        val covered = SITES.map { it.surface }.toSet()
        val uncovered = SubProgramSurface.entries.filterNot { it in covered }

        assertEquals("surfaces nothing draws: $uncovered", emptyList<SubProgramSurface>(), uncovered)
    }

    @Test
    fun `a surface constant may be drawn on two different backgrounds`() {
        val menuSites = SITES.filter { it.surface == SubProgramSurface.PROGRAMS_MENU }

        assertTrue(
            "PROGRAMS_MENU must keep at least one theme-following and one on-dark site, or ADR-2 is dead " +
                "letter and the enum could have carried the tone after all",
            menuSites.any { it.source == ToneSource.THEME_FOLLOWING } &&
                menuSites.any { it.source == ToneSource.ON_DARK },
        )
    }

    @Test
    fun `the site list is pinned so adding a drawing site is deliberate`() {
        assertEquals(SITE_COUNT, SITES.size)
    }

    private companion object {

        /** Raised only together with a new entry in [SITES] and its stated tone source. */
        const val SITE_COUNT = 7

        val SITES = listOf(
            DrawingSite(
                name = "main-window dropdown menu",
                surface = SubProgramSurface.PROGRAMS_MENU,
                source = ToneSource.THEME_FOLLOWING,
                note = "MainProgramsMenuCoordinator.applyProgramAccents - a PopupMenu on the theme surface.",
            ),
            DrawingSite(
                name = "main-window programs panel strip",
                surface = SubProgramSurface.PROGRAMS_MENU,
                source = ToneSource.ON_DARK,
                note = "MainProgramsPanelManager.rebuild - main_programs_panel_accent is opaque and dark.",
            ),
            DrawingSite(
                name = "programs panel overflow popup",
                surface = SubProgramSurface.PROGRAMS_MENU,
                source = ToneSource.THEME_FOLLOWING,
                note = "MainProgramsPanelManager.showOverflowPopup - resolvePopupBackground puts it on the " +
                    "theme's own menu surface, not on the strip.",
            ),
            DrawingSite(
                name = "quick-launch panel grid",
                surface = SubProgramSurface.QUICK_ACCESS_PANEL,
                source = ToneSource.THEME_FOLLOWING,
                note = "AppLaunchPanelTileAdapter.applyIconTint, fed by ResolveAppLaunchPanelTilesUseCase.",
            ),
            DrawingSite(
                name = "launcher desktop cell",
                surface = SubProgramSurface.LAUNCHER_SHORTCUT,
                source = ToneSource.THEME_FOLLOWING,
                note = "LauncherCellViewBinder, fed by LauncherCommandVisual.accentRes. The backdrop is a " +
                    "user wallpaper, so contrast is carried by OutlinedImageView's contour, not by the tone.",
            ),
            DrawingSite(
                name = "pinned home widget",
                surface = SubProgramSurface.WIDGET,
                source = ToneSource.THEME_FOLLOWING,
                note = "HomeWidgetAccent.applyIconTint through RemoteViews, resolved in our own process.",
            ),
            DrawingSite(
                name = "OS widget picker preview",
                surface = SubProgramSurface.WIDGET,
                source = ToneSource.EXCLUDED,
                note = "res/xml/widget_*_info.xml previewImage, rendered by the OS in its own process - the " +
                    "app cannot reach it, which is why the baked vectors are kept rather than stripped.",
            ),
        )
    }
}
