package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.textfield.TextInputEditText
import timber.log.Timber

/**
 * A [TextInputEditText] whose caret arithmetic survives a programmatic text clear.
 *
 * S2572: used by every field inside a `TextInputLayout` that declares `app:endIconMode="clear_text"`,
 * because tapping that icon is the reachable way into the framework defect
 * [SafeSelectionMovementMethod] guards.
 *
 * The guard is installed here rather than swapped in per call site: `TextView.setMovementMethod` is
 * final, so a subclass cannot intercept the assignment, and the field would otherwise have to be
 * patched from every fragment that inflates it. Nothing reassigns the movement method after
 * construction on this path - `TextView` does so only in its constructor, in the autolink branch of
 * `setText`, and in `setTextIsSelectable`, none of which an editable Material field reaches.
 */
class SafeSelectionTextInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextInputEditText(context, attrs) {

    init {
        movementMethod = SafeSelectionMovementMethod.INSTANCE
    }
}
