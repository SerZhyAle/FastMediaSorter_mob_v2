package com.sza.fastmediasorter.ui.settings.helpers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import com.sza.fastmediasorter.ui.settings.fragments.OpenSourceLicensesFragment
import com.sza.fastmediasorter.ui.welcome.WelcomeActivity
import timber.log.Timber

/**
 * S2601: the documentation, privacy and licence buttons at the foot of General Settings.
 *
 * Its own class rather than another `setupXxx` in [GeneralSettingsViewSetupHelper], which stood over
 * detekt's LargeClass threshold. `openUrl` has no caller outside this group, so leaving it behind
 * would have left dead code in the class the split exists to shrink.
 */
class GeneralSettingsLinkButtonsSetupHelper(
    private val hostContext: GeneralSettingsHostContext,
) {
    private val binding get() = hostContext.binding
    private val viewModel get() = hostContext.viewModel
    private val fragment get() = hostContext.fragment

    fun setup() {
        binding.btnUserGuide.setOnClickListener {
            val url = when (LocaleHelper.getLanguage(fragment.requireContext())) {
                "ru" -> "$DOCS_ROOT/howto/index-ru.html"
                "uk" -> "$DOCS_ROOT/howto/index-uk.html"
                else -> "$DOCS_ROOT/howto/"
            }
            openUrl(url, fragment.getString(R.string.settings_no_browser_for_docs))
        }
        binding.btnHowToGuides.setOnClickListener {
            val url = when (LocaleHelper.getLanguage(fragment.requireContext())) {
                "ru" -> "$DOCS_ROOT/HOW_TO_RU.html"
                "uk" -> "$DOCS_ROOT/HOW_TO_UK.html"
                else -> "$DOCS_ROOT/HOW_TO.html"
            }
            openUrl(url, fragment.getString(R.string.settings_no_browser_for_docs))
        }
        // S0994: PC-side companion publish-folders guide, shown only when companion import is
        // available (lite/vr hide it).
        binding.btnCompanionPublishGuide.isVisible = viewModel.isCompanionImportAvailable
        binding.btnCompanionPublishGuide.setOnClickListener {
            openUrl(
                SupportIntentFactory.companionPublishGuideUrl(),
                fragment.getString(R.string.settings_no_browser_for_docs),
            )
        }
        binding.btnOpenWelcome.setOnClickListener {
            fragment.startActivity(Intent(fragment.requireContext(), WelcomeActivity::class.java))
        }
        binding.btnPrivacyPolicy.setOnClickListener {
            val url = when (LocaleHelper.getLanguage(fragment.requireContext())) {
                "ru" -> "$DOCS_ROOT/PRIVACY_POLICY.ru.html"
                "uk" -> "$DOCS_ROOT/PRIVACY_POLICY.uk.html"
                else -> "$DOCS_ROOT/PRIVACY_POLICY.html"
            }
            openUrl(url, fragment.getString(R.string.settings_no_browser_for_privacy))
        }
        binding.btnOpenSourceLicenses.setOnClickListener {
            fragment.parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, OpenSourceLicensesFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun openUrl(url: String, notFoundMessage: String) {
        try {
            fragment.startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) })
        } catch (e: ActivityNotFoundException) {
            // The device has no browser at all: the toast is the whole recovery, and the throwable is
            // logged rather than dropped so a support log says which address found no handler.
            Timber.w(e, "GeneralSettings: no activity to open $url")
            Toast.makeText(fragment.requireContext(), notFoundMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val DOCS_ROOT = "https://serzhyale.github.io/FastMediaSorter_mob_v2/docs"
    }
}
