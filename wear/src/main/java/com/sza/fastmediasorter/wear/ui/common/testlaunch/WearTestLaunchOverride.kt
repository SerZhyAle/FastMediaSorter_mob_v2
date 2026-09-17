package com.sza.fastmediasorter.wear.ui.common.testlaunch

import android.content.Intent
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode

/**
 * S3201: what one test launch asked the watch to draw, instead of changing a stored setting.
 *
 * A device test on 2026-09-16 checked the other geometry by flipping the owner's saved view and a
 * smaller glass by `wm density 380`, and restored neither. These values replace both routes: they
 * live for one launch only and nothing writes them anywhere, so a forgotten reset is impossible.
 *
 * @property geometryMode the view to draw instead of the stored one; null keeps the stored one.
 * @property screenDp the shorter screen edge, in dp, to lay the UI out for; null keeps the real one.
 */
data class WearTestLaunchOverride(
    val geometryMode: WearGeometryMode?,
    val screenDp: Int?,
)

/**
 * Reads [WearTestLaunchOverride] from a launch intent.
 *
 * Only the contract lives in `main`. The debug build binds a parser; the release build binds a reader
 * that always answers null, so the extra names never reach the artifact that goes to Play.
 */
interface WearTestLaunchOverrideReader {

    /** Null means a plain launch: draw exactly what the stored settings say. */
    fun read(intent: Intent): WearTestLaunchOverride?
}
