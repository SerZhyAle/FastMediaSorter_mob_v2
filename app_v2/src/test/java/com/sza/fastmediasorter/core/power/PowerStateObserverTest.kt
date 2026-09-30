package com.sza.fastmediasorter.core.power

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.core.util.PowerPolicyDecision
import com.sza.fastmediasorter.core.util.PowerPolicyLevel
import com.sza.fastmediasorter.core.util.PowerPolicyReason
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.PowerSavingTrigger
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.concurrent.thread

private const val PRODUCER_ROUNDS = 200
private const val LOW_CHARGE = 10
private const val HIGH_CHARGE = 90
private const val FULL_SCALE = 100
private const val SETTLE_TIMEOUT_MS = 5_000L
private const val SETTLE_POLL_MS = 10L

/**
 * S2536 / S3276: the level and reason verdict test.
 *
 * S3730: Robolectric only for the two-producer test, which needs a real receiver registration and a
 * main looper to deliver the battery broadcasts on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class PowerStateObserverTest {

    private fun decisionFor(
        trigger: PowerSavingTrigger,
        chargePercent: Int? = 100,
        osPowerSaveMode: Boolean = false,
        animationsDisabled: Boolean = false,
        charging: Boolean = false
    ): PowerPolicyDecision = resolvePowerPolicyDecision(
        trigger = trigger,
        chargePercent = chargePercent,
        osPowerSaveMode = osPowerSaveMode,
        animationsDisabled = animationsDisabled,
        charging = charging
    )

    @Test
    fun `every threshold trigger fires at its own percentage and not one above it`() {
        val thresholdTriggers = PowerSavingTrigger.entries.filter { it.thresholdPercent != null }

        assertEquals(4, thresholdTriggers.size)
        for (trigger in thresholdTriggers) {
            val threshold = requireNotNull(trigger.thresholdPercent)

            assertEquals(
                "$trigger should be SAVING at its own threshold of $threshold",
                PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY),
                decisionFor(trigger, chargePercent = threshold)
            )
            assertEquals(
                "$trigger should be SAVING below $threshold",
                PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY),
                decisionFor(trigger, chargePercent = threshold - 1)
            )
            assertEquals(
                "$trigger should be NORMAL one percent above $threshold",
                PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
                decisionFor(trigger, chargePercent = threshold + 1)
            )
        }
    }

    @Test
    fun `ALWAYS saves at any charge and OFF never saves on charge alone`() {
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.USER_ALWAYS),
            decisionFor(PowerSavingTrigger.ALWAYS, chargePercent = 100)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.USER_ALWAYS),
            decisionFor(PowerSavingTrigger.ALWAYS, chargePercent = 1)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
            decisionFor(PowerSavingTrigger.OFF, chargePercent = 1)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
            decisionFor(PowerSavingTrigger.OFF, chargePercent = 0)
        )
    }

    @Test
    fun `the OS saver raises SAVING whatever the trigger and the charge`() {
        for (trigger in PowerSavingTrigger.entries) {
            val expectedReason = if (trigger == PowerSavingTrigger.ALWAYS) {
                PowerPolicyReason.USER_ALWAYS
            } else {
                PowerPolicyReason.SYSTEM_SAVER
            }
            assertEquals(
                "the OS power saver should win over $trigger at a full charge",
                PowerPolicyDecision(PowerPolicyLevel.SAVING, expectedReason),
                decisionFor(trigger, chargePercent = 100, osPowerSaveMode = true)
            )
        }
    }

    @Test
    fun `the animation switch produces REDUCED and never SAVING`() {
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH),
            decisionFor(PowerSavingTrigger.OFF, chargePercent = 100, animationsDisabled = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH),
            decisionFor(PowerSavingTrigger.BELOW_20, chargePercent = 100, animationsDisabled = true)
        )
    }

    @Test
    fun `saving outranks the animation switch when both apply`() {
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY),
            decisionFor(PowerSavingTrigger.BELOW_20, chargePercent = 5, animationsDisabled = true)
        )
    }

    @Test
    fun `an unreadable charge leaves a threshold trigger where it would be without the reading`() {
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
            decisionFor(PowerSavingTrigger.BELOW_30, chargePercent = null)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH),
            decisionFor(PowerSavingTrigger.BELOW_30, chargePercent = null, animationsDisabled = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.USER_ALWAYS),
            decisionFor(PowerSavingTrigger.ALWAYS, chargePercent = null)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.SYSTEM_SAVER),
            decisionFor(PowerSavingTrigger.BELOW_30, chargePercent = null, osPowerSaveMode = true)
        )
    }

    @Test
    fun `charging suppresses low battery threshold arm but not system or user always`() {
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
            decisionFor(PowerSavingTrigger.BELOW_20, chargePercent = 5, charging = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.REDUCED, PowerPolicyReason.ANIMATION_SWITCH),
            decisionFor(PowerSavingTrigger.BELOW_20, chargePercent = 5, animationsDisabled = true, charging = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.SYSTEM_SAVER),
            decisionFor(PowerSavingTrigger.BELOW_20, chargePercent = 5, osPowerSaveMode = true, charging = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.USER_ALWAYS),
            decisionFor(PowerSavingTrigger.ALWAYS, chargePercent = 100, charging = true)
        )
        assertEquals(
            PowerPolicyDecision(PowerPolicyLevel.NORMAL, PowerPolicyReason.NONE),
            decisionFor(PowerSavingTrigger.BELOW_30, chargePercent = null, charging = true)
        )
    }

    @Test
    fun `no battery level is claimed only after the platform has answered`() {
        assertEquals(false, resolveBatteryLevelUnavailable(batteryIntentSeen = false, chargePercent = null))
        assertEquals(true, resolveBatteryLevelUnavailable(batteryIntentSeen = true, chargePercent = null))
        assertEquals(false, resolveBatteryLevelUnavailable(batteryIntentSeen = true, chargePercent = 42))
    }

    @Test
    fun `an unknown stored name resolves to the default rather than throwing`() {
        assertEquals(PowerSavingTrigger.BELOW_20, PowerSavingTrigger.fromNameOrDefault(null))
        assertEquals(PowerSavingTrigger.BELOW_20, PowerSavingTrigger.fromNameOrDefault("BELOW_42"))
        assertEquals(PowerSavingTrigger.ALWAYS, PowerSavingTrigger.fromNameOrDefault("ALWAYS"))
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `settings and battery producers racing still settle on the verdict of the final inputs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = MutableStateFlow(AppSettings(powerSavingTrigger = PowerSavingTrigger.OFF))
        val repository = mockk<SettingsRepository> { every { getSettings() } returns settings }
        // Unconfined resumes the settings collector inline on the thread that sets the flow, so its
        // recompute() runs on the producer thread while the receiver runs on the main looper.
        val scope = TestScope(UnconfinedTestDispatcher())
        try {
            // The platform keeps ACTION_BATTERY_CHANGED sticky; this is the only way to seed it here.
            @Suppress("DEPRECATION")
            context.sendStickyBroadcast(batteryIntent(HIGH_CHARGE))
            val observer = PowerStateObserver(context, scope, repository)
            observer.onActivityStarted(mockk<Activity>(relaxed = true))

            val settingsProducer = thread {
                repeat(PRODUCER_ROUNDS) { round ->
                    val trigger = if (round % 2 == 0) PowerSavingTrigger.OFF else PowerSavingTrigger.BELOW_20
                    settings.value = AppSettings(powerSavingTrigger = trigger)
                }
            }
            repeat(PRODUCER_ROUNDS) { round ->
                context.sendBroadcast(batteryIntent(if (round % 2 == 0) HIGH_CHARGE else LOW_CHARGE))
                shadowOf(Looper.getMainLooper()).idle()
            }
            settingsProducer.join()

            val expected = PowerPolicyDecision(PowerPolicyLevel.SAVING, PowerPolicyReason.LOW_BATTERY)
            val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
            while (observer.decision.value != expected && System.currentTimeMillis() < deadline) {
                Thread.sleep(SETTLE_POLL_MS)
            }
            assertEquals(expected, observer.decision.value)
        } finally {
            scope.cancel()
        }
    }

    private fun batteryIntent(percent: Int): Intent = Intent(Intent.ACTION_BATTERY_CHANGED)
        .setPackage(ApplicationProvider.getApplicationContext<Context>().packageName)
        .putExtra(BatteryManager.EXTRA_LEVEL, percent)
        .putExtra(BatteryManager.EXTRA_SCALE, FULL_SCALE)
        .putExtra(BatteryManager.EXTRA_PLUGGED, 0)
}
