package com.sza.fastmediasorter.core.memory

import com.sza.fastmediasorter.core.util.MemoryTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryProfileCoordinatorImplTest {

    @Test
    fun `LOW tier gets 8 MB startup cache`() {
        assertEquals(8, MemoryProfileCoordinatorImpl.defaultStartupGlideCacheMb(MemoryTier.LOW))
    }

    @Test
    fun `STANDARD tier audio playback enables RGB565`() {
        val profile = MemoryProfileCoordinatorImpl.computeProfile(
            startupTier = MemoryTier.STANDARD,
            startupGlideCacheMb = 16,
            scenario = MemoryScenario.AUDIO_PLAYBACK,
            flavorOverrides = listOf(DefaultMemoryProfileFlavorOverride()),
        )

        assertEquals(MemoryScenario.AUDIO_PLAYBACK, profile.scenario)
        assertEquals(MemoryTier.STANDARD, profile.tier)
        assertEquals(16, profile.startupGlideMemoryCacheMb)
        assertTrue(profile.useRgb565)
    }

    @Test
    fun `HIGH tier idle keeps RGB8888 by default`() {
        val profile = MemoryProfileCoordinatorImpl.computeProfile(
            startupTier = MemoryTier.HIGH,
            startupGlideCacheMb = 24,
            scenario = MemoryScenario.IDLE,
            flavorOverrides = listOf(DefaultMemoryProfileFlavorOverride()),
        )

        assertFalse(profile.useRgb565)
    }

    @Test
    fun `flavor override can force RGB8888 for VR flows`() {
        val vrOverride = object : MemoryProfileFlavorOverride {
            override fun apply(profile: MemoryProfile): MemoryProfile {
                return profile.copy(useRgb565 = false)
            }
        }

        val profile = MemoryProfileCoordinatorImpl.computeProfile(
            startupTier = MemoryTier.LOW,
            startupGlideCacheMb = 8,
            scenario = MemoryScenario.AUDIO_PLAYBACK,
            flavorOverrides = listOf(DefaultMemoryProfileFlavorOverride(), vrOverride),
        )

        assertFalse(profile.useRgb565)
    }
}