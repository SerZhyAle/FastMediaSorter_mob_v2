package com.sza.fastmediasorter.ui.scheduledops

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.ActivityScheduledOperationsBinding
import com.sza.fastmediasorter.domain.model.PermissionTask
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.ui.common.permissions.permissionRationale
import com.sza.fastmediasorter.ui.common.permissions.permissionRationaleShort
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/** Keeps scheduled-operations UI behavior out of the Activity lifecycle host. */
class ScheduledOperationsScreenManager(
    private val activity: ScheduledOperationsActivity,
    private val binding: ActivityScheduledOperationsBinding,
    mediaCapabilities: MediaCapabilities,
    private val scheduledViewModel: ScheduledOperationsViewModel,
    private val notificationsPermissionLauncher: ActivityResultLauncher<String>,
    folderPickerLauncher: ActivityResultLauncher<Uri?>,
) {
    private val dialogManager = ScheduledOperationsDialogManager(
        mediaCapabilities = mediaCapabilities,
        scheduledViewModel = scheduledViewModel,
        folderPickerLauncher = folderPickerLauncher,
    )
    private lateinit var scheduledAdapter: ScheduledOperationsAdapter
    private var reconcileJob: Job? = null
    private var historyVisible = false

    fun setupViews() {
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setUpNavigation(activity)
        scheduledAdapter = ScheduledOperationsAdapter(
            onToggle = scheduledViewModel::toggleEnabled,
            onEdit = ::showScheduledOperationDialog,
            onDelete = ::confirmDeleteScheduledOp,
            onRunNow = { op -> scheduledViewModel.runNow(op.id) },
            resourceNameProvider = { id -> scheduledViewModel.resources.value.find { it.id == id }?.name },
        )
        binding.rvScheduledOps.layoutManager = LinearLayoutManager(activity)
        binding.rvScheduledOps.itemAnimator = null
        binding.rvScheduledOps.adapter = scheduledAdapter
        binding.rowScheduledOpsEnabled.setOnCheckedChangeListener { isChecked ->
            scheduledViewModel.setEnabled(isChecked)
            renderContentVisibility(isChecked)
            if (isChecked) checkAndRequestScheduledPermissions()
        }
        binding.btnAddScheduledOp.setOnClickListener { showScheduledOperationDialog(null) }
        binding.btnRunAllScheduled.setOnClickListener { scheduledViewModel.runAllNow() }
        binding.btnTogglePauseScheduled.setOnClickListener {
            scheduledViewModel.setPaused(!scheduledViewModel.isPaused.value)
        }
        binding.btnScheduledLog.setOnClickListener { toggleHistory() }
        binding.btnScheduledHistoryClear.setOnClickListener { confirmClearHistory() }
        binding.btnClearAllScheduled.setOnClickListener { confirmClearAll() }
    }

    fun observeData() {
        activity.collectOnLifecycle(scheduledViewModel.operations) { ops ->
            scheduledAdapter.submitList(ops)
            binding.tvNoScheduledOps.isVisible = ops.isEmpty() && scheduledViewModel.isEnabled.value
            scheduleToggleReconcile()
        }
        activity.collectOnLifecycle(scheduledViewModel.isEnabled) { enabled ->
            binding.rowScheduledOpsEnabled.setCheckedSilently(enabled)
            renderContentVisibility(enabled)
            if (enabled) autoOpenFromBrowse()
        }
        activity.collectOnLifecycle(scheduledViewModel.isPaused) { paused ->
            binding.btnTogglePauseScheduled.setIconResource(
                if (paused) R.drawable.ic_play else R.drawable.ic_pause,
            )
            binding.btnTogglePauseScheduled.contentDescription = activity.getString(
                if (paused) {
                    R.string.scheduled_ops_action_resume_all
                } else {
                    R.string.scheduled_ops_action_pause_all
                },
            )
        }
    }

    fun onFolderPicked(uri: Uri?) {
        dialogManager.onFolderPicked(activity, activity.lifecycleScope, uri)
    }

    fun updateNotificationPermissionButton() {
        val button = binding.btnScheduledNotificationPermission
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            button.isVisible = false
            return
        }
        button.isVisible = !hasNotificationPermission()
        button.text = activity.permissionRationaleShort(
            Manifest.permission.POST_NOTIFICATIONS,
            PermissionTask.SCHEDULED_OPERATIONS,
        )
        button.setOnClickListener {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun renderContentVisibility(enabled: Boolean) {
        binding.layoutScheduledOpsActions.isVisible = enabled
        binding.tvNoScheduledOps.isVisible = enabled && scheduledViewModel.operations.value.isEmpty()
        binding.rvScheduledOps.isVisible = enabled
    }

    private fun scheduleToggleReconcile() {
        reconcileJob?.cancel()
        reconcileJob = activity.lifecycleScope.launch {
            delay(RECONCILE_DEBOUNCE_MS)
            val desired = scheduledViewModel.operations.value.isNotEmpty()
            if (scheduledViewModel.isEnabled.value == desired) return@launch
            Timber.d("ScheduledOperationsScreenManager: reconciled toggle -> %b", desired)
            scheduledViewModel.setEnabled(desired)
        }
    }

    private fun toggleHistory() {
        historyVisible = !historyVisible
        Timber.d("S3365: run history toggled")
        binding.containerScheduledHistory.isVisible = historyVisible
        if (historyVisible) renderHistory()
    }

    private fun renderHistory() {
        val rows = binding.containerScheduledHistoryRows
        rows.removeAllViews()
        val entries = ScheduledLogEntryParser.parse(scheduledViewModel.getLog()).reversed()
        binding.tvScheduledHistoryEmpty.isVisible = entries.isEmpty()
        entries.forEach { entry -> rows.addView(buildHistoryRow(entry)) }
    }

    private fun buildHistoryRow(entry: ScheduledLogEntry): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, activity.resources.getDimensionPixelSize(R.dimen.margin_tiny))
        }
        val headline = TextView(activity).apply {
            text = buildString {
                entry.timestamp?.let { timestamp -> append(timestamp) }
                entry.operation?.let { append("  ·  ").append(it) }
                if (entry.source != null && entry.target != null) {
                    append("\n").append(entry.source).append(" → ").append(entry.target)
                }
            }
            setTextAppearance(R.style.TextAppearance_FastMediaSorter_Item_Subtitle)
        }
        val message = TextView(activity).apply {
            text = entry.message
            setTextColor(
                MaterialColors.getColor(
                    this,
                    if (entry.isError) {
                        androidx.appcompat.R.attr.colorError
                    } else {
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    },
                ),
            )
        }
        row.addView(headline)
        row.addView(message)
        row.contentDescription = entry.raw
        return row
    }

    private fun confirmClearHistory() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.scheduled_ops_history_clear_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                scheduledViewModel.clearLog()
                renderHistory()
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }

    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(
            activity,
            R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive,
        )
            .setTitle(R.string.scheduled_ops_confirm_clear)
            .setPositiveButton(R.string.delete) { _, _ ->
                activity.lifecycleScope.launch {
                    scheduledViewModel.operations.value.forEach { op -> scheduledViewModel.delete(op.id) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }

    private fun confirmDeleteScheduledOp(op: ScheduledOperation) {
        MaterialAlertDialogBuilder(
            activity,
            R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive,
        )
            .setTitle(R.string.scheduled_ops_confirm_delete)
            .setPositiveButton(R.string.delete) { _, _ -> scheduledViewModel.delete(op.id) }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }

    private fun showScheduledOperationDialog(existing: ScheduledOperation?) {
        openScheduledOperationDialog(existing, prefilledSourceId = null)
    }

    private fun openScheduledOperationDialog(existing: ScheduledOperation?, prefilledSourceId: Long?) {
        dialogManager.openScheduledOperationDialog(
            context = activity,
            scope = activity.lifecycleScope,
            resources = scheduledViewModel.resources.value,
            existing = existing,
            prefilledSourceId = prefilledSourceId,
        )
    }

    private fun autoOpenFromBrowse() {
        val sourceId = activity.intent.getLongExtra(
            ScheduledOperationsActivity.EXTRA_SOURCE_RESOURCE_ID,
            NO_RESOURCE_ID,
        )
        val ready = scheduledViewModel.isEnabled.value &&
            scheduledViewModel.resources.value.isNotEmpty() && sourceId != NO_RESOURCE_ID
        if (!ready) return
        activity.intent.removeExtra(ScheduledOperationsActivity.EXTRA_SOURCE_RESOURCE_ID)
        Timber.d("S3365: auto-opening create dialog from Browse")
        openScheduledOperationDialog(existing = null, prefilledSourceId = sourceId)
    }

    private fun checkAndRequestScheduledPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!BuildConfig.DECLARES_BATTERY_OPTIMIZATION) return
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(activity.packageName)) {
            explainThenOpenBatteryOptimizationScreen()
        }
    }

    private fun hasNotificationPermission(): Boolean = ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun explainThenOpenBatteryOptimizationScreen() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.perm_title_battery_optimization)
            .setMessage(activity.permissionRationale(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS))
            .setPositiveButton(R.string.grant_permission) { _, _ -> openBatteryOptimizationScreen() }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }

    private fun openBatteryOptimizationScreen() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Timber.w(error, "battery optimization screen unavailable")
        }
    }

    private companion object {
        const val RECONCILE_DEBOUNCE_MS = 250L
        const val NO_RESOURCE_ID = -1L
    }
}
