package com.sza.fastmediasorter.ui.settings.helpers

import android.text.format.Formatter
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.PrefetchCacheMultiplier
import com.sza.fastmediasorter.domain.model.StreamingCacheCleanupMode
import com.sza.fastmediasorter.domain.repository.StreamingCacheRepository
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handles the "Video pre-cache / Streaming cache cleanup / TTL / Clear now" section in
 * [com.sza.fastmediasorter.ui.settings.fragments.GeneralSettingsFragment].
 *
 * Three dropdowns and a "Clear now" button that shows the list of cached entries before
 * confirming deletion. Pattern mirrors [GeneralSettingsCacheHelper].
 */
class GeneralSettingsPrefetchHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val streamingCacheRepository: StreamingCacheRepository
) {
    private var isUpdatingSpinner = false

    fun setup() {
        setupPrefetchCacheDropdown()
        setupCleanupModeDropdown()
        setupTtlDropdown()
        setupClearButton()
    }

    fun updateFromSettings(settings: AppSettings) {
        if (isUpdatingSpinner) return
        isUpdatingSpinner = true
        // S0567: prefetch cache migrated to SettingsDropdownRow - select by enum ordinal (entry order
        // mirrors PrefetchCacheMultiplier.values()). The other two remain AutoCompleteTextView dropdowns.
        binding.actvPrefetchCache.setSelection(settings.prefetchCacheMultiplier.ordinal)
        binding.actvStreamingCleanup.setText(cleanupModeLabel(settings.streamingCacheCleanupMode), false)
        binding.actvStreamingTtl.setText(ttlLabel(settings.streamingCacheTtlDays), false)
        isUpdatingSpinner = false
    }

    // ── Prefetch cache dropdown ────────────────────────────────────────────────

    // S0567: migrated to SettingsDropdownRow; the standalone help icon is folded into the row
    // (sdr_showHelp in the layout). Help wiring is owned by the widget now.
    private fun setupPrefetchCacheDropdown() {
        val labels = PrefetchCacheMultiplier.values().map { prefetchCacheLabel(it) as CharSequence }
        binding.actvPrefetchCache.setEntries(labels)
        binding.actvPrefetchCache.setOnItemSelectedListener { position ->
            if (isUpdatingSpinner) return@setOnItemSelectedListener
            val selected = PrefetchCacheMultiplier.values()[position]
            Timber.d("GeneralSettingsPrefetchHelper: prefetchCacheMultiplier → %s", selected)
            saveSettings { it.copy(prefetchCacheMultiplier = selected) }
        }
    }

    private fun prefetchCacheLabel(m: PrefetchCacheMultiplier): String = when (m) {
        PrefetchCacheMultiplier.LESS -> fragment.getString(R.string.pref_prefetch_cache_less)
        PrefetchCacheMultiplier.AUTO -> fragment.getString(R.string.pref_prefetch_cache_auto)
        PrefetchCacheMultiplier.MORE -> fragment.getString(R.string.pref_prefetch_cache_more)
    }

    // ── Cleanup mode dropdown ─────────────────────────────────────────────────

    private fun setupCleanupModeDropdown() {
        val labels = StreamingCacheCleanupMode.values().map { cleanupModeLabel(it) }
        val adapter = ArrayAdapter(fragment.requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        binding.actvStreamingCleanup.setAdapter(adapter)
        binding.actvStreamingCleanup.setOnItemClickListener { _, _, position, _ ->
            if (isUpdatingSpinner) return@setOnItemClickListener
            val selected = StreamingCacheCleanupMode.values()[position]
            Timber.d("GeneralSettingsPrefetchHelper: streamingCacheCleanupMode → %s", selected)
            saveSettings { it.copy(streamingCacheCleanupMode = selected) }
        }
    }

    private fun cleanupModeLabel(m: StreamingCacheCleanupMode): String = when (m) {
        StreamingCacheCleanupMode.ASK -> fragment.getString(R.string.pref_streaming_cleanup_ask)
        StreamingCacheCleanupMode.AUTO_DELETE -> fragment.getString(R.string.pref_streaming_cleanup_auto_delete)
        StreamingCacheCleanupMode.AUTO_KEEP -> fragment.getString(R.string.pref_streaming_cleanup_auto_keep)
    }

    // ── TTL dropdown ──────────────────────────────────────────────────────────

    private val ttlOptions = listOf(0, 1, 3, 7, 30)

    private fun setupTtlDropdown() {
        val labels = ttlOptions.map { ttlLabel(it) }
        val adapter = ArrayAdapter(fragment.requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        binding.actvStreamingTtl.setAdapter(adapter)
        binding.actvStreamingTtl.setOnItemClickListener { _, _, position, _ ->
            if (isUpdatingSpinner) return@setOnItemClickListener
            val selected = ttlOptions[position]
            Timber.d("GeneralSettingsPrefetchHelper: streamingCacheTtlDays → %d", selected)
            saveSettings { it.copy(streamingCacheTtlDays = selected) }
        }
    }

    private fun ttlLabel(days: Int): String = when (days) {
        0 -> fragment.getString(R.string.pref_streaming_ttl_off)
        1 -> fragment.getString(R.string.pref_streaming_ttl_1d)
        3 -> fragment.getString(R.string.pref_streaming_ttl_3d)
        7 -> fragment.getString(R.string.pref_streaming_ttl_7d)
        30 -> fragment.getString(R.string.pref_streaming_ttl_30d)
        else -> "$days d"
    }

    // ── Clear streaming cache button ───────────────────────────────────────────

    private fun setupClearButton() {
        binding.btnClearStreamingCache.setOnClickListener { showClearDialog() }
    }

    private fun showClearDialog() {
        fragment.lifecycleScope.launch {
            val entries = streamingCacheRepository.getAll()
            val totalBytes = streamingCacheRepository.totalSizeBytes()
            val ctx = fragment.requireContext()

            if (entries.isEmpty()) {
                AlertDialog.Builder(ctx)
                    .setTitle(R.string.pref_streaming_clear_now)
                    .setMessage(ctx.getString(R.string.streaming_cache_empty))
                    .setPositiveButton(android.R.string.ok, null)
                    .showBoundTo(fragment)
                return@launch
            }

            val totalStr = Formatter.formatShortFileSize(ctx, totalBytes)
            val entryLines = entries.joinToString("\n") { e ->
                val name = e.localPath.substringAfterLast("/")
                val size = Formatter.formatShortFileSize(ctx, e.sizeBytes)
                "• $name  ($size)"
            }
            val message = ctx.getString(R.string.pref_streaming_clear_confirm_format, totalStr, entryLines)

            MaterialAlertDialogBuilder(ctx, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
                .setTitle(R.string.pref_streaming_clear_now)
                .setMessage(message)
                .setPositiveButton(R.string.delete) { _, _ ->
                    fragment.lifecycleScope.launch {
                        val count = streamingCacheRepository.clearAll()
                        Timber.i("GeneralSettingsPrefetchHelper: cleared %d streaming cache entries", count)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .showBoundTo(fragment)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun saveSettings(transform: (AppSettings) -> AppSettings) {
        fragment.lifecycleScope.launch {
            val current = viewModel.settings.first()
            viewModel.updateSettings(transform(current))
        }
    }
}
