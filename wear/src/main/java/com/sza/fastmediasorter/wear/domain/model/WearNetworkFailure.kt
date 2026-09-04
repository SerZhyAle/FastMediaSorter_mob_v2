package com.sza.fastmediasorter.wear.domain.model

/**
 * S2488: why a network source refused to open, as far as the wearer needs to know.
 *
 * OTHER keeps the classifier total, so the caller never handles a null and the screen always has a
 * message to show.
 */
enum class WearNetworkFailure {
    CONNECTION_REFUSED,
    TIMEOUT,
    AUTH_REJECTED,
    UNKNOWN_HOST,
    OTHER
}
