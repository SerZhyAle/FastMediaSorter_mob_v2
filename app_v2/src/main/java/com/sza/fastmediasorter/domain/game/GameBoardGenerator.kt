package com.sza.fastmediasorter.domain.game

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random
import javax.inject.Inject

class GameBoardGenerator @Inject constructor() {

    fun createInitialState(config: GameLevelConfig): GameLevelState {
        val normalized = normalizeConfig(config)
        repeat(MAX_GENERATION_ATTEMPTS) { attempt ->
            val random = Random(normalized.seed + attempt * ATTEMPT_SEED_STEP)
            val candidate = tryCreateState(normalized, random) ?: return@repeat
            if (validateGeneratedState(candidate)) return candidate
        }
        error("generated board is invalid after $MAX_GENERATION_ATTEMPTS attempts")
    }

    fun validateCustomBoard(
        board: GameBoard,
        player: GameActor?,
        enemies: List<GameEnemy>
    ): GameBoardValidationResult {
        val errors = mutableSetOf<GameBoardValidationError>()
        if (board.width < MIN_BOARD_SIZE || board.height < MIN_BOARD_SIZE) {
            errors += GameBoardValidationError.INVALID_SIZE
        }
        if (player == null || !board.contains(player.position) || !board.isFloor(player.position)) {
            errors += GameBoardValidationError.MISSING_PLAYER
        }
        val kryvavitsa = enemies.filter { it.type == GameEnemyType.KRYVAVITSA }
        if (kryvavitsa.size != 1 || kryvavitsa.any { !board.contains(it.position) || !board.isFloor(it.position) }) {
            errors += GameBoardValidationError.MISSING_KRYVAVITSA
        }
        if (board.exitPositions().isEmpty()) {
            errors += GameBoardValidationError.MISSING_EXIT
        }
        if (player != null && kryvavitsa.any { it.position.isOrthogonallyAdjacentTo(player.position) }) {
            errors += GameBoardValidationError.PLAYER_START_ADJACENT_TO_ENEMY
        }
        if (player != null && errors.none { it == GameBoardValidationError.MISSING_PLAYER }) {
            val reachable = reachableFloorsAndExits(board, player.position)
            if (board.exitPositions().none { it in reachable }) {
                errors += GameBoardValidationError.EXIT_NOT_REACHABLE
            }
            if (kryvavitsa.size == 1 && kryvavitsa.none { it.position in reachable }) {
                errors += GameBoardValidationError.KRYVAVITSA_NOT_REACHABLE
            }
        }
        return GameBoardValidationResult(errors)
    }

    private fun normalizeConfig(config: GameLevelConfig): GameLevelConfig {
        val width = config.width.coerceIn(MIN_BOARD_SIZE, MAX_BOARD_SIZE)
        val height = config.height.coerceIn(MIN_BOARD_SIZE, MAX_BOARD_SIZE)
        val maximumShadowCount = maximumShadowCount(width, height)
        return config.copy(
            width = width,
            height = height,
            shadowCount = config.shadowCount.coerceIn(1, maximumShadowCount)
        )
    }

