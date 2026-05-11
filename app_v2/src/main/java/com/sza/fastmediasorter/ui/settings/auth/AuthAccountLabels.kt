package com.sza.fastmediasorter.ui.settings.auth

import com.sza.fastmediasorter.domain.repository.AuthAccountDomain

internal fun AuthAccountDomain.visibleLabel(dismissedLabel: String): String {
    if (!isDismissed) return displayName
    return displayName.takeIf { it.isNotBlank() } ?: dismissedLabel
}