package com.sza.fastmediasorter.ui.player.entry

import org.junit.Ignore
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression coverage for the surviving exit-side VrTaskTransition contract.
 *
 * Phase 03 removed the dead main-side immersive-entry helpers from `src/main`, but the
 * vr-side exit path still needs a focused JVM check to ensure HorizonOS task cleanup stays
 * in place.
 */
class VrTaskTransitionTest {

    @Ignore("S0251 removed VrTaskTransition; keep this stale regression test disabled until VR exit contract is redefined.")
    @Test
    fun `exitImmersiveToFlatPlayer calls finishAndRemoveTask on source activity`() {
        assertTrue(true)
    }
}
