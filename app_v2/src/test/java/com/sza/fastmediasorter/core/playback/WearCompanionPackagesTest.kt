package com.sza.fastmediasorter.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2810 / S2941: the Wear OS companion bridge is recognised by exact package name (Google and
 * Samsung companion apps) and by the Samsung per-model plugin prefix. A S2925 device pass on a
 * Galaxy Watch7 connected through `com.samsung.wearable.watch7plugin`, which the exact list missed.
 */
class WearCompanionPackagesTest {

    @Test
    fun `google companion app is a wear companion`() {
        assertTrue(WearCompanionPackages.isWearCompanion("com.google.android.wearable.app"))
    }

    @Test
    fun `samsung companion app is a wear companion`() {
        assertTrue(WearCompanionPackages.isWearCompanion("com.samsung.android.wearable.app"))
    }

    @Test
    fun `samsung per-model plugin package is a wear companion`() {
        assertTrue(WearCompanionPackages.isWearCompanion("com.samsung.wearable.watch7plugin"))
        assertTrue(WearCompanionPackages.isWearCompanion("com.samsung.wearable.watch5plugin"))
        assertTrue(WearCompanionPackages.isWearCompanion("com.samsung.wearable.watch4plugin"))
    }

    @Test
    fun `app own package is not a wear companion`() {
        assertFalse(WearCompanionPackages.isWearCompanion("com.sza.fastmediasorter"))
    }

    @Test
    fun `system android package is not a wear companion`() {
        assertFalse(WearCompanionPackages.isWearCompanion("android"))
    }

    @Test
    fun `unrelated samsung package is not a wear companion`() {
        assertFalse(WearCompanionPackages.isWearCompanion("com.samsung.android.wearable.app.something"))
        assertFalse(WearCompanionPackages.isWearCompanion("com.samsung.wearable"))
    }

    @Test
    fun `null package name is not a wear companion`() {
        assertFalse(WearCompanionPackages.isWearCompanion(null))
    }
}
