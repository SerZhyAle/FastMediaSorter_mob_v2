package com.sza.fastmediasorter.ui.wear.companion

/**
 * S2460: the two documentation destinations the companion window offers. The island names the
 * destination and the host resolves the address, so no URL reaches the composable.
 */
enum class WearDocLink {
    /** The Wear pages on the project site - the watch portal. */
    PORTAL,

    /** The step-by-step "put FastMedia on your watch" guide. */
    INSTALL_GUIDE
}
