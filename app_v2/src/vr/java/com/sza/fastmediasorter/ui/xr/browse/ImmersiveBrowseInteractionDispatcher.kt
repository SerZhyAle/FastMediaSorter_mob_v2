package com.sza.fastmediasorter.ui.xr.browse

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
            hoveredIndex = -1
            isTriggerPressed = false
            return -1
        }

        val px = uvX * ImmersiveBrowseGridRenderer.PANEL_WIDTH
        val py = uvY * ImmersiveBrowseGridRenderer.PANEL_HEIGHT

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
        if (clickTriggered) {
            hit?.let { onCellSelected?.invoke(it) }
        }
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
