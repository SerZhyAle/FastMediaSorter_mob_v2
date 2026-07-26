package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.sza.fastmediasorter.databinding.ItemLauncherAppShortcutBinding
import com.sza.fastmediasorter.domain.model.launcher.AppShortcut
import com.sza.fastmediasorter.ui.launcher.grid.LauncherCellViewBinder

/**
 * S0427: rows of the long-press quick-actions popup. A plain adapter - the window that hosts it owns
 * the loading and the launching.
 */
class LauncherAppShortcutAdapter(
    context: Context,
    private val shortcuts: List<AppShortcut>,
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = shortcuts.size

    override fun getItem(position: Int): AppShortcut = shortcuts[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val binding = convertView?.tag as? ItemLauncherAppShortcutBinding
            ?: ItemLauncherAppShortcutBinding.inflate(inflater, parent, false)
                .also { it.root.tag = it }
        val shortcut = shortcuts[position]
        binding.shortcutLabel.text = shortcut.label
        binding.shortcutIcon.setImageDrawable(shortcut.icon)
        // A disabled shortcut is still listed by the platform; dimming it the same way an unavailable
        // cell is dimmed keeps one visual language for "present but not usable".
        binding.root.alpha = if (shortcut.isEnabled) {
            ENABLED_ALPHA
        } else {
            LauncherCellViewBinder.UNAVAILABLE_ALPHA
        }
        binding.root.contentDescription = shortcut.label
        return binding.root
    }

    private companion object {
        const val ENABLED_ALPHA = 1.0f
    }
}
