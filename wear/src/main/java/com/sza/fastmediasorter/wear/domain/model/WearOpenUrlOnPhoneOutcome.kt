package com.sza.fastmediasorter.wear.domain.model

/** S2496: what came of asking the paired phone to open one address. */
enum class WearOpenUrlOnPhoneOutcome {

    /** The Wear OS companion accepted the address; the phone is showing it. */
    OPENED,

    /** No phone is currently reachable over the bridge. */
    NO_CONNECTED_PHONE,

    /**
     * A phone was reachable but refused or failed to take the address.
     *
     * Kept apart from [NO_CONNECTED_PHONE] because the two call for opposite advice - bring the phone
     * closer, versus try again - and one shared failure value could offer neither.
     */
    FAILED
}
