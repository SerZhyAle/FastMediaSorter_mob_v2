package com.sza.fastmediasorter.domain.model

/**
 * S4009: the one place on the phone that names the watch face's Play package.
 *
 * The face ships as its own Play application, so both addresses are derived from this id rather
 * than written out twice; the watch module keeps a mirror of the id because the modules share no code.
 */
object WatchFaceLinks {

    const val PACKAGE_ID = "com.sza.fastmediasorter.watchface"

    const val MARKET_URL = "market://details?id=$PACKAGE_ID"

    const val WEB_URL = "https://play.google.com/store/apps/details?id=$PACKAGE_ID"
}
