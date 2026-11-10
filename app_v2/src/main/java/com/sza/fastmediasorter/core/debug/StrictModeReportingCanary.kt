package com.sza.fastmediasorter.core.debug

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Answers what a session log without StrictMode lines cannot: was the code clean, or had reporting
 * stopped? Debug builds only, armed from the debug policy setup on API 28 and up.
 *
 * It makes one deliberate main-thread disk read and expects that violation back through the penalty
 * listener, which proves the policy, the listener and its executor at once. Reading the policy alone
 * would not: a `ThreadPolicy` exposes its mask but not its listener.
 *
 * Its lines say `StrictMode reporting:`, so a search for a violation site never matches them.
 */
class StrictModeReportingCanary : Application.ActivityLifecycleCallbacks {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** The clock reading of the probe awaiting delivery; written by the main and the listener thread. */
    private val pendingSinceMs = AtomicLong(NOT_PENDING)

    @Volatile private var lastAlive: Boolean? = null

    // Main thread only: both are touched from the lifecycle callbacks and from arm().
    private var lastPolicy: String? = null
    private var lastProbeMs = NEVER_PROBED

    /** Call on the main thread, after the policy is set. */
    fun arm(application: Application) {
        val policy = StrictMode.getThreadPolicy().toString()
        lastPolicy = policy
        Timber.i("StrictMode reporting: armed, policy=$policy")
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * True when [violation] is this canary's own read, which must never read as a defect. Runs on the
     * listener's thread.
     */
    fun consume(violation: Throwable, nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        if (!isCanaryViolation(violation)) return false
        val sentAtMs = pendingSinceMs.getAndSet(NOT_PENDING)
        if (sentAtMs != NOT_PENDING) {
            reportVerdict(alive = true, detail = "canary delivered after ${nowMs - sentAtMs} ms")
        }
        return true
    }

    override fun onActivityResumed(activity: Activity) {
        reportPolicyChange(activity.javaClass.simpleName)
        val nowMs = SystemClock.uptimeMillis()
        if (isProbeDue(nowMs, lastProbeMs)) {
            lastProbeMs = nowMs
            probe(activity.applicationContext, nowMs)
        }
    }

    private fun reportPolicyChange(activityName: String) {
        val policy = StrictMode.getThreadPolicy().toString()
        if (policy != lastPolicy) {
            Timber.w("StrictMode reporting: policy changed before $activityName, $lastPolicy -> $policy")
            lastPolicy = policy
        }
    }

    private fun probe(context: Context, nowMs: Long) {
        if (!pendingSinceMs.compareAndSet(NOT_PENDING, nowMs)) return
        Timber.d("S4518: canary read sent")
        readDisk(context)
        mainHandler.postDelayed(
            {
                if (pendingSinceMs.compareAndSet(nowMs, NOT_PENDING)) {
                    reportVerdict(alive = false, detail = "no canary delivery, policy=${StrictMode.getThreadPolicy()}")
                }
            },
            DELIVERY_TIMEOUT_MS,
        )
    }

    /** The answer does not matter: BlockGuard reports the read before the file system is asked. */
    private fun readDisk(context: Context) {
        File(context.filesDir, CANARY_FILE_NAME).exists()
    }

    private fun reportVerdict(alive: Boolean, detail: String) {
        if (alive == lastAlive) return
        lastAlive = alive
        if (alive) {
            Timber.i("StrictMode reporting: alive, $detail")
        } else {
            Timber.w("StrictMode reporting: SILENT, $detail")
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    internal companion object {
        private const val NOT_PENDING = 0L
        private const val NEVER_PROBED = 0L
        private const val CANARY_FILE_NAME = ".strictmode-canary"
        private val DELIVERY_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5)
        private val PROBE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)

        fun isCanaryViolation(violation: Throwable): Boolean =
            violation.stackTrace.any { frame -> frame.className == StrictModeReportingCanary::class.java.name }

        fun isProbeDue(nowMs: Long, lastProbeMs: Long): Boolean =
            lastProbeMs == NEVER_PROBED || nowMs - lastProbeMs >= PROBE_INTERVAL_MS
    }
}
