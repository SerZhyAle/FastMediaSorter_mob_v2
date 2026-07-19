package com.sza.fastmediasorter.ui.xr.browse

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Renders a page of [ImmersiveBrowseCell] tiles into a fixed 1024x512 panel bitmap that the XR
 * layer maps onto a quad. Paints and reusable rects are allocated once and reused every frame to
 * keep per-frame allocation off the VR render loop (jank/GC pressure is far more visible in HMD).
 */
class ImmersiveBrowseGridRenderer {

    val pageSize: Int = COLUMNS * ROWS

    private val panelBgColor = Color.argb(PANEL_BG_ALPHA, PANEL_BG_RED, PANEL_BG_GREEN, PANEL_BG_BLUE)
    private val accentColor = Color.argb(ALPHA_OPAQUE, ACCENT_RED, ACCENT_GREEN, ACCENT_BLUE)
    private val highlightColor = Color.argb(ALPHA_OPAQUE, HIGHLIGHT_RED, HIGHLIGHT_GREEN, HIGHLIGHT_BLUE)
    private val placeholderColor = Color.argb(ALPHA_OPAQUE, PLACEHOLDER_RED, PLACEHOLDER_GREEN, PLACEHOLDER_BLUE)
    private val labelStripColor = Color.argb(LABEL_STRIP_ALPHA, 0, 0, 0)

    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = panelBgColor
        style = Paint.Style.FILL
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = placeholderColor
        style = Paint.Style.FILL
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val labelStripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelStripColor
        style = Paint.Style.FILL
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = highlightColor
        style = Paint.Style.STROKE
        strokeWidth = HIGHLIGHT_STROKE
    }
    private val breadcrumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = BREADCRUMB_TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = LABEL_TEXT_SIZE
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = BADGE_TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        textSize = CHEVRON_TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = PAGE_TEXT_SIZE
    }

    private val panelRect = RectF(0f, 0f, PANEL_WIDTH.toFloat(), PANEL_HEIGHT.toFloat())
    private val labelStripRect = RectF()
    private val badgeRect = RectF()

    /**
     * Assigns each cell in the page window [pageOffset, pageOffset+pageSize) a pixel-space rect in a
     * COLUMNS x ROWS grid; every off-page cell gets an empty rect so draw()/hit-testing can skip it.
     */
    fun layout(cells: List<ImmersiveBrowseCell>, pageOffset: Int) {
        val gridLeft = GRID_MARGIN
        val gridTop = HEADER_HEIGHT + GRID_MARGIN
        val gridRight = PANEL_WIDTH - GRID_MARGIN
        val gridBottom = PANEL_HEIGHT - FOOTER_HEIGHT - GRID_MARGIN
        val cellWidth = (gridRight - gridLeft - CELL_SPACING * (COLUMNS - 1)) / COLUMNS
        val cellHeight = (gridBottom - gridTop - CELL_SPACING * (ROWS - 1)) / ROWS

        cells.forEachIndexed { i, cell ->
            val slot = i - pageOffset
            if (slot < 0 || slot >= pageSize) {
                cell.bounds = RectF()
                return@forEachIndexed
            }
            val col = slot % COLUMNS
            val row = slot / COLUMNS
            val left = gridLeft + col * (cellWidth + CELL_SPACING)
            val top = gridTop + row * (cellHeight + CELL_SPACING)
            cell.bounds = RectF(left, top, left + cellWidth, top + cellHeight)
        }
    }

    fun draw(
        canvas: Canvas,
        cells: List<ImmersiveBrowseCell>,
        pageOffset: Int,
        hoveredIndex: Int,
        breadcrumb: String,
    ) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawRoundRect(panelRect, PANEL_CORNER, PANEL_CORNER, panelBgPaint)

        val crumb = ellipsize(breadcrumb, breadcrumbPaint, PANEL_WIDTH - GRID_MARGIN * 2)
        canvas.drawText(crumb, GRID_MARGIN, HEADER_BASELINE, breadcrumbPaint)

        cells.forEachIndexed { i, cell ->
            val slot = i - pageOffset
            if (slot in 0 until pageSize) {
                drawCell(canvas, cell, hoveredIndex)
            }
        }

        drawPageIndicator(canvas, cells.size, pageOffset)
    }

    private fun drawCell(canvas: Canvas, cell: ImmersiveBrowseCell, hoveredIndex: Int) {
        val bounds = cell.bounds
        if (bounds.isEmpty) return

        val thumb = cell.thumbnail
        if (thumb != null) {
            canvas.drawBitmap(thumb, null, bounds, bitmapPaint)
        } else {
            canvas.drawRoundRect(bounds, CELL_CORNER, CELL_CORNER, placeholderPaint)
        }

        if (cell.isFolder) {
            val chevronY = bounds.top + CHEVRON_MARGIN + chevronPaint.textSize
            canvas.drawText(FOLDER_GLYPH, bounds.left + CHEVRON_MARGIN, chevronY, chevronPaint)
        }

        labelStripRect.set(bounds.left, bounds.bottom - LABEL_STRIP_HEIGHT, bounds.right, bounds.bottom)
        canvas.drawRect(labelStripRect, labelStripPaint)
        val label = ellipsize(cell.label, labelPaint, bounds.width() - LABEL_PADDING * 2)
        canvas.drawText(label, bounds.left + LABEL_PADDING, bounds.bottom - LABEL_BASELINE_OFFSET, labelPaint)

        cell.stereoBadge?.let { badge -> drawBadge(canvas, bounds, badge) }

        if (cell.index == hoveredIndex) {
            canvas.drawRoundRect(bounds, CELL_CORNER, CELL_CORNER, highlightPaint)
        }
    }

    private fun drawBadge(canvas: Canvas, cellBounds: RectF, text: String) {
        val pillWidth = badgeTextPaint.measureText(text) + BADGE_PADDING * 2
        val left = cellBounds.right - BADGE_MARGIN - pillWidth
        val top = cellBounds.top + BADGE_MARGIN
        badgeRect.set(left, top, left + pillWidth, top + BADGE_HEIGHT)
        canvas.drawRoundRect(badgeRect, BADGE_CORNER, BADGE_CORNER, badgePaint)
        // Vertically centre the glyph baseline inside the pill.
        val textY = badgeRect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2
        canvas.drawText(text, badgeRect.left + BADGE_PADDING, textY, badgeTextPaint)
    }

    private fun drawPageIndicator(canvas: Canvas, cellCount: Int, pageOffset: Int) {
        val totalPages = if (cellCount == 0) 1 else (cellCount - 1) / pageSize + 1
        val currentPage = pageOffset / pageSize + 1
        // Language-neutral page affordance: flanking chevrons signal that more pages exist (S1116);
        // a single page shows just the counter.
        val pageText = if (totalPages > 1) "◀  $currentPage / $totalPages  ▶" else "$currentPage / $totalPages"
        val pageX = (PANEL_WIDTH - pagePaint.measureText(pageText)) / 2f
        val pageY = PANEL_HEIGHT - PAGE_BASELINE_OFFSET
        canvas.drawText(pageText, pageX, pageY, pagePaint)
    }

    /** Trims [text] with a trailing ellipsis so it fits [maxWidth] px under [paint]. */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsisWidth = paint.measureText(ELLIPSIS)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + ellipsisWidth > maxWidth) {
            end--
        }
        return text.substring(0, end) + ELLIPSIS
    }

    companion object {
        // S1116: panel doubled in resolution (was 1024x512) so text stays crisp on the enlarged
        // browse quad; fonts + geometry scaled up for in-headset legibility. Aspect stays 2:1.
        const val PANEL_WIDTH = 1536
        const val PANEL_HEIGHT = 768
        const val COLUMNS = 4
        const val ROWS = 2

        private const val HEADER_HEIGHT = 92f
        private const val FOOTER_HEIGHT = 66f
        private const val GRID_MARGIN = 36f
        private const val CELL_SPACING = 24f

        private const val PANEL_CORNER = 48f
        private const val CELL_CORNER = 24f
        private const val BADGE_CORNER = 15f
        private const val HIGHLIGHT_STROKE = 7f

        private const val BREADCRUMB_TEXT_SIZE = 48f
        private const val LABEL_TEXT_SIZE = 40f
        private const val BADGE_TEXT_SIZE = 34f
        private const val PAGE_TEXT_SIZE = 40f
        private const val CHEVRON_TEXT_SIZE = 46f

        private const val HEADER_BASELINE = 64f
        private const val CHEVRON_MARGIN = 12f
        private const val LABEL_STRIP_HEIGHT = 54f
        private const val LABEL_PADDING = 12f
        private const val LABEL_BASELINE_OFFSET = 16f
        private const val BADGE_MARGIN = 12f
        private const val BADGE_PADDING = 15f
        private const val BADGE_HEIGHT = 46f
        private const val PAGE_BASELINE_OFFSET = 22f

        private const val FOLDER_GLYPH = "▸"
        private const val ELLIPSIS = "…"

        private const val ALPHA_OPAQUE = 255
        private const val PANEL_BG_ALPHA = 220
        private const val PANEL_BG_RED = 15
        private const val PANEL_BG_GREEN = 15
        private const val PANEL_BG_BLUE = 25
        private const val ACCENT_RED = 66
        private const val ACCENT_GREEN = 165
        private const val ACCENT_BLUE = 245
        private const val HIGHLIGHT_RED = 255
        private const val HIGHLIGHT_GREEN = 193
        private const val HIGHLIGHT_BLUE = 7
        private const val PLACEHOLDER_RED = 40
        private const val PLACEHOLDER_GREEN = 40
        private const val PLACEHOLDER_BLUE = 55
        private const val LABEL_STRIP_ALPHA = 160
    }
}
