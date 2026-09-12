package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareTarget
import com.sza.fastmediasorter.core.share.ShareTargetAvailabilityResolver
import com.sza.fastmediasorter.core.share.ShareTargetIconResolver
import com.sza.fastmediasorter.core.share.ShareTargetRegistry
import com.sza.fastmediasorter.databinding.FragmentSettingsDestinationsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.usecase.IsShareTargetEnabledUseCase
import com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.util.getApplicationInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * S0452/S0463/S0999/S2390: Manages the «Send file to..» (Отправить в) group in the Operations tab.
 *
 * Constructs 2-column balanced toggle rows for each registered [ShareTarget], resolves real installed-app
 * labels and un-tinted full-color launcher icons asynchronously off the main thread, and updates enabled/disabled
 * state into [AppSettings.enabledShareTargets] and [AppSettings.disabledShareTargets].
 */
class OperationsSendCommandsManager(
    private val fragment: Fragment,
    private val binding: FragmentSettingsDestinationsBinding,
    private val viewModel: SettingsViewModel,
    private val shareTargetRegistry: ShareTargetRegistry,
    private val shareTargetAvailabilityResolver: ShareTargetAvailabilityResolver,
    private val shareTargetIconResolver: ShareTargetIconResolver,
    private val isShareTargetEnabledUseCase: IsShareTargetEnabledUseCase,
) {
    private val context: Context get() = fragment.requireContext()
    private val sendCommandRows = mutableMapOf<String, SettingsToggleRow>()
    private var isUpdatingFromSettings = false

    fun setup() {
        val container = binding.containerSendCommands
        container.removeAllViews()
        sendCommandRows.clear()
        val targets = shareTargetRegistry.all()
        binding.cardSendCommands.isVisible = targets.isNotEmpty()
        if (targets.isEmpty()) return

        val columns = fragment.resources.getInteger(R.integer.settings_send_commands_columns)
        val targetCount = targets.size
        val current = viewModel.settings.value
        val rows = targets.map { target ->
            buildSendCommandRow(target, current).also { sendCommandRows[target.id] = it }
        }

        val effectiveColumns = minOf(columns, targetCount)
        if (effectiveColumns <= 1) {
            container.orientation = LinearLayout.VERTICAL
            rows.forEach(container::addView)
        } else {
            container.orientation = LinearLayout.HORIZONTAL
            distributeColumnMajor(container, rows, effectiveColumns)
        }
        upgradeSendCommandLabelsAndIcons(targets)
        Timber.d("S2390: OperationsSendCommandsManager setup complete, targets=${targets.size}, cols=$effectiveColumns")
    }

    fun onConfigurationChanged(
        @Suppress("UnusedParameter") newConfig: Configuration,
    ) {
        setup()
    }

    fun updateAvailability(settings: AppSettings) {
        sendCommandRows.forEach { (id, row) ->
            val target = shareTargetRegistry.byId(id) ?: return@forEach
            val available = shareTargetAvailabilityResolver.isAvailable(target, settings)
            row.isEnabled = available
            val subtitleText: CharSequence? = if (available) {
                target.subtitleRes?.let { fragment.getString(it) }
            } else {
                fragment.getString(R.string.settings_send_command_unavailable)
            }
            row.setSubtitle(subtitleText)
            isUpdatingFromSettings = true
            row.setCheckedSilently(isShareTargetEnabledUseCase(target.id, settings))
            isUpdatingFromSettings = false
        }
    }

    private fun buildSendCommandRow(target: ShareTarget, current: AppSettings): SettingsToggleRow {
        return SettingsToggleRow(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setTitle(fragment.getString(target.titleRes))
            target.iconRes?.let { setIcon(it) }
            val available = shareTargetAvailabilityResolver.isAvailable(target, current)
            isEnabled = available
            val subtitleText: CharSequence? = if (available) {
                target.subtitleRes?.let { fragment.getString(it) }
            } else {
                fragment.getString(R.string.settings_send_command_unavailable)
            }
            setSubtitle(subtitleText)
            val hm = target.helpMessageRes
            if (hm != null) setHelp(target.titleRes, hm)
            setCheckedSilently(isShareTargetEnabledUseCase(target.id, current))
            setOnCheckedChangeListener { isChecked ->
                if (isUpdatingFromSettings) return@setOnCheckedChangeListener
                val s = viewModel.settings.value
                val enabled = s.enabledShareTargets.toMutableSet()
                val disabled = s.disabledShareTargets.toMutableSet()
                if (isChecked) {
                    enabled.add(target.id)
                    disabled.remove(target.id)
                } else {
                    disabled.add(target.id)
                    enabled.remove(target.id)
                }
                viewModel.updateSettings(
                    s.copy(enabledShareTargets = enabled, disabledShareTargets = disabled)
                )
            }
        }
    }

    private fun distributeColumnMajor(
        container: LinearLayout,
        rows: List<SettingsToggleRow>,
        columns: Int,
    ) {
        val gap = fragment.resources.getDimensionPixelSize(R.dimen.dialog_field_spacing)
        val base = rows.size / columns
        val remainder = rows.size % columns
        var index = 0
        for (col in 0 until columns) {
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    SEND_COMMANDS_COLUMN_WEIGHT,
                ).apply { if (col > 0) marginStart = gap }
            }
            val count = base + if (col < remainder) 1 else 0
            repeat(count) {
                column.addView(rows[index])
                index++
            }
            container.addView(column)
        }
    }

    private fun upgradeSendCommandLabelsAndIcons(targets: List<ShareTarget>) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                targets.associate { t ->
                    t.id to (resolveShareTargetLabel(t) to shareTargetIconResolver.resolveIcon(t))
                }
            }
            resolved.forEach { (id, pair) ->
                sendCommandRows[id]?.let { row ->
                    row.setTitle(pair.first)
                    pair.second?.let { icon -> row.setIcon(icon) }
                }
            }
        }
    }

    private fun resolveShareTargetLabel(target: ShareTarget): CharSequence {
        if (target.packages.isEmpty()) return fragment.getString(target.titleRes)
        val pm = context.packageManager
        val installedLabel = target.packages.firstNotNullOfOrNull { pkg ->
            try {
                pm.getApplicationLabel(pm.getApplicationInfoCompat(pkg))
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
        return installedLabel ?: fragment.getString(target.titleRes)
    }

    companion object {
        private const val SEND_COMMANDS_COLUMN_WEIGHT = 1f
    }
}
