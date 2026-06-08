package com.sza.fastmediasorter.ui.game.helpers

import com.sza.fastmediasorter.domain.game.GameCell
import com.sza.fastmediasorter.domain.game.GameEnemyType
import com.sza.fastmediasorter.domain.game.GamePosition
import com.sza.fastmediasorter.ui.game.GameMoveActor
import com.sza.fastmediasorter.ui.game.GameUiState

class GameBoardRenderMapper {

    fun map(ready: GameUiState.Ready, labels: GameBoardAccessibilityLabels): GameBoardRenderState {
        val levelState = ready.levelState
        val board = levelState.board
        val enemiesByPosition = levelState.enemies.associateBy { enemy -> enemy.position }
        val cells = ArrayList<GameBoardRenderCell>(board.width * board.height)

        for (rowIndex in 0 until board.height) {
            for (columnIndex in 0 until board.width) {
                val position = GamePosition(rowIndex, columnIndex)
                val baseCell = when (board.cellAt(position)) {
                    GameCell.FLOOR -> GameBoardBaseCell.FLOOR
                    GameCell.WALL -> GameBoardBaseCell.WALL
                    GameCell.EXIT -> GameBoardBaseCell.EXIT
                }
                val actorCell = when {
                    position == levelState.player.position -> GameBoardActorCell.PLAYER
                    enemiesByPosition[position]?.type == GameEnemyType.KRYVAVITSA -> GameBoardActorCell.KRYVAVITSA
                    enemiesByPosition[position]?.type == GameEnemyType.SHADOW -> GameBoardActorCell.SHADOW
                    else -> null
                }
                cells += GameBoardRenderCell(rowIndex, columnIndex, baseCell, actorCell)
            }
        }

        val player = levelState.player.position
        val exitHighlights = board.exitPositions().map { position ->
            GameBoardHighlightCell(position.row, position.col)
        }
        val startHighlights = (exitHighlights + GameBoardHighlightCell(player.row, player.col)).distinct()
        val actorTransitions = ready.turnMoves.map { move ->
            GameBoardActorTransition(
                actorCell = move.actor.toActorCell(),
                fromRow = move.from.row,
                fromColumn = move.from.col,
                toRow = move.to.row,
                toColumn = move.to.col
            )
        }
        return GameBoardRenderState(
            boardWidth = board.width,
            boardHeight = board.height,
            levelNumber = ready.levelNumber,
            isLargeBoard = ready.customBoard && (board.width > LARGE_BOARD_SIZE || board.height > LARGE_BOARD_SIZE),
            cells = cells,
            playerRow = player.row,
            playerColumn = player.col,
            startHighlights = startHighlights,
            turnKey = levelState.stats.turns,
            actorTransitions = actorTransitions,
            introHighlightKey = listOf(
                ready.levelNumber,
                levelState.config.seed
            ).hashCode(),
            defeatConnection = ready.defeatConnection?.let { connection ->
                GameBoardDefeatConnection(
                    playerRow = connection.playerPosition.row,
                    playerColumn = connection.playerPosition.col,
                    enemyRow = connection.enemyPosition.row,
                    enemyColumn = connection.enemyPosition.col
                )
            },
            contentDescription = labels.summary(
                labels.player,
                player.row + 1,
                player.col + 1,
                board.width,
                board.height
            )
        )
    }

    private fun GameMoveActor.toActorCell(): GameBoardActorCell = when (this) {
        GameMoveActor.PLAYER -> GameBoardActorCell.PLAYER
        GameMoveActor.KRYVAVITSA -> GameBoardActorCell.KRYVAVITSA
        GameMoveActor.SHADOW -> GameBoardActorCell.SHADOW
    }

    companion object {
        private const val LARGE_BOARD_SIZE = 18
    }
}

class GameBoardAccessibilityLabels(
    val floor: String,
    val wall: String,
    val exit: String,
    val player: String,
    val kryvavitsa: String,
    val shadow: String,
    val summary: (actorLabel: String, row: Int, column: Int, width: Int, height: Int) -> String
)

data class GameBoardRenderState(
    val boardWidth: Int,
    val boardHeight: Int,
    val levelNumber: Int,
    val isLargeBoard: Boolean,
    val cells: List<GameBoardRenderCell>,
    val playerRow: Int,
    val playerColumn: Int,
    val startHighlights: List<GameBoardHighlightCell>,
    // Monotonic per-turn key; the view (re)starts the move animation only when it changes.
    val turnKey: Int,
    // Actor displacements for the latest turn; empty when nothing moved (no animation).
    val actorTransitions: List<GameBoardActorTransition>,
    val introHighlightKey: Int,
    val defeatConnection: GameBoardDefeatConnection?,
    val contentDescription: String
)

data class GameBoardActorTransition(
    val actorCell: GameBoardActorCell,
    val fromRow: Int,
    val fromColumn: Int,
    val toRow: Int,
    val toColumn: Int
)

data class GameBoardHighlightCell(
    val row: Int,
    val column: Int
)

data class GameBoardDefeatConnection(
    val playerRow: Int,
    val playerColumn: Int,
    val enemyRow: Int,
    val enemyColumn: Int
)

data class GameBoardRenderCell(
    val row: Int,
    val column: Int,
    val baseCell: GameBoardBaseCell,
    val actorCell: GameBoardActorCell?
)

enum class GameBoardBaseCell {
    FLOOR,
    WALL,
    EXIT
}

enum class GameBoardActorCell {
    PLAYER,
    KRYVAVITSA,
    SHADOW
}
