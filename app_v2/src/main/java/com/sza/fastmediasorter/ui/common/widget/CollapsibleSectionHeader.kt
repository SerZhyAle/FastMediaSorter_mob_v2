package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.res.use
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
    private val prefixView: TextView
    private val titleView: TextView
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
    private var collapsedPrefixText: CharSequence? = null
    private var expandedPrefixText: CharSequence? = null

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
        prefixView = findViewById(R.id.csh_prefix)
        titleView = findViewById(R.id.csh_title)
        trailingSlot = findViewById(R.id.csh_trailingSlot)
        defaultHeaderBackground = headerRow.background

        bindClicks()
        applyAttributes(attrs, defStyleAttr)
        renderTitle()
        syncHelpVisibility()
        applyVirtualState()
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
     * Updates the expanded state.
     *
     * In virtual mode the header is a static label, so expand/collapse is ignored.
     */
    fun setExpanded(expanded: Boolean, notify: Boolean = true) {
        if (virtual || this.expanded == expanded) return
        this.expanded = expanded
        renderTitle()
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
            collapsedPrefixText = typedArray.getText(R.styleable.CollapsibleSectionHeader_csh_collapsedPrefix)
            expandedPrefixText = typedArray.getText(R.styleable.CollapsibleSectionHeader_csh_expandedPrefix)
            val showHelp = typedArray.getBoolean(R.styleable.CollapsibleSectionHeader_csh_showHelp, false)
            helpIcon.visibility = if (showHelp && hasHelpPayload()) View.VISIBLE else View.GONE

            typedArray.getColorStateList(R.styleable.CollapsibleSectionHeader_csh_titleTextColor)?.let {
                titleView.setTextColor(it)
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
        if (virtual) {
            prefixView.visibility = View.GONE
            titleView.text = titleText
        } else {
            val prefix = if (expanded) expandedPrefixText ?: "▼" else collapsedPrefixText ?: "▶"
            if (prefix.isNullOrEmpty()) {
                prefixView.visibility = View.GONE
            } else {
                prefixView.visibility = View.VISIBLE
                prefixView.text = prefix
            }
            titleView.text = titleText
        }
        updateHeaderContentDescription()
    }

    private fun updateHeaderContentDescription() {
        headerRow.contentDescription = if (virtual) {
            titleView.text
        } else if (expanded) {
            collapseContentDescriptionText ?: titleView.text
        } else {
            expandContentDescriptionText ?: titleView.text
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
}
