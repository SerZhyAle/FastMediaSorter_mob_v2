package com.sza.fastmediasorter.ui.icon

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.ResourceTypeIconMap
import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * Cloud-provider refinement of the [ResourceTypeIconMap] CLOUD entry: known providers get their
 * branded glyph, anything else falls back to the generic cloud icon. Shared by the connection
 * badge mapper and the legacy composer path so the provider table cannot drift between them
 * (S0890 dedup).
 */
object CloudProviderIconMap {

    @DrawableRes
    fun iconFor(providerName: String?): Int = when (providerName) {
        "GOOGLE_DRIVE" -> R.drawable.ic_provider_google_drive
        "ONEDRIVE" -> R.drawable.ic_provider_onedrive
        "DROPBOX" -> R.drawable.ic_provider_dropbox
        else -> ResourceTypeIconMap.iconFor(ResourceType.CLOUD)
    }
}
