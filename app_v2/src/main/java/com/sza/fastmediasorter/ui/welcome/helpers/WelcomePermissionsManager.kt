package com.sza.fastmediasorter.ui.welcome.helpers

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.PageWelcomePermissionsBinding
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGroupHeader
import com.sza.fastmediasorter.domain.model.PermissionStatus
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.CheckPermissionStatusUseCase
import com.sza.fastmediasorter.ui.common.permissions.PermissionDenialHandler
import com.sza.fastmediasorter.ui.settings.fragments.PermissionRow
import com.sza.fastmediasorter.ui.settings.fragments.PermissionRowAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Owns the welcome permissions page (S0402): builds the adaptive permission set from the user's
 * functionality choices (all-files only when the file manager is on, RECORD_AUDIO only when audio is
 * on, POST_NOTIFICATIONS in the common 33+ batch), renders the rows into the page, and runs the
 * grant-all stepwise flow (regular batch then special-permission walk) via Activity-registered
 * ActivityResult launchers. The Settings permission screen keeps the full, non-adaptive list.
 *
 * Field-injected into WelcomeActivity (Hilt deps) then [attach]ed to the Activity so it can own the
 * launchers; the grant-all run state survives recreation through [onSaveInstanceState]/[onRestoreInstanceState].
 */
