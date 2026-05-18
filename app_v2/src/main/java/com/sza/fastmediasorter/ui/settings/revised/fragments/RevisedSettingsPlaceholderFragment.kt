package com.sza.fastmediasorter.ui.settings.revised.fragments

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsRevisedPlaceholderBinding

class RevisedSettingsPlaceholderFragment : Fragment(R.layout.fragment_settings_revised_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentSettingsRevisedPlaceholderBinding.bind(view)
        val titleResId = requireArguments().getInt(ARG_TITLE_RES_ID)

        binding.placeholderTitle.setText(titleResId)
        binding.placeholderSectionLabel.setText(titleResId)
        binding.placeholderAnchor.setOnClickListener {
            // The anchor stays intentionally inert in Phase 02: search and keyboard plumbing
            // need a focus target before real settings rows replace this placeholder fragment.
        }
    }

    companion object {
        private const val ARG_TITLE_RES_ID = "arg_title_res_id"

        fun newInstance(@StringRes titleResId: Int): RevisedSettingsPlaceholderFragment {
            return RevisedSettingsPlaceholderFragment().apply {
                arguments = bundleOf(ARG_TITLE_RES_ID to titleResId)
            }
        }
    }
}