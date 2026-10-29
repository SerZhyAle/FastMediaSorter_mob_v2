package com.sza.fastmediasorter.ui.launcher.grid

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellSeating
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership

/**
 * S0404: desktop grid sizing. The column count is derived from the screen at render time and the
 * user's density factor only nudges it, because the target devices (head units, TV boxes) report
 * densities that make any fixed column count wrong somewhere.
 *
 * The resolved count is persisted per orientation, so seeding and edit mode place cells on the same
 * grid the surface renders.
 */
object LauncherGridGeometry {

    /** Nominal cell edge at density factor 1.0 - a comfortable touch target with room for a label. */
    const val BASE_CELL_DP = 96f

    const val MIN_COLUMNS = 3
    const val MAX_COLUMNS = 12

    private const val NOMINAL_SHORTCUT_CELL_SIZE_DP = 88f
    private const val MIN_SHORTCUT_CELL_SIZE_DP = 0f
    private const val MIN_SHORTCUT_SCALE = 0.55f
    private const val MAX_SHORTCUT_SCALE = 1.0f
    private const val DEFAULT_SHORTCUT_ICON_SIZE_DP = 44f
    private const val DEFAULT_SHORTCUT_MONOGRAM_TEXT_SIZE_SP = 16f
    private const val DEFAULT_SHORTCUT_MODE_BADGE_SIZE_DP = 18f
    private const val DEFAULT_SHORTCUT_CONTENT_PADDING_VERTICAL_DP = 4f
    private const val DEFAULT_SHORTCUT_LABEL_MARGIN_TOP_DP = 3f
    private const val MIN_SHORTCUT_ICON_SIZE_DP = 20f
    private const val MIN_SHORTCUT_MONOGRAM_TEXT_SIZE_SP = 10f
    private const val MIN_SHORTCUT_MODE_BADGE_SIZE_DP = 12f
    private const val MIN_SHORTCUT_CONTENT_PADDING_VERTICAL_DP = 1f
    private const val MIN_SHORTCUT_LABEL_MARGIN_TOP_DP = 1f

    // S4475: the caption's own numbers. Defaults mirror item_launcher_cell_shortcut.xml and its dimens, the
    // same way the icon and badge defaults above mirror the layout.
    private const val DEFAULT_LABEL_TEXT_SIZE_SP = 10f
    private const val MIN_LABEL_TEXT_SIZE_SP = 8f
    private const val DEFAULT_CONTENT_PADDING_HORIZONTAL_DP = 4f
    private const val MIN_CONTENT_PADDING_HORIZONTAL_DP = 2f
    private const val DEFAULT_LABEL_PLATE_PADDING_HORIZONTAL_DP = 4f
    private const val MIN_LABEL_PLATE_PADDING_HORIZONTAL_DP = 2f
    private const val DEFAULT_LABEL_PLATE_PADDING_VERTICAL_DP = 1f
    private const val MIN_LABEL_PLATE_PADDING_VERTICAL_DP = 0.5f
    private const val FULL_LABEL_LINES = 2

    // Above a real font's ascent + descent (about 1.17 for Roboto, a little more for OEM faces), so a
    // line counted as fitting is never clipped by the cell. The sp-to-dp factor is the user's font scale.
    private const val LABEL_LINE_HEIGHT_FACTOR = 1.25f

    // S4475: the section header's own numbers; the chevron mirrors icon_tier_24 and the title range the
    // M3 label-large size the layout inherits.
    private const val DEFAULT_SECTION_CHEVRON_SIZE_DP = 24f
    private const val MIN_SECTION_CHEVRON_SIZE_DP = 16f
    private const val DEFAULT_SECTION_INSET_HORIZONTAL_DP = 4f
    private const val MIN_SECTION_INSET_HORIZONTAL_DP = 2f
    private const val SECTION_TITLE_MAX_TEXT_SIZE_SP = 14f
    private const val SECTION_TITLE_MIN_TEXT_SIZE_SP = 10f

