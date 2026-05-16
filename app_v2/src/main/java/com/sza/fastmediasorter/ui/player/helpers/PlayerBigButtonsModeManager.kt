package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R

/**
 * Applies Big Buttons Mode layout transforms to the player's top command panel and bottom
 * playback button row. All methods are designed to be called from the UI thread after views
 * have been measured. Stateless with respect to layout XML — all changes are applied and
 * restored in memory via stored LayoutParams snapshots.
 *
 * Usage: call apply* before showing the player; call restore* on orientation change before
 * re-applying (see [CommandPanelController.updateOrientation]).
 */
class PlayerBigButtonsModeManager(private val context: Context) {

    private data class SavedButtonState(
        val originalIndex: Int,
        val originalParent: ViewGroup? = null,
        val width: Int,
        val height: Int,
        val weight: Float,
        val paddingLeft: Int,
        val paddingTop: Int,
        val paddingRight: Int,
        val paddingBottom: Int,
        val scaleType: ImageView.ScaleType? = null
    )

    // Top panel state
    private val topPanelSavedStates = mutableMapOf<Int, SavedButtonState>()
    private val topPanelWrappers = mutableMapOf<Int, LinearLayout>()
    private val topPanelHiddenContainers = mutableMapOf<View, Int>()
    private val topPanelSeparatorViews = mutableListOf<View>()
    private var topPanelOriginalHeight: Int? = null

    // Bottom row state — keyed by child view id
    private val bottomRowSavedStates = mutableMapOf<Int, SavedButtonState>()

    // ── Top command panel ─────────────────────────────────────────────────────────────────────

    /**
     * Scales [topCommandPanel] to 2× its current measured height and distributes
     * [visibleButtons] + [overflowButton] equally across the full panel width.
     * Each ImageButton is wrapped in a vertical LinearLayout that includes a short-label TextView.
     * Idempotent: second call with the same panel is a no-op.
     */
    fun applyToTopCommandPanel(
        topCommandPanel: LinearLayout,
        visibleButtons: List<View>,
        overflowButton: View?,
        bigButtonsMode: Boolean
    ) {
        if (!bigButtonsMode) return
        if (topPanelSavedStates.isNotEmpty()) return // already applied

        // S0158/S0208: fixed dimen prevents runaway growth on restore→apply cycles.
        val buttonHeight = topCommandPanel.resources.getDimensionPixelSize(R.dimen.player_big_button_height)

        val panelParams = topCommandPanel.layoutParams
        topPanelOriginalHeight = topPanelOriginalHeight ?: panelParams.height
        panelParams.height = buttonHeight
        topCommandPanel.layoutParams = panelParams

        val allButtons = (visibleButtons + listOfNotNull(overflowButton))
            .distinctBy { it.id }

        for (button in allButtons) {
            if (button.id == View.NO_ID) continue
            val originalParent = button.parent as? ViewGroup ?: continue
            val lp = button.layoutParams as? LinearLayout.LayoutParams
            topPanelSavedStates[button.id] = SavedButtonState(
                originalIndex = originalParent.indexOfChild(button),
                originalParent = originalParent,
                width = lp?.width ?: LinearLayout.LayoutParams.WRAP_CONTENT,
                height = lp?.height ?: LinearLayout.LayoutParams.WRAP_CONTENT,
                weight = lp?.weight ?: 0f,
                paddingLeft = button.paddingLeft,
                paddingTop = button.paddingTop,
                paddingRight = button.paddingRight,
                paddingBottom = button.paddingBottom
            )
        }

        val dp8 = (8 * context.resources.displayMetrics.density).toInt()
        val dp1 = context.resources.displayMetrics.density.toInt().coerceAtLeast(1)

        allButtons.forEachIndexed { index, button ->
            val savedState = topPanelSavedStates[button.id] ?: return@forEachIndexed
            val originalParent = savedState.originalParent ?: return@forEachIndexed
            rememberAndHideIntermediateTopPanelContainer(topCommandPanel, button)

            val wrapper = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                // S0208: 85% icon + 15% label (weights 17 / 3 of weightSum 20).
                weightSum = 20f
            }

            // S0208: icon dominates 85% of wrapper height; FIT_CENTER scales it up proportionally.
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 17f
            )
            when (button) {
                is ImageView -> {
                    button.scaleType = ImageView.ScaleType.FIT_CENTER
                    button.setPadding(dp8, dp8, dp8, dp8)
                }
                is TextView -> {
                    button.textSize = 18f
                }
            }

            originalParent.removeView(button)
            wrapper.addView(button)