class WelcomePermissionsManager @Inject constructor(
    private val registry: PermissionRegistryRepository,
    private val settingsRepository: SettingsRepository,
    private val checkStatus: CheckPermissionStatusUseCase,
) {

    // These permissions cannot be granted via requestPermission() - each requires a dedicated system
    // settings screen and is silently ignored when passed to requestMultiplePermissions().
    private val specialGrantPermissions = setOf(
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        Manifest.permission.MANAGE_MEDIA,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    )

    private var activity: FragmentActivity? = null
    private var binding: PageWelcomePermissionsBinding? = null
    private val adapter = PermissionRowAdapter { entry, status -> onRowAction(entry, status) }

    // Adaptive permission list for the current functionality choices; resolved asynchronously from
    // settings in attach(). Empty until the first settings emission arrives (a single DataStore read).
    private var welcomeEntries: List<PermissionEntry> = emptyList()

    private var requestMultiple: ActivityResultLauncher<Array<String>>? = null
    private var requestSingle: ActivityResultLauncher<String>? = null
    private var specialSettingsLauncher: ActivityResultLauncher<Intent>? = null

    // True while a "Grant all" run is walking the user through every denied permission: one
    // requestMultiplePermissions() dialog for the regular permissions, then the special-permission
    // system screens opened one at a time.
    private var grantAllInProgress = false

    // Special permissions already shown in the current run, so a declined permission is not re-opened
    // forever (each return triggers the next one, not the same one).
    private val shownSpecialInRun = mutableSetOf<String>()

    /** Wire the manager to its host Activity (owns the ActivityResult launchers). */
    fun attach(activity: FragmentActivity) {
        this.activity = activity
        val registryHost = activity.activityResultRegistry

        requestMultiple = registryHost.register(
            KEY_REQUEST_MULTIPLE,
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            refreshRows()
            // After the regular permissions dialog, walk through the denied special permissions.
            launchNextSpecialPermission()
        }

        requestSingle = registryHost.register(
            KEY_REQUEST_SINGLE,
            ActivityResultContracts.RequestPermission(),
        ) {
            refreshRows()
        }

        // Special permissions require dedicated system settings screens and cannot be requested via
        // requestMultiplePermissions(). Launching for result gives a reliable return callback and, on
        // API 34+, avoids the predictive-back system delivering a spurious back event to this Activity.
        specialSettingsLauncher = registryHost.register(
            KEY_SPECIAL_SETTINGS,
            ActivityResultContracts.StartActivityForResult(),
        ) {
            refreshRows()
            if (grantAllInProgress) {
                // Mid-run: continue with the next denied special permission. When none remain,
                // launchNextSpecialPermission() ends the run.
                launchNextSpecialPermission()
            }
        }

        // Refresh statuses whenever the page returns to the foreground (returning from a system
        // settings screen, or the user toggling a permission in OS settings), mirroring the Settings
        // fragment's onResume refresh.
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                refreshRows()
            }
        })

        // Resolve the adaptive set from the user's functionality choices, then render.
        activity.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            welcomeEntries = registry.getWelcomeEntries(
                allFiles = settings.allFiles,
                audioEnabled = settings.supportAudio,
            )
            refreshRows()
        }
    }

    /** Render the adaptive permission rows + grant-all affordance into the page. */
    fun bind(binding: PageWelcomePermissionsBinding) {
        Timber.d("S0402: permissions page bound, adaptiveEntries=${welcomeEntries.size}")
        this.binding = binding
        binding.rvPermissions.apply {
            layoutManager = LinearLayoutManager(binding.root.context)
            adapter = this@WelcomePermissionsManager.adapter
        }
        binding.btnGrantAll.setOnClickListener { startGrantAllRun() }
        binding.btnOpenSettings.setOnClickListener { openAppSettings() }
        refreshRows()
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_GRANT_ALL_IN_PROGRESS, grantAllInProgress)
        outState.putStringArrayList(STATE_SHOWN_SPECIAL_IN_RUN, ArrayList(shownSpecialInRun))
    }

    fun onRestoreInstanceState(state: Bundle?) {
        state ?: return
        grantAllInProgress = state.getBoolean(STATE_GRANT_ALL_IN_PROGRESS, false)
        state.getStringArrayList(STATE_SHOWN_SPECIAL_IN_RUN)?.let {
            shownSpecialInRun.clear()
            shownSpecialInRun.addAll(it)
        }
    }

    private fun onRowAction(entry: PermissionEntry, status: PermissionStatus) {
        val act = activity ?: return
        when (status) {
            PermissionStatus.DENIED ->
                if (entry.manifestName in specialGrantPermissions) launchSpecialGrantSettings(entry)
                else requestSingle?.launch(entry.manifestName)
            PermissionStatus.PERMANENTLY_DENIED -> PermissionDenialHandler.handle(act, entry)
            PermissionStatus.GRANTED -> openAppSettings()
            PermissionStatus.NOT_APPLICABLE -> Unit
        }
    }

    private fun startGrantAllRun() {
        val act = activity ?: return
        // Start a fresh run: regular permissions first (one batch dialog), then each denied special
        // permission via its own system screen, one at a time.
        grantAllInProgress = true
        shownSpecialInRun.clear()
        // Special permissions are excluded - they require dedicated system screens and are silently
        // ignored by requestMultiplePermissions().
        val denied = welcomeEntries
            .filter { checkStatus(act, it) == PermissionStatus.DENIED }
            .filter { it.manifestName !in specialGrantPermissions }
            .map { it.manifestName }
            .toTypedArray()
        if (denied.isNotEmpty()) {
            // launchNextSpecialPermission() runs in the requestMultiple callback.
            requestMultiple?.launch(denied)
        } else {
            // No regular permissions left - open the first pending special permission directly.
            launchNextSpecialPermission()
        }
    }

    /**
     * Advances a "Grant all" run: opens the system settings screen for the next still-denied special
     * permission not yet shown in this run. When none remain, the run ends.
     */
    private fun launchNextSpecialPermission() {
        val act = activity ?: return
        val entry = welcomeEntries
            .filter { it.manifestName in specialGrantPermissions }
            .filter { it.manifestName !in shownSpecialInRun }
            .firstOrNull { checkStatus(act, it) == PermissionStatus.DENIED }
        if (entry != null) {
            shownSpecialInRun += entry.manifestName
            launchSpecialGrantSettings(entry)
        } else {
            Timber.i("WelcomePermissions: grant-all run finished (shown ${shownSpecialInRun.size} special permissions)")
            grantAllInProgress = false
            shownSpecialInRun.clear()
        }
    }

    private fun launchSpecialGrantSettings(entry: PermissionEntry) {
        val act = activity ?: return
        val launcher = specialSettingsLauncher ?: return
        val pkg = act.packageName
        val intent = when (entry.manifestName) {
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> try {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$pkg")
                    }
                } catch (e: Exception) {
                    Timber.i(e, "All-files-access app intent unavailable, using global manage-all-files screen")
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                }
                else -> null
            }
            Manifest.permission.MANAGE_MEDIA -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                        data = Uri.parse("package:$pkg")
                    }
                else -> null
            }
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ->
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$pkg")
                }
            else -> null
        }
        val target = intent ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
        }
        try {
            launcher.launch(target)
        } catch (e: ActivityNotFoundException) {
            // Some ROMs / emulators do not provide an activity for this intent action.
            Timber.w(e, "No activity for ${target.action}, falling back to app settings")
            launcher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
            })
        }
    }

    private fun openAppSettings() {
        val act = activity ?: return
        act.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", act.packageName, null)
        })
    }

    /** Rebuild the rows from the cached adaptive set and update the grant-all CTA visibility. */
    private fun refreshRows() {
        val act = activity ?: return
        adapter.refresh(buildRows(act))
        val hasDenied = welcomeEntries.any { checkStatus(act, it) == PermissionStatus.DENIED }
        binding?.btnGrantAll?.visibility = if (hasDenied) View.VISIBLE else View.GONE
    }

    private fun buildRows(act: FragmentActivity): List<PermissionRow> {
        val groups = registry.getGroups()
        val rows = mutableListOf<PermissionRow>()
        val required = welcomeEntries.filterNot { it.optional }
        groups.forEach { header ->
            val groupEntries = required.filter { it.group == header.group }
            if (groupEntries.isEmpty()) return@forEach
            rows += PermissionRow.Header(header)
            groupEntries.forEach { entry ->
                rows += PermissionRow.Entry(entry, checkStatus(act, entry))
            }
        }
        val optional = welcomeEntries.filter { it.optional }
        if (optional.isNotEmpty()) {
            rows += PermissionRow.Header(
                PermissionGroupHeader(
                    group = optional.first().group,
                    titleRes = R.string.perm_group_optional,
                )
            )
            optional.forEach { entry ->
                rows += PermissionRow.Entry(entry, checkStatus(act, entry))
            }
        }
        return rows
    }

    companion object {
        private const val STATE_GRANT_ALL_IN_PROGRESS = "welcome_perm_grant_all_in_progress"
        private const val STATE_SHOWN_SPECIAL_IN_RUN = "welcome_perm_shown_special_in_run"

        // Stable ActivityResult keys so launchers survive recreation without auto-unregister timing
        // issues (the manager is field-injected into the Activity, not a lifecycle-owning component).
        private const val KEY_REQUEST_MULTIPLE = "welcome_perm_request_multiple"
        private const val KEY_REQUEST_SINGLE = "welcome_perm_request_single"
        private const val KEY_SPECIAL_SETTINGS = "welcome_perm_special_settings"
    }
}
