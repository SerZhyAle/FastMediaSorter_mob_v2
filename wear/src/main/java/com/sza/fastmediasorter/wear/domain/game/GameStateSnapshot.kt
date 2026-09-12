package com.sza.fastmediasorter.wear.domain.game

/**
 * The watch's own saved game (S1710). It never reconciles against the phone's save, so it carries
 * its own schema version and its own serialization.
 *
 * The stored form is one string because the watch settings store holds strings; an absent key is a
 * first run and a string this file cannot read is discarded as if absent - a save is a convenience,
 * never a reason to surface an error to the player.
 */
data class GameStateSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val level: Int,
    val difficulty: GameDifficulty,
    val board: GameBoard,
    val player: GameActor,
    val enemies: List<GameEnemy>,
    val stats: GameStats,
    val config: GameLevelConfig,
    val status: GameStatus,
    val customBoard: Boolean = false,
    val updatedAtEpochMillis: Long = 0L
) {
    fun toLevelState(): GameLevelState = GameLevelState(
        board = board,
        player = player,
        enemies = enemies,
        stats = stats,
        config = config,
        status = status
    )

    fun toStorage(): String = listOf(
        schemaVersion.toString(),
        level.toString(),
        difficulty.name,
        board.width.toString(),
        board.height.toString(),
        board.cells.joinToString("") { charOf(it).toString() },
        "${player.position.row}$PART_SEPARATOR${player.position.col}",
        enemies.joinToString(RECORD_SEPARATOR) { encodeEnemy(it) },
        encodeStats(stats),
        encodeConfig(config),
        status.name,
        if (customBoard) STORED_TRUE else STORED_FALSE,
        updatedAtEpochMillis.toString()
    ).joinToString(FIELD_SEPARATOR)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun fromLevelState(
            state: GameLevelState,
            customBoard: Boolean = false,
            updatedAtEpochMillis: Long = System.currentTimeMillis()
        ): GameStateSnapshot = GameStateSnapshot(
            level = state.config.levelNumber,
            difficulty = state.config.difficulty,
            board = state.board,
            player = state.player,
            enemies = state.enemies,
            stats = state.stats,
            config = state.config,
            status = state.status,
            customBoard = customBoard,
            updatedAtEpochMillis = updatedAtEpochMillis
        )

        /** Null for an absent, a truncated, or an unreadable save - the caller treats all three alike. */
        fun fromStorage(value: String?): GameStateSnapshot? = value
            ?.split(FIELD_SEPARATOR)
            ?.takeIf { it.size == FIELD_COUNT && it[INDEX_SCHEMA] == CURRENT_SCHEMA_VERSION.toString() }
            ?.let(::decodeFields)
    }
}

private const val FIELD_SEPARATOR = "\u001F"
private const val RECORD_SEPARATOR = "\u001E"
private const val PART_SEPARATOR = ","

private const val FIELD_COUNT = 13
private const val INDEX_SCHEMA = 0
private const val INDEX_LEVEL = 1
private const val INDEX_DIFFICULTY = 2
private const val INDEX_WIDTH = 3
private const val INDEX_HEIGHT = 4
private const val INDEX_CELLS = 5
private const val INDEX_PLAYER = 6
private const val INDEX_ENEMIES = 7
private const val INDEX_STATS = 8
private const val INDEX_CONFIG = 9
private const val INDEX_STATUS = 10
private const val INDEX_CUSTOM_BOARD = 11
private const val INDEX_UPDATED_AT = 12

private const val ENEMY_FIELD_COUNT = 4
private const val INDEX_ENEMY_ID = 0
private const val INDEX_ENEMY_TYPE = 1
private const val INDEX_ENEMY_ROW = 2
private const val INDEX_ENEMY_COL = 3

private const val CONFIG_FIELD_COUNT = 6
private const val CONFIG_NUMBER_COUNT = 4
private const val CONFIG_NUMBER_LEVEL = 0
private const val CONFIG_NUMBER_WIDTH = 1
private const val CONFIG_NUMBER_HEIGHT = 2
private const val CONFIG_NUMBER_SHADOWS = 3
private const val INDEX_CONFIG_LEVEL = 0
private const val INDEX_CONFIG_DIFFICULTY = 1
private const val INDEX_CONFIG_WIDTH = 2
private const val INDEX_CONFIG_HEIGHT = 3
private const val INDEX_CONFIG_SHADOWS = 4
private const val INDEX_CONFIG_SEED = 5

