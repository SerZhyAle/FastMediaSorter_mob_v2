package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.res.use
import androidx.core.view.ViewCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import timber.log.Timber

/**
 * Reusable header for collapsible settings groups and similar UI sections.
 *
 * The view owns only its visual expanded state and emits changes to the caller.
 * Persistence and content-container visibility stay outside this component.
 */
class CollapsibleSectionHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headerRow: LinearLayout
    private val helpIcon: ImageButton
    private val chevronView: ImageView
    private val iconView: ImageView
    private val prefixView: TextView
    private val titleView: TextView
    private val summaryView: TextView
    private val trailingSlot: FrameLayout

    private var expanded = false
    private var titleText: CharSequence = ""
    private var helpTitleText: CharSequence? = null
    private var helpMessageText: CharSequence? = null
    private var expandContentDescriptionText: CharSequence? = null
    private var collapseContentDescriptionText: CharSequence? = null
    private var virtual = false
    private var expandedChangeListener: ((Boolean) -> Unit)? = null
    private var defaultHeaderBackground = background

    /**
     * `true` when the optional help icon is visible.
     */
    @get:JvmName("getHelpVisible")
    val isHelpVisible: Boolean
        get() = helpIcon.visibility == View.VISIBLE

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_collapsible_section_header, this, true)

        headerRow = findViewById(R.id.csh_headerRow)
        helpIcon = findViewById(R.id.csh_iconHelp)
        chevronView = findViewById(R.id.csh_chevron)
        iconView = findViewById(R.id.csh_icon)
        prefixView = findViewById(R.id.csh_prefix)
        titleView = findViewById(R.id.csh_title)
        summaryView = findViewById(R.id.csh_summary)
        trailingSlot = findViewById(R.id.csh_trailingSlot)
        defaultHeaderBackground = headerRow.background

        bindClicks()
        applyAttributes(attrs, defStyleAttr)
        renderTitle()
        applyVirtualState()
        updateChevron(animate = false)
        syncHelpVisibility()
    }

    /**
     * Updates the header title using a direct text value.
     */
    fun setTitle(text: CharSequence) {
        titleText = text
        renderTitle()
    }

    /**
     * Updates the header title from a string resource.
     */
    fun setTitle(@StringRes resId: Int) {
        setTitle(context.getText(resId))
    }

    /**
     * Sets the optional collapsed-state summary shown under the title, or hides it when null/blank.
     *
     * Summary visibility is independent of the expanded state - the caller decides when to populate it.
     */
    fun setSummary(text: CharSequence?) {
        if (text.isNullOrBlank()) {
            summaryView.text = ""
            summaryView.visibility = View.GONE
        } else {
            summaryView.text = text
            summaryView.visibility = View.VISIBLE
        }
    }

    /**
     * Resource-backed overload for [setSummary].
     */
    fun setSummary(@StringRes resId: Int) {
        setSummary(context.getText(resId))
    }

    /**
     * Sets an optional leading category icon. Passing `null` hides the icon slot (S0776).
     */
    fun setIcon(icon: Drawable?) {
        iconView.setImageDrawable(icon)
        iconView.visibility = if (icon == null) View.GONE else View.VISIBLE
    }

    /**
     * Sets an optional leading category icon from a drawable resource (S0776).
     */
    fun setIcon(@DrawableRes resId: Int) {
        iconView.setImageResource(resId)
        iconView.visibility = View.VISIBLE
    }

    /**
     * Updates the expanded state.
     *
     * In virtual mode the header is a static label, so expand/collapse is ignored.
     */
    fun setExpanded(expanded: Boolean, notify: Boolean = true) {
        if (virtual || this.expanded == expanded) return
        this.expanded = expanded
        renderTitle()
        updateChevron(animate = notify)
        if (notify) {
            expandedChangeListener?.invoke(expanded)
        }
    }

    /**
     * Returns the current expanded state.
     */
    fun isExpanded(): Boolean = expanded

    /**
     * Registers a listener invoked when the expanded state changes from user interaction
     * or from [setExpanded] with `notify = true`.
     */
    fun setOnExpandedChangeListener(listener: ((Boolean) -> Unit)?) {
        expandedChangeListener = listener
    }

    /**
     * Configures the help tooltip payload and makes the help button available.
     */
    fun setHelp(@StringRes titleRes: Int, @StringRes messageRes: Int) {
        helpTitleText = context.getText(titleRes)
        helpMessageText = context.getText(messageRes)
        setHelpVisible(true)
    }

    /**
     * Overrides the interactive header content descriptions used for collapsed/expanded states.
     */
    fun setExpandCollapseContentDescriptions(
        expandDescription: CharSequence?,
        collapseDescription: CharSequence?,
    ) {
        expandContentDescriptionText = expandDescription
        collapseContentDescriptionText = collapseDescription
        updateHeaderContentDescription()
    }

    /**
     * Resource-backed overload for [setExpandCollapseContentDescriptions].
     */
    fun setExpandCollapseContentDescriptions(
        @StringRes expandResId: Int,
        @StringRes collapseResId: Int,
    ) {
        setExpandCollapseContentDescriptions(
            context.getText(expandResId),
            context.getText(collapseResId),
        )
    }

    /**
     * Shows or hides the help button without dropping the stored help payload.
     */
    fun setHelpVisible(visible: Boolean) {
        helpIcon.visibility = if (visible && hasHelpPayload()) View.VISIBLE else View.GONE
        updateHelpContentDescription()
    }

    /**
     * Switches between interactive collapsible mode and static virtual-label mode.
     */
    fun setVirtual(virtual: Boolean) {
        if (this.virtual == virtual) return
        this.virtual = virtual
        applyVirtualState()
        renderTitle()
    }

    /**
     * Injects or removes an optional trailing control hosted by this header.
     */
    fun setTrailingControl(view: View?) {
        trailingSlot.removeAllViews()
        if (view == null) {
            trailingSlot.visibility = View.GONE
            return
        }
        (view.parent as? ViewGroup)?.removeView(view)
        trailingSlot.addView(view)
        trailingSlot.visibility = View.VISIBLE
    }

    private fun bindClicks() {
        headerRow.setOnClickListener {
            if (!virtual) {
                setExpanded(!expanded)
            }
        }
        helpIcon.setOnClickListener {
            val title = helpTitleText
            val message = helpMessageText
            if (title.isNullOrEmpty() || message.isNullOrEmpty()) {
                Timber.w("CollapsibleSectionHeader: help requested without payload")
                return@setOnClickListener
            }
            TooltipDialog.show(context, title.toString(), message.toString())
        }
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.CollapsibleSectionHeader, defStyleAttr, 0).use { typedArray ->
            titleText = typedArray.getText(R.styleable.CollapsibleSectionHeader_csh_title) ?: ""
            expanded = typedArray.getBoolean(R.styleable.CollapsibleSectionHeader_csh_expanded, false)
            helpTitleText = typedArray.getText(R.styleable.CollapsibleSectionHeader_csh_helpTitle)
            helpMessageText = typedArray.getText(R.styleable.CollapsibleSectionHeader_csh_helpMessage)
            virtual = typedArray.getBoolean(R.styleable.CollapsibleSectionHeader_csh_virtual, false)
            setSummary(typedArray.getText(R.styleable.CollapsibleSectionHeader_csh_summary))
            val showHelp = typedArray.getBoolean(R.styleable.CollapsibleSectionHeader_csh_showHelp, false)
            helpIcon.visibility = if (showHelp && hasHelpPayload()) View.VISIBLE else View.GONE

            val iconRes = typedArray.getResourceId(R.styleable.CollapsibleSectionHeader_csh_icon, 0)
            if (iconRes != 0) setIcon(iconRes)

            typedArray.getColorStateList(R.styleable.CollapsibleSectionHeader_csh_titleTextColor)?.let {
                titleView.setTextColor(it)
            }
            typedArray.getColorStateList(R.styleable.CollapsibleSectionHeader_csh_chevronTint)?.let {
                chevronView.imageTintList = it
            }
            typedArray.getColorStateList(R.styleable.CollapsibleSectionHeader_csh_prefixTextColor)?.let {
                prefixView.setTextColor(it)
            }
            if (typedArray.hasValue(R.styleable.CollapsibleSectionHeader_csh_titleTextSize)) {
                val textSizePx = typedArray.getDimension(R.styleable.CollapsibleSectionHeader_csh_titleTextSize, titleView.textSize)
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                prefixView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            }
            if (typedArray.hasValue(R.styleable.CollapsibleSectionHeader_csh_titleBold)) {
                titleView.setTypeface(titleView.typeface, if (typedArray.getBoolean(R.styleable.CollapsibleSectionHeader_csh_titleBold, true)) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
            typedArray.getDrawable(R.styleable.CollapsibleSectionHeader_csh_headerBackground)?.let {
                headerRow.background = it
                defaultHeaderBackground = it
            }
            if (typedArray.hasValue(R.styleable.CollapsibleSectionHeader_csh_headerPaddingHorizontal) ||
                typedArray.hasValue(R.styleable.CollapsibleSectionHeader_csh_headerPaddingVertical)
            ) {
                val horizontal = typedArray.getDimensionPixelSize(
                    R.styleable.CollapsibleSectionHeader_csh_headerPaddingHorizontal,
                    headerRow.paddingLeft,
                )
                val vertical = typedArray.getDimensionPixelSize(
                    R.styleable.CollapsibleSectionHeader_csh_headerPaddingVertical,
                    headerRow.paddingTop,
                )
                headerRow.setPadding(horizontal, vertical, horizontal, vertical)
            }
            if (typedArray.hasValue(R.styleable.CollapsibleSectionHeader_csh_prefixPaddingEnd)) {
                prefixView.setPaddingRelative(
                    prefixView.paddingStart,
                    prefixView.paddingTop,
                    typedArray.getDimensionPixelSize(
                        R.styleable.CollapsibleSectionHeader_csh_prefixPaddingEnd,
                        prefixView.paddingEnd,
                    ),
                    prefixView.paddingBottom,
                )
            }
        }
    }

    private fun renderTitle() {
        titleView.text = titleText
        updateHeaderContentDescription()
    }

    private fun updateChevron(animate: Boolean) {
        val target = if (expanded) EXPANDED_ROTATION else COLLAPSED_ROTATION
        if (animate) {
            chevronView.animate()
                .rotation(target)
                .setDuration(ROTATION_ANIMATION_DURATION_MS)
                .start()
        } else {
            chevronView.animate().cancel()
            chevronView.rotation = target
        }
    }

    private fun updateHeaderContentDescription() {
        if (virtual) {
            headerRow.contentDescription = titleView.text
            return
        }
        val baseDescription = if (expanded) {
            collapseContentDescriptionText ?: titleView.text
        } else {
            expandContentDescriptionText ?: titleView.text
        }
        val stateText = context.getText(
            if (expanded) R.string.collapsible_section_state_expanded
            else R.string.collapsible_section_state_collapsed,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            headerRow.contentDescription = baseDescription
            ViewCompat.setStateDescription(headerRow, stateText)
        } else {
            // setStateDescription is not announced by TalkBack below API 30, so fold the state into contentDescription.
            headerRow.contentDescription = "$baseDescription, $stateText"
        }
    }

    private fun syncHelpVisibility() {
        if (!hasHelpPayload()) {
            helpIcon.visibility = View.GONE
        }
        updateHelpContentDescription()
    }

    private fun updateHelpContentDescription() {
        helpIcon.contentDescription = helpTitleText?.toString().orEmpty()
    }

    private fun applyVirtualState() {
        headerRow.background = if (virtual) null else defaultHeaderBackground
        chevronView.visibility = if (virtual) View.GONE else View.VISIBLE
        if (virtual) {
            headerRow.setOnClickListener(null)
            headerRow.isClickable = false
            headerRow.isFocusable = false
        } else {
            headerRow.setOnClickListener {
                setExpanded(!expanded)
            }
            headerRow.isClickable = true
            headerRow.isFocusable = true
        }
        helpIcon.nextFocusForwardId = headerRow.id
    }

    private fun hasHelpPayload(): Boolean {
        return !helpTitleText.isNullOrEmpty() && !helpMessageText.isNullOrEmpty()
    }

    companion object {
        // Chevron base orientation (ic_arrow_drop_down) points down = expanded; rotate to point right = collapsed.
        private const val EXPANDED_ROTATION = 0f
        private const val COLLAPSED_ROTATION = -90f
        private const val ROTATION_ANIMATION_DURATION_MS = 150L
    }
}
