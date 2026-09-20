package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ViewMediaItemBinding

/**
 * The list, grid and plank row primitive of `docs/ui/PHONE_UI_COMPONENT_PATTERNS.md` section 2.2.
 *
 * The root carries `Widget.FastMediaSorter.Item.Row`, so the selection highlight, the focus ring and
 * the ripple are three layers of one state-list pair rather than four competing mechanisms:
 * [setSelectionState] sets [isSelected] and nothing else. An adapter that paints the root with
 * `setBackgroundColor` destroys the selector and with it the focus ring of exactly the rows being
 * selected, which is the regression this class exists to make impossible.
 *
 * The three [LayoutMode] values are applied by a [ConstraintSet] rather than by three layout files,
 * and the mode's constraints re-resolve against the orientation on configuration change, so no item
 * layout needs a `layout-land` counterpart (section 5.3 decision 2). The re-resolve exists because
 * a `ConstraintSet` holds pixels, not resource references: a dimension resolved at construction
 * would stay at its portrait value for the life of a view inside a rotation-absorbing activity.
 *
 * Adopt it through [setTitle], [setSubtitle], [setDetail], [setBadge], [setSelectionState] and
 * [thumbnailView]; the thumbnail is loaded by [MediaItemThumbnailBinder], never by an inline Glide
 * call in a `bind()`.
 */
class MediaItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    /**
     * Shape of the row.
     *
     * [LIST] - compact row, small leading icon and a three-line text block.
     * [GRID] - cell, thumbnail on top spanning the cell width and the text block below it.
     * [PLANK] - wide row, large leading thumbnail filling the row height beside the text block.
     */
    enum class LayoutMode { LIST, GRID, PLANK }

    private val binding = ViewMediaItemBinding.inflate(LayoutInflater.from(context), this)

    private val thumbnail: ImageView get() = binding.ivThumbnail
    private val titleView: TextView get() = binding.tvTitle
    private val subtitleView: TextView get() = binding.tvSubtitle
    private val detailView: TextView get() = binding.tvDetail
    private val badgeView: TextView get() = binding.tvBadge
    private val selectionBox: AppCompatCheckBox get() = binding.cbSelect
    private val trailingSlot: FrameLayout get() = binding.flTrailingSlot

    private val listConstraints = ConstraintSet()
    private val gridConstraints = ConstraintSet()
    private val plankConstraints = ConstraintSet()

    /** Mode currently applied. Change it through [setLayoutMode]. */
    var layoutMode: LayoutMode = LayoutMode.LIST
        private set

    private var selectionVisible = false
    private var badgeAllowed = true
    private var constraintOrientation: Int = resources.configuration.orientation

    init {
        applyRowDefaults()
        listConstraints.clone(this)
        rebuildGridConstraints(contextConfig(resources.configuration.orientation))
        buildPlankConstraints()
        readAttributes(attrs, defStyleAttr)
        applyFocusTraversal()
    }

    /** Applies the constraint set of [mode] and remembers it for the next configuration change. */
    fun setLayoutMode(mode: LayoutMode) {
        layoutMode = mode
        constraintsFor(mode).applyTo(this)
    }

    /** Sets the primary line. Empty text hides the view so the chain packs without a blank row. */
    fun setTitle(text: CharSequence?) {
        bindText(titleView, text)
        contentDescription = text
    }

    /** Sets the secondary line. */
    fun setSubtitle(text: CharSequence?) {
        bindText(subtitleView, text)
    }

    /** Sets the tertiary line - size, date, counters. */
    fun setDetail(text: CharSequence?) {
        bindText(detailView, text)
    }

    /**
     * Fills the badge pill, or hides it for `null` and for a badge with neither text nor icon.
     * Ignored while `mivShowBadge` is false, so a surface can opt out once in XML.
     */
    fun setBadge(badge: ItemBadge?) {
        val shown = badge?.takeIf { badgeAllowed && it.hasContent() }
        badgeView.visibility = if (shown == null) View.GONE else View.VISIBLE
        if (shown == null) return
        badgeView.text = shown.text ?: ""
        badgeView.setCompoundDrawablesRelativeWithIntrinsicBounds(shown.iconRes ?: 0, 0, 0, 0)
        applyBadgeTone(shown.tone)
    }

    /**
     * The one selection mechanism: the row's own [isSelected] drives the state-list background, and
     * the checkbox follows it only as a visible affordance. No background is read, replaced or tinted
     * here, which is what keeps the focus ring alive on a selected row.
     */
    fun setSelectionState(selected: Boolean) {
        isSelected = selected
        if (selectionVisible) {
            selectionBox.isChecked = selected
        }
    }

    /** Shows or hides the selection checkbox. Hiding it does not clear the row's selected state. */
    fun setSelectionVisible(visible: Boolean) {
        selectionVisible = visible
        selectionBox.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            selectionBox.isChecked = isSelected
        }
    }

    /** The image child, handed to [MediaItemThumbnailBinder] rather than loaded into directly. */
    fun thumbnailView(): ImageView = thumbnail

    /** The trailing container, for a per-screen action strip. */
    fun trailingSlotView(): FrameLayout = trailingSlot

    /** Replaces the trailing slot content and re-declares the focus order across the new children. */
    fun setTrailingContent(content: View?) {
        trailingSlot.removeAllViews()
        if (content != null) {
            trailingSlot.addView(content)
        }
        trailingSlot.visibility = if (content == null) View.GONE else View.VISIBLE
        applyFocusTraversal()
    }

    /**
     * Re-resolves the mode's constraints on rotation. The cached sets carry pixels fixed at
     * construction, and the orientation-qualified dimensions (item_grid_thumbnail_size,
     * item_row_padding_vertical) would serve their portrait values to a view that survived an
     * absorbed rotation - which is exactly the staleness this re-resolve exists to prevent.
     */
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        val orientation = newConfig?.orientation ?: resources.configuration.orientation
        if (orientation != constraintOrientation) {
            applyOrientation(orientation)
        }
    }

    /**
     * Test seam for the orientation switch (section 5.3 decision 2: the switch is testable without
     * a device rotation). Runs the same re-resolve [onConfigurationChanged] runs, with the
     * orientation forced instead of read from the delivered configuration.
     */
    @VisibleForTesting
    internal fun applyOrientationForTest(isLandscape: Boolean) {
        applyOrientation(
            if (isLandscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT,
        )
    }

    private fun applyOrientation(orientation: Int) {
        constraintOrientation = orientation
        val qualified = contextConfig(orientation)
        applyRowPadding(qualified)
        rebuildGridConstraints(qualified)
        setLayoutMode(layoutMode)
    }

    private fun contextConfig(orientation: Int): Context {
        val override = Configuration(resources.configuration)
        override.orientation = orientation
        return context.createConfigurationContext(override)
    }

    /** Mirrors the padding the row style declares, resolved for [qualified]'s orientation. */
    private fun applyRowPadding(qualified: Context) {
        val horizontal = qualified.resources.getDimensionPixelSize(R.dimen.item_row_padding_horizontal)
        val vertical = qualified.resources.getDimensionPixelSize(R.dimen.item_row_padding_vertical)
        setPadding(horizontal, vertical, horizontal, vertical)
    }

    private fun applyRowDefaults() {
        if (background == null) {
            setBackgroundResource(R.drawable.item_focus_selector)
        }
        if (foreground == null) {
            foreground = ContextCompat.getDrawable(context, R.drawable.item_interaction_overlay)
        }
        isFocusable = true
        isFocusableInTouchMode = false
    }

    private fun readAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) {
            setLayoutMode(LayoutMode.LIST)
            return
        }
        context.obtainStyledAttributes(attrs, R.styleable.MediaItemView, defStyleAttr, 0).use { typed ->
            val modeIndex = typed.getInt(R.styleable.MediaItemView_mivLayoutMode, 0)
            badgeAllowed = typed.getBoolean(R.styleable.MediaItemView_mivShowBadge, true)
            setSelectionVisible(typed.getBoolean(R.styleable.MediaItemView_mivShowSelection, false))
            val slotLayout = typed.getResourceId(R.styleable.MediaItemView_mivTrailingSlot, 0)
            if (slotLayout != 0) {
                LayoutInflater.from(context).inflate(slotLayout, trailingSlot, true)
            }
            setLayoutMode(LayoutMode.entries.getOrElse(modeIndex) { LayoutMode.LIST })
        }
    }

    private fun constraintsFor(mode: LayoutMode): ConstraintSet = when (mode) {
        LayoutMode.LIST -> listConstraints
        LayoutMode.GRID -> gridConstraints
        LayoutMode.PLANK -> plankConstraints
    }

    private fun rebuildGridConstraints(qualified: Context) {
        gridConstraints.clone(listConstraints)
        with(gridConstraints) {
            constrainWidth(R.id.ivThumbnail, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(R.id.ivThumbnail, dimen(qualified, R.dimen.item_grid_thumbnail_size))
            clear(R.id.ivThumbnail, ConstraintSet.BOTTOM)
            connect(R.id.ivThumbnail, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            stackTextBlockBelow(this, R.id.ivThumbnail)
            connect(R.id.tvBadge, ConstraintSet.TOP, R.id.ivThumbnail, ConstraintSet.TOP)
            connect(R.id.tvBadge, ConstraintSet.END, R.id.ivThumbnail, ConstraintSet.END)
            clear(R.id.tvBadge, ConstraintSet.BOTTOM)
        }
    }

    private fun buildPlankConstraints() {
        plankConstraints.clone(listConstraints)
        with(plankConstraints) {
            constrainWidth(R.id.ivThumbnail, dimen(context, R.dimen.item_thumbnail_size))
            constrainHeight(R.id.ivThumbnail, ConstraintSet.MATCH_CONSTRAINT)
        }
    }

    /** Re-anchors the title/subtitle/detail chain under [anchorId] and across the full width. */
    private fun stackTextBlockBelow(set: ConstraintSet, anchorId: Int) {
        set.connect(R.id.tvTitle, ConstraintSet.TOP, anchorId, ConstraintSet.BOTTOM)
        set.connect(R.id.tvTitle, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(R.id.tvTitle, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        set.clear(R.id.tvTitle, ConstraintSet.BOTTOM)
        set.connect(R.id.tvTitle, ConstraintSet.BOTTOM, R.id.tvSubtitle, ConstraintSet.TOP)
        set.connect(R.id.tvDetail, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
    }

    private fun applyBadgeTone(tone: ItemBadge.Tone) {
        val containerAttr = when (tone) {
            ItemBadge.Tone.NEUTRAL -> com.google.android.material.R.attr.colorSecondaryContainer
            ItemBadge.Tone.ACCENT -> com.google.android.material.R.attr.colorPrimaryContainer
            ItemBadge.Tone.ERROR -> com.google.android.material.R.attr.colorErrorContainer
        }
        val labelAttr = when (tone) {
            ItemBadge.Tone.NEUTRAL -> com.google.android.material.R.attr.colorOnSecondaryContainer
            ItemBadge.Tone.ACCENT -> com.google.android.material.R.attr.colorOnPrimaryContainer
            ItemBadge.Tone.ERROR -> com.google.android.material.R.attr.colorOnErrorContainer
        }
        badgeView.setBackgroundColor(MaterialColors.getColor(this, containerAttr))
        badgeView.setTextColor(MaterialColors.getColor(this, labelAttr))
    }

    /**
     * Declares the traversal order across whatever focusable controls the row ends up holding, which
     * section 3.2 requires of any row with more than two of them and which zero item layout declares
     * today. Re-run whenever the trailing slot changes.
     */
    private fun applyFocusTraversal() {
        val chain = focusableControls()
        chain.forEachIndexed { index, view ->
            val next = chain.getOrNull(index + 1)
            val previous = chain.getOrNull(index - 1)
            if (next != null) {
                view.nextFocusRightId = next.id
                view.nextFocusDownId = next.id
            }
            if (previous != null) {
                view.nextFocusLeftId = previous.id
                view.nextFocusUpId = previous.id
            }
        }
    }

    private fun focusableControls(): List<View> {
        val found = mutableListOf<View>()
        collectFocusable(trailingSlot, found)
        return found
    }

    private fun collectFocusable(parent: ViewGroup, sink: MutableList<View>) {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child.isFocusable && child.id != View.NO_ID) {
                sink.add(child)
            }
            if (child is ViewGroup) {
                collectFocusable(child, sink)
            }
        }
    }

    private fun bindText(view: TextView, text: CharSequence?) {
        view.text = text ?: ""
        view.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun dimen(qualified: Context, resId: Int): Int = qualified.resources.getDimensionPixelSize(resId)
}