    /** Higher [densityFactor] shrinks cells, so more of them fit across. */
    fun columns(availableWidthDp: Float, densityFactor: Float): Int {
        val cellDp = BASE_CELL_DP / densityFactor
        if (cellDp <= 0f) return MIN_COLUMNS
        return (availableWidthDp / cellDp).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    }

    /** The square edge of one grid cell. Every cell rect derives from this (see [boundsFor]). */
    fun cellSizePx(availableWidthPx: Int, columns: Int): Int =
        if (columns <= 0) availableWidthPx else availableWidthPx / columns

    /**
     * S1757: visual dimensions for a shortcut cell proportional to the cell's edge size.
     * When [cellSizeDp] is at nominal size (>= 88dp), standard dimensions are returned.
     * At smaller sizes (dense grid mode), icon, paddings, and margins scale down so up to 2 lines of
     * label text fit without clipping.
     *
     * S4475: S1757 sized the icon from the cell ratio alone, so at 64dp the caption kept about 24dp -
     * one line at an enlarged font. The second line, the only one that carries the ellipsis, was then
     * cut off by the cell and the caption looked hard-clipped. The icon is now what is left after
     * [FULL_LABEL_LINES] caption lines at the user's font scale, and [labelMaxLines] drops to one only
     * when even the smallest icon leaves no room for two - so whatever the caption shows ends in an
     * ellipsis rather than in a cut.
     */
    data class ShortcutCellLayoutSpec(
        val iconSizeDp: Float,
        val monogramTextSizeSp: Float,
        val modeBadgeSizeDp: Float,
        val contentPaddingVerticalDp: Float,
        val labelMarginTopDp: Float,
        val labelTextSizeSp: Float,
        val labelMaxLines: Int,
        val contentPaddingHorizontalDp: Float,
        val labelPlatePaddingHorizontalDp: Float,
        val labelPlatePaddingVerticalDp: Float,
    ) {
        /** What the icon, the paddings and [labelMaxLines] caption lines add up to at [fontScale]. */
        fun contentHeightDp(fontScale: Float): Float = iconSizeDp +
            2 * contentPaddingVerticalDp +
            labelMarginTopDp +
            labelBlockHeightDp(labelTextSizeSp, labelMaxLines, labelPlatePaddingVerticalDp, fontScale)
    }

    /** The icon size and the caption line count that fit one cell, decided together. */
    private data class LabelFit(val lines: Int, val iconSizeDp: Float)

