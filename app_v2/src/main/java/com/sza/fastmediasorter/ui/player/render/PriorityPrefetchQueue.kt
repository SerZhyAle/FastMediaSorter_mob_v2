package com.sza.fastmediasorter.ui.player.render

import java.util.PriorityQueue

/**
 * Priority-based implementation of [PrefetchQueue].
 *
 * Ordering: NEXT (0) > PREVIOUS (1) > LOOKAHEAD (2).
 * When [slideshowBias] is enabled, forward targets (NEXT, LOOKAHEAD) are prioritized higher.
 * Congestion detection reduces effective depth by skipping LOOKAHEAD items.
 */
class PriorityPrefetchQueue(
    private val isCongested: () -> Boolean = { false }
) : PrefetchQueue {

    private val lock = Any()

    @Volatile
    var slideshowBias: Boolean = false

    private var config = PrefetchQueueConfig()

    private val queue = PriorityQueue<RenderTarget>(
        compareBy { computePriority(it) }
    )

    override fun offer(target: RenderTarget): Boolean {
        synchronized(lock) {
            if (queue.size >= config.maxDepth) {
                return false
            }
            // Avoid duplicates based on target identity
            if (queue.any { it.path == target.path }) {
                return false
            }
            return queue.offer(target)
        }
    }

    override fun offerAll(targets: List<RenderTarget>): Int {
        synchronized(lock) {
            var added = 0
            for (target in targets) {
                if (queue.size >= config.maxDepth) {
                    break
                }
                if (queue.any { it.path == target.path }) {
                    continue
                }
                if (queue.offer(target)) {
                    added++
                }
            }
            return added
        }
    }

    override fun pollNext(): RenderTarget? {
        synchronized(lock) {
            if (queue.isEmpty()) {
                return null
            }

            val congested = isCongested()

            // Rebuild queue with current priority comparator (handles slideshowBias changes)
            val sorted = queue.sortedBy { computePriority(it) }
            queue.clear()
            queue.addAll(sorted)

            // If congested, skip LOOKAHEAD items
            if (congested) {
                val iterator = sorted.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (candidate.priority != RenderPriority.LOOKAHEAD) {
                        queue.remove(candidate)
                        return candidate
                    }
                }
                // All remaining are LOOKAHEAD - skip them during congestion
                return null
            }

            return queue.poll()
        }
    }

    override fun clear() {
        synchronized(lock) {
            queue.clear()
        }
    }

    override fun size(): Int {
        synchronized(lock) {
            return queue.size
        }
    }

    override fun updateConfig(config: PrefetchQueueConfig) {
        synchronized(lock) {
            this.config = config
            // Trim queue if new maxDepth is smaller
            while (queue.size > config.maxDepth) {
                val sorted = queue.sortedByDescending { computePriority(it) }
                if (sorted.isNotEmpty()) {
                    queue.remove(sorted.first())
                }
            }
        }
    }

    /**
     * Computes numeric priority for sorting.
     * Lower value = higher priority.
     *
     * Base priorities: NEXT=0, PREVIOUS=1, LOOKAHEAD=2
     * With [slideshowBias]: NEXT=0, LOOKAHEAD=1, PREVIOUS=2
     */
    private fun computePriority(target: RenderTarget): Int {
        return if (slideshowBias) {
            when (target.priority) {
                RenderPriority.NEXT -> 0
                RenderPriority.LOOKAHEAD -> 1
                RenderPriority.PREVIOUS -> 2
            }
        } else {
            when (target.priority) {
                RenderPriority.NEXT -> 0
                RenderPriority.PREVIOUS -> 1
                RenderPriority.LOOKAHEAD -> 2
            }
        }
    }
}
