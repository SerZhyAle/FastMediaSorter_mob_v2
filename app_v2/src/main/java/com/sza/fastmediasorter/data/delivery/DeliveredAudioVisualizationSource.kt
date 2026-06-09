package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Resolves paths for looping decoration videos under `filesDir/delivery/AUDIO_VISUALIZATIONS/`
 * (S0386 Phase 07).
 */
@Singleton
class DeliveredAudioVisualizationSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityRepository: DeliverableCapabilityRepository,
    private val bundledSets: BundledDeliverableSets
) {
    private data class BackgroundAsset(
        val fileName: String,
        val rawResId: Int
    )

    private val bgFiles = listOf(
        BackgroundAsset("anim_audio_bg_1.mp4", R.raw.anim_audio_bg_1),
        BackgroundAsset("anim_audio_bg_2.mp4", R.raw.anim_audio_bg_2),
        BackgroundAsset("anim_audio_bg_3.mp4", R.raw.anim_audio_bg_3),
        BackgroundAsset("anim_audio_bg_4.mp4", R.raw.anim_audio_bg_4),
        BackgroundAsset("anim_audio_bg_5.mp4", R.raw.anim_audio_bg_5)
    )

    fun isInstalled(): Boolean {
        return capabilityRepository.isInstalledBlocking(DeliverableSet.AUDIO_VISUALIZATIONS)
    }

    fun getRandomBackgroundFile(): File? {
        if (!isInstalled()) return null
        val dir = File(context.filesDir, "delivery/${DeliverableSet.AUDIO_VISUALIZATIONS.name}")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        
        var existingFiles = bgFiles.map { File(dir, it.fileName) }.filter { it.isFile }
        if (existingFiles.isEmpty() && bundledSets.contains(DeliverableSet.AUDIO_VISUALIZATIONS)) {
            materializeBundledVideos(dir)
            existingFiles = bgFiles.map { File(dir, it.fileName) }.filter { it.isFile }
        }

        if (existingFiles.isEmpty()) return null
        return existingFiles[Random.nextInt(existingFiles.size)]
    }

    private fun materializeBundledVideos(targetDir: File) {
        bgFiles.forEach { asset ->
            val target = File(targetDir, asset.fileName)
            if (target.isFile) {
                return@forEach
            }

            runCatching {
                context.resources.openRawResource(asset.rawResId).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }.onFailure { error ->
                Timber.w(error, "DeliveredAudioVisualizationSource: failed to materialize bundled asset %s", asset.fileName)
            }
        }
    }
}
