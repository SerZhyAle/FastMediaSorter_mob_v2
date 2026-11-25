package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LanguageSplitInstaller
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.dialog.SearchableLanguagePickerDialog
import com.sza.fastmediasorter.ui.dialog.UiLanguagePickerItems
import kotlinx.coroutines.launch

/**
 * S2601: the interface-language row - its picker, and the split download a language the install never
 * carried needs before it can be applied. S4603: the chosen language applies at once, without a restart.
 *
 * Its own class rather than another `setupXxx` in [GeneralSettingsViewSetupHelper], which stood over
 * detekt's LargeClass threshold. Nothing outside this group reads the current selection code, so the
 * whole exchange moves as one piece.
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
                applyLanguageWhenAvailable(code)
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

    // S1190: an install from Play carries only the locales the device asked for, so a language the
    // user never had has to arrive before it is applied - applying first re-creates the screens in the old strings.
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
    // S4603: no return-to-settings flag and no finish - the open screens are re-created in place, so a flag
    // left set would forward the next cold start into Settings.
    private fun applyLanguage(newLanguageCode: String) {
        LocaleHelper.applyLanguageLive(fragment.requireContext(), newLanguageCode)
    }
}
