package com.sza.fastmediasorter.domain.game

enum class GameCell {
    FLOOR,
    WALL,
    EXIT
}

enum class GameDirection(val rowDelta: Int, val colDelta: Int) {
    UP(-1, 0),
    DOWN(1, 0),
    LEFT(0, -1),
    RIGHT(0, 1);

    companion object {
        fun fromDelta(rowDelta: Int, colDelta: Int): GameDirection? =
            entries.firstOrNull { it.rowDelta == rowDelta && it.colDelta == colDelta }
    }
}

enum class GameDifficulty {
    EASY,
    NORMAL,
    HARD
}

enum class GameEnemyType {
    KRYVAVITSA,
    SHADOW
}

// Visual skin over the unchanged game logic (S0804). Purely presentational; persisted separately
// from the level snapshot so switching modes never resets an in-progress game.
enum class GameMode {
    CLASSIC,
    KRYVAVITSA,

    // S0993: high-contrast readability skin - solid full-colour tiles for small screens.
    CONTRAST;

    companion object {
        fun fromStorageName(name: String?): GameMode = entries.firstOrNull { it.name == name } ?: CLASSIC
    }
}

enum class GameStatus {
    PLAYING,
    LEVEL_WON,
    GAME_OVER
}

enum class GameMoveRejectReason {
    NOT_PLAYING,
    INVALID_DIRECTION,
    OUT_OF_BOUNDS,
    BLOCKED_BY_WALL,
    BLOCKED_BY_EXIT,
    BLOCKED_BY_ENEMY,
    BLOCKED_BY_PLAYER,
    WALL_AT_BOARD_EDGE,
    WALL_BLOCKED_BY_WALL,
    WALL_BLOCKED_BY_KRYVAVITSA
}

enum class GameBoardValidationError {
    INVALID_SIZE,
    MISSING_PLAYER,
    MISSING_KRYVAVITSA,
    MISSING_EXIT,
    EXIT_NOT_REACHABLE,
    KRYVAVITSA_NOT_REACHABLE,
    PLAYER_START_ADJACENT_TO_ENEMY
}

data class GamePosition(val row: Int, val col: Int) {
    fun move(direction: GameDirection): GamePosition =
        GamePosition(row + direction.rowDelta, col + direction.colDelta)

    fun manhattanDistanceTo(other: GamePosition): Int =
        kotlin.math.abs(row - other.row) + kotlin.math.abs(col - other.col)

    fun isOrthogonallyAdjacentTo(other: GamePosition): Boolean =
        manhattanDistanceTo(other) == 1
}

data class GameActor(val position: GamePosition)

data class GameEnemy(
    val id: String,
    val type: GameEnemyType,
    val position: GamePosition
)

data class GameBoard(
    val width: Int,
    val height: Int,
    val cells: List<GameCell>
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(cells.size == width * height) { "cells size must match board area" }
    }

    fun contains(position: GamePosition): Boolean =
        position.row in 0 until height && position.col in 0 until width

    fun cellAt(position: GamePosition): GameCell {
        require(contains(position)) { "position outside board: $position" }
        return cells[indexOf(position)]
    }

    fun withCell(position: GamePosition, cell: GameCell): GameBoard {
        require(contains(position)) { "position outside board: $position" }
        val nextCells = cells.toMutableList()
        nextCells[indexOf(position)] = cell
        return copy(cells = nextCells)
    }

    fun isFloor(position: GamePosition): Boolean =
        contains(position) && cellAt(position) == GameCell.FLOOR

    fun exitPositions(): List<GamePosition> = cells.mapIndexedNotNull { index, cell ->
        if (cell == GameCell.EXIT) positionOf(index) else null
    }

    private fun indexOf(position: GamePosition): Int = position.row * width + position.col


    private fun positionOf(index: Int): GamePosition = GamePosition(index / width, index % width)

    companion object {
        fun fromRows(vararg rows: String): GameBoard {
            require(rows.isNotEmpty()) { "rows must not be empty" }
            val width = rows.first().length
            require(width > 0) { "rows must not be empty" }
            require(rows.all { it.length == width }) { "all rows must have equal width" }
            val cells = rows.flatMap { row ->
                row.map { char ->
                    when (char) {
                        '#', 'W' -> GameCell.WALL
                        'E', 'X' -> GameCell.EXIT
                        '.', 'P', 'K', 'S' -> GameCell.FLOOR
                        else -> error("unsupported board char: $char")
                    }
                }
            }
            return GameBoard(width, rows.size, cells)
        }
    }
}

data class GameLevelConfig(
    val levelNumber: Int = 1,
    val difficulty: GameDifficulty = GameDifficulty.NORMAL,
    val width: Int = 11,
    val height: Int = 11,
    val shadowCount: Int = 2,
    val seed: Long = 0L
)

data class GameStats(
    val turns: Int = 0,
    val wallPushes: Int = 0,
    val levelsCompleted: Int = 0,
    val survivalStreak: Int = 0,
    val score: Int = 0,
    val highScore: Int = 0
)

data class GameLevelState(
    val board: GameBoard,
    val player: GameActor,
    val enemies: List<GameEnemy>,
    val stats: GameStats = GameStats(),
    val config: GameLevelConfig = GameLevelConfig(),
    val status: GameStatus = GameStatus.PLAYING
) {
    init {
        require(enemies.count { it.type == GameEnemyType.KRYVAVITSA } == 1) {
            "exactly one Kryvavitsa enemy is required"
        }
    }
}

sealed interface GameEvent {
    data class PlayerMoved(val from: GamePosition, val to: GamePosition) : GameEvent
    data class PlayerBlocked(val reason: GameMoveRejectReason, val at: GamePosition) : GameEvent
    data class WallPushed(val from: GamePosition, val to: GamePosition) : GameEvent
    data class WallDestroyed(val position: GamePosition) : GameEvent
    data class ShadowCrushed(val enemyId: String, val position: GamePosition) : GameEvent
    data class EnemyMoved(
        val enemyId: String,
        val type: GameEnemyType,
        val from: GamePosition,
        val to: GamePosition
    ) : GameEvent
    data class EnemyStayed(val enemyId: String, val type: GameEnemyType, val at: GamePosition) : GameEvent
    data class PlayerCaptured(val enemyId: String, val type: GameEnemyType) : GameEvent
    data object ExitReached : GameEvent
}

data class GameTurnResult(
    val state: GameLevelState,
    val accepted: Boolean,
    val events: List<GameEvent> = emptyList(),
    val rejectReason: GameMoveRejectReason? = null
)

data class GameBoardValidationResult(
    val errors: Set<GameBoardValidationError>
) {
    val isValid: Boolean get() = errors.isEmpty()
}