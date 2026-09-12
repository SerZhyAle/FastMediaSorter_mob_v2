package com.sza.fastmediasorter.ui.xr.browse

import timber.log.Timber

/**
 * Maps controller ray UV hits onto the browse grid: resolves the hovered cell, fires selection on a
 * rising-edge trigger, and treats the far-left/far-right strips as page-flip bands. Mirrors the
 * rising-edge tracking of HudInteractionDispatcher (a held trigger must not repeat-fire).
 */
class ImmersiveBrowseInteractionDispatcher(
    private val renderer: ImmersiveBrowseGridRenderer,
) {

    var onCellSelected: ((ImmersiveBrowseCell) -> Unit)? = null
    var onPageScroll: ((Int) -> Unit)? = null

    var hoveredIndex: Int = -1
        private set

    /**
     * S1133: true while [hoveredIndex] was placed by the thumbstick rather than by the ray. Such a
     * selection survives the ray pointing away from the panel - otherwise walking the grid with the
     * stick would erase its own result on the very next frame.
     */
    var isStickSelection: Boolean = false
        private set

    private var isTriggerPressed = false

    /**
     * @return the resolved [hoveredIndex] (a cell's own index, or -1 when nothing is hovered).
     * Page-flip bands take priority over cell hits on a click so edge cells stay reachable by hover
     * yet an edge click still turns the page.
     */
    fun dispatch(
        uvX: Float,
        uvY: Float,
        isHover: Boolean,
        isClick: Boolean,
        cells: List<ImmersiveBrowseCell>,
        pageOffset: Int,
    ): Int {
        if (!isHover) {
            if (isStickSelection) return dispatchStickSelection(isClick, cells)
            hoveredIndex = -1
            isTriggerPressed = false
            return -1
        }

        val px = uvX * ImmersiveBrowseGridRenderer.PANEL_WIDTH
        // S1132: ray UV.y is 0 at the quad BOTTOM and 1 at the TOP (GL convention, xr_raycast.cpp),
        // but cell.bounds live in Canvas space with y=0 at the TOP. Flip Y so the hovered cell matches
        // where the ray visually points; without this the highlight is vertically mirrored.
        val py = (1f - uvY) * ImmersiveBrowseGridRenderer.PANEL_HEIGHT

        val clickTriggered = isClick && !isTriggerPressed
        isTriggerPressed = isClick

        val prevBandRight = ImmersiveBrowseGridRenderer.PANEL_WIDTH * EDGE_BAND_FRACTION
        val nextBandLeft = ImmersiveBrowseGridRenderer.PANEL_WIDTH * (1f - EDGE_BAND_FRACTION)
        if (clickTriggered && px <= prevBandRight) {
            hoveredIndex = -1
            onPageScroll?.invoke(-1)
            return -1
        }
        if (clickTriggered && px >= nextBandLeft) {
            hoveredIndex = -1
            onPageScroll?.invoke(1)
            return -1
        }

        val hit = findHit(cells, pageOffset, px, py)
        hoveredIndex = hit?.index ?: -1
        // The ray is back on the panel, so it owns the selection again (S1133).
        isStickSelection = false
        if (clickTriggered) {
            hit?.let { onCellSelected?.invoke(it) }
        }
        return hoveredIndex
    }

    /**
     * S1133: the ray points past the panel while a thumbstick selection is live. Keeps that
     * selection and turns a rising-edge trigger into an activation of it.
     */
    private fun dispatchStickSelection(isClick: Boolean, cells: List<ImmersiveBrowseCell>): Int {
        val clickTriggered = isClick && !isTriggerPressed
        isTriggerPressed = isClick
        if (clickTriggered) {
            Timber.d("S1133: off-panel trigger activates stick selection $hoveredIndex")
            cells.getOrNull(hoveredIndex)?.let { onCellSelected?.invoke(it) }
        }
        return hoveredIndex
    }

    /**
     * S1133: moves the selection one step across the page grid. [dx] walks columns, [dy] walks rows
     * (positive = up, matching the OpenXR thumbstick convention translated in xr_session.cpp).
     *
     * A step past the top or bottom row is swallowed - the page has no row above or below. A step
     * past the left or right column turns the page instead, because that is the direction the
     * existing edge-band click already means. From no selection at all, any step lands on the first
     * cell of the page.
     *
     * @return the resolved [hoveredIndex], or -1 when the step produced no selection.
     */
    fun navigate(dx: Int, dy: Int, cells: List<ImmersiveBrowseCell>, pageOffset: Int): Int {
        val columns = ImmersiveBrowseGridRenderer.COLUMNS
        val rows = ImmersiveBrowseGridRenderer.ROWS
        val slot = hoveredIndex - pageOffset
        if (hoveredIndex < 0 || slot < 0 || slot >= renderer.pageSize) {
            return selectSlot(0, cells, pageOffset)
        }

        val nextRow = slot / columns - dy
        val nextColumn = slot % columns + dx
        return when {
            nextRow < 0 || nextRow >= rows -> hoveredIndex
            nextColumn in 0 until columns -> selectSlot(nextRow * columns + nextColumn, cells, pageOffset)
            else -> turnPage(nextRow, nextColumn < 0, cells, pageOffset)
        }
    }

    /**
     * Off the left or right edge: turn the page and land on the opposite column of the same row.
     * The bounds test repeats onPageScroll's own guard rather than trusting it, because a refused
     * page flip would otherwise leave the selection on an index of a page that never arrived.
     */
    private fun turnPage(
        row: Int,
        toPrevious: Boolean,
        cells: List<ImmersiveBrowseCell>,
        pageOffset: Int,
    ): Int {
        val columns = ImmersiveBrowseGridRenderer.COLUMNS
        val direction = if (toPrevious) -1 else 1
        val nextPageOffset = pageOffset + direction * renderer.pageSize
        if (nextPageOffset < 0 || nextPageOffset >= cells.size) return hoveredIndex
        onPageScroll?.invoke(direction)
        val wrappedColumn = if (toPrevious) columns - 1 else 0
        return selectSlot(row * columns + wrappedColumn, cells, nextPageOffset)
    }

    private fun selectSlot(slot: Int, cells: List<ImmersiveBrowseCell>, pageOffset: Int): Int {
        val index = pageOffset + slot
        if (index < 0 || index >= cells.size) return hoveredIndex
        hoveredIndex = index
        isStickSelection = true
        return hoveredIndex
    }

    private fun findHit(
        cells: List<ImmersiveBrowseCell>,
        pageOffset: Int,
        px: Float,
        py: Float,
    ): ImmersiveBrowseCell? {
        for (i in cells.indices) {
            val slot = i - pageOffset
            if (slot < 0 || slot >= renderer.pageSize) continue
            val cell = cells[i]
            if (cell.bounds.contains(px, py)) return cell
        }
        return null
    }

    companion object {
        // Fraction of panel width on each side reserved as a page-flip click band (~8%).
        private const val EDGE_BAND_FRACTION = 0.08f
    }
}
