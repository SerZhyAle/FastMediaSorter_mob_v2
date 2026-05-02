package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.utils.PermissionChecker
import timber.log.Timber

class GeneralSettingsPermissionsHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
    private val mediaPermissionsLauncher: ActivityResultLauncher<Array<String>>,
    private val notificationPermissionLauncher: ActivityResultLauncher<String>,
) {
    fun updatePermissionButtonsState() {
        val ctx = fragment.requireContext()

        val hasMediaPermissions = PermissionChecker.hasMediaPermissions(ctx)
        binding.btnLocalFilesPermission.isEnabled = true
        binding.btnLocalFilesPermission.alpha = 1.0f
        binding.btnLocalFilesPermission.text = if (hasMediaPermissions)
            fragment.getString(R.string.manage_local_files_permission)
        else
            fragment.getString(R.string.grant_local_files_permission)
        binding.btnLocalFilesPermission.setOnClickListener { handleLocalFilesPermissionAction() }

        val hasNetworkPermission = PermissionHelper.hasInternetPermission(ctx)
        binding.btnNetworkPermission.isEnabled = !hasNetworkPermission
        binding.btnNetworkPermission.alpha = if (hasNetworkPermission) 0.5f else 1.0f
        binding.btnNetworkPermission.text = if (hasNetworkPermission)
            fragment.getString(R.string.network_permission_granted)
        else
            fragment.getString(R.string.grant_network_permission)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val hasManageMedia = PermissionHelper.hasManageMediaPermission(ctx)
            binding.btnManageMediaPermission.isEnabled = true
            binding.btnManageMediaPermission.alpha = 1.0f
            binding.btnManageMediaPermission.text = if (hasManageMedia)
                fragment.getString(R.string.manage_manage_media_permission)
            else
                fragment.getString(R.string.grant_manage_media_permission)
            binding.btnManageMediaPermission.setOnClickListener {
                if (PermissionHelper.hasManageMediaPermission(ctx)) openAppSettings() else requestManageMediaPermission()
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
            && com.sza.fastmediasorter.BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK
        ) {
            binding.btnNotificationPermission?.visibility = View.VISIBLE
            val hasNotification = androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            binding.btnNotificationPermission?.isEnabled = true
            binding.btnNotificationPermission?.alpha = 1.0f
            binding.btnNotificationPermission?.text = if (hasNotification)
                fragment.getString(R.string.manage_notifications_permission)
            else
                fragment.getString(R.string.grant_notifications_permission)
            binding.btnNotificationPermission?.setOnClickListener {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        ctx, android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) openAppSettings()
                else notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            binding.btnNotificationPermission?.visibility = View.GONE
        }

        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        binding.btnBatteryOptimizationPermission?.isEnabled = true
        binding.btnBatteryOptimizationPermission?.alpha = 1.0f
        binding.btnBatteryOptimizationPermission?.text = if (isIgnoring)
            fragment.getString(R.string.manage_battery_optimization_permission)
        else
            fragment.getString(R.string.grant_battery_optimization_permission)
        binding.btnBatteryOptimizationPermission?.setOnClickListener {
            val pmNow = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (pmNow.isIgnoringBatteryOptimizations(ctx.packageName)) {
                try {
                    // SettingsIntentLauncher.launch not needed — fire-and-forget link to battery optimization list page
                    fragment.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) { openAppSettings() }
            } else {
                try {
                    fragment.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                        }
                    )
                } catch (_: Exception) { openAppSettings() }
            }
        }
    }

    fun handleLocalFilesPermissionAction() {
        if (PermissionChecker.hasMediaPermissions(fragment.requireContext())) openAppSettings()
        else requestMediaPermissions()
    }

    private fun requestMediaPermissions() {
        val permissions = PermissionChecker.getRequiredMediaPermissions()
        if (permissions.isEmpty()) { updatePermissionButtonsState(); return }
        mediaPermissionsLauncher.launch(permissions)
    }

    private fun openAppSettings() {
        try {
            fragment.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", fragment.requireContext().packageName, null)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to open app settings")
        }
    }

    private fun requestManageMediaPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PermissionHelper.requestManageMediaPermission(fragment.requireActivity())
        }
    }
}
