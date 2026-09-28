package com.sza.fastmediasorter.ui.player.helpers

import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.View
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.SheetStreamOffloadOfferBinding
import com.sza.fastmediasorter.domain.model.OffloadOffer
import com.sza.fastmediasorter.ui.common.dialog.BaseAppBottomSheet
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import timber.log.Timber

/**
 * BottomSheetDialogFragment surfaced when `PlayerViewModel.offloadOffer` fires.
 * Presents file size, speeds, ETA, and free-space info, then routes the user's
 * choice back to the VM: download, try streaming anyway, or cancel.
 *
 * Caller must set the offer before showing (via [newInstance]); the offer travels in
 * the fragment arguments, so recreation (rotation, process restore) re-reads it in
 * [onCreate] instead of dismissing.
 * Uses predictive-back safely: [onCancel] delegates to VM.declineOffload.
 */
class StreamOffloadOfferDialog : BaseAppBottomSheet() {

    private var _binding: SheetStreamOffloadOfferBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerViewModel by activityViewModels()

    // Cached from arguments in onCreate; arguments remain the source of truth across recreation.
    private var offer: OffloadOffer? = null

    override val contentLayout: Int = R.layout.sheet_stream_offload_offer
    override val requestKey: String = REQUEST_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val restored = arguments?.let { BundleCompat.getSerializable(it, ARG_OFFER, OffloadOffer::class.java) }
        offer = restored
        if (restored != null) {
            Timber.d("S3772: offload offer restored from arguments")
        }
    }

    override fun bindContent(content: View) {
        _binding = SheetStreamOffloadOfferBinding.bind(content)
        val o = offer
        if (o == null) {
            Timber.e("StreamOffloadOfferDialog: no offer set - dismissing")
            dismissAllowingStateLoss()
            return
        }
        bindOffer(o)
    }

    private fun bindOffer(o: OffloadOffer) {
        val ctx = requireContext()

        // File size row
        val sizeStr = Formatter.formatShortFileSize(ctx, o.fileSizeBytes)
        binding.offloadFileSizeRow.text = ctx.getString(R.string.offload_row_file_size, sizeStr)

        // Speed / required speed row
        val speedStr = o.speedMbps?.let { "%.1f Mbps".format(it) } ?: "-"
        val requiredStr = o.requiredMbps?.let { "%.0f Mbps".format(it) } ?: "-"
        binding.offloadSpeedRow.text =
            ctx.getString(R.string.offload_row_speed, speedStr, requiredStr)

        // ETA row
        val etaStr = o.estDownloadSec?.let {
            DateUtils.formatElapsedTime(it).let { s -> "~ $s" }
        } ?: "-"
        binding.offloadEtaRow.text = ctx.getString(R.string.offload_row_eta, etaStr)

        // Free space row
        val freeStr = Formatter.formatShortFileSize(ctx, o.freeStorageBytes)
        val enoughStr = if (o.hasEnoughSpace)
            ctx.getString(R.string.offload_enough)
        else
            ctx.getString(R.string.offload_not_enough)
        binding.offloadFreeSpaceRow.text =
            ctx.getString(R.string.offload_row_free_space, freeStr, enoughStr)

        // Destination row
        binding.offloadDestinationRow.text =
            ctx.getString(R.string.offload_row_destination, o.destinationLabel)

        // Insufficient-storage warning
        binding.offloadInsufficientStorageHint.isVisible = !o.hasEnoughSpace
        binding.offloadDownloadButton.isEnabled = o.hasEnoughSpace

        // Button actions
        binding.offloadDownloadButton.setOnClickListener {
            viewModel.acceptOffload(o)
            dismissAllowingStateLoss()
        }
        binding.offloadTryAnywayButton.setOnClickListener {
            viewModel.declineOffload(o)
            dismissAllowingStateLoss()
        }
        binding.offloadCancelButton.setOnClickListener {
            viewModel.declineOffload(o)
            dismissAllowingStateLoss()
        }

        // Predictive-back / system dismiss → treat as decline
        dialog?.setOnCancelListener { viewModel.declineOffload(o) }
    }

    override fun onStart() {
        super.onStart()
        // Enter routes through the Download button's own click so its disabled state (insufficient
        // space) is honoured without duplicating that guard here. Download is the default focus.
        DialogKeyboardDelegate.applyToDialogFragment(dialog, onConfirm = {
            binding.offloadDownloadButton.performClick()
        })
        binding.offloadDownloadButton.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "StreamOffloadOfferDialog"

        private const val REQUEST_KEY = "stream_offload_offer_sheet"
        private const val ARG_OFFER = "arg_offload_offer"

        fun newInstance(offer: OffloadOffer): StreamOffloadOfferDialog =
            StreamOffloadOfferDialog().also {
                it.arguments = Bundle().apply { putSerializable(ARG_OFFER, offer) }
            }
    }
}
