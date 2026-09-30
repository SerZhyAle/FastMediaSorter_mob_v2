package com.sza.fastmediasorter.domain.model

/** S4009: how an attempt to open the watch face's store page on the paired watch ended. */
sealed class WatchFaceOpenResult {

    /** The store page was started on the watch; the user still has to tap Install there. */
    data class OpenedOnWatch(val watchName: String) : WatchFaceOpenResult()

    /** No watch is connected to the phone right now. */
    data object NoWatch : WatchFaceOpenResult()

    /** A watch is connected, but it has nothing that can open a store page. */
    data object WatchStoreUnavailable : WatchFaceOpenResult()

    /** The request failed for a reason the user cannot act on. */
    data object Failed : WatchFaceOpenResult()
}
