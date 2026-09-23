package com.sza.fastmediasorter.domain.model

import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes

/** Number of fixed slots in the locked-view panel grid (3x5 portrait / 5x3 landscape). */
const val APP_LAUNCH_PANEL_SLOT_COUNT = 15

/**
 * Render model for one panel cell. A resolved app tile has [isEmpty] = false with a non-null
 * [icon]/[label]; an empty slot has [isEmpty] = true, a blank [label] (the UI supplies the
 * placeholder caption from resources) and a null [icon].
 */
data class AppLaunchPanelTileUi(
    val slotIndex: Int,
    val type: AppLaunchPanelTileType,
    val targetId: String?,
    val label: String,
    val icon: Drawable?,
    val isEmpty: Boolean,
    /**
     * S1124: true when [icon] is a monochrome glyph that must be tinted to a theme on-surface color to
     * stay legible on the light tile (feature / OS-shortcut routes, the shared stream cast glyph). False
     * for full-color assets that must keep their own colors: app launcher icons (own/external) and the
     * colored `ic_resource_*` source badges. Decided at resolve time where the icon source is known -
     * tile type alone is not enough (a Resource route can be either a colored badge or the cast glyph).
     */
    val tintable: Boolean = false,
    /**
     * S2510: the accent identifying this sub-program, or null to keep the neutral on-surface tint.
     * Only meaningful while [tintable] is true. Decided at resolve time, where the icon source is
     * known - the empty-slot placeholder, OS shortcuts and the colored resource badges all leave it
     * null, so colouring a program cannot bleed into a tile that is not one.
     */
    @ColorRes val accentRes: Int? = null,
    /**
     * S3433: the hue of the decorated look, non-null only when [icon] is a product glyph - a feature route, an
     * internal program, an OS-shortcut route or a resource type. The panel then draws the glyph on a circle of
     * this hue (ICON-RENDER 0.10 section 10: an in-app launch grid takes the decorated look). Null for an
     * installed app's own icon, a user picture and the empty slot, which keep their own look.
     */
    @ColorRes val plateHueRes: Int? = null,
)
