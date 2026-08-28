package com.sza.fastmediasorter.ui.launcher.helpers

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetHost
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetRegistry
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherGadgetView
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherTimeZoneCatalog
import com.sza.fastmediasorter.ui.launcher.gadget.LauncherWeatherParamFallback
import com.sza.fastmediasorter.ui.launcher.grid.LauncherCellViewBinder
import timber.log.Timber

/**
 * S1541: builds the view for a gadget cell - registry lookup, the gadget's own view, and the
 * fallback shown for a key the registry does not know - extracted from the activity.
 *
 * Re-pointing a cell is a picker, which belongs to the add-flow, so it arrives as
 * [onWeatherReconfigure] / [onWorldClockReconfigure] rather than as a dependency on that role:
 * rendering must not need the picker chain to exist.
 */
class LauncherGadgetRenderManager(
    private val gadgetRegistry: LauncherGadgetRegistry,
    private val gadgetHost: LauncherGadgetHost,
    private val onWeatherReconfigure: (cellId: Long) -> Unit,
    private val onWorldClockReconfigure: (cellId: Long) -> Unit,
    // S2213: read per bind rather than captured once - a place picked after this manager was built must
    // be visible to the next bind, otherwise the fix would appear to work only after a restart. No
    // default on purpose: a construction site that forgot this would still compile and would silently
    // stop substituting, which is the failure this ticket exists to remove.
    private val savedWeatherLocation: () -> String?,
) {

    /**
     * A GADGET cell's `target` is a registry key, not a command, so a key we do not know is the only
     * "broken gadget" signal there is: [LauncherCellUi.visual] is null for every gadget by contract,
     * so the shortcut's unavailable path cannot double as this one.
     */
    fun bindGadget(cellUi: LauncherCellUi, container: FrameLayout) {
        val decoded = gadgetRegistry.decodeTarget(cellUi.cell.target)
        val gadget = decoded?.first?.let { gadgetRegistry.byKey(it) }
        if (gadget == null) {
            container.addView(unavailableGadgetView(container))
            return
        }
        // S2213: resolved once and fed to both call sites below - wireReconfigure decides from the same
        // param whether the cell still needs its "tap to configure" listener, so substituting in only one
        // of the two would show the city while still treating the cell as unconfigured.
        val param = LauncherWeatherParamFallback.resolve(decoded.first, decoded.second, savedWeatherLocation())
        if (param != decoded.second) {
            Timber.d("S2213: weather cell without its own place took the saved one")
        }
        // A gadget that cannot build its view degrades to a named failed-gadget tile (S2208). Without
        // this, the exception escapes into the HOME activity's render pass, and because
        // the system restarts HOME immediately the desktop crash-loops the device with no way in to
        // remove the offending cell - S2207 did exactly that from one bad layout inflation.
        val view = runCatching { gadget.createView(container, gadgetHost, param) }
            .onFailure {
                Timber.e(it, "Gadget ${decoded.first} failed to build its view; cell degraded")
                Timber.d("S2208: ${decoded.first} view creation failed")
            }
            .getOrNull()
        if (view == null) {
            container.addView(failedGadgetView(container, gadget.labelRes))
            return
        }
        if (view is LauncherGadgetView) {
            view.onFailure = {
                container.removeAllViews()
                container.addView(failedGadgetView(container, gadget.labelRes))
            }
        }
        wireReconfigure(decoded.first, param, cellUi.cell.id, view)
        container.addView(view)
    }

    /**
     * The cell id lives here, not inside the gadget, so re-pointing a cell is wired at the host rather
     * than by handing every gadget its row in the database.
     *
     * S1560: a cell that was seeded or placed without its param has a tap of its own that opens an
     * external app, which leaves its "not configured yet" message with no visible way out. Only that
     * case is redirected; a configured cell keeps the gadget's own behaviour.
     */
    private fun wireReconfigure(key: String, param: String?, cellId: Long, view: View) {
        // Both halves decided in one branch: reading "is it configured" in a second `when` would need
        // an `else` covering keys this function has already returned for, which reads as a rule about
        // every other gadget when it can only ever be this one.
        val (reconfigure, configured) = when (key) {
            LauncherGadgetRegistry.KEY_WEATHER ->
                onWeatherReconfigure to (WeatherLocation.decode(param) != null)
            // S1906: the world clock repoints the same way, and for the same reason - its zone is a
            // param the renderer cannot ask for on its own.
            LauncherGadgetRegistry.KEY_WORLD_CLOCK ->
                onWorldClockReconfigure to (LauncherTimeZoneCatalog.zoneOrNull(param) != null)
            else -> return
        }
        view.setOnLongClickListener {
            reconfigure(cellId)
            true
        }
        if (!configured) {
            view.setOnClickListener { reconfigure(cellId) }
        }
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
        gadgetStateView(container, R.string.launcher_home_cell_unavailable)

    private fun failedGadgetView(container: FrameLayout, @StringRes labelRes: Int): View =
        gadgetStateView(
            container,
            R.string.launcher_home_gadget_failed,
            container.context.getString(labelRes),
        )

    private fun gadgetStateView(
        container: FrameLayout,
        @StringRes messageRes: Int,
        vararg formatArgs: Any,
    ): View =
        TextView(container.context).apply {
            text = context.getString(messageRes, *formatArgs)
            gravity = Gravity.CENTER
            alpha = LauncherCellViewBinder.UNAVAILABLE_ALPHA
            isFocusable = true
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_launcher_mode, 0, 0)
            foreground = ContextCompat.getDrawable(context, R.drawable.focus_button_background)
        }
}
