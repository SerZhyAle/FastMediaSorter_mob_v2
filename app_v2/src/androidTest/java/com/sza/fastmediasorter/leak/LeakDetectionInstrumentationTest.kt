package com.sza.fastmediasorter.leak

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import leakcanary.AppWatcher
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LeakDetectionInstrumentationTest {

    @get:Rule
    val rule = DetectLeaksAfterTestSuccess()

    @Test
    fun testNoLeaksNormally() {
        // This test runs normally and passes, verifying that no leaks are present
        // in a simple test scenario and that DetectLeaksAfterTestSuccess is active.
    }

    @Test
    fun testLeakCanaryInstrumentationFailsOnLeaks() {
        val arguments = InstrumentationRegistry.getArguments()
        val runFailureTest = arguments.getString("runLeakFailureTest", "false") == "true"
        if (!runFailureTest) return

        val leakedObject = Any()
        companionObjectLeakedRef = leakedObject
        AppWatcher.objectWatcher.watch(leakedObject, "Intentionally leaked test object")
    }

    companion object {
        private var companionObjectLeakedRef: Any? = null
    }
}
