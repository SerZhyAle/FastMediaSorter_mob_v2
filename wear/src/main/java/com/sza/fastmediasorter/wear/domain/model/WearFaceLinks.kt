package com.sza.fastmediasorter.wear.domain.model

/**
 * S4009: the watch face's Play page, as the watch opens it.
 *
 * The face ships as its own Play application. The phone keeps the same package id in its own
 * `WatchFaceLinks`; the two modules share no code, so a rename has to land in both.
 */
object WearFaceLinks {

    const val PACKAGE_ID = "com.sza.fastmediasorter.watchface"

    const val MARKET_URL = "market://details?id=$PACKAGE_ID"

    // The face's own minSdk. Below it Play shows the page as incompatible, so the row is not offered.
    private const val FACE_MIN_SDK = 36

    fun isAvailableOn(sdkInt: Int): Boolean = sdkInt >= FACE_MIN_SDK
}
