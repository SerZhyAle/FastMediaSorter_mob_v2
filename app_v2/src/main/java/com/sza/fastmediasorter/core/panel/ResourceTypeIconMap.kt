package com.sza.fastmediasorter.core.panel

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * Enumerable [ResourceType] -> tile-icon mapping for the app-launch panel resource tiles (S0663).
 *
 * Extracted verbatim from `ResolveAppLaunchPanelTilesUseCase` so the mapping is both the runtime
 * source (the use-case delegates to [iconFor]) and a scannable registry for the icon inventory
 * (S0815). [entries] must stay exhaustive over [ResourceType]: [iconFor] uses [Map.getValue] and
 * a missing type would throw, matching the previous exhaustive `when` (no silent icon change).
 */
object ResourceTypeIconMap {

    val entries: Map<ResourceType, Int> = mapOf(
        ResourceType.LOCAL to R.drawable.ic_resource_local,
        ResourceType.SMB to R.drawable.ic_resource_smb,
        ResourceType.SFTP to R.drawable.ic_resource_sftp,
        ResourceType.FTP to R.drawable.ic_resource_ftp,
        ResourceType.CLOUD to R.drawable.ic_resource_cloud,
        // Live streams share the cast glyph (both HTTP and RTSP), as in the original when-branch.
        ResourceType.HTTP_STREAM to R.drawable.ic_cast,
        ResourceType.RTSP_STREAM to R.drawable.ic_cast,
        // S1861: the paired watch.
        ResourceType.WEAR_WATCH to R.drawable.ic_watch,
    )

    @DrawableRes
    fun iconFor(type: ResourceType): Int = entries.getValue(type)

    /**
     * S1124: whether [iconFor] returns a monochrome glyph that needs a theme tint to stay legible on a
     * light tile, vs a colored source badge that must keep its own color. Only the shared cast glyph
     * (streams) is monochrome white; every `ic_resource_*` badge is a distinct full color.
     *
     * S1861: `ic_watch` joins the monochrome set - it is a single-tone glyph, not a colored source
     * badge, and without the theme tint it is lost on a light tile.
     */
    fun isMonochrome(type: ResourceType): Boolean =
        type == ResourceType.HTTP_STREAM ||
            type == ResourceType.RTSP_STREAM ||
            type == ResourceType.WEAR_WATCH
}
