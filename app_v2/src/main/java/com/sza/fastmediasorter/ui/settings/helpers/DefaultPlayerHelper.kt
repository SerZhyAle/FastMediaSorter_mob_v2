package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R

/**
 * Shows a dialog explaining default app association, then opens system Default Apps settings.
 */
object DefaultPlayerHelper {

    /**
     * Best-effort check whether this app is the current default handler for media open intents.
     * Android has no public ROLE_MEDIA_PLAYER constant, so we inspect resolver results.
     */
    fun isAlreadyDefaultPlayer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        val packageName = context.packageName
        val pm = context.packageManager

        val mimeTypesToProbe = listOf("audio/*", "video/*", "image/*", "application/pdf")
        return mimeTypesToProbe.any { mime ->
            val probe = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.parse("content://"), mime)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val resolved = pm.resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolved?.activityInfo?.packageName == packageName
        }
    }

    /**
     * Updates button enabled state and text based on whether the app is already the default player.
     * Call in both setupViews() and onResume() to reflect post-settings-screen state.
     */
    fun applyButtonState(button: TextView, context: Context, normalTextRes: Int) {
        val isDefault = isAlreadyDefaultPlayer(context)
        button.isEnabled = !isDefault
        button.alpha = if (isDefault) 0.5f else 1.0f
        button.text = context.getString(
            if (isDefault) R.string.settings_already_default_player else normalTextRes
        )
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
}
