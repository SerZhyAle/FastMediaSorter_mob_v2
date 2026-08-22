package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.domain.game.GameBoard
import com.sza.fastmediasorter.wear.domain.game.GameCell
import com.sza.fastmediasorter.wear.domain.game.GameEnemyType
import com.sza.fastmediasorter.wear.domain.game.GameLevelState
import com.sza.fastmediasorter.wear.domain.game.GamePosition

/** Share of a cell left as a gap, so neighbouring tiles read as separate squares on a small screen. */
private const val CELL_GAP_FRACTION = 0.08f

/** Actors are drawn inside their tile, never filling it, so the tile under them stays visible. */
private const val ACTOR_RADIUS_FRACTION = 0.34f
private const val SHADOW_RADIUS_FRACTION = 0.26f

/** A shadow is a presence, not a wall: it is drawn translucent so the floor still shows through. */
private const val SHADOW_ALPHA = 0.55f

/** Half of something - a gap split over two neighbours, a centre offset inside a cell. */
private const val HALF = 0.5f

/**
 * Every colour on the board, resolved from the theme before the draw scope opens.
 *
 * A [DrawScope] cannot read a composition local, so the palette has to cross that boundary as a
 * value - and holding it in one place is what keeps a literal colour from creeping into the canvas.
 */
private class BoardPalette(
    val floor: Color,
    val wall: Color,
    val exit: Color,
    val player: Color,
    val kryvavitsa: Color,
    val shadow: Color
)

/**
 * The whole board at once - the owner ruled the game is played by swiping across it, which only
 * works while every cell is on screen, so this never scrolls and never zooms.
 */
@Composable
fun GameBoardCanvas(
    level: GameLevelState,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val palette = BoardPalette(
        floor = MaterialTheme.colors.surface,
        wall = MaterialTheme.colors.onSurfaceVariant,
        exit = MaterialTheme.colors.secondary,
        player = MaterialTheme.colors.primary,
        kryvavitsa = MaterialTheme.colors.error,
        shadow = MaterialTheme.colors.onSurface.copy(alpha = SHADOW_ALPHA)
    )
    Canvas(
        modifier = modifier.semantics { this.contentDescription = contentDescription }
    ) {
        val metrics = metricsFor(level.board, size)
        drawCells(level.board, metrics, palette)
        drawActors(level, metrics, palette)
    }
}

/** Cell size and the origin that centres the board inside whatever space the screen gave it. */
private class BoardMetrics(val cell: Float, val originX: Float, val originY: Float)

private fun metricsFor(board: GameBoard, size: Size): BoardMetrics {
    val cell = minOf(size.width / board.width, size.height / board.height)
    return BoardMetrics(
        cell = cell,
        originX = (size.width - cell * board.width) * HALF,
        originY = (size.height - cell * board.height) * HALF
    )
}

private fun DrawScope.drawCells(board: GameBoard, metrics: BoardMetrics, palette: BoardPalette) {
    val gap = metrics.cell * CELL_GAP_FRACTION
    val tile = metrics.cell - gap
    for (row in 0 until board.height) {
        for (col in 0 until board.width) {
            val colour = when (board.cellAt(GamePosition(row, col))) {
                GameCell.FLOOR -> palette.floor
                GameCell.WALL -> palette.wall
                GameCell.EXIT -> palette.exit
            }
            drawRect(
                color = colour,
                topLeft = Offset(
                    x = metrics.originX + col * metrics.cell + gap * HALF,
                    y = metrics.originY + row * metrics.cell + gap * HALF
                ),
                size = Size(tile, tile)
            )
        }
    }
}

private fun DrawScope.drawActors(
    level: GameLevelState,
    metrics: BoardMetrics,
    palette: BoardPalette
) {
    level.enemies.forEach { enemy ->
        val isKryvavitsa = enemy.type == GameEnemyType.KRYVAVITSA
        drawCircle(
            color = if (isKryvavitsa) palette.kryvavitsa else palette.shadow,
            radius = metrics.cell * if (isKryvavitsa) ACTOR_RADIUS_FRACTION else SHADOW_RADIUS_FRACTION,
            center = centreOf(enemy.position, metrics)
        )
    }
    // The player is drawn last so a capture is readable: the two figures overlap for one frame.
    drawCircle(
        color = palette.player,
        radius = metrics.cell * ACTOR_RADIUS_FRACTION,
        center = centreOf(level.player.position, metrics)
    )
}

private fun centreOf(position: GamePosition, metrics: BoardMetrics): Offset = Offset(
    x = metrics.originX + (position.col + HALF) * metrics.cell,
    y = metrics.originY + (position.row + HALF) * metrics.cell
)
