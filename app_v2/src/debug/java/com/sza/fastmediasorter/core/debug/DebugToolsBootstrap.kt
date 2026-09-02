package com.sza.fastmediasorter.core.debug

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sza.fastmediasorter.core.logging.LoggingHelper
import com.sza.fastmediasorter.ui.debug.CrashActivity
import com.sza.fastmediasorter.ui.debug.DebugActivity
import leakcanary.LeakCanary
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object DebugToolsBootstrap {

    private val installed = AtomicBoolean(false)
    private val crashing = AtomicBoolean(false)

    @JvmStatic
    fun install(application: Application) {
        if (!installed.compareAndSet(false, true)) return

        applyLeakCanarySetting(application)
        DebugNotificationCenter.register(application)
        Timber.plant(UiNotificationTree())

        // S2296: Robolectric boots this real debug Application inside the Gradle test worker, so the
        // handler below - which ends in exitProcess(10) - is installed process-wide in that JVM and
        // kills the whole worker on the first uncaught throwable from any thread. Gradle then reports
        // `tests="1" skipped="1"` with no failure, so the run proves nothing and every gate resting on
        // a Robolectric test answers CANNOT-VERIFY. The on-device crash dialog this handler exists for
        // cannot be shown in a JVM test, so it is not installed there.
        if (isUnderRobolectric()) return

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (crashing.compareAndSet(false, true)) {
                runCatching {
                    // Persist before showing the debug UI because this handler intentionally
                    // bypasses the system crash chain to keep the in-app crash dialog alive.
                    LoggingHelper.persistFatalCrash(thread, throwable)
                    val report = CrashReportFormatter.buildReport(thread, throwable)
                    val intent = CrashActivity.createIntent(application, report)
                    application.startActivity(intent)
                }
            }

            runCatching {
                Thread.sleep(350)
            }

            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
    }

    // Robolectric's documented marker for its own runtime; no other build reports this fingerprint.
    private fun isUnderRobolectric(): Boolean = "robolectric".equals(Build.FINGERPRINT, ignoreCase = true)

    @JvmStatic
    fun onCoroutineException(throwable: Throwable) {
        val message = throwable.message ?: throwable.javaClass.simpleName
        DebugNotificationCenter.showWarning("Coroutine: $message", throwable)
    }

    @JvmStatic
    fun createDebugMenuIntent(context: Context): Intent {
        return Intent(context, DebugActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    @JvmStatic
    fun applyLeakCanarySetting(context: Context) {
        val enabled = DebugFeatureFlags.isLeakCanaryEnabled(context)
        if (enabled) {
            LeakCanary.config = LeakCanary.config.copy(dumpHeap = true)
            Timber.i("LeakCanary enabled in debug build")
        } else {
            LeakCanary.config = LeakCanary.config.copy(dumpHeap = false)
            Timber.i("LeakCanary disabled in debug build")
        }
    }
}