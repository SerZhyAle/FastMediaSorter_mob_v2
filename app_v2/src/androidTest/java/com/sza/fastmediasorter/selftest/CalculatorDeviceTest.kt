package com.sza.fastmediasorter.selftest

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.KeepScreenAwakeManager
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.calculator.CalculatorActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * S3741: the calculator sub-program computes through its own keypad on a real device. The evaluator
 * has JVM tests; this one covers the wiring the JVM cannot see - keys, engine and display together.
 *
 * The calculator ships disabled and shows its fallback until the setting is on, so the test switches
 * it on for its own duration and puts the stored value back afterwards.
 */
@RunWith(JUnit4::class)
class CalculatorDeviceTest {

    @get:Rule
    val baseline = SelfTestBaselineRule()

    @Test
    fun keypadAdditionReachesTheDisplay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, CalculatorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<CalculatorActivity>(intent).use { scenario ->
            lateinit var settings: SettingsRepository
            scenario.onActivity { settings = settingsRepositoryOf(it) }
            val wasEnabled = runBlocking { settings.getSettings().first().enableCalculator }
            runBlocking { settings.updateSettings { it.copy(enableCalculator = true) } }
            try {
                assertTrue("keypad not shown after enabling the calculator", awaitKeypad(scenario))
                listOf(
                    R.id.btnCalculatorClear,
                    R.id.btnCalculatorSeven,
                    R.id.btnCalculatorAdd,
                    R.id.btnCalculatorFive,
                    R.id.btnCalculatorEquals,
                ).forEach { onView(withId(it)).perform(click()) }

                var display = ""
                scenario.onActivity { display = it.findViewById<TextView>(R.id.calculatorDisplay).text.toString() }
                assertTrue("display after 7 + 5 = reads '$display'", display.filter(Char::isDigit) == "12")
            } finally {
                runBlocking { settings.updateSettings { it.copy(enableCalculator = wasEnabled) } }
            }
        }
    }

    private fun awaitKeypad(scenario: ActivityScenario<CalculatorActivity>): Boolean {
        val deadline = SystemClock.uptimeMillis() + KEYPAD_TIMEOUT_MS
        var shown = false
        while (!shown && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { shown = it.findViewById<View>(R.id.calculatorGrid)?.isShown == true }
            if (!shown) SystemClock.sleep(POLL_MS)
        }
        return shown
    }

    /**
     * The default pass runs on the real application, whose Hilt graph an androidTest entry point cannot
     * join, and a second DataStore on the settings file is refused in-process. The activity's own
     * injected repository is the one instance both sides may share.
     */
    private fun settingsRepositoryOf(activity: CalculatorActivity): SettingsRepository {
        val field = KeepScreenAwakeManager::class.java.getDeclaredField("settingsRepository")
        field.isAccessible = true
        return field.get(activity.keepScreenAwakeManager) as SettingsRepository
    }

    private companion object {
        const val KEYPAD_TIMEOUT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
