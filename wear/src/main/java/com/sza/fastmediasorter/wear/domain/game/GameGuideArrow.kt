package com.sza.fastmediasorter.wear.domain.game

/**
 * S2494: which exit the start-of-level hint points at.
 *
 * Nearest by Manhattan distance, which is the phone's rule and the same metric the player moves by -
 * the board admits no diagonals, so a straight-line "nearest" would sometimes name an exit that is
 * further away in moves. Kept in the domain rather than in the canvas because it is a rule of the
 * game and is tested as one.
 */
object GameGuideArrow {

    /** The exit closest to the player, or null on a board carrying none. */
    fun targetFor(state: GameLevelState): GamePosition? {
        val player = state.player.position
        return state.board.exitPositions().minByOrNull { exit -> exit.manhattanDistanceTo(player) }
    }
}
