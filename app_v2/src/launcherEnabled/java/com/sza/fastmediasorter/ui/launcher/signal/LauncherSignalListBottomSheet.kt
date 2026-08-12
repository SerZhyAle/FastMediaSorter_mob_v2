package com.sza.fastmediasorter.ui.launcher.signal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.fastmediasorter.databinding.LauncherSignalListItemBinding
import com.sza.fastmediasorter.databinding.LauncherSignalListSheetBinding

/**
 * S1421 §5.1: every active signal, including the ones the strip could not fit.
 *
 * The owner picked the one overflow rule that loses nothing, and this list is what makes that true - it is
 * the only way to reach a signal the row truncated. It holds no registry and performs no navigation: the
 * strip's owner supplies the list and the tap handler, so there is exactly one navigation path (ADR-2).
 *
 * A picker, so it carries no confirm/cancel pair - CLAUDE.md §11 exempts this shape.
 */
class LauncherSignalListBottomSheet : BottomSheetDialogFragment() {

    // Set by the caller before show(); not persisted across process death, as this is a modal dialog that is
    // never restored from the back stack.
    internal var signals: List<LauncherSignal> = emptyList()
    internal var onTap: (LauncherSignal) -> Unit = {}

    private var binding: LauncherSignalListSheetBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = LauncherSignalListSheetBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = binding?.launcherSignalList ?: return
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = SignalAdapter()
        // Focus the first row once it exists, so the sheet is usable from a D-pad without a first tap.
        // doOnPreDraw fires after layout, which is when the position-0 holder is there to focus.
        list.doOnPreDraw {
            list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private inner class SignalAdapter : RecyclerView.Adapter<SignalAdapter.ViewHolder>() {

        inner class ViewHolder(val itemBinding: LauncherSignalListItemBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                LauncherSignalListItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )

        override fun getItemCount(): Int = signals.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val signal = signals[position]
            holder.itemBinding.launcherSignalItemIcon.setImageResource(signal.iconRes)
            holder.itemBinding.launcherSignalItemLabel.text = signal.label
            holder.itemBinding.launcherSignalItemDetail.text = signal.detail.orEmpty()
            holder.itemBinding.launcherSignalItemDetail.isVisible = !signal.detail.isNullOrBlank()
            holder.itemBinding.root.contentDescription = signal.label
            holder.itemBinding.root.setOnClickListener {
                onTap(signal)
                dismiss()
            }
        }
    }
}
