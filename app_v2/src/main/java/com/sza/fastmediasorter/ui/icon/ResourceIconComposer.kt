package com.sza.fastmediasorter.ui.icon

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * Composes the final [Drawable] shown in resource list/grid items.
 *
 * Strategy:
 * 1. If [MediaResource.iconId] is set and resolves to a known VD → use it as the
 *    primary icon; optionally overlay the connection-type badge.
 * 2. If no custom iconId → fall back to the legacy type-based drawable (same logic
 *    that was previously inlined in ResourceAdapter) so the UI never breaks even
 *    when the DB hasn't been migrated yet.
 *
 * The badge (if any) is placed in the bottom-right corner as a 18dp overlay so
 * it never occludes the primary icon's recognisable silhouette.
 */
object ResourceIconComposer {

    /**
     * Returns the composed drawable for [resource].
     * Falls back to the pre-S0034 legacy icon when no custom icon is assigned.
     */
    fun compose(context: Context, resource: MediaResource): Drawable {
        val themeDrawableRes = resolveThemeDrawable(resource)
        val badgeRes = ConnectionBadgeMapper.badgeFor(resource)

        // If there is no badge, a plain drawable is sufficient - avoids LayerDrawable overhead
        if (badgeRes == null) {
            return ContextCompat.getDrawable(context, themeDrawableRes)
                ?: ContextCompat.getDrawable(context, R.drawable.ic_resource_local)!!
        }

        val themeDrawable = ContextCompat.getDrawable(context, themeDrawableRes) ?: return ContextCompat.getDrawable(context, badgeRes)!!
        val badgeDrawable = ContextCompat.getDrawable(context, badgeRes) ?: return themeDrawable

        // Build a two-layer drawable: theme icon (bottom) + badge (top-right corner, inset from edges)
        val composite = LayerDrawable(arrayOf(themeDrawable, badgeDrawable))
        val badgePx = context.resources.getDimensionPixelSize(R.dimen.resource_icon_badge_size)
        val totalPx = context.resources.getDimensionPixelSize(R.dimen.resource_icon_composite_size)
        val inset = totalPx - badgePx
        // Layer 0 (theme): small inset so badge does not overlap the centre
        composite.setLayerInset(0, 0, 0, badgePx / 2, badgePx / 2)
        // Layer 1 (badge): anchored to bottom-right corner
        composite.setLayerInset(1, inset, inset, 0, 0)
        return composite
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    @DrawableRes
    private fun resolveThemeDrawable(resource: MediaResource): Int {
        // S0034 custom icon - highest priority
        val customRes = ResourceIconRegistry.resolveDrawable(resource.iconId)
        if (customRes != null) return customRes

        // S0034 set-first-icon fallback - uses registry before falling through to pre-S0034 legacy drawables
        val setFirstId = ResourceIconRegistry.firstIdFor(
            ResourceIconDefaults.setForResource(resource.profile, resource.type)
        )
        val setFallbackRes = ResourceIconRegistry.resolveDrawable(setFirstId)
        if (setFallbackRes != null) return setFallbackRes

        // Pre-S0034 legacy fallback - kept for safety during migration window
        return legacyIconFor(resource)
    }

    @DrawableRes
    private fun legacyIconFor(resource: MediaResource): Int = when {
        resource.path == LocalMediaScanner.VIRTUAL_PATH_RECENT      -> R.drawable.ic_virtual_recent
        resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO   -> R.drawable.ic_virtual_music
        resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO   -> R.drawable.ic_virtual_video
        resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS    -> R.drawable.ic_virtual_docs
        resource.id == -100L                                         -> R.drawable.ic_resource_favorites
        else -> when (resource.type) {
            ResourceType.LOCAL -> R.drawable.ic_resource_local
            ResourceType.SMB   -> R.drawable.ic_resource_smb
            ResourceType.SFTP  -> R.drawable.ic_resource_sftp
            ResourceType.FTP   -> R.drawable.ic_resource_ftp
            ResourceType.CLOUD -> when (resource.cloudProvider?.name) {
                "GOOGLE_DRIVE" -> R.drawable.ic_provider_google_drive
                "ONEDRIVE"     -> R.drawable.ic_provider_onedrive
                "DROPBOX"      -> R.drawable.ic_provider_dropbox
                else           -> R.drawable.ic_resource_cloud
            }
            ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> R.drawable.ic_cast
        }
    }
}
