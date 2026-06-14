package com.sza.fastmediasorter.ui.delivery

import android.app.Dialog
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogDeliveryPromptBinding
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * On-demand download prompt for a [DeliverableSet] (S0386 Phase 06, Pillar D). Shows the offer with
 * an estimated size, a determinate progress bar, and success/error outcomes; refusal leaves the
 * capability off. The outcome is delivered via [setFragmentResult] so it survives recreation.
 */
@AndroidEntryPoint
class DeliveryPromptDialogFragment : DialogFragment() {

    private val viewModel: DeliveryPromptViewModel by viewModels()
    private var _binding: DialogDeliveryPromptBinding? = null
    private val binding get() = _binding!!

    private lateinit var deliverableSet: DeliverableSet

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogDeliveryPromptBinding.inflate(LayoutInflater.from(requireContext()))
        val set = DeliverableSet.valueOf(
            requireArguments().getString(ARG_SET) ?: DeliverableSet.OCR_ENGINES.name
        )
        deliverableSet = set
        isCancelable = false
        setupButtons()
        observeState()
        viewModel.start(set)
        // S0386 Phase 13.2: title names the specific capability being enabled (OCR / Translation /
        // Audio visualizations / DTS) so the offer explains what the download is for, not just its size.
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(featureNameRes(set))
            .setView(binding.root)
            .create()
    }

    @androidx.annotation.StringRes
    private fun featureNameRes(set: DeliverableSet): Int = when (set) {
        DeliverableSet.OCR_ENGINES -> R.string.ext_ocr_engines_title
        DeliverableSet.TRANSLATION -> R.string.ext_translation_title
        DeliverableSet.AUDIO_VISUALIZATIONS -> R.string.ext_audio_viz_title
        DeliverableSet.FFMPEG_DTS -> R.string.ext_ffmpeg_dts_title
    }

    private fun setupButtons() {
        binding.btnDeliveryConfirm.setOnClickListener {
            when (viewModel.uiState.value) {
                is DeliveryPromptUiState.Offer,
                is DeliveryPromptUiState.Error -> viewModel.confirmDownload()
                else -> Unit
            }
        }
        binding.btnDeliveryCancel.setOnClickListener {
            viewModel.refuse()
            finish(DeliveryPromptOutcome.REFUSED)
        }
    }

    // No view lifecycle in the onCreateDialog+setView path - collect on the fragment's own
    // lifecycle, wrapped in repeatOnLifecycle so it is not a leak-prone bare collect (Rule 20).
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: DeliveryPromptUiState) {
        when (state) {
            DeliveryPromptUiState.Idle -> Unit

            is DeliveryPromptUiState.Offer -> {
                binding.tvDeliveryMessage.text = offerText(state.estimatedBytes)
                binding.progressDelivery.isVisible = false
                binding.tvDeliveryPercent.isVisible = false
                binding.btnDeliveryConfirm.setText(R.string.delivery_action_download)
                binding.btnDeliveryConfirm.isVisible = true
            }

            is DeliveryPromptUiState.Downloading -> {
                binding.tvDeliveryMessage.setText(R.string.delivery_state_downloading)
                binding.progressDelivery.isVisible = true
                binding.progressDelivery.isIndeterminate = false
                binding.progressDelivery.setProgressCompat(state.percent, true)
                binding.tvDeliveryPercent.isVisible = true
                binding.tvDeliveryPercent.text = getString(R.string.delivery_percent, state.percent)
                binding.btnDeliveryConfirm.isVisible = false
            }

            DeliveryPromptUiState.Verifying -> {
                binding.tvDeliveryMessage.setText(R.string.delivery_state_verifying)
                binding.progressDelivery.isVisible = true
                binding.progressDelivery.isIndeterminate = true
                binding.tvDeliveryPercent.isVisible = false
                binding.btnDeliveryConfirm.isVisible = false
            }

            DeliveryPromptUiState.Success -> finish(DeliveryPromptOutcome.INSTALLED)

            is DeliveryPromptUiState.Error -> {
                // Human-readable headline only (COMMUNICATION_POLICY §2.2); the technical reason is
                // already logged by the downloader, not surfaced to the user.
                binding.tvDeliveryMessage.setText(R.string.delivery_state_error)
                binding.progressDelivery.isVisible = false
                binding.tvDeliveryPercent.isVisible = false
                binding.btnDeliveryConfirm.setText(R.string.delivery_action_retry)
                binding.btnDeliveryConfirm.isVisible = true
            }

            DeliveryPromptUiState.Refused -> finish(DeliveryPromptOutcome.REFUSED)
        }
    }

    private fun offerText(estimatedBytes: Long): String {
        return if (estimatedBytes > 0L) {
            getString(R.string.delivery_offer_message, Formatter.formatShortFileSize(requireContext(), estimatedBytes))
        } else {
            getString(R.string.delivery_offer_message_unknown_size)
        }
    }

    private fun finish(outcome: DeliveryPromptOutcome) {
        if (!isAdded) return
        // Per-set result key so two enable points gating different sets on the same owner cannot
        // cross-wire each other's outcome (the single shared key would deliver one dialog's result
        // to the other gate's listener).
        setFragmentResult(requestKey(deliverableSet), bundleOf(RESULT_OUTCOME to outcome.name))
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "delivery_prompt_result"
        const val RESULT_OUTCOME = "delivery_prompt_outcome"
        private const val ARG_SET = "delivery_set"
        private const val TAG = "delivery_prompt_dialog"

        /** Per-set fragment-result key; the listener and the dialog must agree on it. */
        fun requestKey(set: DeliverableSet): String = "${REQUEST_KEY}_${set.name}"

        /** Show the prompt for [set]; the caller observes [REQUEST_KEY] via setFragmentResultListener. */
        fun show(fragmentManager: FragmentManager, set: DeliverableSet) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            DeliveryPromptDialogFragment().apply {
                arguments = bundleOf(ARG_SET to set.name)
            }.show(fragmentManager, TAG)
        }
    }
}

/** Terminal outcome of the delivery prompt, delivered via fragment result. */
enum class DeliveryPromptOutcome { INSTALLED, REFUSED, FAILED }
