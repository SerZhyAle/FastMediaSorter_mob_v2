package com.sza.fastmediasorter.selftest

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * S3741: every entry of [SubProgramCatalog] opens on this device without taking the app down.
 *
 * Driven by the catalog itself, so a new sub-program is covered with no edit here. The route's own
 * intent builder is used - the same one the programs menu, the panel and the widgets call. An entry
 * whose hardware feature is missing is skipped with that reason rather than passed. A trampoline that
 * finishes at once, or hands over to the system camera or a consent dialog, still counts: the claim is
 * "opens without a crash", and a crash kills the instrumentation process and fails the run.
 *
 * No ActivityScenario and no waitForIdleSync here: SOS strobes its screen and several programs animate
 * without end, so the main looper never goes idle and both of those wait forever (the first phone run
 * hung on `sos` for eleven minutes). The lifecycle monitor is polled against a deadline instead.
 */
@RunWith(Parameterized::class)
class SubProgramLaunchSmokeTest(private val routeKey: String) {

    @get:Rule
    val baseline = SelfTestBaselineRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    @Test
    fun opensWithoutCrash() {
        requiredFeature[routeKey]?.let { feature ->
            assumeTrue("no $feature on this device", context.packageManager.hasSystemFeature(feature))
        }
        val route = InternalRouteCatalog.byKey(routeKey)
        assertNotNull("catalog entry $routeKey has no route", route)
        launchAndFinish(requireNotNull(route).intent(context))
        if (routeKey in toggles) {
            // A toggle's second launch is the stop - without it the recording outlives the test.
            launchAndFinish(route.intent(context))
        }
    }

    @After
    fun leaveForeignWindows() {
        // A consent dialog or the system camera may sit on top of the next entry otherwise.
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    private fun launchAndFinish(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // Not reaching RESUMED is allowed: a trampoline finishes itself or hands over to another app.
        waitUntil(SETTLE_TIMEOUT_MS) { ownActivities(Stage.RESUMED).isNotEmpty() }
        // Finished on every poll, not once: a trampoline such as camera_photos opens its real screen a
        // moment after the first one resumed, and that second activity would otherwise outlive the test
        // and cover the next test's window.
        val gone = waitUntil(SETTLE_TIMEOUT_MS) {
            val live = ownActivities(*LIVE_STAGES)
            instrumentation.runOnMainSync { live.filterNot(Activity::isFinishing).forEach(Activity::finish) }
            live.isEmpty()
        }
        assertTrue("$routeKey left an activity alive after finish(): ${survivors()}", gone)
    }

    private fun survivors(): String = LIVE_STAGES.flatMap { stage ->
        ownActivities(stage).map { "${it.javaClass.simpleName}@$stage finishing=${it.isFinishing}" }
    }.joinToString()

    private fun ownActivities(vararg stages: Stage): List<Activity> {
        val found = mutableListOf<Activity>()
        val collect = {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            stages.forEach { stage -> found += monitor.getActivitiesInStage(stage) }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) collect() else instrumentation.runOnMainSync(collect)
        return found.filter { it.packageName == context.packageName }
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(POLL_MS)
        }
        return condition()
    }

    companion object {
        private const val SETTLE_TIMEOUT_MS = 10_000L
        private const val POLL_MS = 100L
        private val LIVE_STAGES = arrayOf(
            Stage.PRE_ON_CREATE,
            Stage.CREATED,
            Stage.STARTED,
            Stage.RESUMED,
            Stage.PAUSED,
            Stage.STOPPED,
        )

        private val requiredFeature = mapOf(
            InternalRouteCatalog.KEY_PHYSICAL_FLASHLIGHT to PackageManager.FEATURE_CAMERA_FLASH,
            InternalRouteCatalog.KEY_MIRROR to PackageManager.FEATURE_CAMERA_FRONT,
        )

        private val toggles = setOf(InternalRouteCatalog.KEY_QUICK_VOICE)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun keys(): List<String> = SubProgramCatalog.all().map { it.routeKey }
    }
}
