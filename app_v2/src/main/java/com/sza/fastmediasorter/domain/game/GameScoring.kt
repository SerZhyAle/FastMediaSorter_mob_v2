package com.sza.fastmediasorter.domain.game

class GameScoring(
    private val turnPenalty: Int = 10,
    private val wallPushBonus: Int = 10,
    private val shadowCrushBonus: Int = 50,
    private val levelCompletionBonus: Int = 1000,
    private val restartPenalty: Int = 500
) {

    fun afterAcceptedTurn(stats: GameStats, wallPushed: Boolean, shadowsCrushed: Int = 0): GameStats {
        val nextScore = (stats.score - turnPenalty +
            (if (wallPushed) wallPushBonus else 0) +
            (shadowsCrushed * shadowCrushBonus)).coerceAtLeast(0)
        return stats.copy(
            turns = stats.turns + 1,
            wallPushes = stats.wallPushes + if (wallPushed) 1 else 0,
            score = nextScore,
            highScore = maxOf(stats.highScore, nextScore)
        )
    }

    fun afterLevelCompleted(stats: GameStats): GameStats {
        val nextScore = stats.score + levelCompletionBonus
        return stats.copy(
            levelsCompleted = stats.levelsCompleted + 1,
            survivalStreak = stats.survivalStreak + 1,
            score = nextScore,
            highScore = maxOf(stats.highScore, nextScore)
        )
    }

    fun afterLevelRestart(stats: GameStats): GameStats {
        val nextScore = (stats.score - restartPenalty).coerceAtLeast(0)
        return stats.copy(
            survivalStreak = 0,
            score = nextScore,
            highScore = maxOf(stats.highScore, nextScore)
        )
    }

    fun afterGameOver(stats: GameStats): GameStats =
        stats.copy(survivalStreak = 0, highScore = maxOf(stats.highScore, stats.score))
}