package com.sza.fastmediasorter.domain.model.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S2736: the preview block's row count, which is the whole visible effect of the measured layout. */
class LauncherAllAppsPreviewGeometryTest {

    @Test
    fun `tall viewport yields more rows than the old constant`() {
        val rows = LauncherAllAppsPreviewGeometry.previewRows(
            viewportPx = TALL_VIEWPORT_PX,
            previewHeaderPx = HEADER_PX,
            appRowPx = ROW_PX,
            letterTilePx = TILE_PX,
            letterGroups = FEW_GROUPS,
            singleAppGroups = 0,
            columns = COLUMNS,
        )
        assertTrue(
            "expected more than the pre-S2736 two rows, got $rows",
            rows > LauncherAllAppsPreviewGeometry.MIN_PREVIEW_ROWS,
        )
    }

    @Test
    fun `short viewport never drops below the minimum`() {
        val rows = LauncherAllAppsPreviewGeometry.previewRows(
            viewportPx = SHORT_VIEWPORT_PX,
            previewHeaderPx = HEADER_PX,
            appRowPx = ROW_PX,
            letterTilePx = TILE_PX,
            letterGroups = MANY_GROUPS,
            singleAppGroups = 0,
            columns = COLUMNS,
        )
        assertEquals(LauncherAllAppsPreviewGeometry.MIN_PREVIEW_ROWS, rows)
    }

    @Test
    fun `unmeasured viewport keeps the pre-measurement count`() {
        val rows = LauncherAllAppsPreviewGeometry.previewRows(
            viewportPx = 0,
            previewHeaderPx = HEADER_PX,
            appRowPx = ROW_PX,
            letterTilePx = TILE_PX,
            letterGroups = FEW_GROUPS,
            singleAppGroups = 0,
            columns = COLUMNS,
        )
        assertEquals(LauncherAllAppsPreviewGeometry.MIN_PREVIEW_ROWS, rows)
    }

    @Test
    fun `more letter tiles leave fewer preview rows`() {
        val few = LauncherAllAppsPreviewGeometry.previewRows(
            viewportPx = TALL_VIEWPORT_PX,
            previewHeaderPx = HEADER_PX,
            appRowPx = ROW_PX,
            letterTilePx = TILE_PX,
            letterGroups = FEW_GROUPS,
            singleAppGroups = 0,
            columns = COLUMNS,
        )
        val many = LauncherAllAppsPreviewGeometry.previewRows(
            viewportPx = TALL_VIEWPORT_PX,
            previewHeaderPx = HEADER_PX,
            appRowPx = ROW_PX,
            letterTilePx = TILE_PX,
            letterGroups = MANY_GROUPS,
            singleAppGroups = 0,
            columns = COLUMNS,
        )
        assertTrue("expected $many to be smaller than $few", many < few)
    }

    @Test
    fun `single app groups leave no more preview rows than letter tiles`() {
        val letterTiles = LauncherAllAppsPreviewGeometry.previewRows(
            viewportPx = TALL_VIEWPORT_PX,
            previewHeaderPx = HEADER_PX,
            appRowPx = APP_ROW_PX,
            letterTilePx = TILE_PX,
            letterGroups = FEW_GROUPS,
            singleAppGroups = 0,
            columns = COLUMNS,
        )
        val singleApps = LauncherAllAppsPreviewGeometry.previewRows(
            viewportPx = TALL_VIEWPORT_PX,
            previewHeaderPx = HEADER_PX,
            appRowPx = APP_ROW_PX,
            letterTilePx = TILE_PX,
            letterGroups = FEW_GROUPS,
            singleAppGroups = FEW_GROUPS,
            columns = COLUMNS,
        )

        assertTrue("expected $singleApps to be no greater than $letterTiles", singleApps <= letterTiles)
    }

    private companion object {
        const val TALL_VIEWPORT_PX = 1800
        const val SHORT_VIEWPORT_PX = 400
        const val HEADER_PX = 120
        const val ROW_PX = 240
        const val APP_ROW_PX = 300
        const val TILE_PX = 240
        const val COLUMNS = 5
        const val FEW_GROUPS = 5
        const val MANY_GROUPS = 20
    }
}
