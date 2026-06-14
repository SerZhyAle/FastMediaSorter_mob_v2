package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Resolves the looping decoration videos for the audio player from the delivered Set C payload under
 * `filesDir/delivery/AUDIO_VISUALIZATIONS/` (S0386 Phase 07). The videos are no longer bundled in any
 * flavor's base (Phase 05 de-bundle), so this is the sole source: when the set is not installed the
 * player simply shows no decoration video.
 */
@Singleton
class DeliveredAudioVisualizationSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityRepository: DeliverableCapabilityRepository
) {
    private val bgFileNames = listOf(
        "anim_audio_bg_1.mp4",
        "anim_audio_bg_2.mp4",
        "anim_audio_bg_3.mp4",
        "anim_audio_bg_4.mp4",
        "anim_audio_bg_5.mp4",
        "anim_audio_bg_6.mp4",
        "anim_audio_bg_7.mp4",
        "anim_audio_bg_8.mp4",
        "anim_audio_bg_9.mp4",
        "anim_audio_bg_10.mp4",
        "anim_audio_bg_11.mp4"
    )

    fun isInstalled(): Boolean =
        capabilityRepository.isInstalledBlocking(DeliverableSet.AUDIO_VISUALIZATIONS)

    fun getRandomBackgroundFile(): File? {
        if (!isInstalled()) return null
        val dir = File(context.filesDir, "delivery/${DeliverableSet.AUDIO_VISUALIZATIONS.name}")
        if (!dir.isDirectory) return null
        val existing = bgFileNames.map { File(dir, it) }.filter { it.isFile }
        if (existing.isEmpty()) return null
        return existing[Random.nextInt(existing.size)]
    }
}
