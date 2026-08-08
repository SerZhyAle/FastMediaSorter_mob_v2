package com.sza.fastmediasorter.ui.streams.helpers

import android.view.MenuItem
import androidx.annotation.MenuRes
import com.google.android.material.appbar.MaterialToolbar
import timber.log.Timber

/**
 * S1473: shows text labels on the streams command row while the screen is horizontal.
 *
 * A `menu-land` resource variant cannot do this. The streams window declares orientation config
 * changes as self-handled (S0692), so neither the layout nor the menu resource is re-resolved on
 * rotation. Worse, AppCompat decides whether an action item may render text once, in the item
 * view's constructor (`ActionMenuItemView.shouldAllowTextWithIcon`, satisfied by landscape alone),
 * and stores the answer for that view's lifetime. An item view built in portrait therefore refuses
 * text forever. Rebuilding the menu is what makes the labels appear after a rotation rather than
 * only when the screen is opened already horizontal.
 *
 * Rebuilding discards every post-inflate mutation the screen applied - icon tinting and the
 * list/grid toggle's icon-and-title swap - so the owner passes them back in [reapplyFixups].
 *
 * [labelledItemIds] names the items that stay pinned to the row. Every other item keeps the
 * `never` flag from the menu resource, which is what keeps the overflow entries in the overflow.
 */
class StreamsCommandLabelManager(
    private val toolbar: MaterialToolbar,
    @MenuRes private val menuRes: Int,
    private val labelledItemIds: List<Int>,
    private val reapplyFixups: () -> Unit,
) {
    private var appliedLandscape: Boolean? = null

    fun applyForOrientation(isLandscape: Boolean) {
        if (appliedLandscape == isLandscape) return
        appliedLandscape = isLandscape
        toolbar.menu.clear()
        toolbar.inflateMenu(menuRes)
        val flags = if (isLandscape) ALWAYS_WITH_TEXT else MenuItem.SHOW_AS_ACTION_ALWAYS
        labelledItemIds.forEach { id -> toolbar.menu.findItem(id)?.setShowAsAction(flags) }
        Timber.d("S1473: command row rebuilt, landscape=%b (labels %s)", isLandscape, if (isLandscape) "on" else "off")
        reapplyFixups()
    }

    private companion object {
        const val ALWAYS_WITH_TEXT =
            MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT
    }
}
