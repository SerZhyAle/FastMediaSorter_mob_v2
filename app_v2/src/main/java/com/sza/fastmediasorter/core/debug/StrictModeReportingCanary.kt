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

    // Main thread only: touched from the lifecycle callbacks, the idle handler and arm().
    private var armedPolicy: StrictMode.ThreadPolicy? = null
    private var armedPolicyText: String? = null
    private var lastProbeMs = NEVER_PROBED
    private var lastIdleCheckMs = NEVER_CHECKED

    /**
     * Call on the main thread with the policy just set.
     *
     * A same-process Binder call through a Play Services stub (the Cast framework's Dynamite module,
     * measured on API 35) leaves the caller's mask on the main thread with StrictMode's gather bit
     * (bit 31) added, and from then on every violation is collected for a Binder reply that never
     * comes. So the canary restores [policy] whenever the main thread's policy no longer matches it.
     */
    fun arm(application: Application, policy: StrictMode.ThreadPolicy) {
        armedPolicy = policy
        val text = StrictMode.getThreadPolicy().toString()
        armedPolicyText = text
        Timber.i("StrictMode reporting: armed, policy=$text")
        application.registerActivityLifecycleCallbacks(this)
        Looper.myQueue().addIdleHandler {
            val nowMs = SystemClock.uptimeMillis()
            if (isIdleCheckDue(nowMs, lastIdleCheckMs)) {
                lastIdleCheckMs = nowMs
                restorePolicyIfReplaced("main thread idle")
            }
            true
        }
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
        restorePolicyIfReplaced(activity.javaClass.simpleName)
        val nowMs = SystemClock.uptimeMillis()
        if (isProbeDue(nowMs, lastProbeMs)) {
            lastProbeMs = nowMs
            probe(activity.applicationContext, nowMs)
        }
    }

    private fun restorePolicyIfReplaced(where: String) {
        val restored = armedPolicy ?: return
        val policy = StrictMode.getThreadPolicy().toString()
        if (policy == armedPolicyText) return
        StrictMode.setThreadPolicy(restored)
        Timber.d("S4518: debug thread policy re-applied before $where")
        Timber.w("StrictMode reporting: policy changed before $where, $armedPolicyText -> $policy, re-applied")
    }

    private fun probe(context: Context, nowMs: Long) {
        if (!pendingSinceMs.compareAndSet(NOT_PENDING, nowMs)) return
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
        private const val NEVER_CHECKED = 0L
        private const val CANARY_FILE_NAME = ".strictmode-canary"
        private val DELIVERY_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5)
        private val PROBE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)

        // The idle handler runs between every burst of main-thread messages; reading the policy
        // allocates, so the check is throttled rather than run on every idle.
        private val IDLE_CHECK_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1)

        fun isCanaryViolation(violation: Throwable): Boolean =
            violation.stackTrace.any { frame -> frame.className == StrictModeReportingCanary::class.java.name }

        fun isProbeDue(nowMs: Long, lastProbeMs: Long): Boolean =
            lastProbeMs == NEVER_PROBED || nowMs - lastProbeMs >= PROBE_INTERVAL_MS

        fun isIdleCheckDue(nowMs: Long, lastCheckMs: Long): Boolean =
            lastCheckMs == NEVER_CHECKED || nowMs - lastCheckMs >= IDLE_CHECK_INTERVAL_MS
    }
}
