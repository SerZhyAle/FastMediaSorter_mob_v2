package com.sza.fastmediasorter.ui.main.helpers

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.domain.model.isAllFilesPredefined
import com.sza.fastmediasorter.ui.main.MainViewModel
import com.sza.fastmediasorter.ui.main.ResourceAdapter
import timber.log.Timber

/**
 * ItemTouchHelper.Callback for drag-to-reorder in the main resource list.
 *
 * - isLongPressDragEnabled = false: drag is initiated only by touching the drag handle view,
 *   so the existing long-press → edit dialog behavior is fully preserved.
 * - onMove: delegates to adapter.moveItem() for live animation (notifyItemMoved, no DiffUtil).
 * - clearView: persists the final order via viewModel.saveResourceOrder() (ADR-2).
 * - Visual feedback: elevation + alpha on the dragged item during the gesture.
 */
class ResourceItemTouchCallback(
    private val adapter: ResourceAdapter,
    private val viewModel: MainViewModel
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean = false

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // S0488: the pinned All-files row stays first - it cannot be dragged.
        if (adapter.getDragOrderedList().getOrNull(viewHolder.bindingAdapterPosition)?.isAllFilesPredefined == true) {
            return makeMovementFlags(0, 0)
        }
        val dragFlags = if (recyclerView.layoutManager is GridLayoutManager) {
            // Grid: allow all four directions
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        } else {
            // List: vertical only
            ItemTouchHelper.UP or ItemTouchHelper.DOWN
        }
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // S0488: never let another row drop above the pinned All-files row at index 0.
        if (target.bindingAdapterPosition == 0 &&
            adapter.getDragOrderedList().firstOrNull()?.isAllFilesPredefined == true
        ) {
            return false
        }
        return super.canDropOver(recyclerView, current, target)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Swipe disabled - nothing to do
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.apply {
                if (AnimationPolicy.isAnimationAllowed) {
                    animate().setDuration(ANIM_DURATION_MS)
                        .scaleX(DRAG_SCALE)
                        .scaleY(DRAG_SCALE)
                        .alpha(DRAG_ALPHA)
                } else {
                    animate().cancel()
                    scaleX = DRAG_SCALE
                    scaleY = DRAG_SCALE
                    alpha = DRAG_ALPHA
                    Timber.d("S2250: drag selection animation skipped")
                }
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.apply {
            if (AnimationPolicy.isAnimationAllowed) {
                animate().setDuration(ANIM_DURATION_MS)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
            } else {
                animate().cancel()
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
                Timber.d("S2250: drag restore animation skipped")
            }
        }
        // Persist the new order and update DiffUtil snapshot (ADR-2)
        val newOrder = adapter.getDragOrderedList()
        adapter.submitList(newOrder)
        viewModel.saveResourceOrder(newOrder)
    }

    private companion object {
        const val DRAG_SCALE = 1.03f
        const val DRAG_ALPHA = 0.85f
        const val ANIM_DURATION_MS = 150L
    }
}
