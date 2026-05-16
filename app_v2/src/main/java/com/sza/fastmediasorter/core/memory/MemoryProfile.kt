package com.sza.fastmediasorter.core.memory

import com.sza.fastmediasorter.core.util.MemoryTier

data class MemoryProfile(
    val scenario: MemoryScenario,
    val tier: MemoryTier,
    val startupGlideMemoryCacheMb: Int,
    val useRgb565: Boolean,
) {
    companion object {
        val EMPTY = MemoryProfile(
            scenario = MemoryScenario.IDLE,
            tier = MemoryTier.LOW,
            startupGlideMemoryCacheMb = 0,
            useRgb565 = true,
        )
    }
}