private const val STATS_FIELD_COUNT = 6
private const val INDEX_STATS_TURNS = 0
private const val INDEX_STATS_WALL_PUSHES = 1
private const val INDEX_STATS_LEVELS_COMPLETED = 2
private const val INDEX_STATS_SURVIVAL_STREAK = 3
private const val INDEX_STATS_SCORE = 4
private const val INDEX_STATS_HIGH_SCORE = 5
private const val POSITION_FIELD_COUNT = 2

private const val STORED_TRUE = "1"
private const val STORED_FALSE = "0"

private const val CHAR_FLOOR = '.'
private const val CHAR_WALL = '#'
private const val CHAR_EXIT = 'X'
private const val CHAR_VOID = ' '

/** The player and the enemies, accepted only once they are known to stand on the decoded board. */
private class DecodedPlacement(val player: GameActor, val enemies: List<GameEnemy>)

/** The scalar half of a save: everything that is neither the board nor the actors on it. */
private class DecodedHeader(
    val level: Int,
    val difficulty: GameDifficulty,
    val status: GameStatus,
    val updatedAtEpochMillis: Long
)

private fun charOf(cell: GameCell): Char = when (cell) {
    GameCell.FLOOR -> CHAR_FLOOR
    GameCell.WALL -> CHAR_WALL
    GameCell.EXIT -> CHAR_EXIT
    GameCell.VOID -> CHAR_VOID
}

private fun cellOf(char: Char): GameCell? = when (char) {
    CHAR_FLOOR -> GameCell.FLOOR
    CHAR_WALL -> GameCell.WALL
    CHAR_EXIT -> GameCell.EXIT
    CHAR_VOID, '-', '_' -> GameCell.VOID
    else -> null
}

private fun encodeEnemy(enemy: GameEnemy): String = listOf(
    enemy.id,
    enemy.type.name,
    enemy.position.row.toString(),
    enemy.position.col.toString()
).joinToString(PART_SEPARATOR)

private fun encodeStats(stats: GameStats): String = listOf(
    stats.turns,
    stats.wallPushes,
    stats.levelsCompleted,
    stats.survivalStreak,
    stats.score,
    stats.highScore
).joinToString(PART_SEPARATOR)

private fun encodeConfig(config: GameLevelConfig): String = listOf(
    config.levelNumber.toString(),
    config.difficulty.name,
    config.width.toString(),
    config.height.toString(),
    config.shadowCount.toString(),
    config.seed.toString()
).joinToString(PART_SEPARATOR)

// Decoding is a chain of nullable steps rather than one guard: the board has to exist before the
// actors on it can be judged, and the model invariants must never see a half-read save. Any step
// answering null discards the whole string, which the caller treats as no save at all.
private fun decodeFields(parts: List<String>): GameStateSnapshot? =
    decodeBoard(parts[INDEX_WIDTH], parts[INDEX_HEIGHT], parts[INDEX_CELLS])?.let { board ->
        decodePlacement(parts, board)?.let { placement ->
            decodeRemainder(parts, board, placement)
        }
    }

private fun decodeRemainder(
    parts: List<String>,
    board: GameBoard,
    placement: DecodedPlacement
): GameStateSnapshot? =
    decodeStats(parts[INDEX_STATS])?.let { stats ->
        decodeConfig(parts[INDEX_CONFIG])?.let { config ->
            decodeHeader(parts)?.let { header ->
                GameStateSnapshot(
                    level = header.level,
                    difficulty = header.difficulty,
                    board = board,
                    player = placement.player,
                    enemies = placement.enemies,
                    stats = stats,
                    config = config,
                    status = header.status,
                    customBoard = parts[INDEX_CUSTOM_BOARD] == STORED_TRUE,
                    updatedAtEpochMillis = header.updatedAtEpochMillis
                )
            }
        }
    }

private fun decodeHeader(parts: List<String>): DecodedHeader? =
    parts[INDEX_LEVEL].toIntOrNull()?.let { level ->
        difficultyOrNull(parts[INDEX_DIFFICULTY])?.let { difficulty ->
            statusOrNull(parts[INDEX_STATUS])?.let { status ->
                parts[INDEX_UPDATED_AT].toLongOrNull()?.let { updatedAt ->
                    DecodedHeader(level, difficulty, status, updatedAt)
                }
            }
        }
    }

