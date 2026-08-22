package com.sza.fastmediasorter.ui.launcher.share

import android.content.Intent
import com.sza.fastmediasorter.domain.map.MapPoint
import com.sza.fastmediasorter.domain.map.MapsShortLinkResolver
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherGeographicAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.usecase.launcher.AcceptLauncherPlaceUseCase
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
    private val shortLinkResolver: MapsShortLinkResolver,
) {

    suspend fun handle(
        intent: Intent,
        isLandscape: Boolean,
        addedAt: Long,
    ): LauncherPlaceShareOutcome {
        val shape = shapeFrom(intent) ?: return LauncherPlaceShareOutcome.NOT_OURS
        val command = commandFor(shape)
        val orientation = if (isLandscape) {
            LauncherOrientation.LANDSCAPE
        } else {
            LauncherOrientation.PORTRAIT
        }
        return when {
            !acceptPlace(command, orientation, addedAt) -> LauncherPlaceShareOutcome.NOT_PLACED
            command.action == LauncherGeographicAction.SHOW_PLACE -> LauncherPlaceShareOutcome.PLACED_PLACE
            else -> LauncherPlaceShareOutcome.PLACED_ROUTE
        }
    }

    /**
     * S1585: a link is resolved here, once, while the cell is being created.
     *
     * Resolving on tap instead would put a network round-trip in front of starting to drive and would
     * leave the shortcut dead without a connection, which is what strategic ADR-1 rules out. A link
     * that cannot be resolved still becomes a cell - showing the place is a worse answer than a route
     * but a better one than losing the share (strategic 6.1).
     */
    private suspend fun commandFor(shape: LauncherPlaceShareParser.Shape): LauncherCellCommand.Geographic =
        when (shape) {
            is LauncherPlaceShareParser.Shape.Coordinates ->
                geographic(LauncherGeographicAction.NAVIGATION, shape.query, shape.label)

            is LauncherPlaceShareParser.Shape.PlaceText ->
                geographic(LauncherGeographicAction.DIRECTIONS, shape.query, shape.label)

            is LauncherPlaceShareParser.Shape.MapsLink -> resolved(shape)
        }

    private suspend fun resolved(shape: LauncherPlaceShareParser.Shape.MapsLink): LauncherCellCommand.Geographic {
        val point = shortLinkResolver.resolve(shape.link)
        return if (point == null) {
            geographic(LauncherGeographicAction.SHOW_PLACE, shape.link, shape.label.ifEmpty { shape.link })
        } else {
            geographic(LauncherGeographicAction.NAVIGATION, point.asQuery(), shape.label.ifEmpty { point.asQuery() })
        }
    }

    private fun MapPoint.asQuery(): String = "$latitude,$longitude"

    private fun geographic(
        action: LauncherGeographicAction,
        query: String,
        label: String,
    ): LauncherCellCommand.Geographic = LauncherCellCommand.Geographic(
        action = action,
        query = query,
        label = label,
    )

    private fun shapeFrom(intent: Intent): LauncherPlaceShareParser.Shape? {
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
