package com.sza.fastmediasorter.ui.player.render

data class PrefetchQueueConfig(
    val maxDepth: Int = 6,
    val throttleMs: Long = 0L
)

interface PrefetchQueue {
    fun offer(target: RenderTarget): Boolean
    fun offerAll(targets: List<RenderTarget>): Int
    fun pollNext(): RenderTarget?
    fun clear()
    fun size(): Int
    fun updateConfig(config: PrefetchQueueConfig)
}
