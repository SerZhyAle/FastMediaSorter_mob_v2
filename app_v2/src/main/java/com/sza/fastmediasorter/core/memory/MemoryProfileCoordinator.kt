package com.sza.fastmediasorter.core.memory

/**
 * Single source of truth for scenario state and startup image-memory defaults.
 * Runtime screen transitions update the profile, but do not reconfigure Glide's
 * process-wide memory cache size in-place.
 */
interface MemoryProfileCoordinator {
    fun enter(scenario: MemoryScenario): MemoryProfile

    fun current(): MemoryProfile

    fun startupGlideMemoryCacheBytes(): Long
}