            if (button !is TextView) {
                // S0208: label gets 15% of wrapper height; single line + ellipsize prevents
                // long uk/ru translations from wrapping into the icon area.
                wrapper.addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f
                    )
                    text = getShortLabel(button)
                    textSize = context.resources.getDimension(
                        R.dimen.player_big_button_top_label_text_size
                    ) / context.resources.displayMetrics.scaledDensity
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }

            topCommandPanel.addView(wrapper)
            topPanelWrappers[button.id] = wrapper

            // 1dp semi-transparent separator between buttons
            if (index < allButtons.size - 1) {
                val sep = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp1, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(0x33FFFFFF)
                }
                topCommandPanel.addView(sep)
                topPanelSeparatorViews.add(sep)
            }
        }

        topCommandPanel.requestLayout()
    }

    /**
     * Reverses [applyToTopCommandPanel]: removes wrappers, restores original ImageButton
     * positions and LayoutParams.
     */
    fun restoreTopCommandPanel(topCommandPanel: LinearLayout) {
        if (topPanelSavedStates.isEmpty()) return

        val sortedStates = topPanelSavedStates.entries.sortedWith(
            compareBy(
                { System.identityHashCode(it.value.originalParent) },
                { it.value.originalIndex }
            )
        )

        for ((buttonId, savedState) in sortedStates) {
            val wrapper = topPanelWrappers[buttonId] ?: continue
            val button = wrapper.getChildAt(0) ?: continue
            val originalParent = savedState.originalParent ?: continue

            val restoredLp = LinearLayout.LayoutParams(
                savedState.width, savedState.height, savedState.weight
            )
            button.layoutParams = restoredLp
            button.setPadding(
                savedState.paddingLeft, savedState.paddingTop,
                savedState.paddingRight, savedState.paddingBottom
            )

            if (topCommandPanel.indexOfChild(wrapper) >= 0) {
                wrapper.removeView(button)
                topCommandPanel.removeView(wrapper)
                originalParent.addView(button, savedState.originalIndex.coerceAtMost(originalParent.childCount))
            }
        }

        for ((container, originalVisibility) in topPanelHiddenContainers) {
            container.visibility = originalVisibility
        }

        topPanelSeparatorViews.forEach { topCommandPanel.removeView(it) }
        topPanelSeparatorViews.clear()

        topPanelOriginalHeight?.let { originalHeight ->
            val panelParams = topCommandPanel.layoutParams
            panelParams.height = originalHeight
            topCommandPanel.layoutParams = panelParams
        }

        topPanelSavedStates.clear()
        topPanelWrappers.clear()
        topPanelHiddenContainers.clear()
        topPanelOriginalHeight = null
        topCommandPanel.requestLayout()
    }

    // ── Bottom playback row ───────────────────────────────────────────────────────────────────

    /**
     * Scales [buttonRow] to 2× its current measured height and distributes visible children
     * equally across the full row width. Idempotent.
     */
    fun applyToBottomPlaybackRow(buttonRow: LinearLayout, bigButtonsMode: Boolean) {
        if (!bigButtonsMode) return
        if (bottomRowSavedStates.isNotEmpty()) return // already applied

        val visibleChildren = (0 until buttonRow.childCount)
            .map { buttonRow.getChildAt(it) }
            .filter { it.isVisible }

        for (child in visibleChildren) {
            if (child.id == View.NO_ID) continue
            val lp = child.layoutParams as? LinearLayout.LayoutParams
            bottomRowSavedStates[child.id] = SavedButtonState(
                originalIndex = buttonRow.indexOfChild(child),
                width = lp?.width ?: LinearLayout.LayoutParams.WRAP_CONTENT,
                height = lp?.height ?: LinearLayout.LayoutParams.WRAP_CONTENT,
                weight = lp?.weight ?: 0f,
                paddingLeft = child.paddingLeft,
                paddingTop = child.paddingTop,
                paddingRight = child.paddingRight,
                paddingBottom = child.paddingBottom,
                scaleType = (child as? ImageView)?.scaleType
            )
        }

        // S0208: unified big-button height applies to both top and bottom panels.
        val buttonHeight = buttonRow.resources.getDimensionPixelSize(R.dimen.player_big_button_height)

        val rowParams = buttonRow.layoutParams
        rowParams.height = buttonHeight
        buttonRow.layoutParams = rowParams

        val dp8 = (8 * context.resources.displayMetrics.density).toInt()

        for (child in visibleChildren) {
            child.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            when (child) {
                // S0208: bottom row stays icon-only — no text labels added. MaterialButtons here
                // already render glyph-style "text" (▶ / ‖ / skip arrows); bumped to 22sp so they
                // scale with the new 100dp row height.
                is MaterialButton -> child.textSize = 22f
                is ImageView -> {
                    child.scaleType = ImageView.ScaleType.FIT_CENTER
                    child.setPadding(dp8, dp8, dp8, dp8)
                }
            }
        }

        buttonRow.weightSum = visibleChildren.size.toFloat()
        buttonRow.requestLayout()
    }

    /**
     * Reverses [applyToBottomPlaybackRow]: restores original LayoutParams for each child.
     */
    fun restoreBottomPlaybackRow(buttonRow: LinearLayout) {
        if (bottomRowSavedStates.isEmpty()) return

        for ((childId, savedState) in bottomRowSavedStates) {
            val child = buttonRow.findViewById<View>(childId) ?: continue
            child.layoutParams = LinearLayout.LayoutParams(
                savedState.width, savedState.height, savedState.weight
            )
            child.setPadding(
                savedState.paddingLeft, savedState.paddingTop,
                savedState.paddingRight, savedState.paddingBottom
            )
            val scaleType = savedState.scaleType
            if (child is ImageView && scaleType != null) {
                child.scaleType = scaleType
            }
        }

        bottomRowSavedStates.clear()
        buttonRow.requestLayout()
    }

    // ── Overflow menu ─────────────────────────────────────────────────────────────────────────

    /**
     * Shows a [ListPopupWindow] with 2× row height and 18sp text for each command.
     * When [bigButtonsMode] is false, this method is a no-op (caller falls back to PopupMenu).
     */
    fun buildBigButtonsOverflowMenu(
        anchor: View,
        commands: List<CommandPanelLayoutPlanner.PlayerCommand>,
        bigButtonsMode: Boolean,
        onItemSelected: (CommandPanelLayoutPlanner.PlayerCommand) -> Unit
    ) {
        if (!bigButtonsMode) return

        val rowHeight = context.resources.getDimensionPixelSize(R.dimen.player_big_button_overflow_row_height)
        val dp16 = (16 * context.resources.displayMetrics.density).toInt()
        val dp12 = (12 * context.resources.displayMetrics.density).toInt()
        val dp24 = (24 * context.resources.displayMetrics.density).toInt()

        val adapter = object : ArrayAdapter<CommandPanelLayoutPlanner.PlayerCommand>(
            context, 0, commands
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val cmd = getItem(position)
                    ?: return convertView ?: View(context)

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, rowHeight
                    )
                    setPadding(dp16, 0, dp16, 0)
                }

                if (cmd.iconResId != 0) {
                    row.addView(ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp24, dp24).also {
                            it.marginEnd = dp12
                        }
                        setImageResource(cmd.iconResId)
                        setColorFilter(Color.DKGRAY)
                    })
                }

                row.addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = context.getString(cmd.titleResId)
                    textSize = 18f
                    maxLines = 1
                })

                return row
            }
        }

        val popup = ListPopupWindow(context).apply {
            setAnchorView(anchor)
            setAdapter(adapter)
            // ListPopupWindow.WRAP_CONTENT leaves adapter rows measured against a zero-width
            // constraint, which collapses the popup to icon-only width and wraps the label
            // character-by-character on long translations (uk: "Поділитися" → "Под/ілит/ися").
            // Pre-measure the widest row explicitly and cap at 85% of screen width.
            width = measureMenuContentWidth(adapter)
            height = ListPopupWindow.WRAP_CONTENT
            isModal = true
            setOnItemClickListener { _, _, position, _ ->
                onItemSelected(commands[position])
                dismiss()
            }
        }
        popup.show()
    }

    private fun measureMenuContentWidth(adapter: ArrayAdapter<*>): Int {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val measureParent = LinearLayout(context)
        var convertView: View? = null
        var maxWidth = 0
        for (i in 0 until adapter.count) {
            convertView = adapter.getView(i, convertView, measureParent)
            convertView.measure(widthSpec, heightSpec)
            if (convertView.measuredWidth > maxWidth) maxWidth = convertView.measuredWidth
        }
        val dp8 = (8 * context.resources.displayMetrics.density).toInt()
        val screenWidth = context.resources.displayMetrics.widthPixels
        val cap = (screenWidth * 0.85f).toInt()
        return (maxWidth + dp8).coerceAtMost(cap).coerceAtLeast(1)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private fun getShortLabel(button: View): String {
        val contentDesc = button.contentDescription?.toString() ?: ""
        val cmd = CommandPanelLayoutPlanner.PlayerCommand.entries
            .find { context.getString(it.titleResId) == contentDesc }
        return when {
            cmd != null && cmd.shortTitleResId != 0 -> context.getString(cmd.shortTitleResId)
            cmd != null -> context.getString(cmd.titleResId)
            button.id == R.id.btnBack -> context.getString(R.string.back)
            else -> contentDesc
        }
    }

    private fun rememberAndHideIntermediateTopPanelContainer(
        topCommandPanel: LinearLayout,
        button: View
    ) {
        val directTopChild = resolveDirectTopPanelChild(topCommandPanel, button) ?: return
        if (directTopChild === button) return
        if (topPanelHiddenContainers.containsKey(directTopChild)) return

        // The middle portrait/landscape containers stay in the hierarchy; hide them so the
        // flattened big-buttons wrappers become the only visible top-panel children.
        topPanelHiddenContainers[directTopChild] = directTopChild.visibility
        directTopChild.visibility = View.GONE
    }

    private fun resolveDirectTopPanelChild(topCommandPanel: LinearLayout, button: View): View? {
        var current: View = button
        var parent = current.parent
        while (parent is ViewGroup && parent !== topCommandPanel) {
            current = parent
            parent = current.parent
        }
        return if (parent === topCommandPanel) current else null
    }
}
