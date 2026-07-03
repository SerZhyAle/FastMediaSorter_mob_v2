package com.sza.fastmediasorter.ui.main.helpers

import androidx.appcompat.widget.TooltipCompat
import com.sza.fastmediasorter.databinding.ActivityMainBinding

/**
 * S0810: makes the icon-only top command-bar buttons self-explanatory - a long-press (or hover) reveals
 * each button's name via a back-compat tooltip, reusing the button's existing contentDescription so no
 * new strings are needed. TooltipCompat backports to legacy minSdk 23, unlike the API-26 android:tooltipText
 * XML attribute. The button set is owned here (not spread into MainActivity, which sits at its LOC cap).
 */
object MainCommandBarTooltipManager {

    /** Attach a name tooltip to every command-bar button that already carries a contentDescription. */
    fun apply(binding: ActivityMainBinding) {
        val buttons = listOf(
            binding.btnExit,
            binding.btnAddResource,
            binding.btnFilter,
            binding.btnRefresh,
            binding.btnMainDropdownMenu,
            binding.btnSettings,
            binding.btnToggleView,
            binding.btnFavorites,
            binding.btnStartPlayer,
        )
        buttons.forEach { button ->
            button.contentDescription?.takeIf { it.isNotBlank() }?.let { name ->
                TooltipCompat.setTooltipText(button, name)
            }
        }
    }
}