    /**
     * [fontScale] is the user's text scale: the caption is sized in sp, so a pure-dp estimate would repeat
     * the S1757 miscount on exactly the phones that enlarge the font.
     */
    fun shortcutLayoutSpec(cellSizeDp: Float, fontScale: Float = 1f): ShortcutCellLayoutSpec {
        if (cellSizeDp <= MIN_SHORTCUT_CELL_SIZE_DP) return nominalShortcutSpec()
        val scale = cellScale(cellSizeDp)
        val labelTextSizeSp = interpolateByScale(MIN_LABEL_TEXT_SIZE_SP, DEFAULT_LABEL_TEXT_SIZE_SP, scale)
        val paddingVerticalDp = (DEFAULT_SHORTCUT_CONTENT_PADDING_VERTICAL_DP * scale).coerceIn(
            MIN_SHORTCUT_CONTENT_PADDING_VERTICAL_DP,
            DEFAULT_SHORTCUT_CONTENT_PADDING_VERTICAL_DP,
        )
        val marginTopDp = (DEFAULT_SHORTCUT_LABEL_MARGIN_TOP_DP * scale).coerceIn(
            MIN_SHORTCUT_LABEL_MARGIN_TOP_DP,
            DEFAULT_SHORTCUT_LABEL_MARGIN_TOP_DP,
        )
        val plateVerticalDp = (DEFAULT_LABEL_PLATE_PADDING_VERTICAL_DP * scale).coerceIn(
            MIN_LABEL_PLATE_PADDING_VERTICAL_DP,
            DEFAULT_LABEL_PLATE_PADDING_VERTICAL_DP,
        )
        val fit = fitLabel(
            roomDp = cellSizeDp - 2 * paddingVerticalDp - marginTopDp,
            labelTextSizeSp = labelTextSizeSp,
            plateVerticalDp = plateVerticalDp,
            scale = scale,
            fontScale = fontScale,
        )
        return ShortcutCellLayoutSpec(
            iconSizeDp = fit.iconSizeDp,
            monogramTextSizeSp = (DEFAULT_SHORTCUT_MONOGRAM_TEXT_SIZE_SP * scale).coerceIn(
                MIN_SHORTCUT_MONOGRAM_TEXT_SIZE_SP,
                DEFAULT_SHORTCUT_MONOGRAM_TEXT_SIZE_SP,
            ),
            modeBadgeSizeDp = (DEFAULT_SHORTCUT_MODE_BADGE_SIZE_DP * scale).coerceIn(
                MIN_SHORTCUT_MODE_BADGE_SIZE_DP,
                DEFAULT_SHORTCUT_MODE_BADGE_SIZE_DP,
            ),
            contentPaddingVerticalDp = paddingVerticalDp,
            labelMarginTopDp = marginTopDp,
            labelTextSizeSp = labelTextSizeSp,
            labelMaxLines = fit.lines,
            contentPaddingHorizontalDp = (DEFAULT_CONTENT_PADDING_HORIZONTAL_DP * scale).coerceIn(
                MIN_CONTENT_PADDING_HORIZONTAL_DP,
                DEFAULT_CONTENT_PADDING_HORIZONTAL_DP,
            ),
            labelPlatePaddingHorizontalDp = (DEFAULT_LABEL_PLATE_PADDING_HORIZONTAL_DP * scale).coerceIn(
                MIN_LABEL_PLATE_PADDING_HORIZONTAL_DP,
                DEFAULT_LABEL_PLATE_PADDING_HORIZONTAL_DP,
            ),
            labelPlatePaddingVerticalDp = plateVerticalDp,
        )
    }

    /** The layout's own defaults - what a cell with no measured size (not laid out yet) is drawn at. */
    private fun nominalShortcutSpec() = ShortcutCellLayoutSpec(
        iconSizeDp = DEFAULT_SHORTCUT_ICON_SIZE_DP,
        monogramTextSizeSp = DEFAULT_SHORTCUT_MONOGRAM_TEXT_SIZE_SP,
        modeBadgeSizeDp = DEFAULT_SHORTCUT_MODE_BADGE_SIZE_DP,
        contentPaddingVerticalDp = DEFAULT_SHORTCUT_CONTENT_PADDING_VERTICAL_DP,
        labelMarginTopDp = DEFAULT_SHORTCUT_LABEL_MARGIN_TOP_DP,
        labelTextSizeSp = DEFAULT_LABEL_TEXT_SIZE_SP,
        labelMaxLines = FULL_LABEL_LINES,
        contentPaddingHorizontalDp = DEFAULT_CONTENT_PADDING_HORIZONTAL_DP,
        labelPlatePaddingHorizontalDp = DEFAULT_LABEL_PLATE_PADDING_HORIZONTAL_DP,
        labelPlatePaddingVerticalDp = DEFAULT_LABEL_PLATE_PADDING_VERTICAL_DP,
    )

    /**
     * Two caption lines when the smallest icon still leaves room for them, one otherwise. The icon is the
     * scaled one, shrunk only as far as the lines need: the icon sits at a fixed offset whatever the
     * caption wraps to (S2014), so its size can depend on the cell and the font but never on the text.
     */
    private fun fitLabel(
        roomDp: Float,
        labelTextSizeSp: Float,
        plateVerticalDp: Float,
        scale: Float,
        fontScale: Float,
    ): LabelFit {
        val scaledIconDp = (DEFAULT_SHORTCUT_ICON_SIZE_DP * scale).coerceIn(
            MIN_SHORTCUT_ICON_SIZE_DP,
            DEFAULT_SHORTCUT_ICON_SIZE_DP,
        )
        val roomForFull = roomDp -
            labelBlockHeightDp(labelTextSizeSp, FULL_LABEL_LINES, plateVerticalDp, fontScale)
        if (roomForFull >= MIN_SHORTCUT_ICON_SIZE_DP) {
            return LabelFit(FULL_LABEL_LINES, minOf(scaledIconDp, roomForFull))
        }
        val roomForOne = roomDp - labelBlockHeightDp(labelTextSizeSp, 1, plateVerticalDp, fontScale)
        return LabelFit(1, minOf(scaledIconDp, roomForOne.coerceAtLeast(MIN_SHORTCUT_ICON_SIZE_DP)))
    }

