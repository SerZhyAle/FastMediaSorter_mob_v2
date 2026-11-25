package com.sza.fastmediasorter.ui.common.widget

import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * COLLAPSIBLE-GROUP section 3 rule 2: the one place that knows how the state marker of a group header turns.
 *
 * The marker artwork (`ic_arrow_drop_down`) points down. Expanded keeps it there; collapsed turns it toward
 * reading end - right in LTR, left in RTL. Headers that are not built on [CollapsibleSectionHeader]
 * (launcher desktop and menu, tiles) call [apply] instead of keeping their own angles.
 */
object CollapsibleMarker {

    private const val EXPANDED_ROTATION = 0f
    private const val COLLAPSED_ROTATION_LTR = -90f
    private const val COLLAPSED_ROTATION_RTL = 90f

    /** Rotation of the downward-pointing marker artwork for a header state and a reading direction. */
    fun rotation(expanded: Boolean, layoutDirection: Int): Float = when {
        expanded -> EXPANDED_ROTATION
        layoutDirection == View.LAYOUT_DIRECTION_RTL -> COLLAPSED_ROTATION_RTL
        else -> COLLAPSED_ROTATION_LTR
    }

    /**
     * Sets [marker] to the state without animation. The direction comes from the app configuration, not
     * from the view: a view built in a binder has not resolved its own layout direction yet, and reading
     * it there would return LTR for an RTL locale.
     */
    fun apply(marker: View, expanded: Boolean) {
        val direction = marker.resources.configuration.layoutDirection
        Timber.d("S4626: marker expanded=$expanded layoutDirection=$direction")
        marker.rotation = rotation(expanded, direction)
    }

    /**
     * Gives a header the topic and the expanded or collapsed state a screen reader announces. Below API 30
     * `setStateDescription` is not announced by TalkBack, so the state is folded into the description.
     */
    fun describe(root: View, caption: CharSequence, expanded: Boolean) {
        val state = root.context.getText(
            if (expanded) R.string.collapsible_section_state_expanded else R.string.collapsible_section_state_collapsed,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.contentDescription = caption
            ViewCompat.setStateDescription(root, state)
        } else {
            root.contentDescription = "$caption, $state"
        }
    }
}
