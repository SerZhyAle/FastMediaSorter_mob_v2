package com.sza.fastmediasorter.ui.launcher.share

import android.content.Intent
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherGeographicAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.usecase.launcher.AcceptLauncherPlaceUseCase
import com.sza.fastmediasorter.ui.share.UrlInTextDetector
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
        val command = commandFrom(intent) ?: return LauncherPlaceShareOutcome.NOT_OURS
        val orientation = if (isLandscape) {
            LauncherOrientation.LANDSCAPE
        } else {
            LauncherOrientation.PORTRAIT
        }
        if (!acceptPlace(command, orientation, addedAt)) return LauncherPlaceShareOutcome.NOT_PLACED
        return if (command.action == LauncherGeographicAction.SHOW_PLACE) {
            LauncherPlaceShareOutcome.PLACED_PLACE
        } else {
            LauncherPlaceShareOutcome.PLACED_ROUTE
        }
    }

    private fun commandFrom(intent: Intent): LauncherCellCommand.Geographic? {
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith(TEXT_MIME_PREFIX) != true) {
            return null
        }
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (shared.isEmpty()) return null
        val coordinateQuery = COORDINATE.find(shared)?.value
        if (coordinateQuery != null) {
            return LauncherCellCommand.Geographic(
                action = LauncherGeographicAction.DIRECTIONS,
                query = coordinateQuery,
                label = labelFrom(shared),
            )
        }
        val placeText = UrlInTextDetector.httpUrls(shared)
            .fold(shared) { value, url -> value.replace(url, "") }
            .trim()
        if (placeText.isNotEmpty()) {
            return LauncherCellCommand.Geographic(
                action = LauncherGeographicAction.DIRECTIONS,
                query = placeText,
                label = labelFrom(placeText),
            )
        }
        val url = UrlInTextDetector.firstHttpUrl(shared) ?: return null
        return LauncherCellCommand.Geographic(
            action = LauncherGeographicAction.SHOW_PLACE,
            query = url,
            label = url,
        )
    }

    private fun labelFrom(value: String): String = value.lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()

    private companion object {
        const val TEXT_MIME_PREFIX = "text/"
        val COORDINATE = Regex("-?\\d{1,2}\\.\\d+\\s*,\\s*-?\\d{1,3}\\.\\d+")
    }
}
