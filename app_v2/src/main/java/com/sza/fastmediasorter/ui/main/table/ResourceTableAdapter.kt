package com.sza.fastmediasorter.ui.main.table

import android.graphics.Typeface
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemResourceTableRowBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.isFileCountUnknown
import com.sza.fastmediasorter.ui.icon.ResourceIconComposer
import com.sza.fastmediasorter.ui.main.ResourceAdapter

/**
 * S4041: the rows of the wide-window table. Selecting and opening are separate gestures by contract:
 * a single click, a tap or keyboard focus only selects, while a second click on the same row within the
 * double-tap timeout, Enter, numpad Enter or the D-pad centre opens. The secondary mouse button and the
 * row's overflow open the existing row menu.
 */
class ResourceTableAdapter(
    private val onSelect: (MediaResource) -> Unit,
    private val onOpen: (MediaResource) -> Unit,
    private val onShowMenu: (View, MediaResource) -> Unit,
) : ListAdapter<MediaResource, ResourceTableAdapter.RowHolder>(RowDiff) {

    private var selectedId: Long? = null

    fun setSelectedResource(resourceId: Long?) {
        if (resourceId == selectedId) return
        val previousId = selectedId
        selectedId = resourceId
        currentList.forEachIndexed { index, resource ->
            if (resource.id == previousId || resource.id == resourceId) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder =
        RowHolder(ItemResourceTableRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RowHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.applySelection(getItem(position))
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) = holder.bind(getItem(position))

    inner class RowHolder(private val binding: ItemResourceTableRowBinding) : RecyclerView.ViewHolder(binding.root) {

        private var lastClickAt = 0L
        private var lastClickId: Long? = null
        private var openActionId = View.NO_ID

        /** The overflow button of the row, which a keyboard-opened menu anchors on. */
        val menuAnchor: View get() = binding.btnResourceTableMore

        fun bind(resource: MediaResource) {
            val context = binding.root.context
            val isFavoritesEntry = resource.id == FAVORITES_ENTRY_ID
            binding.ivResourceTableIcon.setImageDrawable(ResourceIconComposer.compose(context, resource))
            binding.tvResourceTableName.text = resource.name
            binding.tvResourceTableSource.text = ResourceAdapter.sourceLabel(context, resource)
            ResourceAdapter.applySourceChip(binding.tvResourceTableSource, resource)
            binding.tvResourceTableFiles.text =
                if (isFavoritesEntry) "" else ResourceAdapter.formatListFileCount(context, resource)
            // Italic tells an unknown count from a counted zero without relying on colour, as in the list.
            binding.tvResourceTableFiles.setTypeface(
                null,
                if (!isFavoritesEntry && resource.isFileCountUnknown) Typeface.ITALIC else Typeface.NORMAL,
            )
            binding.tvResourceTableContent.text = if (isFavoritesEntry) {
                ""
            } else {
                ResourceAdapter.formatMediaTypes(context, resource.supportedMediaTypes, resource.allFiles)
            }
            binding.tvResourceTablePath.text = ResourceAdapter.displayPath(resource)

            binding.btnResourceTableMore.isVisible = !isFavoritesEntry
            binding.btnResourceTableMore.contentDescription =
                context.getString(R.string.resource_more_actions_for, resource.name)
            binding.btnResourceTableMore.setOnClickListener { view -> onShowMenu(view, resource) }

            bindInput(resource, isFavoritesEntry)
            applySelection(resource)
        }

        private fun bindInput(resource: MediaResource, isFavoritesEntry: Boolean) {
            val root = binding.root
            root.setOnClickListener { onRowClick(resource) }
            root.setOnKeyListener { _, keyCode, event ->
                if (keyCode !in OPEN_KEYS) return@setOnKeyListener false
                // Both halves are consumed so the default press handling cannot turn the key into a select.
                if (event.action == KeyEvent.ACTION_UP) onOpen(resource)
                true
            }
            root.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus && !view.isInTouchMode) onSelect(resource)
            }
            root.setOnGenericMotionListener { _, event ->
                val secondary = event.action == MotionEvent.ACTION_BUTTON_PRESS &&
                    event.buttonState == MotionEvent.BUTTON_SECONDARY
                if (secondary && !isFavoritesEntry) onShowMenu(binding.btnResourceTableMore, resource)
                secondary
            }
            // A screen-reader user cannot double-tap twice in a row, so opening is offered as its own action.
            // Rebinding replaces it, since each add registers one more entry on the recycled view.
            if (openActionId != View.NO_ID) ViewCompat.removeAccessibilityAction(root, openActionId)
            val openLabel = root.context.getString(R.string.action_open)
            openActionId = ViewCompat.addAccessibilityAction(root, openLabel) { _, _ ->
                onOpen(resource)
                true
            }
        }

        private fun onRowClick(resource: MediaResource) {
            val now = SystemClock.uptimeMillis()
            val isSecondClick = lastClickId == resource.id &&
                now - lastClickAt <= ViewConfiguration.getDoubleTapTimeout()
            if (isSecondClick) {
                lastClickAt = 0L
                lastClickId = null
                onOpen(resource)
            } else {
                lastClickAt = now
                lastClickId = resource.id
                onSelect(resource)
            }
        }

        fun applySelection(resource: MediaResource) {
            val selected = resource.id == selectedId
            binding.root.isSelected = selected
            binding.root.isActivated = selected
        }
    }

    private object RowDiff : DiffUtil.ItemCallback<MediaResource>() {
        override fun areItemsTheSame(oldItem: MediaResource, newItem: MediaResource) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MediaResource, newItem: MediaResource) = oldItem == newItem
    }

    private companion object {
        const val PAYLOAD_SELECTION = "payload_table_selection"

        // The pseudo-resource the list uses for the Favorites entry; it has no count, types or menu.
        const val FAVORITES_ENTRY_ID = -100L

        val OPEN_KEYS = setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER)
    }
}
