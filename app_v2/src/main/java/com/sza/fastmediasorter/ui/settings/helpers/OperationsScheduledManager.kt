package com.sza.fastmediasorter.ui.settings.helpers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.ui.dialog.ScheduledOperationDialog
import com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog
import com.sza.fastmediasorter.ui.settings.ScheduledOperationsAdapter
import com.sza.fastmediasorter.ui.settings.ScheduledOperationsViewModel
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.utils.collectOnLifecycle
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
    private val isUpdatingFromSettings: () -> Boolean,
) {

    private lateinit var scheduledAdapter: ScheduledOperationsAdapter

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
            androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.scheduled_ops_confirm_clear)
                .setPositiveButton(R.string.delete) { _, _ ->
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        scheduledViewModel.operations.value.forEach { scheduledViewModel.delete(it.id) }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        fragment.collectOnLifecycle(scheduledViewModel.operations) { ops ->
            scheduledAdapter.submitList(ops)
        }
    }

    /** Applies the master scheduled toggle + dependent visibility from the latest settings. */
    fun render(settings: AppSettings) {
        binding.rowEnableScheduledOps.setCheckedSilently(settings.enableScheduledOperations)
        binding.containerScheduledContent.isVisible = settings.enableScheduledOperations
        binding.layoutScheduledActions.isVisible = settings.enableScheduledOperations
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
        val allResources = viewModel.resources.value
        val destinations = viewModel.destinations.value
        ScheduledOperationDialog(
            context = fragment.requireContext(),
            resources = allResources,
            destinations = destinations,
            existing = null,
            prefilledSourceId = sourceId,
            mediaCapabilities = mediaCapabilities,
            onSave = { op -> scheduledViewModel.upsert(op) }
        ).show()
    }

    private fun showScheduledOperationDialog(existing: ScheduledOperation?) {
        val allResources = viewModel.resources.value
        val destinations = viewModel.destinations.value
        ScheduledOperationDialog(
            context = fragment.requireContext(),
            resources = allResources,
            destinations = destinations,
            existing = existing,
            mediaCapabilities = mediaCapabilities,
            onSave = { op -> scheduledViewModel.upsert(op) }
        ).show()
    }

    private fun confirmDeleteScheduledOp(op: ScheduledOperation) {
        androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.scheduled_ops_confirm_delete)
            .setPositiveButton(R.string.delete) { _, _ -> scheduledViewModel.delete(op.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        val pm = fragment.requireContext().getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(fragment.requireContext().packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${fragment.requireContext().packageName}")
                }
                fragment.startActivity(intent)
            } catch (_: Exception) { }
        }
    }
}
