package com.sza.fastmediasorter.domain.model

/** A way the app finds a companion PC again after its address changed. */
enum class CompanionRecoveryChannel {
    /** mDNS discovery on the current Wi-Fi; Android 17 refuses it without the local network grant. */
    LOCAL_NETWORK,

    /** The address the PC announces on the user's Google Drive. */
    GOOGLE_DRIVE,
}
