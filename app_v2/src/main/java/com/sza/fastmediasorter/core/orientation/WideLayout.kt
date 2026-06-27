package com.sza.fastmediasorter.core.orientation

import android.content.Context
import android.content.res.Configuration

/**
 * S0693: single source of truth for the landscape-style layout decision. A window is laid out
 * landscape-style when it is physically landscape OR its available width reaches the threshold, so
 * wide high-resolution devices held in portrait stop collapsing content into the top-left corner.
 * The union keeps every device that is landscape today (incl. small phones below the threshold).
 * Changing the rule here rolls the behavior back everywhere at once.
 */
const val WIDE_LAYOUT_MIN_WIDTH_DP = 600

fun isWideLayout(orientation: Int, screenWidthDp: Int): Boolean =
    orientation == Configuration.ORIENTATION_LANDSCAPE || screenWidthDp >= WIDE_LAYOUT_MIN_WIDTH_DP

fun Configuration.isWideLayout(): Boolean = isWideLayout(orientation, screenWidthDp)

fun Context.isWideLayout(): Boolean = resources.configuration.isWideLayout()
