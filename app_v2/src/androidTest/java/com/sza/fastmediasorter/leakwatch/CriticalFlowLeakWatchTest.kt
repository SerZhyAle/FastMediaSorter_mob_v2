package com.sza.fastmediasorter.leakwatch

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sza.fastmediasorter.ui.main.MainActivity
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.statistics.StatisticsActivity
import com.sza.fastmediasorter.ui.systeminfo.SystemInfoActivity
import leakcanary.LeakAssertions
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * The nightly leak-watch contour (S3371 phase 05): walk every critical flow declared in
 * `scripts/quality/leak-watch-flows.txt` and require the heap to hold no retained instance
 * afterwards.
 *
 * The declaration file is the human-readable half of this class; each row there has exactly one
 * test method here, and the flow id is passed to the leak assertion so the nightly report names
 * the flow rather than a stack of framework frames.
 *
 * The walk is a cold launch, a stop/start bounce and a destroy, because that is where a leak
 * shows: an `onStart` registration whose `onStop` counterpart was forgotten survives the bounce
 * and is still held when the Activity is destroyed.
 *
 * Method order is fixed and the canary sorts first on purpose - it is the only test whose failure
 * means the harness is broken rather than the app, and a nightly reader who sees the flows fail
 * after it should read the canary line first.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CriticalFlowLeakWatchTest {

    /**
     * Prove the harness before trusting its silence. Seeds a retained object, requires the leak
     * assertion to fail naming [SeededLeakScenario.MARKER], then releases it and requires the
     * very next assertion to pass - which is also the proof that the canary cannot contaminate
     * the flow verdicts below.
     */
    @Test
    fun aSeededLeakIsDetectedAndThenCleared() {
        check(!SeededLeakScenario.isSeeded) { "the canary was already seeded before the test ran" }

        SeededLeakScenario.seed()
        var detected: AssertionError? = null
        try {
            LeakAssertions.assertNoLeaks(SEEDED_TAG)
        } catch (expected: AssertionError) {
            detected = expected
        }

        SeededLeakScenario.release()

        val failure = detected
            ?: error(
                "the leak-watch harness reported a clean heap while ${SeededLeakScenario.MARKER} " +
                    "was retained - LeakCanary is not active in this APK, so a green nightly run " +
                    "proves nothing"
            )
        val message = failure.message.orEmpty()
        check(message.contains(SeededLeakScenario.MARKER)) {
            "the detection did not name ${SeededLeakScenario.MARKER}, so a real leak could not be " +
                "told apart from the canary in the nightly report - reported: $message"
        }

        LeakAssertions.assertNoLeaks(SEEDED_CLEARED_TAG)
    }

    @Test
    fun mainListFlowRetainsNothing() = walkFlow("main-list", MainActivity::class.java)

    @Test
    fun networkMonitorFlowRetainsNothing() =
        walkFlow("network-monitor", NetworkMonitorActivity::class.java)

    @Test
    fun settingsFlowRetainsNothing() = walkFlow("settings", SettingsActivity::class.java)

    @Test
    fun statisticsFlowRetainsNothing() = walkFlow("statistics", StatisticsActivity::class.java)

    @Test
    fun systemInfoFlowRetainsNothing() = walkFlow("system-info", SystemInfoActivity::class.java)

    private fun <A : Activity> walkFlow(flowId: String, activityClass: Class<A>) {
        // launch() already returns at RESUMED. The stop/start bounce below is where a leak shows:
        // an onStart registration whose onStop counterpart was forgotten survives the stop and is
        // still held when the Activity is destroyed.
        //
        // Every move is guarded, because an entry screen may finish itself on the way - measured
        // 2026-09-22 on the test phone, MainActivity is already DESTROYED when launch() returns,
        // since it routes on rather than staying on the back stack, and a destroyed scenario
        // refuses every further move. A flow that ended early is still a walked flow: the destroy
        // is what the leak assertion needs, so it must not be reported as a broken test.
        ActivityScenario.launch(activityClass).use { scenario ->
            if (scenario.state != Lifecycle.State.DESTROYED) {
                scenario.moveToState(Lifecycle.State.CREATED)
            }
            if (scenario.state != Lifecycle.State.DESTROYED) {
                scenario.moveToState(Lifecycle.State.RESUMED)
            }
        }
        LeakAssertions.assertNoLeaks(flowId)
    }

    private companion object {
        const val SEEDED_TAG = "seeded-leak-canary"
        const val SEEDED_CLEARED_TAG = "seeded-leak-canary-cleared"
    }
}
