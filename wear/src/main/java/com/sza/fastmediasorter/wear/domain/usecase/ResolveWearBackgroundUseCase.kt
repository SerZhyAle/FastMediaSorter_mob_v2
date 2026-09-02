package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.wear.data.repository.incomingFilesDirectory
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearBackground
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
 */
class ResolveWearBackgroundUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: WearPreferencesRepository
) {

    operator fun invoke(): Flow<WearBackground> = preferencesRepository.backgroundMode
        .map { mode -> resolve(mode) }

    private suspend fun resolve(mode: WearBackgroundMode): WearBackground {
        val resolved = when (mode) {
            WearBackgroundMode.BRANDED_ANIMATION -> WearBackground.BrandedAnimation
            WearBackgroundMode.IMAGE -> deliveredFrame() ?: WearBackground.BrandedAnimation
        }
        Timber.d("S2000: watch background mode=%s resolved=%s", mode, resolved::class.simpleName)
        return resolved
    }

    /**
     * A zero-byte file counts as absent: an interrupted delivery leaves the name in place, and
     * decoding it would fail later on the UI thread instead of falling back here.
     */
    private suspend fun deliveredFrame(): WearBackground.Image? = withContext(Dispatchers.IO) {
        val frame = File(incomingFilesDirectory(context), WearDataLayerPaths.BACKGROUND_IMAGE_FILE_NAME)
        if (frame.canRead() && frame.length() > 0L) WearBackground.Image(frame) else null
    }
}
