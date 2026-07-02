package com.sza.fastmediasorter.ui.game.helpers

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.game.GameMode
import com.sza.fastmediasorter.ui.dialog.ListSelectionAdapter
import com.sza.fastmediasorter.ui.dialog.ListSelectionConfig
import com.sza.fastmediasorter.ui.dialog.ListSelectionDialog

/**
 * S0804: builds and shows the game-mode picker on the canonical [ListSelectionDialog] (ADR-3),
 * keeping [com.sza.fastmediasorter.ui.game.GameActivity] free of dialog-construction logic.
 */
class GameModeMenuManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    fun show(current: GameMode, onSelected: (GameMode) -> Unit) {
        val config = ListSelectionConfig(
            title = context.getString(R.string.game_mode_picker_title),
            lifecycleOwner = lifecycleOwner,
            loader = { GameMode.entries.toList() },
            formatter = object : ListSelectionAdapter.ItemFormatter<GameMode> {
                override fun getDisplayName(item: GameMode): String = context.getString(labelRes(item))
            },
            hasSelection = true,
            isSelected = { it == current },
            allowClear = false,
            emptyMessageRes = R.string.game_mode_picker_unavailable,
            errorMessageRes = R.string.game_mode_picker_unavailable,
            onSelected = { selected -> selected?.let(onSelected) }
        )
        ListSelectionDialog(context, config).show()
    }

    companion object {
        fun labelRes(mode: GameMode): Int = when (mode) {
            GameMode.CLASSIC -> R.string.game_mode_classic
            GameMode.KRYVAVITSA -> R.string.game_mode_kryvavitsa
        }
    }
}
