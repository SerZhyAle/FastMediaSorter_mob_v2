package com.sza.fastmediasorter.widget.registry

import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.core.panel.SubProgramAccentCatalog
import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import timber.log.Timber

/**
 * S2889: the one place a home widget's identity glyph becomes a colour.
 *
 * The widget surface used to carry a second palette, baked into `ic_widget_*_accent` vectors, and it had
 * diverged from [SubProgramAccentCatalog] on every single paired widget - the calculator was orange in the
 * programs menu and indigo on the home screen, the mini-game green in one place and violet in the other.
 * Not one of the baked hues was even a member of the eight-tone palette.
 *
 * The vectors are deliberately NOT edited. `res/xml/widget_*_info.xml` points `previewImage` at them and the
 * OS widget picker renders that preview in its own process, which the app cannot reach; tinting at draw time
 * makes the baked fill invisible everywhere the app itself draws, without breaking a surface this ticket
 * declared out of scope.
 *
 * The pairing is [com.sza.fastmediasorter.core.panel.SubProgramEntry.widgetKey] and nothing else, because
 * which widget stands for which program is a registry decision about launch points, not a colour decision.
 */
object HomeWidgetAccent {

    /**
     * Widgets whose glyph colour states something other than which program it is.
     *
     * They are excluded here rather than at each call site so the completeness test can pin the list: an
     * exclusion that lives in a provider is an exclusion nobody can count.
     */
    private val STATE_CARRYING_WIDGETS = setOf(
        // The home-screen cell is a chronometer plus a transport button - it shows no identity glyph at all.
        "stopwatch",
        // The glyph itself changes with the chosen indicator, so the cell is a readout rather than a tile.
        "network_monitor",
        // Red already means "a recording is running". This route's accent is also red, so tinting the idle
        // glyph with it would erase the only difference between idle and recording.
        "quick_audio_recorder",
    )

    /** The tone of the sub-program this widget stands for, or null when it has none or must keep its own. */
    @ColorRes
    fun accentResFor(widgetKey: String): Int? {
        if (widgetKey in STATE_CARRYING_WIDGETS) return null
        return SubProgramCatalog.all()
            .firstOrNull { it.widgetKey == widgetKey }
            ?.let { SubProgramAccentCatalog.accentFor(it.routeKey) }
    }

    /** Widget keys this object refuses to colour, exposed so a test can pin the set rather than trust it. */
    val stateCarryingWidgets: Set<String> get() = STATE_CARRYING_WIDGETS

    /**
     * Paints [viewId] with the widget's tone, or leaves it alone when the widget has none.
     *
     * `setInt(.., "setColorFilter", ..)` rather than `setColorStateList`: the latter arrived in API 31 while
     * the app's floor is 26 and the `legacy` flavor's is 23, so the reflective setter is the only path that
     * is one path across the whole flavor matrix. The colour is resolved here, in our own process, because a
     * `?attr/` inside a vector delivered through [RemoteViews] resolves against the launcher's theme.
     */
    fun applyIconTint(views: RemoteViews, @IdRes viewId: Int, context: Context, widgetKey: String) {
        val accentRes = accentResFor(widgetKey) ?: return
        views.setInt(viewId, "setColorFilter", ContextCompat.getColor(context, accentRes))
    }
}