// Exactly one kryvavitsa is a constructor invariant of GameLevelState, so a save that lost her is
// rejected here instead of taking the program down when the level is rebuilt.
private fun decodePlacement(parts: List<String>, board: GameBoard): DecodedPlacement? =
    decodePosition(parts[INDEX_PLAYER])?.takeIf(board::contains)?.let { position ->
        decodeEnemies(parts[INDEX_ENEMIES])
            ?.takeIf { enemies -> enemies.all { board.contains(it.position) } }
            ?.takeIf { enemies -> enemies.count { it.type == GameEnemyType.KRYVAVITSA } == 1 }
            ?.let { DecodedPlacement(GameActor(position), it) }
    }

private fun decodeBoard(widthRaw: String, heightRaw: String, cellsRaw: String): GameBoard? =
    widthRaw.toIntOrNull()?.takeIf { it > 0 }?.let { width ->
        heightRaw.toIntOrNull()?.takeIf { it > 0 }?.let { height ->
            cellsRaw.mapNotNull(::cellOf)
                .takeIf { it.size == cellsRaw.length }
                ?.takeIf { it.size == width * height }
                ?.let { GameBoard(width, height, it) }
        }
    }

private fun decodePosition(raw: String): GamePosition? =
    raw.split(PART_SEPARATOR)
        .mapNotNull { it.toIntOrNull() }
        .takeIf { it.size == POSITION_FIELD_COUNT }
        ?.let { GamePosition(it[0], it[1]) }

private fun decodeEnemies(raw: String): List<GameEnemy>? =
    raw.takeIf { it.isNotEmpty() }
        ?.split(RECORD_SEPARATOR)
        ?.let { records -> records.mapNotNull(::decodeEnemy).takeIf { it.size == records.size } }

private fun decodeEnemy(raw: String): GameEnemy? =
    raw.split(PART_SEPARATOR)
        .takeIf { it.size == ENEMY_FIELD_COUNT }
        ?.takeIf { it[INDEX_ENEMY_ID].isNotEmpty() }
        ?.let { fields ->
            enemyTypeOrNull(fields[INDEX_ENEMY_TYPE])?.let { type ->
                val rawPosition = fields[INDEX_ENEMY_ROW] + PART_SEPARATOR + fields[INDEX_ENEMY_COL]
                decodePosition(rawPosition)?.let { GameEnemy(fields[INDEX_ENEMY_ID], type, it) }
            }
        }

private fun decodeStats(raw: String): GameStats? =
    raw.split(PART_SEPARATOR)
        .mapNotNull { it.toIntOrNull() }
        .takeIf { it.size == STATS_FIELD_COUNT }
        ?.let { values ->
            GameStats(
                turns = values[INDEX_STATS_TURNS],
                wallPushes = values[INDEX_STATS_WALL_PUSHES],
                levelsCompleted = values[INDEX_STATS_LEVELS_COMPLETED],
                survivalStreak = values[INDEX_STATS_SURVIVAL_STREAK],
                score = values[INDEX_STATS_SCORE],
                highScore = values[INDEX_STATS_HIGH_SCORE]
            )
        }

private fun decodeConfig(raw: String): GameLevelConfig? =
    raw.split(PART_SEPARATOR)
        .takeIf { it.size == CONFIG_FIELD_COUNT }
        ?.let { fields -> buildConfig(fields) }

private fun buildConfig(fields: List<String>): GameLevelConfig? {
    val numbers = listOf(
        fields[INDEX_CONFIG_LEVEL],
        fields[INDEX_CONFIG_WIDTH],
        fields[INDEX_CONFIG_HEIGHT],
        fields[INDEX_CONFIG_SHADOWS]
    ).mapNotNull { it.toIntOrNull() }.takeIf { it.size == CONFIG_NUMBER_COUNT }
    val difficulty = difficultyOrNull(fields[INDEX_CONFIG_DIFFICULTY])
    val seed = fields[INDEX_CONFIG_SEED].toLongOrNull()
    return if (numbers == null || difficulty == null || seed == null) {
        null
    } else {
        GameLevelConfig(
            levelNumber = numbers[CONFIG_NUMBER_LEVEL],
            difficulty = difficulty,
            width = numbers[CONFIG_NUMBER_WIDTH],
            height = numbers[CONFIG_NUMBER_HEIGHT],
            shadowCount = numbers[CONFIG_NUMBER_SHADOWS],
            seed = seed
        )
    }
}

private fun difficultyOrNull(name: String): GameDifficulty? =
    GameDifficulty.entries.firstOrNull { it.name == name }

private fun statusOrNull(name: String): GameStatus? =
    GameStatus.entries.firstOrNull { it.name == name }

private fun enemyTypeOrNull(name: String): GameEnemyType? =
    GameEnemyType.entries.firstOrNull { it.name == name }
