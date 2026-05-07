package com.sza.fastmediasorter.data.permissions

import android.Manifest
import android.os.Build
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGroup
import com.sza.fastmediasorter.domain.model.PermissionGroupHeader
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionRegistryRepositoryImpl @Inject constructor() : PermissionRegistryRepository {

    private val allEntries = listOf(
        // STORAGE
        PermissionEntry(
            id = "read_external_storage",
            manifestName = Manifest.permission.READ_EXTERNAL_STORAGE,
            titleRes = R.string.perm_title_read_external_storage,
            descriptionRes = R.string.perm_desc_read_external_storage,
            iconRes = 0,
            group = PermissionGroup.STORAGE, optional = false,
            minSdk = 23, maxSdk = 32,
        ),
        PermissionEntry(
            id = "read_media_images",
            manifestName = Manifest.permission.READ_MEDIA_IMAGES,
            titleRes = R.string.perm_title_read_media_images,
            descriptionRes = R.string.perm_desc_read_media_images,
            iconRes = 0,
            group = PermissionGroup.STORAGE, optional = false,
            minSdk = 33,
        ),
        PermissionEntry(
            id = "read_media_video",
            manifestName = Manifest.permission.READ_MEDIA_VIDEO,
            titleRes = R.string.perm_title_read_media_video,
            descriptionRes = R.string.perm_desc_read_media_video,
            iconRes = 0,
            group = PermissionGroup.STORAGE, optional = false,
            minSdk = 33,
        ),
        PermissionEntry(
            id = "read_media_audio",
            manifestName = Manifest.permission.READ_MEDIA_AUDIO,
            titleRes = R.string.perm_title_read_media_audio,
            descriptionRes = R.string.perm_desc_read_media_audio,
            iconRes = 0,
            group = PermissionGroup.STORAGE, optional = false,
            minSdk = 33,
        ),
        PermissionEntry(
            id = "manage_external_storage",
            manifestName = Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            titleRes = R.string.perm_title_manage_external_storage,
            descriptionRes = R.string.perm_desc_manage_external_storage,
            iconRes = 0,
            group = PermissionGroup.STORAGE, optional = false,
            minSdk = 30,
        ),
        PermissionEntry(
            id = "manage_media",
            manifestName = Manifest.permission.MANAGE_MEDIA,
            titleRes = R.string.perm_title_manage_media,
            descriptionRes = R.string.perm_desc_manage_media,
            iconRes = 0,
            group = PermissionGroup.STORAGE, optional = false,
            minSdk = 31,
        ),
        // NETWORK
        PermissionEntry(
            id = "access_local_network",
            manifestName = PermissionHelper.LOCAL_NETWORK_PERMISSION,
            titleRes = R.string.perm_title_access_local_network,
            descriptionRes = R.string.perm_desc_access_local_network,
            iconRes = 0,
            group = PermissionGroup.NETWORK, optional = true,
            minSdk = 37,
        ),
        // MICROPHONE
        PermissionEntry(
            id = "record_audio",
            manifestName = Manifest.permission.RECORD_AUDIO,
            titleRes = R.string.perm_title_record_audio,
            descriptionRes = R.string.perm_desc_record_audio,
            iconRes = 0,
            group = PermissionGroup.MICROPHONE, optional = true,
            flavorGates = setOf("SUPPORT_AUDIO"),
        ),
        // NOTIFICATION
        PermissionEntry(
            id = "post_notifications",
            manifestName = Manifest.permission.POST_NOTIFICATIONS,
            titleRes = R.string.perm_title_post_notifications,
            descriptionRes = R.string.perm_desc_post_notifications,
            iconRes = 0,
            group = PermissionGroup.NOTIFICATION, optional = true,
            minSdk = 33,
            flavorGates = setOf("ENABLE_PERSISTENT_AUDIO_PLAYBACK"),
        ),
        // SYSTEM
        PermissionEntry(
            id = "battery_optimization",
            manifestName = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            titleRes = R.string.perm_title_battery_optimization,
            descriptionRes = R.string.perm_desc_battery_optimization,
            iconRes = 0,
            group = PermissionGroup.SYSTEM, optional = true,
        ),
        // VR
        PermissionEntry(
            id = "hand_tracking",
            manifestName = "com.oculus.permission.HAND_TRACKING",
            titleRes = R.string.perm_title_hand_tracking,
            descriptionRes = R.string.perm_desc_hand_tracking,
            iconRes = 0,
            group = PermissionGroup.VR, optional = true,
            flavorGates = setOf("SUPPORT_VR_PLAYER"),
        ),
        PermissionEntry(
            id = "headset_camera",
            manifestName = "horizonos.permission.HEADSET_CAMERA",
            titleRes = R.string.perm_title_headset_camera,
            descriptionRes = R.string.perm_desc_headset_camera,
            iconRes = 0,
            group = PermissionGroup.VR, optional = true,
            flavorGates = setOf("SUPPORT_VR_PLAYER"),
        ),
    )

    override fun getEntries(): List<PermissionEntry> =
        allEntries.filter { entry ->
            entry.minSdk <= Build.VERSION.SDK_INT &&
            entry.maxSdk >= Build.VERSION.SDK_INT &&
            evaluateFlavorGates(entry.flavorGates)
        }

    override fun getGroups(): List<PermissionGroupHeader> {
        val applicableGroups = getEntries().map { it.group }.toSet()
        return PermissionGroup.entries
            .filter { it in applicableGroups }
            .map { group ->
                PermissionGroupHeader(
                    group = group,
                    titleRes = when (group) {
                        PermissionGroup.STORAGE -> R.string.perm_group_storage
                        PermissionGroup.NETWORK -> R.string.perm_group_network
                        PermissionGroup.MICROPHONE -> R.string.perm_group_microphone
                        PermissionGroup.NOTIFICATION -> R.string.perm_group_notification
                        PermissionGroup.SYSTEM -> R.string.perm_group_system
                        PermissionGroup.VR -> R.string.perm_group_vr
                        else -> 0
                    }
                )
            }
    }

    private fun evaluateFlavorGates(gates: Set<String>): Boolean {
        if (gates.isEmpty()) return true
        return gates.all { fieldName ->
            try {
                val field = BuildConfig::class.java.getField(fieldName)
                field.getBoolean(null)
            } catch (e: Exception) {
                false
            }
        }
    }
}
