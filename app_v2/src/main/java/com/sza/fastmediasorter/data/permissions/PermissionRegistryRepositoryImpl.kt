package com.sza.fastmediasorter.data.permissions

import android.Manifest
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGrantKind
import com.sza.fastmediasorter.domain.model.PermissionGroup
import com.sza.fastmediasorter.domain.model.PermissionGroupHeader
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import timber.log.Timber
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
            group = PermissionGroup.STORAGE,
            optional = false,
            minSdk = 30,
            grantKind = PermissionGrantKind.SYSTEM_SCREEN,
        ),
        PermissionEntry(
            id = "manage_media",
            manifestName = Manifest.permission.MANAGE_MEDIA,
            titleRes = R.string.perm_title_manage_media,
            descriptionRes = R.string.perm_desc_manage_media,
            iconRes = 0,
            group = PermissionGroup.STORAGE,
            optional = false,
            minSdk = 31,
            grantKind = PermissionGrantKind.SYSTEM_SCREEN,
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
            buildGates = setOf("SUPPORT_LOCAL_NETWORK"),
        ),
        // CONTACTS
        PermissionEntry(
            id = "read_contacts",
            manifestName = Manifest.permission.READ_CONTACTS,
            titleRes = R.string.perm_title_read_contacts,
            descriptionRes = R.string.perm_desc_read_contacts,
            iconRes = 0,
            group = PermissionGroup.CONTACTS,
            optional = true,
            buildGates = setOf("SUPPORT_LAUNCHER"),
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
        // LOCATION
        // S0786: opt-in geotag for in-app photo/video capture (embedding done by S0766). Registered here
        // so the user can grant/deny it from onboarding and Settings, not only via system settings; when
        // ungranted the capture simply carries no coordinates. Fine location drives an accurate geotag;
        // the capture path accepts coarse too, so a coarse-only grant still works.
        PermissionEntry(
            id = "access_fine_location",
            manifestName = Manifest.permission.ACCESS_FINE_LOCATION,
            titleRes = R.string.perm_title_location,
            descriptionRes = R.string.perm_desc_location,
            iconRes = 0,
            group = PermissionGroup.LOCATION,
            optional = true,
            minSdk = 23,
        ),
        // MICROPHONE
        PermissionEntry(
            id = "record_audio",
            manifestName = Manifest.permission.RECORD_AUDIO,
            titleRes = R.string.perm_title_record_audio,
            descriptionRes = R.string.perm_desc_record_audio,
            iconRes = 0,
            group = PermissionGroup.MICROPHONE,
            optional = true,
            buildGates = setOf("SUPPORT_AUDIO"),
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
            buildGates = setOf("ENABLE_PERSISTENT_AUDIO_PLAYBACK"),
        ),
        // SYSTEM
        PermissionEntry(
            id = "battery_optimization",
            manifestName = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            titleRes = R.string.perm_title_battery_optimization,
            descriptionRes = R.string.perm_desc_battery_optimization,
            iconRes = 0,
            group = PermissionGroup.SYSTEM,
            optional = true,
            grantKind = PermissionGrantKind.SYSTEM_SCREEN,
        ),
        // S0429: the manifest name is a label here, not a request. BIND_NOTIFICATION_LISTENER_SERVICE is
        // held by the <service> declaration in src/launcherEnabled and can only be turned on by the user
        // on a system screen - exactly how manage_external_storage above is listed. It earns its row so
        // the capability is enumerated where a user looks for it, not only where it was switched on.
        PermissionEntry(
            id = "notification_listener",
            manifestName = Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            titleRes = R.string.perm_title_notification_listener,
            descriptionRes = R.string.perm_desc_notification_listener,
            iconRes = 0,
            group = PermissionGroup.SYSTEM,
            optional = true,
            buildGates = setOf("SUPPORT_LAUNCHER"),
            grantKind = PermissionGrantKind.SYSTEM_SCREEN,
        ),
        // S0241: VR group entries (hand_tracking, headset_camera) removed with the OpenXR stack.
    )

    override fun getEntries(): List<PermissionEntry> =
        allEntries.filter { entry ->
            entry.minSdk <= Build.VERSION.SDK_INT &&
            entry.maxSdk >= Build.VERSION.SDK_INT &&
            evaluateBuildGates(entry.buildGates)
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
                        PermissionGroup.LOCATION -> R.string.perm_group_location
                        PermissionGroup.MICROPHONE -> R.string.perm_group_microphone
                        PermissionGroup.NOTIFICATION -> R.string.perm_group_notification
                        PermissionGroup.SYSTEM -> R.string.perm_group_system
                        PermissionGroup.VR -> R.string.perm_group_vr
                        PermissionGroup.CONTACTS -> R.string.perm_group_contacts
                    }
                )
            }
    }

    /** Every BuildConfig field name referenced by an entry's build gates. Lets a test assert each resolves. */
    @get:VisibleForTesting
    val declaredBuildGateFields: Set<String>
        get() = allEntries.flatMapTo(mutableSetOf()) { it.buildGates }

    private fun evaluateBuildGates(gates: Set<String>): Boolean {
        if (gates.isEmpty()) return true
        return gates.all { fieldName -> resolveBuildGate(fieldName) }
    }

    /**
     * S0970: resolve a gate name to its BuildConfig value via a compile-time map, NOT reflection.
     * R8 constant-folds `public static final boolean` BuildConfig fields into their call sites and then
     * strips the field declarations, so `BuildConfig::class.java.getField(name)` throws
     * NoSuchFieldException on a minified release build - the old code then logged
     * "unknown BuildConfig field" and silently disabled the permission (e.g. audio) on every release.
     * Direct references survive R8 with the correct inlined value. An unmapped name is a developer
     * error (a new gate string with no entry here); surface it and keep the safe default.
     */
    // S1379: these direct reads deliberately stay. This is a NAME-to-value table, not a consumer
    // guard - the caller supplies a gate string and this is the one place that resolves it. Routing
    // it through the capability contract would mean giving that contract a string lookup it does not
    // have, and would still leave the direct read somewhere. Consumer guards go through
    // CapabilityAvailability instead.
    private val buildGateValues: Map<String, Boolean> = mapOf(
        "SUPPORT_AUDIO" to BuildConfig.SUPPORT_AUDIO,
        "SUPPORT_MIC_RECORDING" to BuildConfig.SUPPORT_MIC_RECORDING,
        "SUPPORT_LOCAL_NETWORK" to BuildConfig.SUPPORT_LOCAL_NETWORK,
        "ENABLE_PERSISTENT_AUDIO_PLAYBACK" to BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK,
        "SUPPORT_LAUNCHER" to BuildConfig.SUPPORT_LAUNCHER,
        "IS_NO_LEGAL_FLAVOR" to BuildConfig.IS_NO_LEGAL_FLAVOR,
        "DECLARES_SCREEN_CAPTURE" to BuildConfig.DECLARES_SCREEN_CAPTURE,
        "DECLARES_OVERLAY_PERMISSION" to BuildConfig.DECLARES_OVERLAY_PERMISSION,
        "DECLARES_BATTERY_OPTIMIZATION" to BuildConfig.DECLARES_BATTERY_OPTIMIZATION,
    )

    /**
     * The gate names this class can resolve. A test asserts [declaredBuildGateFields] is a subset,
     * which is what catches a gate string that has no value here: the old check only proved a
     * BuildConfig field of that name existed, so a name that was real but unmapped still fell
     * through to `false` and silently dropped its permission.
     */
    @get:VisibleForTesting
    val mappedBuildGateFields: Set<String>
        get() = buildGateValues.keys

    private fun resolveBuildGate(fieldName: String): Boolean =
        buildGateValues[fieldName] ?: run {
            Timber.e("Permission build-gate references unmapped BuildConfig field: %s", fieldName)
            false
        }
}
