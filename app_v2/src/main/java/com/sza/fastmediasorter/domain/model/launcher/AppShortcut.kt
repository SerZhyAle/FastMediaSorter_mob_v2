package com.sza.fastmediasorter.domain.model.launcher

import android.graphics.drawable.Drawable

/**
 * S0427: one published quick action of an installed app (manifest or dynamic shortcut).
 *
 * The icon travels on the model because only the launcher-apps service can decode it - unlike a cell
 * visual, there is no resource id the UI could resolve on its own (same shape as `LaunchableApp`).
 */
data class AppShortcut(
    val id: String,
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isEnabled: Boolean,
    val disabledMessage: String?,
)
