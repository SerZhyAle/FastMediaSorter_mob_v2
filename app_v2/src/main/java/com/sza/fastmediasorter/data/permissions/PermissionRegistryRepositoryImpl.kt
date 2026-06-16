package com.sza.fastmediasorter.data.permissions

import android.Manifest
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGroup
import com.sza.fastmediasorter.domain.model.PermissionGroupHeader
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

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
            flavorGates = setOf("SUPPORT_LOCAL_NETWORK"),
        ),
        // CAMERA
        PermissionEntry(
            id = "camera",
            manifestName = Manifest.permission.CAMERA,
            titleRes = R.string.perm_title_camera,
            descriptionRes = R.string.perm_desc_camera,
            iconRes = 0,
            group = PermissionGroup.CAMERA, optional = true,
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
        // S0241: VR group entries (hand_tracking, headset_camera) removed with the OpenXR stack.
    )

    override fun getEntries(): List<PermissionEntry> =
        allEntries.filter { entry ->
            entry.minSdk <= Build.VERSION.SDK_INT &&
            entry.maxSdk >= Build.VERSION.SDK_INT &&
            evaluateFlavorGates(entry.flavorGates)
        }

    override fun getWelcomeEntries(): List<PermissionEntry> {
        // Show every permission this build can request; the user decides which to grant. No narrowing by
        // functionality toggles - opting out happens by leaving a permission ungranted, not by hiding it.
        val base = getEntries()
        // POST_NOTIFICATIONS is flavor-gated out of the full Settings list on builds without persistent
        // audio playback, but onboarding always asks for it (welcome-only relaxation). Re-add it directly
        // from the raw registry, honouring only its SDK bound, when getEntries() filtered it out.
        val notifications = allEntries.firstOrNull {
            it.manifestName == Manifest.permission.POST_NOTIFICATIONS &&
                it.minSdk <= Build.VERSION.SDK_INT &&
                it.maxSdk >= Build.VERSION.SDK_INT
        }
        return if (notifications != null && base.none { it.manifestName == notifications.manifestName }) {
            base + notifications
        } else {
            base
        }
    }

    override fun getGroups(): List<PermissionGroupHeader> {
        // SDK-applicable groups, ignoring flavor gates: a group whose only entry is flavor-gated-out
        // still gets a header, so the welcome adaptive set (which re-adds POST_NOTIFICATIONS past its
        // ENABLE_PERSISTENT_AUDIO_PLAYBACK gate) can render it. Both Settings and welcome buildRows
        // skip groups that resolve to no entries, so a header without entries is harmless.
        val applicableGroups = allEntries
            .filter { it.minSdk <= Build.VERSION.SDK_INT && it.maxSdk >= Build.VERSION.SDK_INT }
            .map { it.group }
            .toSet()
        return PermissionGroup.entries
            .filter { it in applicableGroups }
            .map { group ->
                PermissionGroupHeader(
                    group = group,
                    titleRes = when (group) {
                        PermissionGroup.STORAGE -> R.string.perm_group_storage
                        PermissionGroup.NETWORK -> R.string.perm_group_network
                        PermissionGroup.CAMERA -> R.string.perm_group_camera
                        PermissionGroup.MICROPHONE -> R.string.perm_group_microphone
                        PermissionGroup.NOTIFICATION -> R.string.perm_group_notification
                        PermissionGroup.SYSTEM -> R.string.perm_group_system
                        PermissionGroup.VR -> R.string.perm_group_vr
                    }
                )
            }
    }

    /** Every BuildConfig field name referenced by an entry's flavor gates. Lets a test assert each resolves. */
    @get:VisibleForTesting
    val declaredFlavorGateFields: Set<String>
        get() = allEntries.flatMapTo(mutableSetOf()) { it.flavorGates }

    private fun evaluateFlavorGates(gates: Set<String>): Boolean {
        if (gates.isEmpty()) return true
        return gates.all { fieldName ->
            try {
                BuildConfig::class.java.getField(fieldName).getBoolean(null)
            } catch (e: NoSuchFieldException) {
                // A misspelled or removed gate field is a developer error, not a runtime state - surface it
                // loudly while keeping the safe (disabled) default so release never crashes on it.
                Timber.e("Permission flavor-gate references unknown BuildConfig field: %s", fieldName)
                false
            } catch (e: Exception) {
                false
            }
        }
    }
}
