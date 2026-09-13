package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.databinding.GadgetHomeWidgetBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.ui.icon.RecyclableIconTint
import com.sza.fastmediasorter.widget.registry.HomeWidgetAccent

/**
 * S1170: a home-screen widget whose whole behaviour is "show an icon and a label, open one screen on
 * tap", rendered as a launcher desktop cell.
 *
 * Parameterised rather than subclassed: nine of the fourteen catalog widgets differ only in their key,
 * label, icon, span and destination, so they are nine registrations of this one class. Nine
 * near-identical classes would be nine places for the tap contract to drift.
 *
 * The tap always goes through [LauncherGadgetHost.run] and never builds an Intent. That is what routes
 * it through the single launch guard the desktop, both taskbars and the Start menu already share - a
 * gadget that started its own activity would bypass the "cannot open" message and the launch journal.
 *
 * Widgets that are NOT this shape - the two list widgets, the two that keep per-instance state and the
 * one that drives the playback service - carry their own gadget classes instead.
 */
class HomeWidgetGadget(
    override val key: String,
    @StringRes override val labelRes: Int,
    @DrawableRes override val iconRes: Int,
    override val defaultSpanW: Int,
    override val defaultSpanH: Int,
    override val iconTintable: Boolean = false,
    private val command: LauncherCellCommand,
) : LauncherGadget {

    /** Nothing to pick at add time: the destination is fixed by the registration, not by the cell. */
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        // S2889: the tile shows the same widget the picker and the home screen show, so it takes the same
        // tone. Resolved from [key], which IS the widgetKey the sub-program registry pairs against.
        HomeWidgetGadgetView(
            container.context,
            labelRes,
            iconRes,
            iconTintable,
            HomeWidgetAccent.accentResFor(key),
            command,
            host,
        )
}

/**
 * No lifecycle work at all - the cell shows a static icon and label. [LauncherGadgetView] is still the
 * right parent: it is what makes "a gadget owns its own teardown" true by construction rather than by
 * review, so a later edit that adds a Flow here cannot forget to stop it.
 */
private class HomeWidgetGadgetView(
    context: Context,
    @StringRes labelRes: Int,
    @DrawableRes iconRes: Int,
    iconTintable: Boolean,
    @ColorRes accentRes: Int?,
    command: LauncherCellCommand,
    host: LauncherGadgetHost,
) : LauncherGadgetView(context) {

    init {
        val binding = GadgetHomeWidgetBinding.inflate(LayoutInflater.from(context), this)
        binding.gadgetHomeWidgetIcon.setImageResource(iconRes)
        // S2889: the sub-program's own tone wins where it has one. The theme role stays the fallback so a
        // gadget that is not a sub-program - and a sub-program whose glyph carries state instead of
        // identity - keeps the appearance it has today.
        // S3080: applied as a colour filter, because clearing the view's tint list also erases the
        // `android:tint` the glyph declares for itself, leaving an untintable gadget icon black.
        RecyclableIconTint.apply(
            binding.gadgetHomeWidgetIcon,
            when {
                accentRes != null -> ContextCompat.getColor(context, accentRes)
                iconTintable -> MaterialColors.getColor(
                    binding.gadgetHomeWidgetIcon,
                    com.google.android.material.R.attr.colorOnSurface,
                )
                else -> null
            },
        )
        binding.gadgetHomeWidgetLabel.setText(labelRes)
        // The label is the only thing naming this cell, so the whole cell announces it rather than
        // leaving a talkback user with an unlabelled tap target (Rule 16).
        contentDescription = context.getString(labelRes)
        isFocusable = true
        isClickable = true
        setOnClickListener { host.run(command) }
    }
}
