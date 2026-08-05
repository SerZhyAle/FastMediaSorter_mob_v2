package com.sza.fastmediasorter.domain.game

import kotlin.random.Random

class GameRulesEngine(
    private val scoring: GameScoring = GameScoring()
) {

    fun applyMoveDelta(state: GameLevelState, rowDelta: Int, colDelta: Int): GameTurnResult {
        val direction = GameDirection.fromDelta(rowDelta, colDelta)
            ?: return GameTurnResult(state, accepted = false, rejectReason = GameMoveRejectReason.INVALID_DIRECTION)
        return applyMove(state, direction)
    }

    fun applyMove(
        state: GameLevelState,
        direction: GameDirection,
        shadowOrderSeed: Long = state.config.seed + state.stats.turns
    ): GameTurnResult {
        if (state.status != GameStatus.PLAYING) {
            return GameTurnResult(state, accepted = false, rejectReason = GameMoveRejectReason.NOT_PLAYING)
        }

        val playerMove = movePlayer(state, direction)
        if (!playerMove.countedAction) {
            return GameTurnResult(state, accepted = false, rejectReason = playerMove.rejectReason)
        }

        var nextState = playerMove.state
        val events = mutableListOf<GameEvent>()
        events += playerMove.events
        nextState = nextState.copy(
            stats = scoring.afterAcceptedTurn(
                nextState.stats,
                playerMove.wallPushed,
                playerMove.shadowsCrushed
            )
        )

        if (playerMove.playerCaptured) {
            return GameTurnResult(
                nextState.copy(status = GameStatus.GAME_OVER, stats = scoring.afterGameOver(nextState.stats)),
                accepted = true,
                events = events
            )
        }

        if (nextState.board.cellAt(nextState.player.position) == GameCell.EXIT) {
            val completed = nextState.copy(
                stats = scoring.afterLevelCompleted(nextState.stats),
                status = GameStatus.LEVEL_WON
            )
            events += GameEvent.ExitReached
            return GameTurnResult(completed, accepted = true, events = events)
        }

        val enemyTurn = moveEnemies(nextState, shadowOrderSeed)
        events += enemyTurn.events
        nextState = enemyTurn.state
        return GameTurnResult(nextState, accepted = true, events = events)
    }

    // S0928: a skipped turn keeps the player in place but still advances the world - the turn counter
    // ticks (with the normal per-turn penalty) and enemies take their move, capture rules intact.
    fun applySkipTurn(
        state: GameLevelState,
        shadowOrderSeed: Long = state.config.seed + state.stats.turns
    ): GameTurnResult {
        if (state.status != GameStatus.PLAYING) {
            return GameTurnResult(state, accepted = false, rejectReason = GameMoveRejectReason.NOT_PLAYING)
        }
        val ticked = state.copy(
            stats = scoring.afterAcceptedTurn(state.stats, wallPushed = false, shadowsCrushed = 0)
        )
        val enemyTurn = moveEnemies(ticked, shadowOrderSeed)
        return GameTurnResult(enemyTurn.state, accepted = true, events = enemyTurn.events)
    }

    fun advanceLevel(completedState: GameLevelState, nextLevel: GameLevelState): GameLevelState {
        require(completedState.status == GameStatus.LEVEL_WON) { "current level is not completed" }
        return nextLevel.copy(
            stats = completedState.stats,
            status = GameStatus.PLAYING
        )
    }

    fun restartLevel(lostState: GameLevelState, restartedLevel: GameLevelState): GameLevelState {
        require(lostState.status == GameStatus.GAME_OVER) { "current level is not lost" }
        return restartedLevel.copy(
            stats = scoring.afterLevelRestart(lostState.stats),
            status = GameStatus.PLAYING
        )
    }

    /**
     * S1359: restart a level the player is still playing. Routing through [asGameOver] and the
     * existing [restartLevel] is what makes the voluntary price identical to being caught - a second
     * scoring path could drift from it. The engine owns this normalisation because letting the UI
     * hand in a faked GAME_OVER state would make [restartLevel]'s invariant decorative.
     */
    fun restartLevelVoluntarily(
        playingState: GameLevelState,
        restartedLevel: GameLevelState
    ): GameLevelState {
        require(playingState.status == GameStatus.PLAYING) { "current level is not being played" }
        return restartLevel(playingState.asGameOver(), restartedLevel)
    }

    private fun movePlayer(state: GameLevelState, direction: GameDirection): PlayerMoveOutcome {
        val from = state.player.position
        val target = from.move(direction)
        if (!state.board.contains(target)) {
            return blockedPlayerMove(state, GameMoveRejectReason.OUT_OF_BOUNDS, target)
        }
        val targetEnemy = state.enemies.firstOrNull { it.position == target }
        if (targetEnemy != null) {
            return PlayerMoveOutcome(
                state.copy(player = GameActor(target)),
                events = listOf(
                    GameEvent.PlayerMoved(from, target),
                    GameEvent.PlayerCaptured(targetEnemy.id, targetEnemy.type)
                ),
                playerCaptured = true
            )
        }

        return when (state.board.cellAt(target)) {
            GameCell.FLOOR, GameCell.EXIT -> PlayerMoveOutcome(
                state.copy(player = GameActor(target)),
                events = listOf(GameEvent.PlayerMoved(from, target))
            )
            GameCell.WALL -> pushWall(state, direction, from, target)
        }
    }

    private fun pushWall(
        state: GameLevelState,
        direction: GameDirection,
        playerFrom: GamePosition,
        wallPosition: GamePosition
    ): PlayerMoveOutcome {
        val destination = wallPosition.move(direction)
        if (!state.board.contains(destination)) {
            return blockedPlayerMove(state, GameMoveRejectReason.WALL_AT_BOARD_EDGE, wallPosition)
        }
        if (destination == state.player.position) {
            return blockedPlayerMove(state, GameMoveRejectReason.BLOCKED_BY_PLAYER, wallPosition)
        }
        val destinationEnemy = state.enemies.firstOrNull { it.position == destination }
        if (destinationEnemy?.type == GameEnemyType.KRYVAVITSA) {
            return blockedPlayerMove(state, GameMoveRejectReason.WALL_BLOCKED_BY_KRYVAVITSA, wallPosition)
        }
        if (state.board.cellAt(destination) == GameCell.WALL) {
            return blockedPlayerMove(state, GameMoveRejectReason.WALL_BLOCKED_BY_WALL, wallPosition)
        }

        if (state.board.cellAt(destination) == GameCell.EXIT) {
            return PlayerMoveOutcome(
                state.copy(
                    board = state.board.withCell(wallPosition, GameCell.FLOOR),
                    player = GameActor(wallPosition)
                ),
                events = listOf(
                    GameEvent.WallDestroyed(wallPosition),
                    GameEvent.PlayerMoved(playerFrom, wallPosition)
                )
            )
        }

        val crushedShadow = destinationEnemy?.takeIf { it.type == GameEnemyType.SHADOW }
        val nextEnemies = if (crushedShadow != null) {
            state.enemies.filterNot { it.id == crushedShadow.id }
        } else {
            state.enemies
        }
        val nextBoard = state.board
            .withCell(wallPosition, GameCell.FLOOR)
            .withCell(destination, GameCell.WALL)
        return PlayerMoveOutcome(
            state.copy(board = nextBoard, player = GameActor(wallPosition), enemies = nextEnemies),
            events = buildList {
                add(GameEvent.WallPushed(wallPosition, destination))
                add(GameEvent.PlayerMoved(playerFrom, wallPosition))
                if (crushedShadow != null) {
                    add(GameEvent.ShadowCrushed(crushedShadow.id, destination))
                }
            },
            wallPushed = true,
            shadowsCrushed = if (crushedShadow != null) 1 else 0
        )
    }

    private fun moveEnemies(state: GameLevelState, shadowOrderSeed: Long): EnemyTurnOutcome {
        var nextState = state
        val events = mutableListOf<GameEvent>()
        val kryvavitsa = nextState.enemies.first { it.type == GameEnemyType.KRYVAVITSA }
        if (isPlayerCaptured(nextState, kryvavitsa.id)) {
            // Kryvavitsa kills from orthogonal contact; moving first can make it pick an escape fallback.
            events += GameEvent.EnemyStayed(kryvavitsa.id, kryvavitsa.type, kryvavitsa.position)
            events += captureEvent(nextState, kryvavitsa.id)
            return EnemyTurnOutcome(nextState.asGameOver(), events)
        }
        val afterKryvavitsa = moveEnemy(nextState, kryvavitsa, EnemyMovement.HUNT)
        nextState = afterKryvavitsa.state
        events += afterKryvavitsa.events
        if (isPlayerCaptured(nextState, afterKryvavitsa.enemyId)) {
            events += captureEvent(nextState, afterKryvavitsa.enemyId)
            return EnemyTurnOutcome(nextState.asGameOver(), events)
        }

        val random = Random(shadowOrderSeed)
        val shadowIds = nextState.enemies
            .filter { it.type == GameEnemyType.SHADOW }
            .map { it.id }
            .shuffled(random)

        shadowIds.forEach { shadowId ->
            val enemy = nextState.enemies.firstOrNull { it.id == shadowId } ?: return@forEach
            val moved = moveEnemy(nextState, enemy, EnemyMovement.WANDER, random)
            nextState = moved.state
            events += moved.events
            if (isPlayerCaptured(nextState, moved.enemyId)) {
                events += captureEvent(nextState, moved.enemyId)
                return EnemyTurnOutcome(nextState.asGameOver(), events)
            }
        }

        return EnemyTurnOutcome(nextState, events)
    }

    private fun moveEnemy(
        state: GameLevelState,
        enemy: GameEnemy,
        movement: EnemyMovement,
        random: Random = Random(state.config.seed + state.stats.turns)
    ): EnemyMoveOutcome {
        val candidates = when (movement) {
            EnemyMovement.HUNT -> greedyDirections(enemy.position, state.player.position) +
                greedyExitDirections(state, enemy.position) +
                GameDirection.entries.shuffled(random)
            EnemyMovement.WANDER -> GameDirection.entries.shuffled(random)
        }.distinct()
        val destination = candidates
            .map { enemy.position.move(it) }
            .firstOrNull { canEnemyMoveTo(state, enemy, it) }
            ?: return EnemyMoveOutcome(
                state,
                enemy.id,
                listOf(GameEvent.EnemyStayed(enemy.id, enemy.type, enemy.position))
            )

        val nextEnemies = state.enemies.map {
            if (it.id == enemy.id) it.copy(position = destination) else it
        }
        return EnemyMoveOutcome(
            state.copy(enemies = nextEnemies),
            enemy.id,
            listOf(GameEvent.EnemyMoved(enemy.id, enemy.type, enemy.position, destination))
        )
    }

    private fun canEnemyMoveTo(state: GameLevelState, enemy: GameEnemy, position: GamePosition): Boolean =
        state.board.isFloor(position) &&
            position != state.player.position &&
            state.enemies.none { it.id != enemy.id && it.position == position }

    private fun greedyDirections(from: GamePosition, target: GamePosition): List<GameDirection> = buildList {
        when {
            target.col < from.col -> add(GameDirection.LEFT)
            target.col > from.col -> add(GameDirection.RIGHT)
        }
        when {
            target.row < from.row -> add(GameDirection.UP)
            target.row > from.row -> add(GameDirection.DOWN)
        }
    }

    private fun greedyExitDirections(state: GameLevelState, from: GamePosition): List<GameDirection> =
        state.board.exitPositions().firstOrNull()?.let { greedyDirections(from, it) }.orEmpty()

    private fun blockedPlayerMove(
        state: GameLevelState,
        reason: GameMoveRejectReason,
        at: GamePosition
    ): PlayerMoveOutcome = PlayerMoveOutcome(
        state,
        events = listOf(GameEvent.PlayerBlocked(reason, at)),
        countedAction = true,
        rejectReason = reason
    )

    private fun isPlayerCaptured(state: GameLevelState, enemyId: String): Boolean {
        val enemy = state.enemies.firstOrNull { it.id == enemyId } ?: return false
        return enemy.position.isOrthogonallyAdjacentTo(state.player.position)
    }

    private fun captureEvent(state: GameLevelState, enemyId: String): GameEvent.PlayerCaptured {
        val enemy = state.enemies.first { it.id == enemyId }
        return GameEvent.PlayerCaptured(enemy.id, enemy.type)
    }

    private fun GameLevelState.asGameOver(): GameLevelState =
        copy(status = GameStatus.GAME_OVER, stats = scoring.afterGameOver(stats))

    private data class PlayerMoveOutcome(
        val state: GameLevelState,
        val events: List<GameEvent> = emptyList(),
        val wallPushed: Boolean = false,
        val shadowsCrushed: Int = 0,
        val playerCaptured: Boolean = false,
        val countedAction: Boolean = true,
        val rejectReason: GameMoveRejectReason? = null
    )

    private data class EnemyTurnOutcome(
        val state: GameLevelState,
        val events: List<GameEvent>
    )

    private data class EnemyMoveOutcome(
        val state: GameLevelState,
        val enemyId: String,
        val events: List<GameEvent> = emptyList()
    )

    private enum class EnemyMovement {
        HUNT,
        WANDER
    }
}