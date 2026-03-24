package com.sza.fastmediasorter.ui.settings.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Button
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R

/**
 * Shows a dialog explaining default app association, then opens system Default Apps settings.
 */
object DefaultPlayerHelper {

    /**
     * Check if app is already the default media player (API 31+ via RoleManager).
     */
    fun isAlreadyDefaultPlayer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            return roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER) == false &&
                   roleManager?.isRoleHeld("android.app.role.SYSTEM_GALLERY") == true
        }
        return false
    }

    /**
     * Update button text/state based on whether app is already set as default.
     */
    fun applyButtonState(button: Button, context: Context, labelResId: Int) {
        if (isAlreadyDefaultPlayer(context)) {
            button.text = context.getString(R.string.settings_already_default_player)
            button.isEnabled = false
        } else {
            button.text = context.getString(labelResId)
            button.isEnabled = true
        }
    }

    fun showSetDefaultDialog(fragment: Fragment) {
        val context = fragment.requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_default_player_dialog_title)
            .setMessage(R.string.settings_default_player_dialog_message)
            .setPositiveButton(R.string.settings_default_player_dialog_confirm) { _, _ ->
                openDefaultAppsSettings(fragment)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openDefaultAppsSettings(fragment: Fragment) {
        try {
            fragment.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            // Fallback: open app details settings
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", fragment.requireContext().packageName, null)
                }
                fragment.startActivity(intent)
            } catch (_: Exception) {
                // Silently fail — system settings not available
            }
        }
    }

    /**
     * Show dialog for a specific MIME type, then open default apps settings.
     */
    fun showSetDefaultDialogForType(fragment: Fragment, mimeType: String) {
        val context = fragment.requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_default_player_dialog_title)
            .setMessage(R.string.settings_default_player_dialog_message)
            .setPositiveButton(R.string.settings_default_player_dialog_confirm) { _, _ ->
                openDefaultAppsSettings(fragment)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Open chooser for a specific MIME type from an Activity context.
     * Falls back to default apps settings if chooser is unavailable.
     */
    fun openChooserOrFallbackFromActivity(activity: Activity, mimeType: String) {
        try {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
            } catch (_: Exception) {
                // Silently fail
            }
        }
    }
}
