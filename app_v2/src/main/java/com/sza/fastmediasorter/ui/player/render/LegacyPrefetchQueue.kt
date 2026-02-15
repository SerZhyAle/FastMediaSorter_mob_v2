package com.sza.fastmediasorter.ui.player.render

import java.util.ArrayDeque

class LegacyPrefetchQueue(
    initialConfig: PrefetchQueueConfig = PrefetchQueueConfig()
) : PrefetchQueue {

    private var config: PrefetchQueueConfig = initialConfig
    private val queue = ArrayDeque<RenderTarget>()

    override fun offer(target: RenderTarget): Boolean {
        if (queue.size >= config.maxDepth) return false
        queue.addLast(target)
        return true
    }

    override fun offerAll(targets: List<RenderTarget>): Int {
        var accepted = 0
        targets.forEach { target ->
            if (offer(target)) accepted++
        }
        return accepted
    }

    override fun pollNext(): RenderTarget? = queue.pollFirst()

    override fun clear() {
        queue.clear()
    }

    override fun size(): Int = queue.size

    override fun updateConfig(config: PrefetchQueueConfig) {
        this.config = config
        while (queue.size > config.maxDepth) {
            queue.pollLast()
        }
    }
}
