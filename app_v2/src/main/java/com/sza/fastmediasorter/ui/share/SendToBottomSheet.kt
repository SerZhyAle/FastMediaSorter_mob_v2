package com.sza.fastmediasorter.ui.share

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareTarget
import com.sza.fastmediasorter.core.share.ShareTargetIconResolver
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.databinding.SheetSendToBinding
import com.sza.fastmediasorter.databinding.ItemSendToReceiverBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Bottom-sheet presenting the gated receiver list for the «Send to..» menu (S0459 Phase 04).
 *
 * The list is resolved by [SendToMenuManager.receiversFor] (the three content gates plus the
 * host-capability gate), so the sheet shows exactly the receivers usable on the current host. Each
 * row shows the resolved icon (app icon for package-backed targets, neutral glyph for logical) and
 * an app-resolved label (ADR-5): for package-backed receivers the displayed name comes from
 * PackageManager so no brand literal is hardcoded.
 *
 * Unavailable receivers are hidden by the availability gate (goal 7), not listed; the "Not
 * installed" subtitle is only a defensive cue for the rare race where a package is uninstalled
 * between gating and binding. TV/D-pad: first row receives focus once laid out (research 03).
 */
@AndroidEntryPoint
class SendToBottomSheet : BottomSheetDialogFragment() {

    @Inject lateinit var menuManager: SendToMenuManager
    @Inject lateinit var iconResolver: ShareTargetIconResolver

    // Set by the companion factory before the fragment is shown; not persisted across process death
    // (the sheet is a modal dialog - never restored from the back stack).
    internal var content: ShareableContent? = null
    internal var settings: AppSettings? = null

    private var _binding: SheetSendToBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(
            content: ShareableContent,
            settings: AppSettings,
        ): SendToBottomSheet = SendToBottomSheet().also {
            it.content = content
            it.settings = settings
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetSendToBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentContent = content
        val currentSettings = settings
        if (currentContent == null || currentSettings == null) {
            Timber.i("SendToBottomSheet: content or settings missing, dismissing")
            dismiss()
            return
        }

        val receivers = menuManager.receiversFor(requireActivity(), currentContent, currentSettings)
        val adapter = ReceiverAdapter(receivers, currentContent)
        binding.rvSendToReceivers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSendToReceivers.adapter = adapter

        // Request focus on the first row for D-pad/TV navigation once the list has laid out
        // (research 03; doOnPreDraw fires after layout so the position-0 holder exists).
        binding.rvSendToReceivers.doOnPreDraw {
            binding.rvSendToReceivers.findViewHolderForAdapterPosition(0)
                ?.itemView?.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class ReceiverAdapter(
        private val items: List<ShareTarget>,
        private val content: ShareableContent,
    ) : RecyclerView.Adapter<ReceiverAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemSendToReceiverBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                ItemSendToReceiverBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val target = items[position]
            val b = holder.binding

            // Resolved app icon for package-backed targets; neutral glyph fallback (ADR-5).
            val icon = iconResolver.resolveIcon(target)
            when {
                icon != null -> b.ivReceiverIcon.setImageDrawable(icon)
                target.iconRes != null -> b.ivReceiverIcon.setImageResource(target.iconRes!!)
                else -> b.ivReceiverIcon.setImageResource(R.drawable.ic_share)
            }

            // ADR-5: package-backed receivers show the installed app's label to avoid brand
            // literals in the codebase; logical receivers use their declared titleRes.
            val baseLabel = iconResolver.resolveLabel(target) ?: getString(target.titleRes)
            // ADR-4: single-file receivers on a multi-selection note they apply to the first file.
            b.tvReceiverLabel.text =
                if (!target.batchCapable && content.uris.size > 1) {
                    "$baseLabel · ${getString(R.string.share_to_first_file_hint)}"
                } else {
                    baseLabel
                }

            // Defensive non-colour cue: a package target normally fails the availability gate and is
            // not listed; this only fires if the app was uninstalled between gating and binding.
            val unavailable = target.packages.isNotEmpty() && icon == null
            b.tvReceiverSubtitle.isVisible = unavailable
            if (unavailable) {
                b.tvReceiverSubtitle.setText(R.string.share_target_subtitle_not_installed)
            }

            b.root.setOnClickListener {
                menuManager.dispatch(requireActivity(), target, content)
                dismiss()
            }
        }
    }
}
