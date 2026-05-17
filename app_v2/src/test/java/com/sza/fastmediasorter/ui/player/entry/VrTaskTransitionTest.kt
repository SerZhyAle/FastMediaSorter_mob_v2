package com.sza.fastmediasorter.ui.player.entry

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * JVM regression coverage for the surviving exit-side VrTaskTransition contract.
 *
 * Phase 03 removed the dead main-side immersive-entry helpers from `src/main`, but the
 * vr-side exit path still needs a focused JVM check to ensure HorizonOS task cleanup stays
 * in place.
 */
class VrTaskTransitionTest {

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
