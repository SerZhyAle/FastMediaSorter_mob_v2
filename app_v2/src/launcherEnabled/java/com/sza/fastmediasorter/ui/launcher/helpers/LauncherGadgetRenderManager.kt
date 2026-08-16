package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetHost
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.grid.LauncherCellViewBinder
import timber.log.Timber

/**
 * S1541: builds the view for a gadget cell - registry lookup, the gadget's own view, and the
 * fallback shown for a key the registry does not know - extracted from the activity.
 *
 * Re-pointing a weather cell is a picker, which belongs to the add-flow, so it arrives as
 * [onWeatherReconfigure] rather than as a dependency on that role: rendering must not need the
 * picker chain to exist.
 */
class LauncherGadgetRenderManager(
    private val gadgetRegistry: LauncherGadgetRegistry,
    private val gadgetHost: LauncherGadgetHost,
    private val onWeatherReconfigure: (cellId: Long) -> Unit,
) {

    /**
     * A GADGET cell's `target` is a registry key, not a command, so a key we do not know is the only
     * "broken gadget" signal there is: [LauncherCellUi.visual] is null for every gadget by contract,
     * so the shortcut's unavailable path cannot double as this one.
     */
    fun bindGadget(cellUi: LauncherCellUi, container: FrameLayout) {
        Timber.d("S1541: gadget render manager binding a gadget cell")
        val decoded = gadgetRegistry.decodeTarget(cellUi.cell.target)
        val gadget = decoded?.first?.let { gadgetRegistry.byKey(it) }
        if (gadget == null) {
            container.addView(unavailableGadgetView(container))
            return
        }
        val view = gadget.createView(container, gadgetHost, decoded.second)
        if (decoded.first == LauncherGadgetRegistry.KEY_WEATHER) {
            // The cell id lives here, not inside the gadget, so re-pointing a weather cell is wired at
            // the host rather than by handing every gadget its row in the database.
            view.setOnLongClickListener {
                onWeatherReconfigure(cellUi.cell.id)
                true
            }
            // S1560: a seeded weather cell carries no place, and its own tap opens a weather app - which
            // leaves the "no location" message with no visible way out. Only the unconfigured case is
            // redirected; a cell that already has a place keeps the gadget's own behaviour.
            if (WeatherLocation.decode(decoded.second) == null) {
                view.setOnClickListener { onWeatherReconfigure(cellUi.cell.id) }
            }
        }
        container.addView(view)
    }

    /**
     * A plain TextView, not the shortcut item: that one is a MaterialCardView, and the gadget cell is
     * already a MaterialCardView - nesting them draws two concentric outlines and doubles the insets.
     *
     * Focusable even though it does nothing: it is the only child of its cell, and a cell with no
     * focusable child is unreachable by D-pad - which would leave a TV user unable to select a broken
     * gadget in order to remove it (Phase 07).
     */
    fun unavailableGadgetView(container: FrameLayout): View =
        TextView(container.context).apply {
            setText(R.string.launcher_home_cell_unavailable)
            gravity = Gravity.CENTER
            alpha = LauncherCellViewBinder.UNAVAILABLE_ALPHA
            isFocusable = true
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_launcher_mode, 0, 0)
            foreground = ContextCompat.getDrawable(context, R.drawable.focus_button_background)
        }
}
