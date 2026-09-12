package com.sza.fastmediasorter.wear.domain.game

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

/**
 * Watch mirror of the phone generator: same attempts, same seed stepping, same validation, so one
 * config and one seed yield one board on both devices (ADR-1).
 *
 * [maxGenerationAttempts] is a parameter only so a test can exhaust the budget deterministically;
 * every production caller takes the default, which is the phone's constant.
 */
class GameBoardGenerator(
    private val maxGenerationAttempts: Int = DEFAULT_MAX_GENERATION_ATTEMPTS
) {

    /**
     * Returns null when the attempt budget is exhausted. The phone throws at this point; on the
     * watch a shipped throw would end the program mid-game, so the failure is returned instead.
     */
    fun createInitialState(config: GameLevelConfig): GameLevelState? {
        val normalized = normalizeConfig(config)
        repeat(maxGenerationAttempts) { attempt ->
            val random = Random(normalized.seed + attempt * ATTEMPT_SEED_STEP)
            val candidate = tryCreateState(normalized, random) ?: return@repeat
            if (validateGeneratedState(candidate)) return candidate
        }
        return null
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

    @Suppress("ReturnCount")
    private fun tryCreateState(config: GameLevelConfig, random: Random): GameLevelState? {
        val baseBoard = GameBoard.createRoundTemplate(config.width, config.height)
        val playablePositions = allPositions(config.width, config.height).filter { baseBoard.contains(it) }
        val playerPosition = findPosition(baseBoard, random) { true } ?: return null
        val fieldBase = minOf(config.width, config.height)
        val exitPosition = findPosition(baseBoard, random) { position ->
            position != playerPosition &&
                position.manhattanDistanceTo(playerPosition) > fieldBase / EXIT_DISTANCE_DIVISOR
        } ?: return null
        val exitPath = buildReservedPath(baseBoard, playerPosition, exitPosition, random) ?: return null
        val kryvavitsaPosition = findPosition(baseBoard, random) { position ->
            position != playerPosition &&
                position != exitPosition &&
                position !in exitPath &&
                position.manhattanDistanceTo(playerPosition) > fieldBase / KRYVAVITSA_DISTANCE_DIVISOR
        } ?: return null
        // Reserve a corridor from the player to Kryvavitsa as well, so she is always reachable from the start.
        val kryvavitsaPath = buildReservedPath(baseBoard, playerPosition, kryvavitsaPosition, random) ?: return null
        val reservedPath = exitPath + kryvavitsaPath

        val shadowPositions = mutableListOf<GamePosition>()
        repeat(config.shadowCount) {
            val shadowPosition = findPosition(baseBoard, random) { position ->
                position != playerPosition &&
                    position != exitPosition &&
                    position != kryvavitsaPosition &&
                    position !in reservedPath &&
                    position !in shadowPositions &&
                    position.manhattanDistanceTo(playerPosition) > fieldBase / SHADOW_DISTANCE_DIVISOR
            } ?: return null
            shadowPositions += shadowPosition
        }

        val occupiedPositions = buildSet {
            add(playerPosition)
            add(exitPosition)
            add(kryvavitsaPosition)
            addAll(shadowPositions)
        }
        val wallCandidates = playablePositions
            .filter { it !in occupiedPositions && it !in reservedPath }
        val wallTarget = chooseWallTarget(playablePositions.size, random)
        if (wallCandidates.size < wallTarget) return null
        val wallPositions = wallCandidates.shuffled(random).take(wallTarget).toSet()

        var board = baseBoard
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
        val playableCount = board.cells.count { it != GameCell.VOID }
        if (playableCount == 0) return false
        val density = board.cells.count { it == GameCell.WALL }.toDouble() / playableCount.toDouble()
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
        return kryvavitsa.position.manhattanDistanceTo(state.player.position) >
            fieldBase / KRYVAVITSA_DISTANCE_DIVISOR &&
            state.enemies
                .filter { it.type == GameEnemyType.SHADOW }
                .all { it.position.manhattanDistanceTo(state.player.position) > fieldBase / SHADOW_DISTANCE_DIVISOR }
    }

    private fun maximumShadowCapacity(state: GameLevelState): Int {
        val playableCount = state.board.cells.count { it != GameCell.VOID }
        return playableCount - state.board.cells.count { it == GameCell.WALL } - FIXED_ENTITY_COUNT
    }

    private fun maximumShadowCount(width: Int, height: Int): Int =
        maxOf(1, floor(width * height * MAX_SHADOW_LEVEL_FACTOR).toInt())

    private fun allPositions(width: Int, height: Int): List<GamePosition> = buildList {
        for (row in 0 until height) {
            for (col in 0 until width) {
                add(GamePosition(row, col))
            }
        }
    }

    private fun chooseWallTarget(playableCells: Int, random: Random): Int {
        val minimumWalls = ceil(playableCells * MIN_WALL_DENSITY).toInt()
        val extraWallLimit = floor(playableCells * WALL_DENSITY_SPREAD).toInt() + 1
        return minimumWalls + random.nextInt(extraWallLimit)
    }

    private fun randomPosition(board: GameBoard, random: Random): GamePosition {
        val pos = GamePosition(random.nextInt(board.height), random.nextInt(board.width))
        return if (board.contains(pos)) {
            pos
        } else {
            allPositions(board.width, board.height).filter { board.contains(it) }.random(random)
        }
    }

    private fun findPosition(
        board: GameBoard,
        random: Random,
        predicate: (GamePosition) -> Boolean
    ): GamePosition? {
        repeat(MAX_PLACEMENT_ATTEMPTS) {
            val position = randomPosition(board, random)
            if (board.contains(position) && predicate(position)) return position
        }
        val candidates = allPositions(board.width, board.height).filter { board.contains(it) && predicate(it) }
        return candidates.randomOrNull(random)
    }

    private fun buildReservedPath(
        board: GameBoard,
        from: GamePosition,
        to: GamePosition,
        random: Random
    ): Set<GamePosition>? {
        val queue = ArrayDeque<GamePosition>()
        val parent = mutableMapOf<GamePosition, GamePosition>()
        queue += from
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == to) break
            val directions = GameDirection.entries.shuffled(random)
            for (dir in directions) {
                val next = current.move(dir)
                if (board.contains(next) && next !in parent && next != from) {
                    parent[next] = current
                    queue += next
                }
            }
        }
        if (to !in parent && from != to) return null
        val path = mutableSetOf<GamePosition>()
        var curr: GamePosition? = to
        while (curr != null) {
            path += curr
            curr = parent[curr]
        }
        return path
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

    // Mirrors the phone's cellAt move for move; the shape is held identical on purpose
    // (ADR-1), so the guard chain is suppressed rather than restructured.
    @Suppress("ReturnCount")
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
            GameCell.VOID -> Occupant.OUT_OF_BOUNDS
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
        private const val MIN_BOARD_SIZE = 8
        private const val MAX_BOARD_SIZE = 100
        const val DEFAULT_MAX_GENERATION_ATTEMPTS = 1500
        private const val MAX_PLACEMENT_ATTEMPTS = 5000
        private const val ATTEMPT_SEED_STEP = 7919L
        private const val EXIT_DISTANCE_DIVISOR = 4.0
        private const val KRYVAVITSA_DISTANCE_DIVISOR = 3.0
        private const val SHADOW_DISTANCE_DIVISOR = 4.0
        private const val MIN_WALL_DENSITY = 0.30
        private const val MAX_WALL_DENSITY = 0.40
        private const val WALL_DENSITY_SPREAD = 0.10
        private const val MAX_SHADOW_LEVEL_FACTOR = 0.30
        private const val FIXED_ENTITY_COUNT = 3
    }
}
