package com.sza.fastmediasorter.ui.systeminfo.helpers

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.systeminfo.SystemInfoGroup

/**
 * The icon shown before a System information group's name; a section without a group has none.
 * A map rather than a `when` because one branch per group exceeds the detekt complexity limit;
 * `SystemInfoExpansionStateTest` is what stops a new [SystemInfoGroup] from shipping without an icon.
 */
object SystemInfoGroupIcons {

    private val ICONS: Map<SystemInfoGroup, Int> = mapOf(
        SystemInfoGroup.DEVICE to R.drawable.ic_profile_personal_smartphone,
        SystemInfoGroup.USER to R.drawable.ic_contact,
        SystemInfoGroup.OS to R.drawable.ic_android,
        SystemInfoGroup.APP to R.drawable.ic_apps,
        SystemInfoGroup.HARDWARE to R.drawable.ic_sysinfo_hardware,
        SystemInfoGroup.CAMERAS to R.drawable.ic_camera_capture,
        SystemInfoGroup.BATTERY to R.drawable.ic_battery,
        SystemInfoGroup.MEMORY to R.drawable.ic_sysinfo_memory,
        SystemInfoGroup.STORAGE to R.drawable.ic_storage,
        SystemInfoGroup.NETWORK to R.drawable.ic_wifi,
        SystemInfoGroup.DISPLAY to R.drawable.ic_display,
        SystemInfoGroup.LOCALE to R.drawable.ic_translate,
        SystemInfoGroup.TIME to R.drawable.ic_schedule,
        SystemInfoGroup.SYSTEM to R.drawable.ic_settings,
        SystemInfoGroup.BENCHMARKS to R.drawable.ic_speed,
        SystemInfoGroup.OS_SECURITY to R.drawable.ic_lock,
        SystemInfoGroup.PERMISSIONS to R.drawable.ic_sysinfo_permissions,
        SystemInfoGroup.INSTALLER_SIGNATURE to R.drawable.ic_sysinfo_signature,
        SystemInfoGroup.RUNTIMES to R.drawable.ic_developer_options,
        SystemInfoGroup.MOUNTS to R.drawable.ic_folder_open,
        SystemInfoGroup.NETWORK_DIAGNOSTICS to R.drawable.ic_internet,
        SystemInfoGroup.PROCESS_RESOURCES to R.drawable.ic_data_usage,
    )

    @DrawableRes
    fun iconFor(group: SystemInfoGroup?): Int? = group?.let(ICONS::get)
}
