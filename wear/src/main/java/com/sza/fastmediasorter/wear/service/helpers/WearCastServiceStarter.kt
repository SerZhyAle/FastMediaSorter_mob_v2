package com.sza.fastmediasorter.wear.service.helpers

import android.content.Context
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.domain.usecase.ClassifyWearStreamMediaKindUseCase
import com.sza.fastmediasorter.wear.service.WearPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the playback service for an accepted cast (S4339): the one Android-facing step of a cast start, kept out
 * of [WearExchangeCastCoordinator] so the offer's endpoint bookkeeping stays deterministic under test. The start
 * rides the notification's foreground-service allowance, like the listening request it mirrors.
 */
@Singleton
class WearCastServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** True when the service accepted the start; false when the platform refused, so nothing plays. */
    fun start(mediaUri: String, title: String?): Boolean = try {
        ContextCompat.startForegroundService(
            context,
            WearPlaybackService.startIntent(
                context = context,
                fileId = WearPlaybackService.NO_FILE_ID,
                mediaUri = mediaUri,
                positionMs = 0L,
                streamMediaKind = ClassifyWearStreamMediaKindUseCase.AUDIO,
                title = title,
            ),
        )
        true
    } catch (e: IllegalStateException) {
        // Outside the notification's start allowance the platform refuses; nothing plays, so nothing is kept.
        Timber.w(e, "WearCastServiceStarter: the playback service was refused")
        false
    }
}
