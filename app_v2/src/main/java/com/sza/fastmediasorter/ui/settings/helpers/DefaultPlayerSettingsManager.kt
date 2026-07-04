package com.sza.fastmediasorter.ui.settings.helpers

import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.databinding.DialogDefaultAppsBinding

/**
 * Wires the "Default app" subgroup: gates the four registration buttons by the flavor capability
 * surface and dispatches each tap into the shared [DefaultPlayerHelper] flow (the same OS-registration
 * path the welcome screen uses). Buttons are not auto-hidden when already default - the subgroup keeps
 * every supported type reachable for re-registration.
 *
 * S0880: the subgroup lives in [DefaultAppsDialogFragment], reached from a launcher button in the
 * Management tab (previously it was inline in fragment_settings_destinations).
 */
class DefaultPlayerSettingsManager {

    fun bind(
        fragment: Fragment,
        binding: DialogDefaultAppsBinding,
        capabilities: MediaCapabilities,
    ) {
        bindViews(
            fragment, capabilities,
            layoutDefaultPlayerSubgroup = binding.layoutDefaultPlayerSubgroup,
            btnImages = binding.btnSettingsDefaultPlayerImages,
            btnAudio = binding.btnSettingsDefaultPlayerAudio,
            btnVideo = binding.btnSettingsDefaultPlayerVideo,
            btnDocs = binding.btnSettingsDefaultPlayerDocs,
            hint = binding.tvDefaultPlayerSettingsHint,
        )
    }

    private fun bindViews(
        fragment: Fragment,
        capabilities: MediaCapabilities,
        layoutDefaultPlayerSubgroup: android.view.View,
        btnImages: android.widget.Button,
        btnAudio: android.widget.Button,
        btnVideo: android.widget.Button,
        btnDocs: android.widget.Button,
        hint: android.widget.TextView,
    ) {
        if (!capabilities.supportsDefaultPlayer) {
            layoutDefaultPlayerSubgroup.isVisible = false
            return
        }
        layoutDefaultPlayerSubgroup.isVisible = true

        btnImages.isVisible = capabilities.supportsImages
        btnAudio.isVisible = capabilities.supportsAudio
        btnVideo.isVisible = capabilities.supportsVideo
        btnDocs.isVisible = capabilities.supportsDocuments

        hint.text = HtmlCompat.fromHtml(
            fragment.getString(R.string.welcome_default_player_hint),
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        )

        val includeEpub = capabilities.supportsEpub
        btnImages.setOnClickListener { onTypeClick(fragment, "image/*", includeEpub) }
        btnAudio.setOnClickListener { onTypeClick(fragment, "audio/*", includeEpub) }
        btnVideo.setOnClickListener { onTypeClick(fragment, "video/*", includeEpub) }
        // null mime -> documents path (PDF / text / EPUB / Office sub-choice).
        btnDocs.setOnClickListener { onTypeClick(fragment, null, includeEpub) }
    }

    private fun onTypeClick(fragment: Fragment, mimeType: String?, includeEpub: Boolean) {
        if (mimeType == null) {
            DefaultPlayerHelper.showSetDefaultDocumentDialog(fragment, includeEpub)
        } else {
            DefaultPlayerHelper.showSetDefaultDialogForType(fragment, mimeType)
        }
    }
}
