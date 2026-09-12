package com.sza.fastmediasorter.wear.ui.apps

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearAppId

/**
 * The single place on the watch that knows which glyph a mini-program wears.
 *
 * S2511 lifted this table out of `AppsScreen.kt`, where it was private to the composable file: a tile is
 * built outside Compose and outside the app process, so it could not reach it there. Sits beside
 * [WearAppAccentCatalog], which answers the same shape of question about colour.
 *
 * Icons live here rather than on the program record so the domain layer carries no drawing concern.
 *
 * S2474: every sub-app on the watch reuses the exact same icon as its counterpart in the phone app
 * (InternalRouteCatalog in app_v2), ensuring visual recognition across both form factors.
 *
 * Exhaustive with no else branch on purpose: a sixth watch program must fail compilation here rather than
 * silently inherit some default glyph.
 */
object WearAppIconCatalog {

    @DrawableRes
    fun iconFor(id: WearAppId): Int = when (id) {
        WearAppId.CALCULATOR -> R.drawable.ic_app_calculator
        WearAppId.NETWORK_MONITOR -> R.drawable.ic_network_monitor
        WearAppId.GAME -> R.drawable.ic_app_game
        WearAppId.VOICE_RECORDER -> R.drawable.ic_voice_note
        WearAppId.SYSTEM_INFO -> R.drawable.ic_info
        WearAppId.WATER_FLASHLIGHT -> R.drawable.ic_water_flashlight
        WearAppId.MOTION_MONITOR -> R.drawable.ic_steps
        // S2457: no phone counterpart to reuse a glyph from - the diagnostic exists on the watch only,
        // so this is the one icon of the table drawn for this module rather than mirrored into it.
        WearAppId.BODY_SENSOR -> R.drawable.ic_body_sensor
        WearAppId.BLOOD_PRESSURE -> R.drawable.ic_blood_pressure
        // S2509: the share glyph, matching the Home section's row - one entity wears one glyph across
        // both entrances. Not ic_cast, which already stands for a channel this app plays.
        WearAppId.BROADCAST -> R.drawable.ic_share
        // S2825: the phone's own stopwatch glyph, copied name-for-name rather than redrawn.
        WearAppId.STOPWATCH -> R.drawable.ic_stopwatch
        // S3007: the phone's own tourist glyph, copied name-for-name.
        WearAppId.TOURIST -> R.drawable.ic_tourist
    }
}