    private fun tryCreateState(config: GameLevelConfig, random: Random): GameLevelState? {
        val playerPosition = randomPosition(config.width, config.height, random)
        val fieldBase = minOf(config.width, config.height)
        val exitPosition = findPosition(config.width, config.height, random) { position ->
            position != playerPosition && position.manhattanDistanceTo(playerPosition) > fieldBase / 4.0
        } ?: return null
        val exitPath = buildReservedPath(playerPosition, exitPosition, random)
        val kryvavitsaPosition = findPosition(config.width, config.height, random) { position ->
            position != playerPosition &&
                position != exitPosition &&
                position !in exitPath &&
                position.manhattanDistanceTo(playerPosition) > fieldBase / 3.0
        } ?: return null
        // Reserve a corridor from the player to Kryvavitsa as well, so she is always reachable from the start.
        val kryvavitsaPath = buildReservedPath(playerPosition, kryvavitsaPosition, random)
        val reservedPath = exitPath + kryvavitsaPath

        val shadowPositions = mutableListOf<GamePosition>()
        repeat(config.shadowCount) { shadowIndex ->
            val shadowPosition = findPosition(config.width, config.height, random) { position ->
                position != playerPosition &&
                    position != exitPosition &&
                    position != kryvavitsaPosition &&
                    position !in reservedPath &&
                    position !in shadowPositions &&
                    position.manhattanDistanceTo(playerPosition) > fieldBase / 4.0
            } ?: return null
            shadowPositions += shadowPosition
        }

        val occupiedPositions = buildSet {
            add(playerPosition)
            add(exitPosition)
            add(kryvavitsaPosition)
            addAll(shadowPositions)
        }
        val wallCandidates = allPositions(config.width, config.height)
            .filter { it !in occupiedPositions && it !in reservedPath }
        val wallTarget = chooseWallTarget(config.width, config.height, random)
        if (wallCandidates.size < wallTarget) return null
        val wallPositions = wallCandidates.shuffled(random).take(wallTarget).toSet()

        var board = GameBoard(
            width = config.width,
            height = config.height,
            cells = List(config.width * config.height) { GameCell.FLOOR }
        )
        wallPositions.forEach { position ->
            board = board.withCell(position, GameCell.WALL)
        }
        board = board.withCell(exitPosition, GameCell.EXIT)

        return GameLevelState(
            board = board,
            player = GameActor(playerPosition),
            enemies = buildList {
                add(GameEnemy("kryvavitsa", GameEnemyType.KRYVAVITSA, kryvavitsaPosition))
                shadowPositions.forEachIndexed { index, position ->
                    add(GameEnemy("shadow_${index + 1}", GameEnemyType.SHADOW, position))
                }
            },
            config = config
        )
    }

    private fun validateGeneratedState(state: GameLevelState): Boolean =
        hasValidWallDensity(state.board) &&
            hasPathToExit(state.board, state.player.position) &&
            hasPathToKryvavitsa(state) &&
            hasLegalFirstMove(state) &&
            enemiesHaveValidDistances(state) &&
            state.enemies.count { it.type == GameEnemyType.SHADOW } <= maximumShadowCapacity(state)

    private fun hasValidWallDensity(board: GameBoard): Boolean {
        val density = board.cells.count { it == GameCell.WALL }.toDouble() / board.cells.size.toDouble()
        return density >= MIN_WALL_DENSITY && density <= MAX_WALL_DENSITY
    }

    private fun hasPathToExit(board: GameBoard, start: GamePosition): Boolean =
        board.exitPositions().any { it in reachableFloorsAndExits(board, start) }

    private fun hasPathToKryvavitsa(state: GameLevelState): Boolean {
        val kryvavitsa = state.enemies.single { it.type == GameEnemyType.KRYVAVITSA }
        return kryvavitsa.position in reachableFloorsAndExits(state.board, state.player.position)
    }

    private fun hasLegalFirstMove(state: GameLevelState): Boolean = GameDirection.entries.any { direction ->
        val target = state.player.position.move(direction)
        if (!state.board.contains(target)) return@any false
        when (state.cellAt(target)) {
            Occupant.FLOOR, Occupant.EXIT -> true
            Occupant.WALL -> {
                val behindWall = target.move(direction)
                state.board.contains(behindWall) && when (state.cellAt(behindWall)) {
                    Occupant.FLOOR, Occupant.SHADOW, Occupant.EXIT -> true
                    else -> false
                }
            }
            else -> false
        }
    }

    private fun enemiesHaveValidDistances(state: GameLevelState): Boolean {
        val fieldBase = minOf(state.board.width, state.board.height)
        val kryvavitsa = state.enemies.single { it.type == GameEnemyType.KRYVAVITSA }
        return kryvavitsa.position.manhattanDistanceTo(state.player.position) > fieldBase / 3.0 &&
            state.enemies
                .filter { it.type == GameEnemyType.SHADOW }
                .all { it.position.manhattanDistanceTo(state.player.position) > fieldBase / 4.0 }
    }

    private fun maximumShadowCapacity(state: GameLevelState): Int =
        (state.board.width * state.board.height) - state.board.cells.count { it == GameCell.WALL } - FIXED_ENTITY_COUNT

    private fun maximumShadowCount(width: Int, height: Int): Int =
        maxOf(1, floor(width * height * MAX_SHADOW_LEVEL_FACTOR).toInt())

