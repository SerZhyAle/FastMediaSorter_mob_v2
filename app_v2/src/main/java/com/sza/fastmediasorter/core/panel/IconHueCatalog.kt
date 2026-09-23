package com.sza.fastmediasorter.core.panel

import androidx.annotation.ColorRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * S3433: the hue a meaning takes in its colour and decorated looks (ICON-RENDER 0.10 section 10).
 *
 * It chooses, it does not own: a program's hue stays in [SubProgramAccentCatalog] and a media type's in
 * MediaTypeColorCatalog, so a program wears one tone on every entrance - its panel row, its launch tile,
 * its home-screen shortcut. Only the source-type hues are new, and they live in `color_source_*` beside
 * the `color_media_*` family. Resource ids, never resolved ints, for the same day/night reason as the
 * catalogs it delegates to.
 */
object IconHueCatalog {

    /** The hue of an internal program; a route with no accent of its own takes the product accent. */
    @ColorRes
    fun forRoute(routeKey: String): Int = SubProgramAccentCatalog.accentFor(routeKey) ?: R.color.color_icon_accent

    /**
     * The hue of a resource type. Streams and the paired watch are programs rather than storage, so they
     * wear their program's tone instead of a source hue.
     */
    @ColorRes
    fun forResourceType(type: ResourceType): Int = when (type) {
        ResourceType.LOCAL -> R.color.color_source_local
        ResourceType.SMB -> R.color.color_source_smb
        ResourceType.SFTP -> R.color.color_source_sftp
        ResourceType.FTP -> R.color.color_source_ftp
        ResourceType.CLOUD -> R.color.color_source_cloud
        ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> forRoute(InternalRouteCatalog.KEY_STREAMS)
        ResourceType.WEAR_WATCH -> forRoute(InternalRouteCatalog.KEY_WEAR_COMPANION)
    }
}
