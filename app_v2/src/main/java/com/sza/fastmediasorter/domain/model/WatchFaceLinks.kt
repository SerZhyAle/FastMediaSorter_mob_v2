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

    /**
     * S4012: the watch app shares the phone app's Play package (S1681), so a debug suffix on this
     * build must not leak into the store address - hence a literal, not the build's application id.
     */
    const val WATCH_APP_PACKAGE_ID = "com.sza.fastmediasorter"

    const val WATCH_APP_MARKET_URL = "market://details?id=$WATCH_APP_PACKAGE_ID"

    const val WATCH_APP_WEB_URL = "https://play.google.com/store/apps/details?id=$WATCH_APP_PACKAGE_ID"

    /** S4012: the capability the watch app advertises (wear module `res/values/wear.xml`). */
    const val WATCH_APP_CAPABILITY = "fms_watch_app"
}
