package com.sza.fastmediasorter.wear.ui.fdsec

import androidx.annotation.StringRes
import com.sza.fastmediasorter.wear.domain.model.WearFdSecMode

/**
 * S3383: what the credential screen draws.
 *
 * The typed credential is held here as an ordinary string because a Compose text field owns one
 * anyway; what matters is where it does NOT go - it is never logged, never saved into the instance
 * state, and never travels in a navigation argument.
 */
data class FdSecCredentialUiState(
    val mode: WearFdSecMode = WearFdSecMode.OPEN,
    val fileName: String = "",
    val credential: String = "",
    val repeated: String = "",
    val isWorking: Boolean = false,

    /** S3397: keep the credential for later containers once it has opened this one. */
    val remember: Boolean = false,

    /** The outcome, once there is one. Each class has its own string and never borrows another's. */
    @StringRes val messageRes: Int? = null,

    /** Set only in [WearFdSecMode.OPEN], once a viewer has something to show. */
    val playerRoute: String? = null
) {

    /** The second field exists only where a mistyped credential would be unrecoverable. */
    val asksTwice: Boolean get() = mode == WearFdSecMode.ENCRYPT

    val entriesMatch: Boolean get() = !asksTwice || credential == repeated

    /** Only viewing offers it; packing and restoring take a credential for that one errand. */
    val offersRemember: Boolean get() = mode == WearFdSecMode.OPEN

    val canConfirm: Boolean get() = !isWorking && entriesMatch && messageRes == null

    /** The warning stands while the field is empty, and says what an empty credential really buys. */
    val showsEmptyWarning: Boolean get() = credential.isEmpty()
}
