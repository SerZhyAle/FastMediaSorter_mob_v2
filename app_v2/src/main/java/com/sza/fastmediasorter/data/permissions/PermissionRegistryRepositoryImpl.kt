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
import com.sza.fastmediasorter.domain.model.PermissionRationale
import com.sza.fastmediasorter.domain.model.PermissionTask
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
            group = PermissionGroup.STORAGE,
            optional = false,
            minSdk = 23,
            maxSdk = 32,
            // S1436: the storage row carries the plain "access to your media" paragraph - what the startup
            // gate explains below API 30, and what any media-access request explains at any level. The
            // manifest name the platform actually wants differs per API; the sentence does not.
            rationaleRes = R.string.perm_rationale_read_external_storage,
            taskAddenda = mapOf(
                PermissionTask.LOCAL_RESOURCE_CREATION to
                    R.string.perm_addendum_read_external_storage_local_resource,
            ),
        ),
        // S1436: the window is the manifest's own `android:maxSdkVersion="28"`. Without this row the
        // permissions screen and onboarding could not request write at all - and the storage check
        // has always demanded it on API 23-28, so the screen reported granted while the app did not.
        PermissionEntry(
            id = "write_external_storage",
            manifestName = Manifest.permission.WRITE_EXTERNAL_STORAGE,
            titleRes = R.string.perm_title_write_external_storage,
            descriptionRes = R.string.perm_desc_write_external_storage,
            group = PermissionGroup.STORAGE,
            optional = false,
            minSdk = 23,
            maxSdk = 28,
        ),
        PermissionEntry(
            id = "read_media_images",
            manifestName = Manifest.permission.READ_MEDIA_IMAGES,
            titleRes = R.string.perm_title_read_media_images,
            descriptionRes = R.string.perm_desc_read_media_images,
            group = PermissionGroup.STORAGE,
            optional = false,
            minSdk = 33,
        ),
        PermissionEntry(
            id = "read_media_video",
            manifestName = Manifest.permission.READ_MEDIA_VIDEO,
            titleRes = R.string.perm_title_read_media_video,
            descriptionRes = R.string.perm_desc_read_media_video,
            group = PermissionGroup.STORAGE,
            optional = false,
            minSdk = 33,
        ),
        PermissionEntry(
            id = "read_media_audio",
            manifestName = Manifest.permission.READ_MEDIA_AUDIO,
            titleRes = R.string.perm_title_read_media_audio,
            descriptionRes = R.string.perm_desc_read_media_audio,
            group = PermissionGroup.STORAGE,
            optional = false,
            minSdk = 33,
        ),
        // S2012: same shape as the activity_recognition gate below - the gate names the declaration
        // site, and MANAGE_EXTERNAL_STORAGE is declared only by src/noLegal/AndroidManifest.xml since
        // Google Play refused two updates over it as `Not a core feature`. Without the gate the store
        // flavors would list a row whose only action opens a system screen their merged manifest
        // gives them no standing on. IS_NO_LEGAL_FLAVOR rather than a new DECLARES_* field because the
        // runtime half of this ticket reads the merged manifest directly, so the registry table is the
        // gate's only consumer and it mirrors that one file exactly.
        PermissionEntry(
            id = "manage_external_storage",
            manifestName = Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            titleRes = R.string.perm_title_manage_external_storage,
            descriptionRes = R.string.perm_desc_manage_external_storage,
            group = PermissionGroup.STORAGE,
            optional = false,
            minSdk = 30,
            buildGates = setOf("IS_NO_LEGAL_FLAVOR"),
            grantKind = PermissionGrantKind.SYSTEM_SCREEN,
            // S1436: one text where six stood - the row keeps its short label, the paragraph carries the
            // meaning all six carried between them, and what is specific to picking a folder is the
            // addendum rather than a seventh wording owned by the picker.
            rationaleRes = R.string.perm_rationale_manage_external_storage,
            taskAddenda = mapOf(
                PermissionTask.FOLDER_PICKING to R.string.perm_addendum_manage_external_storage_folder_picking,
            ),
        ),
        PermissionEntry(
            id = "manage_media",
            manifestName = Manifest.permission.MANAGE_MEDIA,
            titleRes = R.string.perm_title_manage_media,
            descriptionRes = R.string.perm_desc_manage_media,
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
            group = PermissionGroup.NETWORK,
            optional = true,
            minSdk = 37,
            buildGates = setOf("SUPPORT_LOCAL_NETWORK"),
            // S1436: the union of the two lists that disagreed - SMB, SFTP, FTP and DLNA. Chromecast is
            // deliberately not named: `docs/FLAVOR_MATRIX.md` has SUPPORT_CAST false in `vr`, which still
            // ships local network, so the sentence would be untrue in the one build it is checked against.
            rationaleRes = R.string.perm_rationale_access_local_network,
        ),
        // CONTACTS
        PermissionEntry(
            id = "read_contacts",
            manifestName = Manifest.permission.READ_CONTACTS,
            titleRes = R.string.perm_title_read_contacts,
            descriptionRes = R.string.perm_desc_read_contacts,
            group = PermissionGroup.CONTACTS,
            optional = true,
            buildGates = setOf("SUPPORT_LAUNCHER"),
            // S1206: the row's four-word label is a list entry, not an explanation - the request made
            // while pinning a person needs the paragraph, and the pinning sentence on top of it.
            taskAddenda = mapOf(
                PermissionTask.CONTACT_CELL_PINNING to R.string.perm_addendum_contacts_cell_pinning,
            ),
            rationaleRes = R.string.perm_rationale_read_contacts,
        ),
        // CAMERA
        PermissionEntry(
            id = "camera",
            manifestName = Manifest.permission.CAMERA,
            titleRes = R.string.perm_title_camera,
            descriptionRes = R.string.perm_desc_camera,
            group = PermissionGroup.CAMERA,
            optional = true,
            // S1436: the description said only "shoot inside the app" while the companion pairing scan
            // has always used the same camera - a use the list never admitted to.
            taskAddenda = mapOf(
                PermissionTask.QR_PAIRING to R.string.perm_addendum_camera_qr_pairing,
            ),
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
            group = PermissionGroup.LOCATION,
            optional = true,
            minSdk = 23,
            // S1436: the geotag toggle used to request this with nothing said at all - the paragraph the
            // request now shows is this one, so the list and the request answer the question alike.
            rationaleRes = R.string.perm_rationale_location,
        ),
        // S1436: the coarse permission is declared beside the fine one and must be requested with it -
        // from API 31 the platform ignores a fine-only request. Its own row also stops the list from
        // claiming location is ungranted when the user chose the approximate answer, which the geotag
        // path accepts.
        PermissionEntry(
            id = "access_coarse_location",
            manifestName = Manifest.permission.ACCESS_COARSE_LOCATION,
            titleRes = R.string.perm_title_access_coarse_location,
            descriptionRes = R.string.perm_desc_access_coarse_location,
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
            group = PermissionGroup.MICROPHONE,
            optional = true,
            // S1459: gated on "does this build declare the mic", not on the voice-note feature.
            // SUPPORT_MIC_RECORDING names S0100's Browse voice notes, which lite excludes while still
            // recording video with audio - so lite asked for the permission and then hid its row.
            buildGates = setOf("DECLARES_MIC_RECORDING"),
            // S1436: four wordings across five call sites became this one, and what a screen recording
            // adds - that its sound comes from here - is the addendum rather than a fifth wording.
            rationaleRes = R.string.perm_rationale_record_audio,
            taskAddenda = mapOf(
                PermissionTask.SCREEN_RECORDING to R.string.perm_addendum_record_audio_screen_recording,
            ),
        ),
        // NOTIFICATION
        PermissionEntry(
            id = "post_notifications",
            manifestName = Manifest.permission.POST_NOTIFICATIONS,
            titleRes = R.string.perm_title_post_notifications,
            descriptionRes = R.string.perm_desc_post_notifications,
            group = PermissionGroup.NOTIFICATION,
            optional = true,
            minSdk = 33,
            buildGates = setOf("ENABLE_PERSISTENT_AUDIO_PLAYBACK"),
            shownInWelcomeDespiteGates = true,
            // S1436: the screen-recording message used to bundle this permission and the microphone into
            // one sentence, so a denial never said which of the two was missing. The scheduled-operations
            // sentence replaces a hand-written button label that explained nothing.
            taskAddenda = mapOf(
                PermissionTask.SCREEN_RECORDING to R.string.perm_addendum_post_notifications_screen_recording,
                PermissionTask.SCHEDULED_OPERATIONS to R.string.perm_addendum_post_notifications_scheduled_ops,
            ),
        ),
        // SYSTEM
        PermissionEntry(
            id = "battery_optimization",
            manifestName = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            titleRes = R.string.perm_title_battery_optimization,
            descriptionRes = R.string.perm_desc_battery_optimization,
            group = PermissionGroup.SYSTEM,
            optional = true,
            buildGates = setOf("DECLARES_BATTERY_OPTIMIZATION"),
            grantKind = PermissionGrantKind.SYSTEM_SCREEN,
            // S1436: scheduled operations used to open the system exclusion screen with nothing said at
            // all - the biggest jump of any request here, and the one that explained itself least.
            rationaleRes = R.string.perm_rationale_battery_optimization,
        ),
        // S1436: the gesture toggle stays the feature switch and keeps its own grant route - this row
        // reports only whether the permission is held, which is a different state from whether the
        // gesture strip is switched on. Gated on the variant that declares the permission.
        PermissionEntry(
            id = "system_alert_window",
            manifestName = Manifest.permission.SYSTEM_ALERT_WINDOW,
            titleRes = R.string.perm_title_system_alert_window,
            descriptionRes = R.string.perm_desc_system_alert_window,
            group = PermissionGroup.SYSTEM,
            optional = true,
            minSdk = 23,
            buildGates = setOf("DECLARES_OVERLAY_PERMISSION"),
            grantKind = PermissionGrantKind.SYSTEM_SCREEN,
        ),
        // S1436: declared only in the build without legal restrictions, so the row exists only there.
        // The in-flow request during an actual install stays where it is - this row is the list entry,
        // not a replacement for asking at the moment it matters.
        PermissionEntry(
            id = "request_install_packages",
            manifestName = Manifest.permission.REQUEST_INSTALL_PACKAGES,
            titleRes = R.string.perm_title_request_install_packages,
            descriptionRes = R.string.perm_desc_request_install_packages,
            group = PermissionGroup.SYSTEM,
            optional = true,
            minSdk = 26,
            buildGates = setOf("IS_NO_LEGAL_FLAVOR"),
            grantKind = PermissionGrantKind.SYSTEM_SCREEN,
        ),
        // S1436: the manifest name is the permission the capture path declares, so phase 04 can pair
        // this row with a real manifest entry - but the consent itself is the MediaProjection dialog,
        // which the system raises again on every capture and cannot be answered in advance.
        PermissionEntry(
            id = "screen_capture_consent",
            manifestName = Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,
            titleRes = R.string.perm_title_screen_capture_consent,
            descriptionRes = R.string.perm_desc_screen_capture_consent,
            group = PermissionGroup.SYSTEM,
            optional = true,
            buildGates = setOf("DECLARES_SCREEN_CAPTURE"),
            grantKind = PermissionGrantKind.PER_USE_CONSENT,
        ),
        // S1436: found by the phase-04 parity gate on its first run. `src/launcherEnabled` declares this
        // and S1415's SIM indicators request it from their own code path, so before this row it was a
        // dangerous permission the list could not show and the user could not review in one place.
        PermissionEntry(
            id = "read_phone_state",
            manifestName = Manifest.permission.READ_PHONE_STATE,
            titleRes = R.string.perm_title_read_phone_state,
            descriptionRes = R.string.perm_desc_read_phone_state,
            group = PermissionGroup.SYSTEM,
            optional = true,
            minSdk = 23,
            buildGates = setOf("SUPPORT_LAUNCHER"),
        ),
        // S1623: the gate names the declaration site, not the feature. S1179 put this row on
        // SUPPORT_LAUNCHER while `src/launcherEnabled` declared the permission; S1614 moved the
        // declaration into `src/noLegal/AndroidManifest.xml` because Play treats ACTIVITY_RECOGNITION as
        // a health feature, and the gate stayed behind - standard then offered a row for a permission its
        // merged manifest does not carry. IS_NO_LEGAL_FLAVOR mirrors that file exactly, the way the
        // request_install_packages row above does. Move the declaration back and this gate moves with it.
        PermissionEntry(
            id = "activity_recognition",
            manifestName = Manifest.permission.ACTIVITY_RECOGNITION,
            titleRes = R.string.perm_title_activity_recognition,
            descriptionRes = R.string.perm_desc_activity_recognition,
            group = PermissionGroup.SYSTEM,
            optional = true,
            minSdk = 29,
            buildGates = setOf("IS_NO_LEGAL_FLAVOR"),
            rationaleRes = R.string.perm_rationale_activity_recognition,
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
            group = PermissionGroup.SYSTEM,
            optional = true,
            buildGates = setOf("SUPPORT_LAUNCHER"),
            grantKind = PermissionGrantKind.SYSTEM_SCREEN,
        ),
        // S1433: Network Monitor rows. Declared by src/networkMonitor, which only standard and
        // noLegal mount, so the build gate is what keeps the row out of the flavors whose merged
        // manifest never declares the permission - the parity test fails those variants otherwise.
        PermissionEntry(
            id = "nearby_wifi_devices",
            manifestName = Manifest.permission.NEARBY_WIFI_DEVICES,
            titleRes = R.string.perm_title_nearby_wifi_devices,
            descriptionRes = R.string.perm_desc_nearby_wifi_devices,
            group = PermissionGroup.NETWORK,
            optional = true,
            minSdk = 33,
            buildGates = setOf("SUPPORT_NETWORK_MONITOR"),
        ),
        PermissionEntry(
            id = "bluetooth_connect",
            manifestName = Manifest.permission.BLUETOOTH_CONNECT,
            titleRes = R.string.perm_title_bluetooth_connect,
            descriptionRes = R.string.perm_desc_bluetooth_connect,
            group = PermissionGroup.NETWORK,
            optional = true,
            minSdk = 31,
            buildGates = setOf("SUPPORT_NETWORK_MONITOR"),
        ),
        // S0241: VR group entries (hand_tracking, headset_camera) removed with the OpenXR stack.
    )

    override fun getEntries(): List<PermissionEntry> =
        allEntries.filter { entry ->
            entry.minSdk <= Build.VERSION.SDK_INT &&
            entry.maxSdk >= Build.VERSION.SDK_INT &&
            evaluateBuildGates(entry.buildGates)
        }

    override fun getWelcomeEntries(): List<PermissionEntry> =
        // Show every permission this build can request; the user decides which to grant. No narrowing by
        // functionality toggles - opting out happens by leaving a permission ungranted, not by hiding it.
        // The SDK window binds every entry; the build gates bind only those not declared welcome-only.
        allEntries.filter { entry ->
            entry.minSdk <= Build.VERSION.SDK_INT &&
            entry.maxSdk >= Build.VERSION.SDK_INT &&
            (entry.shownInWelcomeDespiteGates || evaluateBuildGates(entry.buildGates))
        }

    override fun getRationale(manifestName: String, task: PermissionTask?): PermissionRationale? {
        // Matched against the raw list, not getEntries(): an explanation is asked for at the moment the
        // work needs the permission, and a row filtered out by an SDK window or a build gate would then
        // silently produce no text at all.
        val entry = allEntries.firstOrNull { it.manifestName == manifestName } ?: return null
        return PermissionRationale(
            titleRes = entry.titleRes,
            summaryRes = entry.descriptionRes,
            detailRes = entry.rationaleRes ?: entry.descriptionRes,
            taskAddendumRes = task?.let { entry.taskAddenda[it] },
        )
    }

    override fun getGroups(): List<PermissionGroupHeader> {
        // SDK-applicable groups, ignoring flavor gates: a group whose only entry is flavor-gated-out
        // still gets a header, so the welcome set (which keeps an entry marked shownInWelcomeDespiteGates
        // past its own build gate) can render it. Both Settings and welcome buildRows
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
                        PermissionGroup.CONTACTS -> R.string.perm_group_contacts
                    }
                )
            }
    }

    /**
     * S1454: every row's manifest name as the registry defines it, before any build gate or SDK window
     * narrows the set. A staleness check needs this to tell a row that was deleted from one that merely
     * has its gate off in the variant under test - against the gated set, every flavor-specific row
     * looks deleted.
     */
    @get:VisibleForTesting
    val allRowManifestNames: Set<String>
        get() = allEntries.map { it.manifestName }.toSet()

    /** Every BuildConfig field name referenced by an entry's build gates. Lets a test assert each resolves. */
    @get:VisibleForTesting
    val declaredBuildGateFields: Set<String>
        get() = allEntries.flatMapTo(mutableSetOf()) { it.buildGates }

    /**
     * S1436: every row this build can ever show, with the SDK window deliberately ignored. The
     * manifest-parity test compares against this and not [getEntries], because a manifest declaration
     * is not SDK-scoped the way a row is: `write_external_storage` is declared and real, and merely
     * out of window on the machine the test happens to run on.
     */
    @get:VisibleForTesting
    val entriesForBuild: List<PermissionEntry>
        get() = allEntries.filter { evaluateBuildGates(it.buildGates) }

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
        "DECLARES_MIC_RECORDING" to BuildConfig.DECLARES_MIC_RECORDING,
        "SUPPORT_LOCAL_NETWORK" to BuildConfig.SUPPORT_LOCAL_NETWORK,
        "ENABLE_PERSISTENT_AUDIO_PLAYBACK" to BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK,
        "SUPPORT_LAUNCHER" to BuildConfig.SUPPORT_LAUNCHER,
        "SUPPORT_NETWORK_MONITOR" to BuildConfig.SUPPORT_NETWORK_MONITOR,
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