    private fun allPositions(width: Int, height: Int): List<GamePosition> = buildList {
        for (row in 0 until height) {
            for (col in 0 until width) {
                add(GamePosition(row, col))
            }
        }
    }

    private fun chooseWallTarget(width: Int, height: Int, random: Random): Int {
        val cellCount = width * height
        val minimumWalls = ceil(cellCount * MIN_WALL_DENSITY).toInt()
        val extraWallLimit = floor(cellCount * WALL_DENSITY_SPREAD).toInt() + 1
        return minimumWalls + random.nextInt(extraWallLimit)
    }

    private fun randomPosition(width: Int, height: Int, random: Random): GamePosition =
        GamePosition(random.nextInt(height), random.nextInt(width))

    private fun findPosition(
        width: Int,
        height: Int,
        random: Random,
        predicate: (GamePosition) -> Boolean
    ): GamePosition? {
        repeat(MAX_PLACEMENT_ATTEMPTS) {
            val position = randomPosition(width, height, random)
            if (predicate(position)) return position
        }
        val candidates = allPositions(width, height).filter(predicate)
        return candidates.randomOrNull(random)
    }

    private fun buildReservedPath(from: GamePosition, to: GamePosition, random: Random): Set<GamePosition> {
        val path = mutableListOf(from)
        var current = from
        if (random.nextBoolean()) {
            current = appendHorizontalPath(path, current, to.col)
            appendVerticalPath(path, current, to.row)
        } else {
            current = appendVerticalPath(path, current, to.row)
            appendHorizontalPath(path, current, to.col)
        }
        return path.toSet()
    }

    private fun appendHorizontalPath(path: MutableList<GamePosition>, from: GamePosition, targetCol: Int): GamePosition {
        var currentCol = from.col
        while (currentCol != targetCol) {
            currentCol += if (targetCol > currentCol) 1 else -1
            path += GamePosition(from.row, currentCol)
        }
        return GamePosition(from.row, currentCol)
    }

    private fun appendVerticalPath(path: MutableList<GamePosition>, from: GamePosition, targetRow: Int): GamePosition {
        var currentRow = from.row
        while (currentRow != targetRow) {
            currentRow += if (targetRow > currentRow) 1 else -1
            path += GamePosition(currentRow, from.col)
        }
        return GamePosition(currentRow, from.col)
    }

    private fun reachableFloorsAndExits(board: GameBoard, start: GamePosition): Set<GamePosition> {
        val visited = mutableSetOf<GamePosition>()
        val queue = ArrayDeque<GamePosition>()
        queue += start
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            GameDirection.entries
                .map { current.move(it) }
                .filter { board.contains(it) && board.cellAt(it) != GameCell.WALL && it !in visited }
                .forEach { queue += it }
        }
        return visited
    }

    private fun GameLevelState.cellAt(position: GamePosition): Occupant {
        if (!board.contains(position)) return Occupant.OUT_OF_BOUNDS
        if (player.position == position) return Occupant.PLAYER
        enemies.firstOrNull { it.position == position }?.let { enemy ->
            return when (enemy.type) {
                GameEnemyType.KRYVAVITSA -> Occupant.KRYVAVITSA
                GameEnemyType.SHADOW -> Occupant.SHADOW
            }
        }
        return when (board.cellAt(position)) {
            GameCell.FLOOR -> Occupant.FLOOR
            GameCell.WALL -> Occupant.WALL
            GameCell.EXIT -> Occupant.EXIT
        }
    }

    private enum class Occupant {
        FLOOR,
        WALL,
        PLAYER,
        KRYVAVITSA,
        SHADOW,
        EXIT,
        OUT_OF_BOUNDS
    }

    companion object {
        private const val MIN_BOARD_SIZE = 10
        private const val MAX_BOARD_SIZE = 100
        private const val MAX_GENERATION_ATTEMPTS = 1500
        private const val MAX_PLACEMENT_ATTEMPTS = 5000
        private const val ATTEMPT_SEED_STEP = 7919L
        private const val MIN_WALL_DENSITY = 0.30
        private const val MAX_WALL_DENSITY = 0.40
        private const val WALL_DENSITY_SPREAD = 0.10
        private const val MAX_SHADOW_LEVEL_FACTOR = 0.30
        private const val FIXED_ENTITY_COUNT = 3
    }
}