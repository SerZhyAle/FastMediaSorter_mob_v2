package com.sza.fastmediasorter.core.memory

import android.content.Context
import com.sza.fastmediasorter.core.util.MemoryTier
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryProfileCoordinatorImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val flavorOverrides: Set<@JvmSuppressWildcards MemoryProfileFlavorOverride>,
) : MemoryProfileCoordinator {

    private val startupTier = MemoryTier.detect(context)
    private val startupGlideCacheMb = defaultStartupGlideCacheMb(startupTier)

    // Glide resolves its process-wide memory cache once at startup, so runtime transitions
    // only update scenario defaults consumed by later request-time logic.
    @Volatile
    private var active: MemoryProfile = compute(MemoryScenario.IDLE)

    override fun enter(scenario: MemoryScenario): MemoryProfile {
        val updated = compute(scenario)
        active = updated
        Timber.i(
            "MEM_PROFILE | scenario=%s | tier=%s | startupGlideMemMb=%d | rgb565=%s",
            updated.scenario.name,
            updated.tier.name,
            updated.startupGlideMemoryCacheMb,
            updated.useRgb565,
        )
        return updated
    }

    override fun current(): MemoryProfile = active

    override fun startupGlideMemoryCacheBytes(): Long = startupGlideCacheMb.toLong() * BYTES_PER_MB

    private fun compute(scenario: MemoryScenario): MemoryProfile {
        return computeProfile(
            startupTier = startupTier,
            startupGlideCacheMb = startupGlideCacheMb,
            scenario = scenario,
            flavorOverrides = flavorOverrides,
        )
    }

    companion object {
        internal const val BYTES_PER_MB: Long = 1024L * 1024L

        internal fun defaultStartupGlideCacheMb(tier: MemoryTier): Int = when (tier) {
            MemoryTier.LOW -> 8
            MemoryTier.STANDARD -> 16
            MemoryTier.HIGH -> 24
        }

        internal fun computeProfile(
            startupTier: MemoryTier,
            startupGlideCacheMb: Int,
            scenario: MemoryScenario,
            flavorOverrides: Iterable<MemoryProfileFlavorOverride>,
        ): MemoryProfile {
            val baseProfile = MemoryProfile(
                scenario = scenario,
                tier = startupTier,
                startupGlideMemoryCacheMb = startupGlideCacheMb,
                useRgb565 = startupTier == MemoryTier.LOW || scenario == MemoryScenario.AUDIO_PLAYBACK,
            )
            return flavorOverrides.fold(baseProfile) { profile, override ->
                override.apply(profile)
            }
        }
    }
}