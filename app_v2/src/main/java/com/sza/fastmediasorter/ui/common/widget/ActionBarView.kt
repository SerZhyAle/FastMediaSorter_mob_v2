package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R

/**
 * S3249: the single action-strip container of `docs/ui/PHONE_UI_COMPONENT_PATTERNS.md` section 2.3.
 *
 * Two icon-button idioms used to coexist inside one screen - `MaterialButton` with the M3 icon style
 * for a command bar and raw `<ImageButton>` for an operations bar - and they differed in touch
 * target, ripple shape and disabled tint. Actions are declared as [Action] records instead, so the
 * bar owns those three decisions once and every screen that adopts it inherits the same contract.
 *
 * The view carries no business logic (Rule 3): it renders records and reports the id that was
 * clicked.
 */
class ActionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** One control of the strip. [contentDescription] doubles as the label a wide bar may show. */
    data class Action(
        @IdRes val id: Int,
        @DrawableRes val icon: Int,
        @StringRes val contentDescription: Int,
    )

    /** Where the strip sits, which is the only thing separating the three bar styles. */
    enum class Placement { TOP, BOTTOM, FLOATING }

    val placement: Placement

    private val itemTint: ColorStateList?
    private val itemBackgroundTint: ColorStateList?
    private val itemSize: Int

    /**
     * Generated children by action id. The bar builds them, so it owns their addresses - a
     * findViewById walk over its own children would re-derive what this map already holds (S1693).
     */
    private val actionViews = LinkedHashMap<Int, MaterialButton>()

    init {
        // The four values are read into locals first: a lambda body cannot satisfy the compiler's
        // definite-assignment rule for a val property.
        val typed = context.obtainStyledAttributes(attrs, R.styleable.ActionBarView, defStyleAttr, 0)
        val ordinal: Int
        val tint: ColorStateList?
        val backgroundTint: ColorStateList?
        val size: Int
        try {
            ordinal = typed.getInt(R.styleable.ActionBarView_abvPlacement, Placement.TOP.ordinal)
            tint = typed.getColorStateList(R.styleable.ActionBarView_abvItemTint)
            backgroundTint = typed.getColorStateList(R.styleable.ActionBarView_abvItemBackgroundTint)
            size = typed.getDimensionPixelSize(
                R.styleable.ActionBarView_abvItemSize,
                resources.getDimensionPixelSize(R.dimen.touch_target_min_height),
            )
        } finally {
            typed.recycle()
        }
        placement = Placement.entries.getOrElse(ordinal) { Placement.TOP }
        itemTint = tint
        itemBackgroundTint = backgroundTint
        itemSize = size
    }

    /**
     * Replaces the strip's children with one button per record. Called again on a configuration
     * change or a permission change, so it rebuilds rather than appends.
     */
    fun setActions(actions: List<Action>, onAction: (Int) -> Unit) {
        removeAllViews()
        actionViews.clear()
        val inflater = LayoutInflater.from(context)
        actions.forEach { action ->
            val button = inflater.inflate(R.layout.view_action_bar_item, this, false) as MaterialButton
            bindAction(button, action)
            button.setOnClickListener { onAction(action.id) }
            addView(button)
            actionViews[action.id] = button
        }
    }

    /** Disables one action without removing it, so the strip keeps its width and its focus order. */
    fun setActionEnabled(@IdRes id: Int, enabled: Boolean) {
        actionViews[id]?.isEnabled = enabled
    }

    /** Hides one action. A hidden child is not a D-pad focus target, which is the intent. */
    fun setActionVisible(@IdRes id: Int, visible: Boolean) {
        actionViews[id]?.isVisible = visible
    }

    /** True while at least one action is visible - what decides whether the bar itself is shown. */
    fun hasVisibleAction(): Boolean = actionViews.values.any { it.isVisible }

    private fun bindAction(button: MaterialButton, action: Action) {
        button.id = action.id
        button.setIconResource(action.icon)
        button.contentDescription = context.getString(action.contentDescription)
        itemTint?.let(button::setIconTint)
        itemBackgroundTint?.let(button::setBackgroundTintList)
        if (placement == Placement.FLOATING) {
            // A floating control draws over content, so it needs a pill of its own; a docked one
            // inherits the bar's surface and must stay square-cornered with it.
            button.cornerRadius = itemSize / CIRCULAR_RADIUS_DIVISOR
        }
        button.layoutParams = LayoutParams(itemSize, itemSize)
    }

    private companion object {
        /** Half the edge is what turns the button's square into the circle a floating control uses. */
        const val CIRCULAR_RADIUS_DIVISOR = 2
    }
}
