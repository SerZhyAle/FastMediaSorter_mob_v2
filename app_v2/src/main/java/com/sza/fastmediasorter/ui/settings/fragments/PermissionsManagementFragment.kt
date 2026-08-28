package com.sza.fastmediasorter.ui.settings.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.permissions.PermissionGrantIntentFactory
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionStatus
import com.sza.fastmediasorter.domain.repository.PermissionRegistryRepository
import com.sza.fastmediasorter.domain.repository.PermissionRequestMarkerRepository
import com.sza.fastmediasorter.domain.usecase.BuildPermissionRowsUseCase
import com.sza.fastmediasorter.domain.usecase.CheckPermissionStatusUseCase
import com.sza.fastmediasorter.domain.usecase.PermissionAction
import com.sza.fastmediasorter.domain.usecase.ResolvePermissionActionUseCase
import com.sza.fastmediasorter.ui.common.permissions.PermissionDenialHandler
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PermissionsManagementFragment : Fragment() {

    companion object {
        private const val STATE_GRANT_ALL_IN_PROGRESS = "grant_all_in_progress"
        private const val STATE_SHOWN_SPECIAL_IN_RUN = "shown_special_in_run"
    }

    @Inject lateinit var registry: PermissionRegistryRepository

    @Inject lateinit var checkStatus: CheckPermissionStatusUseCase

    @Inject lateinit var buildRows: BuildPermissionRowsUseCase

    @Inject lateinit var resolveAction: ResolvePermissionActionUseCase

    @Inject lateinit var requestMarker: PermissionRequestMarkerRepository

    @Inject lateinit var grantIntentFactory: PermissionGrantIntentFactory

    private lateinit var adapter: PermissionRowAdapter

    // True while a "Grant all" run is walking the user through every denied permission. A run is:
    // 1) one requestMultiplePermissions() dialog for the regular permissions, then
    // 2) the special-permission system screens, opened one at a time.
    private var grantAllInProgress = false

    // Special permissions already shown in the current "Grant all" run - so a permission the user
    // declined is not re-opened forever (each return triggers the next one, not the same one).
    private val shownSpecialInRun = mutableSetOf<String>()

    private val requestMultiple = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshAdapter()
        updateGrantAllVisibility()
        // After the regular permissions dialog, walk through the denied special permissions.
        launchNextSpecialPermission()
    }

    private val requestSingle = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshAdapter()
        updateGrantAllVisibility()
    }

    // Special permissions (MANAGE_EXTERNAL_STORAGE, MANAGE_MEDIA, etc.) require dedicated system
    // settings screens and cannot be requested via requestMultiplePermissions(). We launch them for
    // result so that returning from the settings screen (via Back or grant) is handled cleanly.
    // On API 34+ the predictive back system can deliver a spurious back event to the underlying
    // Activity when a child Activity launched with plain startActivity() finishes - using
    // ActivityResultLauncher prevents that and gives us a reliable return callback.
    private val specialSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            refreshAdapter()
            updateGrantAllVisibility()
            if (grantAllInProgress) {
                // Mid "Grant all" run - continue with the next denied special permission. When none
                // remain, launchNextSpecialPermission() ends the run.
                launchNextSpecialPermission()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_permissions_management, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.d("S2178: permissions screen laid out, action divider above the list")

        // Survive config change / process death while a system permission screen is open, so the
        // "Grant all" run resumes from where it left off when specialSettingsLauncher fires.
        savedInstanceState?.let { state ->
            grantAllInProgress = state.getBoolean(STATE_GRANT_ALL_IN_PROGRESS, false)
            state.getStringArrayList(STATE_SHOWN_SPECIAL_IN_RUN)?.let { shownSpecialInRun.addAll(it) }
        }

        adapter = PermissionRowAdapter { entry, status ->
            when (resolveAction(entry, status)) {
                PermissionAction.RequestFromSystem -> requestPermission(entry)
                PermissionAction.OpenSpecialGrantScreen -> launchSpecialGrantSettings(entry)
                // Both a permanent denial and an already granted permission end on the app settings
                // page; the denial is explained by a snackbar first, so the user learns why the
                // system dialog will not come back.
                PermissionAction.OpenAppSettings ->
                    if (status == PermissionStatus.PERMANENTLY_DENIED) {
                        PermissionDenialHandler.handle(this, entry, status)
                    } else {
                        openAppSettings()
                    }
                PermissionAction.None -> Unit
            }
        }

        view.findViewById<RecyclerView>(R.id.rv_permissions).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PermissionsManagementFragment.adapter
        }

        view.findViewById<Button>(R.id.btn_grant_all).setOnClickListener {
            // Start a fresh "Grant all" run: regular permissions first (one batch dialog), then
            // each denied special permission via its own system screen, one at a time.
            grantAllInProgress = true
            shownSpecialInRun.clear()
            // Exclude special permissions - they require dedicated system settings screens
            // and are silently ignored when passed to requestMultiplePermissions().
            val requestable = registry.getEntries()
                .filterNot { resolveAction.isSpecialGrant(it) }
                .filter { isRequestable(checkStatus(requireContext(), it)) }
            if (requestable.isNotEmpty()) {
                requestable.forEach { requestMarker.markRequested(it.id) }
                // launchNextSpecialPermission() is called in the requestMultiple callback.
                requestMultiple.launch(requestable.map { it.manifestName }.toTypedArray())
            } else {
                // No regular permissions left - open the first pending special permission directly.
                launchNextSpecialPermission()
            }
        }

        view.findViewById<Button>(R.id.btn_open_system_settings).setOnClickListener {
            openAppSettings()
        }

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        refreshAdapter()
        updateGrantAllVisibility()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_GRANT_ALL_IN_PROGRESS, grantAllInProgress)
        outState.putStringArrayList(STATE_SHOWN_SPECIAL_IN_RUN, ArrayList(shownSpecialInRun))
    }

    override fun onResume() {
        super.onResume()
        refreshAdapter()
        updateGrantAllVisibility()
    }

    private fun refreshAdapter() = adapter.refresh(buildRows(registry.getEntries(), requireContext()))

    private fun updateGrantAllVisibility() {
        val hasPending = registry.getEntries().any { isRequestable(checkStatus(requireContext(), it)) }
        view?.findViewById<Button>(R.id.btn_grant_all)?.visibility = if (hasPending) View.VISIBLE else View.GONE
    }

    /**
     * A grant-all run can still change something only while the system would show a request. The same
     * predicate drives the batch contents and the button's visibility, so the button never promises a
     * run that would do nothing.
     */
    private fun isRequestable(status: PermissionStatus): Boolean =
        status == PermissionStatus.NOT_YET_REQUESTED || status == PermissionStatus.DENIED

    private fun requestPermission(entry: PermissionEntry) {
        requestMarker.markRequested(entry.id)
        requestSingle.launch(entry.manifestName)
    }

    /**
     * Advances a "Grant all" run: opens the system settings screen for the next still-denied
     * special permission that has not been shown yet in this run. When none remain, the run ends.
     */
    private fun launchNextSpecialPermission() {
        val entry = registry.getEntries()
            .filter { resolveAction.isSpecialGrant(it) }
            .filter { it.manifestName !in shownSpecialInRun }
            .firstOrNull { checkStatus(requireContext(), it) == PermissionStatus.DENIED }
        if (entry != null) {
            shownSpecialInRun += entry.manifestName
            launchSpecialGrantSettings(entry)
        } else {
            Timber.d("PermissionsManagement: grant-all run finished (shown ${shownSpecialInRun.size} special permissions)")
            grantAllInProgress = false
            shownSpecialInRun.clear()
        }
    }

    private fun launchSpecialGrantSettings(entry: PermissionEntry) {
        val target = grantIntentFactory.grantIntent(entry)
        try {
            specialSettingsLauncher.launch(target)
        } catch (e: ActivityNotFoundException) {
            // Some ROMs / emulators do not provide an activity for this intent action.
            Timber.e(e, "launchSpecialGrantSettings: no activity for ${target.action}, falling back to app settings")
            specialSettingsLauncher.launch(grantIntentFactory.appDetailsIntent())
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        })
    }
}
