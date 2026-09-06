package com.sza.fastmediasorter.ui.browse.helpers

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.BrowseSwipeAction
import com.sza.fastmediasorter.domain.model.BrowseSwipeDirection
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.ui.browse.MediaFileAdapter
import timber.log.Timber

/**
 * ItemTouchHelper.Callback for the Browse file list: drag-to-reorder in MANUAL sort mode, and
 * S2533's configurable horizontal swipe actions.
 *
 * Drag is initiated only via the drag handle view - long-press drag is disabled. [onDragComplete]
 * is called with the final ordered paths when drag ends.
 *
 * A RecyclerView accepts only one ItemTouchHelper and attaching a second silently detaches the
 * first, so the swipe lives in this callback rather than beside it. [resolveSwipe] returning null
 * for a direction removes that direction from the movement flags, so a row whose action does not
 * apply does not move at all.
 *
 * Drag takes the horizontal axis under any `GridLayoutManager`, a one-column grid included, so
 * [resolveSwipe] is required to refuse there - the two halves must answer "does drag already own
 * this axis" the same way, or one row would carry drag and swipe flags for the same direction.
 */
class BrowseFileRowTouchCallback(
    private val adapter: MediaFileAdapter,
    private val onDragComplete: (orderedPaths: List<String>) -> Unit,
    private val fileAt: (position: Int) -> MediaFile?,
    private val resolveSwipe: (MediaFile, BrowseSwipeDirection) -> BrowseSwipeAction?,
    private val onSwipeAction: (MediaFile, BrowseSwipeAction) -> Unit,
) : ItemTouchHelper.Callback() {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelBounds = Rect()

    // Resolved once when the gesture starts: onChildDraw runs every frame, and each resolveSwipe
    // call reads the settings and walks the applicability policy.
    private var swipeLeftAction: BrowseSwipeAction? = null
    private var swipeRightAction: BrowseSwipeAction? = null

    // clearView ends every interaction, a swipe included, so the manual order is saved only when
    // the interaction that ended was a drag. Without this a swipe in NAME order writes a manual
    // order for a directory the user never reordered.
    private var dragging = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int {
        val dragFlags = if (recyclerView.layoutManager is GridLayoutManager) {
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        } else {
            ItemTouchHelper.UP or ItemTouchHelper.DOWN
        }
        var swipeFlags = 0
        val file = fileAt(viewHolder.bindingAdapterPosition)
        if (file != null) {
            if (resolveSwipe(file, BrowseSwipeDirection.LEFT) != null) swipeFlags = swipeFlags or ItemTouchHelper.LEFT
            if (resolveSwipe(file, BrowseSwipeDirection.RIGHT) != null) swipeFlags = swipeFlags or ItemTouchHelper.RIGHT
        }
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        Timber.d("BrowseFileRowTouchCallback: onMove $from -> $to")
        adapter.moveItem(from, to)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        val file = fileAt(position)
        val swipeDirection = directionOf(direction)
        val action = if (file != null && swipeDirection != null) resolveSwipe(file, swipeDirection) else null
        // The framework leaves a swiped row detached and none of these actions removes the row on
        // its own, so the position is re-bound to put it back before the action opens its dialog.
        if (position != RecyclerView.NO_POSITION) adapter.notifyItemChanged(position)
        if (file != null && action != null) onSwipeAction(file, action)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
            drawSwipeBackground(c, recyclerView, viewHolder, dX)
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    /**
     * Names the action while the row is still moving, so a gesture begun by mistake can be
     * abandoned before it commits. Icon plus word, never colour alone.
     */
    private fun drawSwipeBackground(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
    ) {
        val action = (if (dX < 0) swipeLeftAction else swipeRightAction) ?: return
        val context = recyclerView.context
        val itemView = viewHolder.itemView

        backgroundPaint.color = MaterialColors.getColor(
            recyclerView,
            com.google.android.material.R.attr.colorSurfaceVariant,
        )
        val left = if (dX < 0) itemView.right + dX else itemView.left.toFloat()
        val right = if (dX < 0) itemView.right.toFloat() else itemView.left + dX
        c.drawRect(left, itemView.top.toFloat(), right, itemView.bottom.toFloat(), backgroundPaint)

        val onSurface = MaterialColors.getColor(
            recyclerView,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
        )
        val label = context.getString(BrowseSwipeActionCatalog.labelResFor(action))
        labelPaint.color = onSurface
        labelPaint.textSize = context.resources.getDimension(R.dimen.text_size_normal)
        labelPaint.getTextBounds(label, 0, label.length, labelBounds)

        val icon = ContextCompat.getDrawable(context, BrowseSwipeActionCatalog.iconResFor(action))
        val margin = context.resources.getDimensionPixelSize(R.dimen.margin_normal)
        val iconSize = icon?.intrinsicHeight ?: 0
        val centerY = (itemView.top + itemView.bottom) / 2

        val baseline = (centerY + labelBounds.height() / 2).toFloat()
        icon?.setTint(onSurface)
        if (dX < 0) {
            val labelLeft = itemView.right - margin - labelBounds.width()
            c.drawText(label, labelLeft.toFloat(), baseline, labelPaint)
            val iconRight = labelLeft - margin
            icon?.setBounds(iconRight - iconSize, centerY - iconSize / 2, iconRight, centerY + iconSize / 2)
        } else {
            val iconLeft = itemView.left + margin
            icon?.setBounds(iconLeft, centerY - iconSize / 2, iconLeft + iconSize, centerY + iconSize / 2)
            val labelLeft = iconLeft + iconSize + margin
            c.drawText(label, labelLeft.toFloat(), baseline, labelPaint)
        }
        icon?.draw(c)
    }

    private fun directionOf(direction: Int): BrowseSwipeDirection? = when (direction) {
        ItemTouchHelper.LEFT -> BrowseSwipeDirection.LEFT
        ItemTouchHelper.RIGHT -> BrowseSwipeDirection.RIGHT
        else -> null
    }

    override fun isLongPressDragEnabled(): Boolean = false

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                dragging = true
                viewHolder?.itemView?.alpha = DRAG_ALPHA
                viewHolder?.itemView?.elevation = DRAG_ELEVATION_PX
            }
            ItemTouchHelper.ACTION_STATE_SWIPE -> {
                val file = viewHolder?.let { fileAt(it.bindingAdapterPosition) }
                swipeLeftAction = file?.let { resolveSwipe(it, BrowseSwipeDirection.LEFT) }
                swipeRightAction = file?.let { resolveSwipe(it, BrowseSwipeDirection.RIGHT) }
            }
            else -> Unit
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = 1f
        viewHolder.itemView.elevation = 0f
        swipeLeftAction = null
        swipeRightAction = null
        if (!dragging) return
        dragging = false
        val orderedPaths = adapter.getOrderedPaths()
        Timber.d("BrowseFileRowTouchCallback: clearView, saving ${orderedPaths.size} paths")
        onDragComplete(orderedPaths)
    }

    private companion object {
        const val DRAG_ALPHA = 0.85f
        const val DRAG_ELEVATION_PX = 8f
    }
}
