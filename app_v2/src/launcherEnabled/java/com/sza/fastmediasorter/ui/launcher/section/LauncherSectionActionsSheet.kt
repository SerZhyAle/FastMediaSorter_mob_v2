package com.sza.fastmediasorter.ui.launcher.section

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.LauncherSignalListItemBinding
import com.sza.fastmediasorter.databinding.SheetLauncherSectionActionsBinding
import com.sza.fastmediasorter.ui.common.dialog.BaseAppBottomSheet

/**
 * S1742 §03.1: bottom sheet listing options available for a section header.
 *
 * Caller populates [items] and [onItemClick] before calling [show]. The first row automatically takes focus
 * so D-pad and TV users can navigate without an initial tap.
 */
class LauncherSectionActionsSheet : BaseAppBottomSheet() {

    enum class Action {
        RENAME,
        RESORT,
        MOVE_UP,
        MOVE_DOWN,
        DELETE,
    }

    data class ActionItem(
        val action: Action,
        val label: String,
        val iconResId: Int? = R.drawable.ic_rename,
    )

    internal var items: List<ActionItem> = emptyList()
    internal var onItemClick: (Action) -> Unit = {}

    private var binding: SheetLauncherSectionActionsBinding? = null

    override val contentLayout: Int = R.layout.sheet_launcher_section_actions
    override val requestKey: String = REQUEST_KEY

    override fun bindContent(content: View) {
        val sheet = SheetLauncherSectionActionsBinding.bind(content)
        binding = sheet
        val list = sheet.launcherSectionActionsList
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

    private companion object {
        const val REQUEST_KEY = "launcher_section_actions_sheet"
    }
}