    private fun labelBlockHeightDp(
        labelTextSizeSp: Float,
        lines: Int,
        plateVerticalDp: Float,
        fontScale: Float,
    ): Float {
        val textScale = if (fontScale > 0f) fontScale else 1f
        return lines * labelTextSizeSp * textScale * LABEL_LINE_HEIGHT_FACTOR + 2 * plateVerticalDp
    }

    /** The cell's size relative to the nominal one, held between the S1757 floor and 1. */
    private fun cellScale(cellSizeDp: Float): Float =
        if (cellSizeDp <= MIN_SHORTCUT_CELL_SIZE_DP) {
            MAX_SHORTCUT_SCALE
        } else {
            (cellSizeDp / NOMINAL_SHORTCUT_CELL_SIZE_DP).coerceIn(MIN_SHORTCUT_SCALE, MAX_SHORTCUT_SCALE)
        }

    private fun interpolateByScale(min: Float, max: Float, scale: Float): Float =
        min + (max - min) * ((scale - MIN_SHORTCUT_SCALE) / (MAX_SHORTCUT_SCALE - MIN_SHORTCUT_SCALE))

    /**
     * S4475: what a section header gives back in a dense grid. The strip is a fixed
     * [LauncherSectionMembership.HEADER_SPAN_W] cells wide, so the chevron, the root inset and the strip
     * padding are the only width a longer title can be given, and the title shrinks between
     * [titleMaxTextSizeSp] and [titleMinTextSizeSp] before its ellipsis shows.
     */
    data class SectionHeaderLayoutSpec(
        val chevronSizeDp: Float,
        val rootInsetHorizontalDp: Float,
        val stripPaddingHorizontalDp: Float,
        val titleMinTextSizeSp: Float,
        val titleMaxTextSizeSp: Float,
    )

    fun sectionHeaderLayoutSpec(cellSizeDp: Float): SectionHeaderLayoutSpec {
        val scale = cellScale(cellSizeDp)
        return SectionHeaderLayoutSpec(
            chevronSizeDp = (DEFAULT_SECTION_CHEVRON_SIZE_DP * scale).coerceIn(
                MIN_SECTION_CHEVRON_SIZE_DP,
                DEFAULT_SECTION_CHEVRON_SIZE_DP,
            ),
            rootInsetHorizontalDp = (DEFAULT_SECTION_INSET_HORIZONTAL_DP * scale).coerceIn(
                MIN_SECTION_INSET_HORIZONTAL_DP,
                DEFAULT_SECTION_INSET_HORIZONTAL_DP,
            ),
            stripPaddingHorizontalDp = (DEFAULT_LABEL_PLATE_PADDING_HORIZONTAL_DP * scale).coerceIn(
                MIN_LABEL_PLATE_PADDING_HORIZONTAL_DP,
                DEFAULT_LABEL_PLATE_PADDING_HORIZONTAL_DP,
            ),
            titleMinTextSizeSp = SECTION_TITLE_MIN_TEXT_SIZE_SP,
            titleMaxTextSizeSp = SECTION_TITLE_MAX_TEXT_SIZE_SP,
        )
    }

    /**
     * How many rows the canvas needs to show every cell - the lowest occupied row, plus its own
     * height. The desktop is one screen plus downward scroll (strategic §3.3), so the canvas grows to
     * its content rather than being clipped to the viewport.
     */
    fun rowsFor(cells: List<LauncherCell>): Int =
        cells.maxOfOrNull { safeRow(it.rowIndex) + safeSpanH(it.spanH) } ?: 1

