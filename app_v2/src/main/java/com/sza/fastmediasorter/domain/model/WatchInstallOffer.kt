package com.sza.fastmediasorter.domain.model

/**
 * S4012: a connected watch the phone may offer FastMediaSorter's watch app and watch face to.
 *
 * [watchAppInstalled] is true only when the watch advertises the app's capability; the face ships as
 * its own package with no capability of its own, so it is always offered.
 */
data class WatchInstallOffer(
    val watchName: String,
    val watchAppInstalled: Boolean,
)
