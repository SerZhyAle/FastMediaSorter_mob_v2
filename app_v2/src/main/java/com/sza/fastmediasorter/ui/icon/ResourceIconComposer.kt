package com.sza.fastmediasorter.ui.icon

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.ResourceTypeIconMap
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SyntheticResourceIds
import com.sza.fastmediasorter.domain.model.isAllFilesPredefined
import com.sza.fastmediasorter.util.NotesFolderPolicy

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

    private val PLACEHOLDER_ICON_ID = ResourceIconRegistry.firstIdFor(ResourceIconSet.OTHER)

    /**
     * Returns the composed drawable for [resource].
     * Falls back to the pre-S0034 legacy icon when no custom icon is assigned.
     */
    fun compose(context: Context, resource: MediaResource): Drawable {
        val themeDrawableRes = resolveThemeDrawable(resource)
        val badgeRes = ConnectionBadgeMapper.badgeFor(resource)

        // If there is no badge, a plain drawable is sufficient - avoids LayerDrawable overhead
        if (badgeRes == null) {
            return loadThemeDrawable(context, themeDrawableRes)
                ?: ContextCompat.getDrawable(context, R.drawable.ic_resource_local)!!
        }

        val themeDrawable = loadThemeDrawable(context, themeDrawableRes)
            ?: return ContextCompat.getDrawable(context, badgeRes)!!
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

    /**
     * Resolves [ic_folder] with an explicit source color tint so it renders safely even when
     * invoked from an `@ApplicationContext` (e.g. launcher tiles).
     */
    private fun loadThemeDrawable(context: Context, @DrawableRes resId: Int): Drawable? {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return null
        return if (resId == R.drawable.ic_folder) {
            DrawableCompat.wrap(drawable.mutate()).also { wrapped ->
                DrawableCompat.setTint(wrapped, ContextCompat.getColor(context, R.color.color_source_local))
            }
        } else {
            drawable
        }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * S4477: the OTHER set's first icon doubles as the placeholder that Recent and the pinned All files
     * resource were seeded with, so the two tiles drew the same circle. For those two resources an absent
     * or placeholder id resolves to a dedicated glyph instead. Any other stored id is the user's pick and
     * wins - a user who deliberately picked the placeholder for one of them cannot be told apart from the
     * seed, which is the accepted cost of needing no migration.
     *
     * S4823: the notes folder is created by the Notes screen and then gets a RANDOM id of the OTHER set from
     * the main screen's icon backfill, so any OTHER-set id counts as its seed and it draws the Notes program's
     * glyph. A user who picked an OTHER-set icon for it is overruled on the same accepted terms.
     */
    @DrawableRes
    internal fun predefinedGlyphFor(resource: MediaResource): Int? {
        val storedId = resource.iconId
        val seeded = storedId == null || storedId == PLACEHOLDER_ICON_ID
        return when {
            isNotesFolder(resource) && (seeded || storedId in otherSetIds) -> R.drawable.ic_send_note
            !seeded -> null
            resource.path == LocalMediaScanner.VIRTUAL_PATH_RECENT -> R.drawable.ic_tile_recent
            resource.isAllFilesPredefined -> R.drawable.ic_tile_all_files
            else -> null
        }
    }

    private val otherSetIds: Set<String> by lazy { ResourceIconRegistry.idsFor(ResourceIconSet.OTHER).toSet() }

    private fun isNotesFolder(resource: MediaResource): Boolean =
        resource.type == ResourceType.LOCAL && NotesFolderPolicy.isNotesDirectory(resource.path)

    @VisibleForTesting
    @DrawableRes
    internal fun themeDrawableFor(resource: MediaResource): Int = resolveThemeDrawable(resource)

    @DrawableRes
    private fun resolveThemeDrawable(resource: MediaResource): Int {
        predefinedGlyphFor(resource)?.let {
            return it
        }

        // S0034 custom icon - highest priority
        val customRes = ResourceIconRegistry.resolveDrawable(resource.iconId)
        if (customRes != null) return customRes

        // S1378: a resource bound to a removable volume carries its own source glyph, which beats
        // every default below but never a user-assigned icon - the check above already returned.
        if (resource.storageVolumeId != null) return R.drawable.ic_resource_removable

        // S0034 set-first-icon fallback for typed media profiles; unprofiled resources (OTHER)
        // fall through to legacy source/folder glyphs instead of the ico-05-001 empty ring.
        val defaultSet = ResourceIconDefaults.setForResource(resource.profile, resource.type)
        if (defaultSet != ResourceIconSet.OTHER) {
            val setFirstId = ResourceIconRegistry.firstIdFor(defaultSet)
            val setFallbackRes = ResourceIconRegistry.resolveDrawable(setFirstId)
            if (setFallbackRes != null) return setFallbackRes
        }

        // Pre-S0034 / unprofiled fallback
        return legacyIconFor(resource)
    }

    @DrawableRes
    private fun legacyIconFor(resource: MediaResource): Int = when {
        resource.path == LocalMediaScanner.VIRTUAL_PATH_RECENT -> R.drawable.ic_virtual_recent
        resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO -> R.drawable.ic_virtual_music
        resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO -> R.drawable.ic_virtual_video
        resource.path == LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS -> R.drawable.ic_virtual_docs
        resource.id == SyntheticResourceIds.FAVORITES -> R.drawable.ic_resource_favorites
        // S0890/S4847: local folders draw a folder glyph; cloud refines to provider; others share map.
        else -> when (resource.type) {
            ResourceType.LOCAL -> R.drawable.ic_folder
            ResourceType.CLOUD -> CloudProviderIconMap.iconFor(resource.cloudProvider?.name)
            else -> ResourceTypeIconMap.iconFor(resource.type)
        }
    }
}
