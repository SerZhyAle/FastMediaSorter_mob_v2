package com.sza.fastmediasorter.wear.domain.game

/**
 * S2494: hands out the seed every generated board is built from.
 *
 * The generator decides nothing by itself - one config and one seed yield one board on the watch and
 * on the phone alike (ADR-1 of S2158) - so this is the single place randomness enters the game.
 *
 * The nonce is what makes two boards drawn inside the same clock tick differ: a lost level restarted
 * by a tap is generated within a millisecond of the board it replaces, and a seed taken from the
 * clock alone would hand the player the same board back.
 *
 * [clock] is a property rather than a call so the sequence can be pinned in a test; the production
 * value mixes both clocks the phone mixes, because `nanoTime` alone is not comparable across boots
 * and `currentTimeMillis` alone is too coarse to separate two starts.
 */
class GameSeedSource {

    internal var clock: () -> Long = { System.nanoTime() xor System.currentTimeMillis() }

    private var nonce: Long = 0L

    fun nextSeed(levelNumber: Int): Long {
        nonce += 1
        return clock() + (nonce * SEED_NONCE_STEP) + levelNumber
    }

    private companion object {
        /** The phone's step, kept identical so the two devices spread seeds the same way. */
        const val SEED_NONCE_STEP = 104729L
    }
}
