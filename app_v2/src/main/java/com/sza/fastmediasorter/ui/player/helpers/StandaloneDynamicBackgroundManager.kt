package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.lifecycle.LifecycleCoroutineScope
import com.github.chrisbanes.photoview.PhotoView
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.DynamicBackgroundProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException

/**
 * S3702: the LETTERBOX-BARS frame and LETTERBOX-HALO behind a photo in the standalone player, the
 * same [DynamicBackgroundProcessor] the in-app player uses.
 *
 * Settings are read once per shown photo rather than collected: the standalone host has no settings
 * observer, and a change made while it is open lands on the next photo. A trimmed layout without
 * `ivDynamicBackground` simply shows no bars.
 */
class StandaloneDynamicBackgroundManager(
    private val lifecycleScope: LifecycleCoroutineScope,
    private val settingsRepository: SettingsRepository,
    background: ImageView?,
) {

    private var processor: DynamicBackgroundProcessor? = null
    private var shownModel: Any? = null

    init {
        bindBackground(background)
    }

    /** S1549: a re-inflated hierarchy gets a fresh processor on its own background view. */
    fun bindBackground(background: ImageView?) {
        processor?.clear()
        processor = background?.let {
            DynamicBackgroundProcessor(backgroundView = it, coroutineScope = lifecycleScope)
                .also { fresh -> fresh.adoptShownMediaKey(shownModel) }
        }
    }

    /** Called once the photo's drawable is decoded; [photoView] is the surface showing it. */
    fun onImageReady(drawable: Drawable, model: Any?, photoView: PhotoView) {
        val target = processor ?: return
        lifecycleScope.launch {
            val settings = try {
                settingsRepository.getSettings().first()
            } catch (e: IOException) {
                // Bars never block the image (LETTERBOX-BARS rule 10): no settings, no bars.
                Timber.w(e, "StandaloneDynamicBackgroundManager: settings unavailable, no bars")
                target.clear()
                return@launch
            }
            if (!settings.dynamicBackgroundExtension) {
                target.clear()
                return@launch
            }
            target.setHaloSettings(settings.letterboxHalo)
            // LETTERBOX-BARS host condition: no bars under a photo zoomed past fit.
            photoView.setOnMatrixChangeListener { _ ->
                target.setZoomed(photoView.scale > photoView.minimumScale + ZOOM_FIT_TOLERANCE)
            }
            shownModel = model
            target.process(drawable, photoView.width, photoView.height, mediaKey = model)
        }
    }

    /** LETTERBOX-BARS rule 9: anything but a shown photo carries no bars. */
    fun clear() {
        shownModel = null
        processor?.clear()
    }

    private companion object {
        // PhotoView settles a fit-reset a hair off minimumScale; this band keeps that from reading as zoom.
        const val ZOOM_FIT_TOLERANCE = 0.01f
    }
}