    /**
     * How many rows a viewport of [availableHeightPx] covers - the second axis of [columns], and the
     * only thing that lets edit mode fill the screen instead of stopping under the last cell (S1288).
     *
     * Rounded up on purpose: a partially visible row at the bottom edge costs nothing and doubles as
     * the sign that the desktop scrolls, while rounding down leaves exactly the strip of bare
     * wallpaper this exists to remove. Returns 0 when either input is still unknown, so a caller that
     * asks before the surface is measured falls back to its content-driven row count.
     */
    fun rowsForViewport(availableHeightPx: Int, cellSizePx: Int): Int =
        if (availableHeightPx <= 0 || cellSizePx <= 0) 0 else (availableHeightPx + cellSizePx - 1) / cellSizePx

    /** The squares a cell occupies once clamped to the grid it is being drawn on. */
    data class CellFootprint(val row: Int, val col: Int, val spanW: Int, val spanH: Int) {
        val rows: IntRange get() = row until row + spanH
        val cols: IntRange get() = col until col + spanW
    }

    /** Pixel rect of one cell on the canvas, relative to the grid's content box. */
    data class CellBounds(val left: Int, val top: Int, val width: Int, val height: Int)

    /**
     * The single definition of where a cell sits on the grid. Everything that needs a cell's squares -
     * layout, hit-testing, the empty-slot sweep - goes through this, and that is the point: this clamp
     * used to be re-typed at each call site, and one copy that forgot to floor the row at 0 would mark
     * a different square occupied than the one the renderer drew, leaving a "tap to add" slot sitting
     * on top of a live cell.
     *
     * The clamps are load-bearing, not defensive padding: `spanW`/`colIndex` were persisted against
     * whatever column count was current when the user placed the cell, and both the density factor and
     * rotation change that count underneath. Unclamped, a cell saved on a wider grid lays out past the
     * right edge and silently disappears.
     *
     * S2599: the clamps themselves now live in [LauncherCellSeating], so the repository can seat a cell
     * by the same rule this draws it by - the two copies that used to answer differently are what let a
     * stored cell be found free and drawn on top of its neighbour.
     */
    fun footprint(row: Int, col: Int, spanW: Int, spanH: Int, columns: Int): CellFootprint {
        val width = LauncherCellSeating.seatSpanW(spanW, columns)
        return CellFootprint(
            row = safeRow(row),
            col = LauncherCellSeating.seatColumn(col, width, columns),
            spanW = width,
            spanH = safeSpanH(spanH),
        )
    }

    /**
     * The width a cell is actually drawn at.
     *
     * S1642: a section header is normalised to [LauncherSectionMembership.HEADER_SPAN_W] rather than to the
     * column count. Normalised, not merely read: a desktop written by an S1428 build stored its headers at
     * the widest grid there is, and drawing one of those at its stored span would cover a strip of squares
     * the table has since freed. Reading the constant here narrows them on sight, without a write.
     *
     * Every caller that positions a cell or counts occupied squares goes through this. If the renderer and
     * the empty-slot sweep read different spans, edit mode draws "tap to add" squares on top of a live
     * header - the failure [footprint]'s own KDoc describes.
     */
    fun renderSpanW(cell: LauncherCell, columns: Int): Int =
        if (cell.kind == LauncherCellKind.SECTION) {
            LauncherSectionMembership.HEADER_SPAN_W.coerceIn(1, columns.coerceAtLeast(1))
        } else {
            cell.spanW
        }

    fun footprintOf(cell: LauncherCell, columns: Int): CellFootprint =
        footprint(cell.rowIndex, cell.colIndex, renderSpanW(cell, columns), cell.spanH, columns)

    /**
     * S1428: a cell paired with the row it is drawn on once collapsed sections are folded shut.
     *
     * S1645 adds the column for the same reason the row exists: a packed header is drawn beside the
     * previous one rather than at the column it is stored at. Every other cell reports its stored
     * column here, so callers read one field and never have to know which case they are in.
     */
    data class RenderedCell(val item: LauncherCellUi, val renderRow: Int, val renderCol: Int)

