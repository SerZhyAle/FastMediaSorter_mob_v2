package com.sza.fastmediasorter.leakwatch

import leakcanary.AppWatcher

/**
 * The canary of the nightly leak-watch contour (S3371 phase 05).
 *
 * A contour that reports "no leaks" is indistinguishable from a contour that is not running at
 * all: LeakCanary's instrumentation rule silently does nothing when the watcher is not installed,
 * when the APK is not a debug build, or when the heap dumper has no permission. Seeding a
 * retained object and requiring the harness to find it is the only evidence that a green run
 * means anything.
 *
 * The canary is identifiable by construction, so it can never be mistaken for a product leak in
 * the nightly report: it is its own type, [Canary], and it is watched under [MARKER], which the
 * heap analysis prints beside the retained instance.
 */
object SeededLeakScenario {

    /**
     * The token the report carries. Read by a human triaging the nightly artifact and by
     * [CriticalFlowLeakWatchTest], which requires the detection failure to name it.
     */
    const val MARKER: String = "S3371-SEEDED-LEAK-CANARY"

    /**
     * The static field that does the leaking. A companion-scoped reference outlives every
     * Activity, which is exactly the shape of the leak this contour hunts in product code.
     */
    private var retained: Canary? = null

    val isSeeded: Boolean
        get() = retained != null

    /** Retain a canary and hand it to the watcher, so the next leak assertion must fail. */
    fun seed() {
        val canary = Canary()
        retained = canary
        AppWatcher.objectWatcher.expectWeaklyReachable(canary, MARKER)
    }

    /**
     * Drop the reference. The watcher clears its own weak reference at the next GC, which the
     * leak assertion triggers itself, so the flows walked after the canary test are judged
     * against a clean watcher.
     */
    fun release() {
        retained = null
    }

    /** A dedicated type so a heap analysis names the canary rather than an anonymous [Any]. */
    class Canary
}
