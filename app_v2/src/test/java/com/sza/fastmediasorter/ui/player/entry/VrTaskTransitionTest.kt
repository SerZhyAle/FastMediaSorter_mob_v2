package com.sza.fastmediasorter.ui.player.entry

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import com.sza.fastmediasorter.BuildConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * JVM unit tests for [VrTaskTransition.shouldEnterImmersiveTask]. Runs against the
 * current flavor's BuildConfig — on non-VR flavors SUPPORT_VR_PLAYER is false so every
 * call returns false; on the vr flavor only intents whose target equals
 * PLAYER_ACTIVITY_CLASS return true. The tests express that invariant and therefore
 * hold under both test targets (testStandardDebugUnitTest and testVrDebugUnitTest).
 *
 * Both Intent and ComponentName are Android framework classes that JUnit stubs to
 * null-returning defaults without Robolectric; MockK fills in the data so the logic
 * can be exercised purely on the JVM without a framework runtime.
 *
 * enterImmersive / exitImmersiveToPanel are thin wrappers over framework APIs and are
 * verified by the manual test plan in PLAN/spec_vr-hybrid-app-model.md §9.2.
 * exitImmersiveToFlatPlayer has a dedicated JVM test (S0132 P03.1) verifying that
 * finishAndRemoveTask() is invoked — the missing call was the root cause of the task
 * switcher cloning regression tracked by archived ticket S0038.
 */
class VrTaskTransitionTest {

    private fun intentWithClassName(className: String?): Intent {
        val intent = mockk<Intent>()
        val component = if (className == null) {
            null
        } else {
            mockk<ComponentName>().also { every { it.className } returns className }
        }
        every { intent.component } returns component
        return intent
    }

    @Test
    fun `returns false when intent has no component`() {
        assertFalse(VrTaskTransition.shouldEnterImmersiveTask(intentWithClassName(null)))
    }

    @Test
    fun `returns false when intent target class does not equal PLAYER_ACTIVITY_CLASS`() {
        assertFalse(
            VrTaskTransition.shouldEnterImmersiveTask(intentWithClassName("com.example.Unrelated")),
        )
    }

    @Test
    fun `matching target returns SUPPORT_VR_PLAYER flag value`() {
        // On vr/vrUnlicensed PLAYER_ACTIVITY_CLASS resolves to VrPlayerActivity; on every
        // other flavor it resolves to PlayerActivity. Either way, the same check is applied.
        assertEquals(
            BuildConfig.SUPPORT_VR_PLAYER,
            VrTaskTransition.shouldEnterImmersiveTask(intentWithClassName(BuildConfig.PLAYER_ACTIVITY_CLASS)),
        )
    }

    @Test
    fun `explicit standard PlayerActivity target is never immersive unless it is the flavor's target`() {
        // A BrowseEventHandler.createStandardPlayerIntent(...) in the VR flavor constructs
        // Intent(ctx, PlayerActivity::class.java) — which differs from
        // BuildConfig.PLAYER_ACTIVITY_CLASS on the vr flavor (VrPlayerActivity there).
        // On non-VR flavors the two class names coincide, but SUPPORT_VR_PLAYER=false still
        // gates the result to false.
        val standardTarget = "com.sza.fastmediasorter.ui.player.PlayerActivity"
        val expected = BuildConfig.SUPPORT_VR_PLAYER &&
            standardTarget == BuildConfig.PLAYER_ACTIVITY_CLASS
        assertEquals(
            expected,
            VrTaskTransition.shouldEnterImmersiveTask(intentWithClassName(standardTarget)),
        )
    }

    @Test
    fun `exitImmersiveToFlatPlayer calls finishAndRemoveTask on source activity`() {
        // S0132 P03.1 (ex-S0038): regression guard — exitImmersiveToFlatPlayer must call
        // finishAndRemoveTask() on the source VR activity so HorizonOS removes the task
        // record from its task switcher. Without this call, repeated enter/exit cycles
        // accumulate one window per cycle in the switcher.
        val activity = mockk<Activity>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        val component = mockk<ComponentName>().also {
            every { it.className } returns "com.sza.fastmediasorter.ui.player.PlayerActivity"
        }
        every { intent.component } returns component
        every { intent.getStringExtra(any()) } returns "/test/file.mp4"
        every { intent.flags } returns 0x30020000

        VrTaskTransition.exitImmersiveToFlatPlayer(activity, intent)

        verify(exactly = 1) { activity.startActivity(intent) }
        verify(exactly = 1) { activity.finishAndRemoveTask() }
    }
}
