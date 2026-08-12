package com.sza.fastmediasorter.ui.launcher.share

import android.content.Intent
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherGeographicAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.usecase.launcher.AcceptLauncherPlaceUseCase
import timber.log.Timber
import javax.inject.Inject

enum class LauncherPlaceShareOutcome {
    NOT_OURS,
    PLACED_ROUTE,
    PLACED_PLACE,
    NOT_PLACED,
}

/** Keeps the transparent share host free of parsing and desktop-placement decisions. */
class LauncherPlaceShareManager @Inject constructor(
    private val acceptPlace: AcceptLauncherPlaceUseCase,
) {

    suspend fun handle(
        intent: Intent,
        isLandscape: Boolean,
        addedAt: Long,
    ): LauncherPlaceShareOutcome {
        val command = commandFrom(intent)
        Timber.d("S1175: place share received, parsed=%s", command?.action)
        val orientation = if (isLandscape) {
            LauncherOrientation.LANDSCAPE
        } else {
            LauncherOrientation.PORTRAIT
        }
        return when {
            command == null -> LauncherPlaceShareOutcome.NOT_OURS
            !acceptPlace(command, orientation, addedAt) -> LauncherPlaceShareOutcome.NOT_PLACED
            command.action == LauncherGeographicAction.SHOW_PLACE -> LauncherPlaceShareOutcome.PLACED_PLACE
            else -> LauncherPlaceShareOutcome.PLACED_ROUTE
        }
    }

    private fun commandFrom(intent: Intent): LauncherCellCommand.Geographic? {
        val isSharedText = intent.action == Intent.ACTION_SEND &&
            intent.type?.startsWith(TEXT_MIME_PREFIX) == true
        return if (isSharedText) {
            LauncherPlaceShareParser.parse(intent.getStringExtra(Intent.EXTRA_TEXT))
        } else {
            null
        }
    }

    private companion object {
        const val TEXT_MIME_PREFIX = "text/"
    }
}
