package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LanguageSplitInstaller
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.dialog.SearchableLanguagePickerDialog
import com.sza.fastmediasorter.ui.dialog.UiLanguagePickerItems
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S2601: the interface-language row - its picker, the restart handshake, and the split download a
 * language the install never carried needs before it can be applied.
 *
 * Its own class rather than another `setupXxx` in [GeneralSettingsViewSetupHelper], which stood over
 * detekt's LargeClass threshold. Nothing outside this group reads the current selection code or the
 * restart dialog, so the whole exchange moves as one piece.
 */
class GeneralSettingsLanguageSetupHelper(
    private val hostContext: GeneralSettingsHostContext,
    private val languageSplitInstaller: LanguageSplitInstaller,
) {
    private val binding get() = hostContext.binding
    private val viewModel get() = hostContext.viewModel
    private val fragment get() = hostContext.fragment

    // S0567: raw Spinner -> SettingsDropdownRow. S1190: -> SettingsSelectionRow, because the interface
    // language set is now whatever locales_config.xml declares and no longer fits an inline dropdown.
    fun setup() {
        Timber.d("S2601: language group setup entered in its extracted helper")
        val current = currentLanguageSelectionCode()
        binding.rowLanguage.setValue(UiLanguagePickerItems.label(fragment.requireContext(), current))
        // S1214: bound to the view lifecycle, not to the tap - a picker restored after host recreation
        // must still find a listener. The language in effect is re-read here rather than captured when
        // the picker opened, because the listener can outlive that moment.
        fragment.childFragmentManager.setFragmentResultListener(
            SearchableLanguagePickerDialog.RESULT_KEY,
            fragment.viewLifecycleOwner
        ) { _, bundle ->
            val code = bundle.getString(SearchableLanguagePickerDialog.RESULT_LANGUAGE_CODE)
                ?: return@setFragmentResultListener
            if (code != currentLanguageSelectionCode()) {
                showRestartDialog(code)
            }
        }
        binding.rowLanguage.setOnRowClickListener { showLanguagePicker() }
    }

    private fun showLanguagePicker() {
        val manager = fragment.childFragmentManager
        // A second tap while the picker is already up would stack a duplicate showing the same choice.
        if (manager.findFragmentByTag(SearchableLanguagePickerDialog.TAG) != null) return
        SearchableLanguagePickerDialog.newInstanceForUiLanguage(currentLanguageSelectionCode())
            .show(manager, SearchableLanguagePickerDialog.TAG)
    }

    private fun currentLanguageSelectionCode(): String {
        return if (LocaleHelper.isFollowingSystemLanguage(fragment.requireContext())) {
            LocaleHelper.FOLLOW_SYSTEM_LANGUAGE
        } else {
            LocaleHelper.resolveSupportedLanguageCode(viewModel.settings.value.language)
        }
    }

    private fun showRestartDialog(newLanguageCode: String) {
        val languageName = UiLanguagePickerItems.label(fragment.requireContext(), newLanguageCode)
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.restart_app_title)
            .setMessage(fragment.getString(R.string.restart_app_message, languageName))
            .setPositiveButton(R.string.restart) { _, _ -> applyLanguageWhenAvailable(newLanguageCode) }
            // Declining needs no restore: the row keeps showing the language still in effect, because
            // its value only changes once the observer sees the saved setting.
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .showBoundTo(fragment)
    }

    // S1190: an install from Play carries only the locales the device asked for, so a language the
    // user never had has to arrive before it is applied - applying first restarts into the old strings.
    private fun applyLanguageWhenAvailable(newLanguageCode: String) {
        // The sentinel names no split: whatever the system is set to is a locale the install already has.
        if (newLanguageCode == LocaleHelper.FOLLOW_SYSTEM_LANGUAGE) {
            applyLanguage(newLanguageCode)
            return
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            when (languageSplitInstaller.ensureLanguage(newLanguageCode)) {
                is LanguageSplitInstaller.Outcome.Failed -> Toast.makeText(
                    fragment.requireContext(),
                    R.string.language_download_failed,
                    Toast.LENGTH_LONG
                ).show()
                else -> applyLanguage(newLanguageCode)
            }
        }
    }

    // S2571: LocaleHelper saves the language alone, matching WelcomeActivity.onWelcomeLanguageSelected.
    // The settings write that used to sit here ran in viewModelScope, which changeLanguage then killed
    // by finishing the activity, so the language was persisted in one place and lost in the other.
    private fun applyLanguage(newLanguageCode: String) {
        Timber.d("S2571: settings language change -> $newLanguageCode, saved by LocaleHelper alone")
        LocaleHelper.markReturnToSettings(fragment.requireContext())
        LocaleHelper.changeLanguage(fragment.requireActivity(), newLanguageCode)
    }
}