    /**
     * S1428: the desktop as it is drawn - every cell inside a collapsed section dropped, everything
     * below one lifted by that section's height. Storage is untouched: this is a projection of the
     * stored rows, computed on every render (strategic §5.1.6).
     *
     * The row arithmetic itself lives in
     * [LauncherSectionMembership][com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership]
     * rather than here, and deliberately: this object is in `src/launcherEnabled`, which the four
     * launcher-less flavors do not compile, so a shared unit test cannot reach it. Only the plumbing
     * that needs [LauncherCellUi] stays on this side.
     *
     * A section is addressed by the encoded target of its header cell, never by its row: a header the
     * user drags to another row is the same section, and a row that later carries a different header is
     * not.
     */
    fun renderPlan(
        cells: List<LauncherCellUi>,
        collapsedSections: Set<String>,
        columns: Int,
    ): List<RenderedCell> {
        val stored = cells.map { it.cell }
        val headerRows = LauncherSectionMembership.headerRows(stored)
        val collapsedHeaderRows = collapsedHeaderRowsOf(stored, collapsedSections)
        val drawnRowOf = { cell: LauncherCell ->
            LauncherSectionMembership.renderRowFor(
                row = cell.rowIndex,
                isHeader = cell.kind == LauncherCellKind.SECTION,
                headerRows = headerRows,
                collapsedHeaderRows = collapsedHeaderRows,
            )
        }
        val sections = LauncherSectionMembership.sectionsInOrder(stored)
        // S1645: packing runs on the folded rows, so it continues the lift instead of fighting it.
        val packed = LauncherSectionMembership.packedHeaderPositions(
            cells = stored,
            collapsedTargets = collapsedSections,
            columns = columns,
            renderRowOf = drawnRowOf,
        )
        // S2214: packing compression lifts rows below packed header chains so no empty rows remain.
        val lifts = LauncherSectionMembership.packingLifts(
            cells = stored,
            collapsedTargets = collapsedSections,
            columns = columns,
            renderRowOf = drawnRowOf,
        )
        return cells.mapNotNull { item ->
            val drawnRow = drawnRowOf(item.cell) ?: return@mapNotNull null
            val packedPosition = packed[item.cell.target]
                ?.takeIf { item.cell.kind == LauncherCellKind.SECTION }
            val owner = LauncherSectionMembership.ownerOf(item.cell, sections)
            val packingLift = owner?.target?.let { lifts[it] } ?: 0
            RenderedCell(
                item = item,
                renderRow = packedPosition?.row ?: (drawnRow - packingLift).coerceAtLeast(0),
                renderCol = packedPosition?.col ?: item.cell.colIndex,
            )
        }
            // S1645: reading order, not storage order. The container adds children in this order and
            // TalkBack follows that, so a packed header must be announced where it is seen - which the
            // order the cells arrive in from the database does not describe once packing moves one.
            .sortedWith(compareBy({ it.renderRow }, { it.renderCol }))
    }

    /**
     * Rows the canvas needs for an already folded desktop - [rowsFor] over drawn rows instead of stored
     * ones. Folding has to shrink the canvas with it, or the desktop keeps scrolling over the empty
     * height the hidden cells used to occupy.
     */
    fun rowsForRendered(rendered: List<RenderedCell>): Int =
        rendered.maxOfOrNull { safeRow(it.renderRow) + safeSpanH(it.item.cell.spanH) } ?: 1

    /** [footprintOf] at the drawn row, so layout and the empty-slot sweep agree on a folded desktop. */
    fun footprintOfRendered(rendered: RenderedCell, columns: Int): CellFootprint = footprint(
        row = rendered.renderRow,
        col = rendered.renderCol,
        spanW = renderSpanW(rendered.item.cell, columns),
        spanH = rendered.item.cell.spanH,
        columns = columns,
    )

    /** Where a cell actually lands, given the column count resolved right now. */
    fun boundsFor(cell: LauncherCell, cellSize: Int, columns: Int): CellBounds =
        boundsOf(footprintOf(cell, columns), cellSize)

