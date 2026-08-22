package com.sza.fastmediasorter.ui.launcher.section

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
import com.sza.fastmediasorter.databinding.LauncherSignalListItemBinding
import com.sza.fastmediasorter.databinding.SheetLauncherSectionActionsBinding

/**
 * S1742 §03.1: bottom sheet listing options available for a section header.
 *
 * Caller populates [items] and [onItemClick] before calling [show]. The first row automatically takes focus
 * so D-pad and TV users can navigate without an initial tap.
 */
class LauncherSectionActionsSheet : BottomSheetDialogFragment() {

    enum class Action {
        RENAME
    }

    data class ActionItem(
        val action: Action,
        val label: String,
        val iconResId: Int? = R.drawable.ic_rename,
    )

    internal var items: List<ActionItem> = emptyList()
    internal var onItemClick: (Action) -> Unit = {}

    private var binding: SheetLauncherSectionActionsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = SheetLauncherSectionActionsBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = binding?.launcherSectionActionsList ?: return
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = ActionsAdapter()
        list.doOnPreDraw {
            list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private inner class ActionsAdapter : RecyclerView.Adapter<ActionsAdapter.ViewHolder>() {

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

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val iconResId = item.iconResId
            if (iconResId != null) {
                holder.itemBinding.launcherSignalItemIcon.setImageResource(iconResId)
                holder.itemBinding.launcherSignalItemIcon.isVisible = true
            } else {
                holder.itemBinding.launcherSignalItemIcon.isVisible = false
            }
            holder.itemBinding.launcherSignalItemLabel.text = item.label
            holder.itemBinding.launcherSignalItemDetail.isVisible = false
            holder.itemBinding.root.contentDescription = item.label
            holder.itemBinding.root.setOnClickListener {
                onItemClick(item.action)
                dismiss()
            }
        }
    }
}
