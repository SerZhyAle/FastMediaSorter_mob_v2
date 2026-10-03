package com.sza.fastmediasorter.testing

import android.os.Looper
import com.sza.fastmediasorter.FastMediaSorterApp
import kotlinx.coroutines.job
import org.robolectric.Shadows
import timber.log.Timber

/**
 * The application every Robolectric test in `app_v2` runs against, wired once in
 * `src/test/resources/robolectric.properties` rather than per class.
 *
 * S2750: on API 26..32 `ContextCompat.registerReceiver(.., RECEIVER_NOT_EXPORTED)` has no platform
 * flag to pass, so androidx.core emulates a non-exported receiver by registering it behind the
 * signature permission `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` and refuses to
 * proceed unless the app already holds it. The manifest declares and uses it, but Robolectric grants
 * no manifest permission on its own, so every test pinned below SDK 33 died in
 * `FastMediaSorterApp.onCreate` before its first assertion. Granting it here keeps the real startup
 * graph and the real receiver registration under test - the alternatives were to skip the watcher in
 * tests or to change how the app registers on device, and both hide what they claim to check.
 */
class RobolectricFastMediaSorterApp : FastMediaSorterApp() {

    override fun onCreate() {
        Shadows.shadowOf(this).grantPermissions(packageName + DYNAMIC_RECEIVER_PERMISSION_SUFFIX)
        super.onCreate()
    }

    /**
     * S4072: cancelling the scopes stops new emissions, but a collector already running on an IO
     * thread would still read the Context Robolectric is about to reset, and the next test's runTest
     * reports that throw as its own. Waiting closes that window. The main looper is idled while
     * waiting because some children finish their cancellation through a Main dispatch, and this
     * callback runs on the main thread - a plain blocking join would hold every test for the timeout.
     */
    override fun onTerminate() {
        super.onTerminate()
        val jobs = backgroundScopes.map { it.coroutineContext.job }
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + SCOPE_JOIN_TIMEOUT_NANOS
        while (jobs.any { !it.isCompleted } && System.nanoTime() < deadline) {
            mainLooper.idle()
            Thread.sleep(SCOPE_POLL_INTERVAL_MS)
        }
        if (jobs.any { !it.isCompleted }) {
            Timber.w("RobolectricFastMediaSorterApp: app scopes still busy at teardown")
        }
    }

    private companion object {
        const val DYNAMIC_RECEIVER_PERMISSION_SUFFIX = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        const val SCOPE_JOIN_TIMEOUT_NANOS = 2_000_000_000L
        const val SCOPE_POLL_INTERVAL_MS = 5L
    }
}
