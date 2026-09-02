package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.core.util.formatFileSize
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.usecase.CalculateOptimalCacheSizeUseCase
import com.sza.fastmediasorter.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class GeneralSettingsCacheHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val viewModel: SettingsViewModel,
    private val fragment: Fragment,
    private val audioMetadataCacheRepository: AudioMetadataCacheRepository,
    private val calculateOptimalCacheSizeUseCase: CalculateOptimalCacheSizeUseCase,
    // S0707: shared with the other General-settings input owners so the cancel-path restore
    // write below cannot re-trigger actvCacheSizeLimit's commit listener mid-update.
    private val setIsUpdatingSpinner: (Boolean) -> Unit,
) {
    fun checkAndSuggestOptimalCacheSize() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            // S1535: awaits storage rather than reading viewModel.settings.value, which is still the
            // AppSettings() seed this early and made every device whose optimum differs from the
            // default 2048 MB look permanently mis-sized.
            val settings = viewModel.awaitPersistedSettings()
            if (!settings.isCacheSizeUserModified) {
                val optimalSizeMb = calculateOptimalCacheSizeUseCase()
                if (settings.cacheSizeMb != optimalSizeMb) applyOptimalCacheSize(settings, optimalSizeMb)
            }
        }
    }

    fun showCacheSizeRestartDialog(newCacheSizeMb: Int, isUserModified: Boolean = true) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.restart_app_title)
            .setMessage(fragment.getString(R.string.restart_app_cache_message, newCacheSizeMb))
            .setPositiveButton(R.string.restart) { _, _ ->
                applyCacheSizeAndRestart(newCacheSizeMb, isUserModified)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                val currentCacheSize = viewModel.settings.value.cacheSizeMb
                // S0707: raise the shared suppression flag for the duration of this programmatic
                // restore so SettingsInputRow's commit listener does not re-fire. Cleared on the
                // same post() that dismisses the dialog, after the text change has settled.
                setIsUpdatingSpinner(true)
                // S0567: SettingsInputRow exposes a `text` property (was AutoCompleteTextView.setText).
                binding.actvCacheSizeLimit.text = fragment.getString(R.string.number_format, currentCacheSize)
                binding.actvCacheSizeLimit.post {
                    setIsUpdatingSpinner(false)
                    dialog.dismiss()
                }
            }
            .setCancelable(false)
            .showBoundTo(fragment)
    }

    fun updateCacheSize() {
        fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = fragment.requireContext().cacheDir
                val audioMetaCacheDir = java.io.File(fragment.requireContext().filesDir, AudioMetadataCacheRepository.CACHE_DIR_NAME)
                val totalSize = calculateDirectorySize(cacheDir) + calculateDirectorySize(audioMetaCacheDir)
                val formattedSize = formatFileSize(fragment.requireContext(), totalSize)
                val audioMetaCacheSize = audioMetadataCacheRepository.getCacheSize()
                withContext(Dispatchers.Main) {
                    binding.tvCacheSize.text = fragment.getString(R.string.cache_size_format, formattedSize)
                    if (audioMetaCacheSize > AudioMetadataCacheRepository.MAX_CACHE_BYTES) showAudioCacheSizeWarning()
                    binding.btnClearCache.text = fragment.getString(R.string.clear_cache)
                    binding.btnClearCache.isEnabled = true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to calculate cache size")
                withContext(Dispatchers.Main) {
                    binding.tvCacheSize.text = fragment.getString(R.string.cache_size_format, "N/A")
                    binding.btnClearCache.text = fragment.getString(R.string.clear_cache)
                    binding.btnClearCache.isEnabled = true
                }
            }
        }
    }

    fun autoCalculateCacheSize() {
        val optimalSizeMb = calculateOptimalCacheSizeUseCase()
        val storageInfo = calculateOptimalCacheSizeUseCase.getStorageInfo()
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.auto_calculate_cache_title)
            .setMessage(fragment.getString(R.string.auto_calculate_cache_message, optimalSizeMb, storageInfo))
            .setPositiveButton(R.string.apply) { _, _ ->
                val current = viewModel.settings.value
                if (current.cacheSizeMb != optimalSizeMb) showCacheSizeRestartDialog(optimalSizeMb, isUserModified = false)
                else Toast.makeText(fragment.requireContext(), R.string.cache_size_already_optimal, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundTo(fragment)
    }

    fun clearCache() {
        MaterialAlertDialogBuilder(fragment.requireContext(), R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(R.string.clear_cache)
            .setMessage(R.string.clear_cache_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                binding.btnClearCache.isEnabled = false
                binding.btnClearCache.text = fragment.getString(R.string.cache_size_calculating)
                fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        try { com.bumptech.glide.Glide.get(fragment.requireContext()).clearDiskCache() }
                        catch (e: Exception) { Timber.e(e, "Glide clearDiskCache failed") }

                        try {
                            // S0194: FastMediaSorterApp.unifiedCache is now dagger.Lazy<UnifiedFileCache>.
                            val app = fragment.requireActivity().application as com.sza.fastmediasorter.FastMediaSorterApp
                            val cache = app.unifiedCache.get()
                            val stats = cache.getCacheStats()
                            cache.clearAll()
                            Timber.d("Cleared UnifiedFileCache: ${stats.totalSizeMB} MB")
                        } catch (e: Exception) { Timber.e(e, "Failed to clear UnifiedFileCache") }

                        val cacheDir = fragment.requireContext().cacheDir
                        deleteRecursive(cacheDir)
                        com.sza.fastmediasorter.core.cache.TranslationCacheManager.clearAll()

                        try { com.sza.fastmediasorter.core.cache.MediaFilesCacheManager.clearAllCaches() }
                        catch (e: Exception) { Timber.e(e, "Failed to clear MediaFilesCacheManager") }

                        try {
                            // S0194: dagger.Lazy<CachedFileListRepository>.
                            val app = fragment.requireActivity().application as com.sza.fastmediasorter.FastMediaSorterApp
                            app.cachedFileListRepository.get().deleteAllCachedFiles()
                        } catch (e: Exception) { Timber.e(e, "Failed to clear CachedFileListRepository") }

                        try {
                            // S0194: dagger.Lazy<PlaybackPositionRepository>.
                            val app = fragment.requireActivity().application as com.sza.fastmediasorter.FastMediaSorterApp
                            app.playbackPositionRepository.get().deleteAllPositions()
                        } catch (e: Exception) { Timber.e(e, "Failed to clear playback positions") }

                        try { audioMetadataCacheRepository.clearCache() }
                        catch (e: Exception) { Timber.d(e, "Failed to clear AudioMetadataCache") }

                        withContext(Dispatchers.Main) {
                            try { com.bumptech.glide.Glide.get(fragment.requireContext()).clearMemory() }
                            catch (e: Exception) { Timber.e(e, "Glide clearMemory failed") }
                            Toast.makeText(fragment.requireContext(), R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                            binding.btnClearCache.isEnabled = true
                            updateCacheSize()
                        }
                    } catch (e: Exception) {
                        e.rethrowIfCancellation()
                        Timber.e(e, "Failed to clear cache")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(fragment.requireContext(), R.string.cache_clear_failed, Toast.LENGTH_SHORT).show()
                            binding.btnClearCache.isEnabled = true
                            updateCacheSize()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundTo(fragment)
    }

    /**
     * S1535: the automatic path persists the size and stops there. Glide reads its disk-cache size
     * when the process initialises, so the new value lands on the next ordinary launch; forcing a
     * restart for a change the user never asked for is what produced the Settings restart cycle.
     */
    private fun applyOptimalCacheSize(current: AppSettings, optimalSizeMb: Int) {
        persistCacheSize(current, newCacheSizeMb = optimalSizeMb, isUserModified = false)
        if (!fragment.isAdded) return
        Toast.makeText(
            fragment.requireContext(),
            fragment.getString(R.string.cache_size_installed_toast, optimalSizeMb),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun applyCacheSizeAndRestart(newCacheSizeMb: Int, isUserModified: Boolean) {
        val context = fragment.requireContext()
        // Reached from a dialog the user operated, so the screen has rendered and settings.value is loaded.
        persistCacheSize(viewModel.settings.value, newCacheSizeMb, isUserModified)
        // The user confirmed a restart, and only a fresh process re-reads the Glide cache size.
        // saveLanguage() re-applies the current language purely to make LocaleManager kill the process.
        LocaleHelper.saveLanguage(context, LocaleHelper.getLanguage(context))
        LocaleHelper.markReturnToSettings(context)
        LocaleHelper.restartApp(fragment.requireActivity())
    }

    // S1535: takes the base settings as an argument rather than re-reading viewModel.settings.value.
    // awaitPersistedSettings() collects the DataStore flow separately from the stateIn() that backs
    // settings, so the two are not ordered - a re-read here could still hand back the AppSettings()
    // seed and copy() would then write every other setting back to its default.
    private fun persistCacheSize(base: AppSettings, newCacheSizeMb: Int, isUserModified: Boolean) {
        viewModel.updateSettings(base.copy(cacheSizeMb = newCacheSizeMb, isCacheSizeUserModified = isUserModified))
        fragment.requireContext()
            .getSharedPreferences("glide_config", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("cache_size_mb", newCacheSizeMb)
            .apply()
    }

    private fun showAudioCacheSizeWarning() {
        if (!fragment.isAdded || fragment.activity?.isFinishing == true) return
        MaterialAlertDialogBuilder(fragment.requireContext(), R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(R.string.audio_cache_size_warning_title)
            .setMessage(R.string.audio_cache_size_warning_message)
            .setPositiveButton(R.string.delete_old_files) { _, _ ->
                fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    audioMetadataCacheRepository.trimIfNeeded()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundTo(fragment)
    }

    private fun calculateDirectorySize(directory: java.io.File): Long {
        var size = 0L
        try {
            directory.listFiles()?.forEach { file ->
                size += if (file.isDirectory) calculateDirectorySize(file) else file.length()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating directory size: ${directory.absolutePath}")
        }
        return size
    }

    private fun deleteRecursive(fileOrDirectory: java.io.File): Boolean {
        return try {
            if (fileOrDirectory.isDirectory) {
                fileOrDirectory.listFiles()?.forEach { child -> deleteRecursive(child) }
            }
            if (fileOrDirectory != fragment.requireContext().cacheDir) fileOrDirectory.delete() else true
        } catch (e: Exception) {
            Timber.e(e, "Error deleting: ${fileOrDirectory.absolutePath}")
            false
        }
    }
}
