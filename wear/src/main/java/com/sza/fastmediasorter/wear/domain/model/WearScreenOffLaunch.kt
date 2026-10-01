package com.sza.fastmediasorter.wear.domain.model

import android.content.Intent

/**
 * S4018: the launch flag that opens the watch app straight into the dark sheet.
 *
 * Kept apart from [WearLaunchTarget] because it names a mode laid over whatever screen is shown, not an
 * address to navigate to: a target resolves to a route, and this resolves to nothing but the sheet.
 * Writer and reader share this file so the extra's name is one literal.
 */
fun Intent.markWearScreenOffRequest(): Intent = putExtra(EXTRA_SCREEN_OFF, true)

/** True only when [markWearScreenOffRequest] wrote the flag. */
fun readWearScreenOffRequest(intent: Intent): Boolean = intent.getBooleanExtra(EXTRA_SCREEN_OFF, false)

private const val EXTRA_SCREEN_OFF = "com.sza.fastmediasorter.wear.launch.screen_off"
