package com.sza.fastmediasorter.domain.model.launcher

/**
 * S2736: how many rows of apps the All Apps preview block occupies.
 *
 * The block used to be two rows because two was a constant, which left the height under the letter
 * tiles unused on every screen taller than that. The count is derived here rather than in the
 * launcher surface so a unit test can reach it: the surface lives in `src/launcherEnabled`, which
 * five of the seven flavors do not compile.
 */
object LauncherAllAppsPreviewGeometry {

    /** What the block occupied before it was measured, and the floor it never drops below. */
    const val MIN_PREVIEW_ROWS = 2

    /**
     * The rows left for the preview once the letter tiles have taken their share of [viewportPx].
     *
     * Every height is a measured pixel value, not a resource dimension: the cell grows with the font
     * scale and with the icon, so only a laid-out child reports the height the arithmetic needs.
     * An unmeasured input yields [MIN_PREVIEW_ROWS], so a caller that asks before the first layout
     * keeps the previous behaviour instead of collapsing the block to nothing.
     */
    fun previewRows(
        viewportPx: Int,
        previewHeaderPx: Int,
        appRowPx: Int,
        letterTilePx: Int,
        letterGroups: Int,
        singleAppGroups: Int,
        columns: Int,
    ): Int {
        val measurable = viewportPx > 0 && appRowPx > 0 && columns > 0
        if (!measurable) return MIN_PREVIEW_ROWS
        val groups = letterGroups.coerceAtLeast(0)
        val letterRows = (groups + columns - 1) / columns
        val singleAppRows = singleAppGroups.coerceIn(0, letterRows)
        val letterTileRows = letterRows - singleAppRows
        val letterHeight = letterTileRows * letterTilePx.coerceAtLeast(0) +
            singleAppRows * appRowPx
        val free = viewportPx - previewHeaderPx.coerceAtLeast(0) - letterHeight
        return (free / appRowPx).coerceAtLeast(MIN_PREVIEW_ROWS)
    }
}
