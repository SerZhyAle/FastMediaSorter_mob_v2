package com.sza.fastmediasorter.wear.domain.broadcast

/**
 * The watch's listener cap held across LAN and relay (contract DEVICE-EXCHANGE 7.6 and amendment M): four listeners
 * in all, and the server counts only relay listeners, so the watch tells it how many are left after the people
 * connected on the LAN. The relay upload takes a LAN slot too, but it is not a person and lowers nothing.
 */
object WearExchangeListenerCap {

    const val WATCH_LISTENER_CAP = 4

    // `maxListeners` 0 means "no cap" to the server; at four LAN listeners the upload cannot hold a slot, so the
    // descriptor carries no relay endpoint and a floor of one admits nobody past the cap.
    private const val MIN_RELAY_LISTENERS = 1

    fun maxListeners(humanLanListeners: Int): Int =
        (WATCH_LISTENER_CAP - humanLanListeners.coerceAtLeast(0)).coerceAtLeast(MIN_RELAY_LISTENERS)
}
