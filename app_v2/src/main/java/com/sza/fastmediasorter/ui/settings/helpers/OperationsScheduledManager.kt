package com.sza.fastmediasorter.ui.settings.helpers

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.PermissionTask
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.ui.common.permissions.permissionRationale
import com.sza.fastmediasorter.ui.common.permissions.permissionRationaleShort
import com.sza.fastmediasorter.ui.dialog.SchedOpPickSide
import com.sza.fastmediasorter.ui.dialog.ScheduledOperationDialog
import com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog
import com.sza.fastmediasorter.ui.settings.ScheduledOperationsAdapter
import com.sza.fastmediasorter.ui.settings.ScheduledOperationsViewModel
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the scheduled-operations subgroup embedded in the Operations tab: the list adapter, the
 * create/edit/delete dialogs, the run-log viewer, the notification + battery-optimization permission
 * flow, and the auto-open / auto-expand intents. Stays gated by [BuildConfig.ENABLE_SCHEDULED_OPERATIONS]
 * and the master toggle. The notification permission launcher stays registered in the fragment and is
 * injected here.
 */
class OperationsScheduledManager(
    private val binding: FragmentSettingsDestinationsBinding,
    private val viewModel: SettingsViewModel,
    private val scheduledViewModel: ScheduledOperationsViewModel,
    private val fragment: Fragment,
    private val mediaCapabilities: MediaCapabilities,
    private val notificationsPermissionLauncher: ActivityResultLauncher<String>,
    private val folderPickerLauncher: ActivityResultLauncher<Uri?>,
    private val isUpdatingFromSettings: () -> Boolean,
) {

    private lateinit var scheduledAdapter: ScheduledOperationsAdapter

    // S1009: the currently shown scheduled-op dialog + which side requested a local-folder pick, so the
    // SAF result (delivered to the fragment launcher) routes back to the right field.
    private var currentDialog: ScheduledOperationDialog? = null
    private var pendingPickSide: SchedOpPickSide? = null

    // S0998: debounces the list->toggle reconcile so the stateIn(emptyList) seed emit and the real
    // Room emit coalesce into one settled reconcile (no off->on flicker / double write on screen open).
    private var reconcileJob: Job? = null

    fun setup() {
        updateScheduledNotificationPermissionButton()
        binding.rowEnableScheduledOps.setTrailingControl(binding.layoutScheduledActions)

        binding.rowEnableScheduledOps.setOnCheckedChangeListener { isChecked ->
            if (isUpdatingFromSettings()) return@setOnCheckedChangeListener
            val current = viewModel.settings.value
            viewModel.updateSettings(current.copy(enableScheduledOperations = isChecked))
            binding.containerScheduledContent.isVisible = isChecked
            binding.layoutScheduledActions.isVisible = isChecked
            if (isChecked) checkAndRequestScheduledPermissions()
        }

        scheduledAdapter = ScheduledOperationsAdapter(
            onToggle = { op -> scheduledViewModel.toggleEnabled(op) },
            onEdit = { op -> showScheduledOperationDialog(op) },
            onDelete = { op -> confirmDeleteScheduledOp(op) },
            onRunNow = { op -> scheduledViewModel.runNow(op.id) },
            resourceNameProvider = { id ->
                viewModel.resources.value.find { it.id == id }?.name
            }
        )
        binding.rvScheduledOps.layoutManager = LinearLayoutManager(fragment.requireContext())
        // S1397: no item animator. Deleting the last operation makes reconcileToggleWithList() collapse
        // containerScheduledContent while DefaultItemAnimator still holds animating views, and the
        // wrap_content list inside a NestedScrollView can re-measure mid-animation - both leave a
        // tmpDetached holder without a parent, and RecyclerView throws when the animation ends.
        binding.rvScheduledOps.itemAnimator = null
        binding.rvScheduledOps.adapter = scheduledAdapter

        binding.btnAddScheduledOp.setOnClickListener { showScheduledOperationDialog(null) }

        binding.btnScheduledLog.setOnClickListener {
            // Reuses the shared ScrollableTextDialog; "clear" is an extra action that empties the log
            // in place (the dialog stays open) via the returned dialog's message TextView.
            val emptyMsg = fragment.getString(R.string.scheduled_ops_log_empty)
            val log = scheduledViewModel.getLog()
            var logDialog: androidx.appcompat.app.AlertDialog? = null
            logDialog = ScrollableTextDialog.show(
                context = fragment.requireContext(),
                title = fragment.getString(R.string.scheduled_ops_log_title),
                message = log.ifBlank { emptyMsg },
                monospace = true,
                showShare = false,
                showSave = false,
                extraAction = ScrollableTextDialog.ExtraAction(
                    icon = R.drawable.ic_delete_sweep,
                    contentDescription = fragment.getString(R.string.scheduled_ops_log_clear),
                    dismissOnClick = false,
                    onClick = {
                        scheduledViewModel.clearLog()
                        logDialog?.findViewById<android.widget.TextView>(R.id.tvErrorMessage)?.text = emptyMsg
                    }
                )
            )
        }

        binding.btnClearAllScheduled.setOnClickListener {
            MaterialAlertDialogBuilder(fragment.requireContext(), R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
                .setTitle(R.string.scheduled_ops_confirm_clear)
                .setPositiveButton(R.string.delete) { _, _ ->
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        scheduledViewModel.operations.value.forEach { scheduledViewModel.delete(it.id) }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showBoundTo(fragment)
        }

        fragment.collectOnLifecycle(scheduledViewModel.operations) { ops ->
            scheduledAdapter.submitList(ops)
            scheduleToggleReconcile()
        }
    }

    /** Applies the master scheduled toggle + dependent visibility from the latest settings. */
    fun render(settings: AppSettings) {
        Timber.d(
            "S1397: scheduled container visible=%b animating=%b",
            settings.enableScheduledOperations,
            binding.rvScheduledOps.isAnimating,
        )
        binding.rowEnableScheduledOps.setCheckedSilently(settings.enableScheduledOperations)
        binding.containerScheduledContent.isVisible = settings.enableScheduledOperations
        binding.layoutScheduledActions.isVisible = settings.enableScheduledOperations
    }

    /**
     * S0998: (re)arm the debounced reconcile. Runs on the view lifecycle, cancelling any pending
     * pass so only the settled list state drives one write. Not called from [render] - the toggle
     * must stay hand-checkable (empty list) to reveal the Add button, and a settings-driven reconcile
     * would immediately bounce a manual check back off.
     */
    private fun scheduleToggleReconcile() {
        if (!BuildConfig.ENABLE_SCHEDULED_OPERATIONS) return
        reconcileJob?.cancel()
        reconcileJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            delay(RECONCILE_DEBOUNCE_MS)
            reconcileToggleWithList()
        }
    }

    /**
     * S0998: on the settings screen the master toggle mirrors the scheduled-operations list - an
     * empty list unchecks it, a non-empty list re-checks it. Idempotent (writes only on real
     * divergence) so it cannot loop with the settings-driven [render]. The toggle stays interactive:
     * the user can still check it manually on an empty list to add the first operation, and deleting
     * the last operation flips it back off.
     */
    private fun reconcileToggleWithList() {
        val ops = scheduledViewModel.operations.value
        val desired = ops.isNotEmpty()
        if (viewModel.settings.value.enableScheduledOperations == desired) return
        Timber.d("OperationsScheduledManager: reconciled toggle -> %b (ops=%d)", desired, ops.size)
        viewModel.updateSettings(viewModel.settings.value.copy(enableScheduledOperations = desired))
    }

    fun onResume() {
        updateScheduledNotificationPermissionButton()
    }

    /**
     * If SettingsActivity was launched from the Scheduled Tasks widget with EXTRA_OPEN_SCHEDULED,
     * expand the scheduled-operations section so the user lands directly on it. Extra is consumed
     * so it doesn't re-trigger on orientation change.
     */
    fun checkAndExpandFromIntent() {
        if (!BuildConfig.ENABLE_SCHEDULED_OPERATIONS) return
        if (!fragment.requireActivity().intent.getBooleanExtra(SettingsActivity.EXTRA_OPEN_SCHEDULED, false)) return
        fragment.requireActivity().intent.removeExtra(SettingsActivity.EXTRA_OPEN_SCHEDULED)
        binding.headerScheduled.setExpanded(true, notify = true)
    }

    /**
     * If SettingsActivity was launched from Browse with EXTRA_SOURCE_RESOURCE_ID,
     * auto-open ScheduledOperationDialog with that resource pre-selected as source.
     * The extra is consumed (removed) so it doesn't re-trigger on orientation change.
     */
    fun onResourcesReady() {
        if (!BuildConfig.ENABLE_SCHEDULED_OPERATIONS) return
        if (!viewModel.settings.value.enableScheduledOperations) return
        val sourceId = fragment.requireActivity().intent.getLongExtra(
            SettingsActivity.EXTRA_SOURCE_RESOURCE_ID, -1L
        )
        if (sourceId == -1L) return
        fragment.requireActivity().intent.removeExtra(SettingsActivity.EXTRA_SOURCE_RESOURCE_ID)
        openScheduledOperationDialog(existing = null, prefilledSourceId = sourceId)
    }

    private fun showScheduledOperationDialog(existing: ScheduledOperation?) {
        openScheduledOperationDialog(existing, prefilledSourceId = null)
    }

    /**
     * S1009: build + show the dialog. Resolves any hidden FK (an ad-hoc local folder, filtered out of
     * the visible lists) so an edited operation still displays and preserves it, hosts the SAF folder
     * picker via the fragment launcher, and resolves staged folders on Save.
     */
    private fun openScheduledOperationDialog(existing: ScheduledOperation?, prefilledSourceId: Long?) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val resources = augmentWithHiddenFk(viewModel.resources.value, existing?.sourceResourceId)
            val destinations = augmentWithHiddenFk(viewModel.destinations.value, existing?.targetResourceId)
            val dialog = ScheduledOperationDialog(
                context = fragment.requireContext(),
                resources = resources,
                destinations = destinations,
                existing = existing,
                prefilledSourceId = prefilledSourceId,
                mediaCapabilities = mediaCapabilities,
                onPickLocalFolder = { side ->
                    pendingPickSide = side
                    folderPickerLauncher.launch(null)
                },
                onSave = { draft -> scheduledViewModel.saveOperation(draft) },
            )
            currentDialog = dialog
            // Drop the reference on dismiss so a dismissed dialog (and its Activity context) is not retained.
            dialog.setOnDismissListener { currentDialog = null }
            dialog.show()
        }
    }

    /**
     * S1009: receive a picked folder from the fragment SAF launcher. Persists the tree permission,
     * checks writability (rejecting a non-writable receiver with a toast), then stages the folder into
     * the currently shown dialog. A read-only source is allowed and forces COPY.
     */
    fun onFolderPicked(uri: Uri?) {
        if (uri == null) return
        val side = pendingPickSide ?: return
        pendingPickSide = null
        val context = fragment.requireContext()
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            Timber.w(e, "Could not persist folder permission for %s", uri)
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val path = uri.toString()
            val writable = scheduledViewModel.isFolderWritable(path)
            if (side == SchedOpPickSide.TARGET && !writable) {
                Toast.makeText(context, R.string.error_folder_not_writable, Toast.LENGTH_LONG).show()
                return@launch
            }
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: path
            currentDialog?.onLocalFolderPicked(side, path, name, readOnly = !writable)
        }
    }

    private suspend fun augmentWithHiddenFk(
        visible: List<MediaResource>,
        fkId: Long?,
    ): List<MediaResource> {
        if (fkId == null || visible.any { it.id == fkId }) return visible
        val resolved = scheduledViewModel.resolveResourceById(fkId) ?: return visible
        return listOf(resolved) + visible
    }

    private fun confirmDeleteScheduledOp(op: ScheduledOperation) {
        MaterialAlertDialogBuilder(fragment.requireContext(), R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(R.string.scheduled_ops_confirm_delete)
            .setPositiveButton(R.string.delete) { _, _ -> scheduledViewModel.delete(op.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundTo(fragment)
    }

    private fun updateScheduledNotificationPermissionButton() {
        val btn = binding.btnScheduledNotificationPermission ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            btn.isVisible = false
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            fragment.requireContext(), Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        btn.isVisible = !hasPermission
        // S1436: the label used to be a hand-written "Notification permission" that said nothing about
        // why scheduled operations want it; the sentence now comes from the permission's registry row.
        btn.text = fragment.requireContext().permissionRationaleShort(
            Manifest.permission.POST_NOTIFICATIONS,
            PermissionTask.SCHEDULED_OPERATIONS,
        )
        btn.setOnClickListener {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun checkAndRequestScheduledPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotification = ContextCompat.checkSelfPermission(
                fragment.requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasNotification) {
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // S1436: the release build strips REQUEST_IGNORE_BATTERY_OPTIMIZATIONS from the manifest, so
        // asking for it there would send the user to a screen that cannot help them.
        if (!BuildConfig.DECLARES_BATTERY_OPTIMIZATION) return
        val pm = fragment.requireContext().getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(fragment.requireContext().packageName)) {
            explainThenOpenBatteryOptimizationScreen()
        }
    }

    /**
     * S1436: the exclusion screen used to open with nothing said first. It is the longest jump any
     * request here makes - out of the app, into a system list - so the registry's paragraph is shown
     * before it, and the user can decline without leaving the settings screen at all.
     */
    private fun explainThenOpenBatteryOptimizationScreen() {
        Timber.d("S1436: battery optimization explained before the system screen")
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.perm_title_battery_optimization)
            .setMessage(
                fragment.requireContext().permissionRationale(
                    Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                )
            )
            .setPositiveButton(R.string.grant_permission) { _, _ -> openBatteryOptimizationScreen() }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundTo(fragment)
    }

    private fun openBatteryOptimizationScreen() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${fragment.requireContext().packageName}")
            }
            fragment.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Some vendor builds ship no handler for this intent; the schedule still runs, it may just
            // be delayed, so there is nothing to recover and nothing worth interrupting the user with.
            Timber.w(e, "battery optimization screen unavailable")
        }
    }

    private companion object {
        // Long enough to coalesce the stateIn seed + Room emit; imperceptible for a settings toggle.
        private const val RECONCILE_DEBOUNCE_MS = 250L
    }
}