    fun boundsOf(footprint: CellFootprint, cellSize: Int): CellBounds = CellBounds(
        left = footprint.col * cellSize,
        top = footprint.row * cellSize,
        width = footprint.spanW * cellSize,
        height = footprint.spanH * cellSize,
    )

    /** One square of the grid, addressed the way [CellFootprint] reports a cell's origin. */
    data class Slot(val row: Int, val col: Int)

    /**
     * S1466: the square under a point of the grid's content box, or null when the point is outside it.
     *
     * The inverse of [boundsOf] and it shares that function's assumption - cells tile the canvas with no
     * gutter, so the square is plain integer division by [cellSize].
     *
     * Outside the grid yields no slot rather than a clamped one: the caller places a new cell at the
     * answer, and a clamped one would drop the item on the last row while the user pressed past it.
     * A point on the seam between two cells belongs to the later one, because the division floors.
     */
    fun slotAt(xPx: Int, yPx: Int, cellSize: Int, columns: Int, rows: Int): Slot? {
        val measurable = cellSize > 0 && columns > 0 && rows > 0 && xPx >= 0 && yPx >= 0
        if (!measurable) return null
        val col = xPx / cellSize
        val row = yPx / cellSize
        return Slot(row = row, col = col).takeIf { col < columns && row < rows }
    }

    /**
     * S2033: the stored square a press on drawn square [slot] points at.
     *
     * The inverse of [renderPlan]'s row projection, reading the header rows and the collapsed header rows
     * through the same derivation [renderPlan] uses, so the two directions cannot end up disagreeing about
     * which sections are folded on a desktop the user has just rearranged.
     *
     * The column is returned untouched. Folding moves rows only, and the header packing of S1645 moves the
     * headers themselves rather than the content stored under them, so no press on a content square owes
     * its column to a fold.
     */
    fun storedSlotFor(
        slot: Slot,
        cells: List<LauncherCellUi>,
        collapsedSections: Set<String>,
        columns: Int = MAX_COLUMNS,
    ): Slot {
        val stored = cells.map { it.cell }
        val headerRows = LauncherSectionMembership.headerRows(stored)
        val collapsedHeaderRows = collapsedHeaderRowsOf(stored, collapsedSections)
        val drawnRowOf = { cell: LauncherCell ->
            LauncherSectionMembership.renderRowFor(
                row = cell.rowIndex,
                isHeader = cell.kind == LauncherCellKind.SECTION,
                headerRows = headerRows,
                collapsedHeaderRows = collapsedHeaderRows,
            )
        }
        val sections = LauncherSectionMembership.sectionsInOrder(stored)
        val packed = LauncherSectionMembership.packedHeaderPositions(
            cells = stored,
            collapsedTargets = collapsedSections,
            columns = columns,
            renderRowOf = drawnRowOf,
        )
        val packingLift = LauncherSectionMembership.packingLiftForRenderRow(
            renderRow = slot.row,
            sections = sections,
            packedPositions = packed,
            renderRowOf = drawnRowOf,
        )
        val unpackedRenderRow = (slot.row + packingLift).coerceAtLeast(0)
        return slot.copy(
            row = LauncherSectionMembership.storedRowFor(
                renderRow = unpackedRenderRow,
                headerRows = headerRows,
                collapsedHeaderRows = collapsedHeaderRows,
            ),
        )
    }

    /**
     * The rows carrying a folded header. One derivation for both directions of the projection - two would
     * be free to drift, and a fold either side reads differently is exactly a cell landing off its square.
     */
    private fun collapsedHeaderRowsOf(cells: List<LauncherCell>, collapsedSections: Set<String>): Set<Int> =
        cells
            .filter { it.kind == LauncherCellKind.SECTION && it.target in collapsedSections }
            .map { it.rowIndex.coerceAtLeast(0) }
            .toSet()

    /** Row and height clamps do not depend on the column count, so [rowsFor] can share them. */
    private fun safeRow(row: Int): Int = row.coerceAtLeast(0)

    private fun safeSpanH(spanH: Int): Int = spanH.coerceAtLeast(1)
}
