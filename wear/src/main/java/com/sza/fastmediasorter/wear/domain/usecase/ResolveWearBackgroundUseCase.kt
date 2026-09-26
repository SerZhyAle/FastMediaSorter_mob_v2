package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.wear.data.repository.incomingFilesDirectory
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearBackground
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.repository.WearClockStyleRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * S2000: the single place that turns "which background did the owner choose" plus "is there a frame
 * on disk" into one answer.
 *
 * Strategic 3.3.8 fixes the fallback: a mode of [WearBackgroundMode.IMAGE] with no usable picture -
 * never chosen, never delivered, deleted since - draws the branded animation rather than nothing.
 * Keeping that decision here is what stops each screen from re-deriving it and disagreeing.
 *
 * S3707: [WearBackgroundMode.FOLLOW_PHONE] takes the mode matching the phone launcher wallpaper from
 * the last clock style, and a phone that never sent one reads as the branded animation. A launcher
 * photo or camera maps to IMAGE, so the frame the Wear companion delivered is what the watch draws.
 */
class ResolveWearBackgroundUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: WearPreferencesRepository,
    private val clockStyleRepository: WearClockStyleRepository
) {

    operator fun invoke(): Flow<WearBackground> = combine(
        preferencesRepository.backgroundMode,
        clockStyleRepository.style.map { it.launcherBackdrop }.distinctUntilChanged()
    ) { mode, launcherBackdrop ->
        if (mode == WearBackgroundMode.FOLLOW_PHONE) {
            Timber.d("S3707: watch backdrop follows the phone launcher wallpaper")
            launcherBackdrop ?: WearBackgroundMode.BRANDED_ANIMATION
        } else {
            mode
        }
    }
        // Deliberately not deduplicated by mode: a frame redelivered under the same IMAGE mode must be
        // re-read, and only a fresh store emission after the delivery carries that news.
        .map { mode -> resolve(mode) }

    private suspend fun resolve(mode: WearBackgroundMode): WearBackground = when (mode) {
        WearBackgroundMode.BRANDED_ANIMATION,
        WearBackgroundMode.FOLLOW_PHONE -> WearBackground.BrandedAnimation
        WearBackgroundMode.BRANDED_STILL -> WearBackground.BrandedStill
        WearBackgroundMode.IMAGE -> deliveredFrame() ?: WearBackground.BrandedAnimation
        WearBackgroundMode.NONE -> WearBackground.None
    }

    /**
     * A zero-byte file counts as absent: an interrupted delivery leaves the name in place, and
     * decoding it would fail later on the UI thread instead of falling back here.
     */
    private suspend fun deliveredFrame(): WearBackground.Image? = withContext(Dispatchers.IO) {
        val frame = File(incomingFilesDirectory(context), WearDataLayerPaths.BACKGROUND_IMAGE_FILE_NAME)
        if (frame.canRead() && frame.length() > 0L) WearBackground.Image(frame, frame.lastModified()) else null
    }
}
