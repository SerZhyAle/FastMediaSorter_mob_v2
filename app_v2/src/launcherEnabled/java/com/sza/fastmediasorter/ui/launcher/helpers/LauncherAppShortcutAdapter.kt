package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.databinding.ItemLauncherAppShortcutBinding
import com.sza.fastmediasorter.domain.model.launcher.AppShortcut
import com.sza.fastmediasorter.ui.launcher.grid.LauncherCellViewBinder

/**
 * S1401: one row of the long-press menu. The launcher's own actions and the app's published shortcuts
 * are two different things wearing the same row, which is exactly the point - the user sees one menu.
 */
sealed interface LauncherAppMenuRow {

    val label: String

    /** A launcher action. Only rows that can actually run are built, so there is no disabled state. */
    data class Action(
        override val label: String,
        @param:DrawableRes val iconRes: Int,
        val onSelected: () -> Unit
    ) : LauncherAppMenuRow

    data class Shortcut(val shortcut: AppShortcut) : LauncherAppMenuRow {
        override val label: String get() = shortcut.label
    }
}

/**
 * S0427: rows of the long-press popup. A plain adapter - the window that hosts it owns the loading and
 * the launching.
 */
class LauncherAppShortcutAdapter(
    context: Context,
    private val rows: List<LauncherAppMenuRow>,
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = rows.size

    override fun getItem(position: Int): LauncherAppMenuRow = rows[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding = convertView?.tag as? ItemLauncherAppShortcutBinding
            ?: ItemLauncherAppShortcutBinding.inflate(inflater, parent, false)
                .also { it.root.tag = it }
        val row = rows[position]
        binding.shortcutLabel.text = row.label
        binding.root.contentDescription = row.label
        when (row) {
            is LauncherAppMenuRow.Action -> {
                binding.shortcutIcon.setImageResource(row.iconRes)
                binding.root.alpha = ENABLED_ALPHA
            }

            is LauncherAppMenuRow.Shortcut -> {
                binding.shortcutIcon.setImageDrawable(row.shortcut.icon)
                // A disabled shortcut is still listed by the platform; dimming it the same way an
                // unavailable cell is dimmed keeps one visual language for "present but not usable".
                binding.root.alpha = if (row.shortcut.isEnabled) {
                    ENABLED_ALPHA
                } else {
                    LauncherCellViewBinder.UNAVAILABLE_ALPHA
                }
            }
        }
        return binding.root
    }

    private companion object {
        const val ENABLED_ALPHA = 1.0f
    }
}
