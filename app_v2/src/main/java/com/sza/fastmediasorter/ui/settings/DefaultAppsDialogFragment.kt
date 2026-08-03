package com.sza.fastmediasorter.ui.settings

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.DialogDefaultAppsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerSettingsManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S0880: hosts the default-player registration subgroup (per-type buttons + the primary-player /
 * accept-shared toggles) that used to sit inline in the Operations settings screen. Only reachable
 * from the "Set as default" launcher, which is itself gated by [MediaCapabilities.supportsDefaultPlayer],
 * so the dialog never needs its own top-level gate. The actual OS-registration logic is delegated to
 * [DefaultPlayerSettingsManager] (buttons) and [DefaultPlayerManager] (toggles) - no logic is duplicated.
 */
@AndroidEntryPoint
class DefaultAppsDialogFragment : DialogFragment() {

    private var _binding: DialogDefaultAppsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by activityViewModels()

    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

    private val defaultPlayerSettingsManager = DefaultPlayerSettingsManager()

    /** Orientation the currently inflated layout variant was chosen for. */
    private var inflatedOrientation = Configuration.ORIENTATION_UNDEFINED

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogDefaultAppsBinding.inflate(inflater, container, false)
        inflatedOrientation = resources.configuration.orientation
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindContent()
        observeSettings()
    }

    private fun bindContent() {
        binding.btnClose.setOnClickListener { dismiss() }
        // Per-type registration buttons + hint (gated by media-type capability inside the manager).
        defaultPlayerSettingsManager.bind(this, binding, mediaCapabilities)
        setupToggles()
    }

    /**
     * S1123 mechanism, second confirmed case: the host SettingsActivity declares `configChanges` for
     * orientation, so a rotation recreates neither it nor this fragment and [onCreateView] never runs
     * again - the variant inflated when the dialog opened stays on screen for the rest of its life.
     * Swap the content for the one the new configuration resolves, as a recreate would have produced.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val hostDialog = dialog ?: return
        if (newConfig.orientation == inflatedOrientation) return
        Timber.d("S1333: default-apps dialog re-inflate, orientation=${newConfig.orientation}")

        _binding = DialogDefaultAppsBinding.inflate(LayoutInflater.from(requireContext()))
        inflatedOrientation = newConfig.orientation
        hostDialog.setContentView(binding.root)
        bindContent()
        // onStart does not run again either, so the window chrome has to be re-applied by hand.
        applyDialogChrome()
        // The switches live in the freshly inflated rows and default to off, while the settings
        // StateFlow does not re-emit here - push the current value or both rows silently read OFF.
        renderToggles(viewModel.settings.value)
    }

    private fun setupToggles() {
        binding.rowPrimaryMediaPlayer.setOnCheckedChangeListener { isChecked ->
            DefaultPlayerManager.applyPrimaryPlayerState(requireContext(), isChecked, mediaCapabilities)
            viewModel.updateSettings(viewModel.settings.value.copy(isPrimaryMediaPlayer = isChecked))
        }
        binding.rowAcceptSharedFiles.setOnCheckedChangeListener { isChecked ->
            DefaultPlayerManager.applyShareReceiverState(requireContext(), isChecked, mediaCapabilities)
            viewModel.updateSettings(viewModel.settings.value.copy(acceptSharedFiles = isChecked))
        }
    }

    private fun observeSettings() {
        // Reads the _binding field through the property, not a captured instance, so a re-inflate keeps
        // this single collector driving the rows on screen - never register it a second time.
        collectOnLifecycle(viewModel.settings) { renderToggles(it) }
    }

    // setCheckedSilently reflects the persisted value without re-firing the OS component-state write.
    private fun renderToggles(settings: AppSettings) {
        binding.rowPrimaryMediaPlayer.setCheckedSilently(settings.isPrimaryMediaPlayer)
        binding.rowAcceptSharedFiles.setCheckedSilently(settings.acceptSharedFiles)
    }

    override fun onStart() {
        super.onStart()
        applyDialogChrome()
    }

    private fun applyDialogChrome() {
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        // Settings panel: every control applies immediately, so there is no positive action - a no-op
        // confirm keeps Esc-dismiss and focus traversal without a false Enter-confirm.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {})
        binding.btnClose.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DefaultAppsDialogFragment"
    }
}
