package com.sza.fastmediasorter.ui.game.helpers

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.game.GameMode

/**
 * S0804: presentation-only skin for the game board. Resolved once per mode and cached by the view,
 * so [com.sza.fastmediasorter.ui.game.GameBoardView.onDraw] stays allocation-free.
 *
 * A null actor/exit drawable means "draw the classic primitive" (star/triangle/circle, framed exit);
 * a non-null drawable is blitted into the cell instead.
 */
class GameBoardTheme private constructor(
    val floorColor: Int,
    val wallColor: Int,
    // When true, moving actors hop (a heavy "stomp") instead of sliding smoothly.
    val stomp: Boolean,
    val playerDrawable: Drawable?,
    val kryvavitsaDrawable: Drawable?,
    val shadowDrawable: Drawable?,
    val doorDrawable: Drawable?
) {
    companion object {
        // Classic values mirror the historical hardcoded board paints so the skin is pixel-identical.
        private const val CLASSIC_FLOOR = "#FFF5F5F5"
        private const val CLASSIC_WALL = "#FF5F6368"
        private const val KRYVAVITSA_FLOOR = "#FFECECEC"
        private const val KRYVAVITSA_WALL = "#FF111111"

        fun forMode(context: Context, mode: GameMode): GameBoardTheme = when (mode) {
            GameMode.CLASSIC -> GameBoardTheme(
                floorColor = Color.parseColor(CLASSIC_FLOOR),
                wallColor = Color.parseColor(CLASSIC_WALL),
                stomp = false,
                playerDrawable = null,
                kryvavitsaDrawable = null,
                shadowDrawable = null,
                doorDrawable = null
            )
            GameMode.KRYVAVITSA -> GameBoardTheme(
                floorColor = Color.parseColor(KRYVAVITSA_FLOOR),
                wallColor = Color.parseColor(KRYVAVITSA_WALL),
                stomp = true,
                playerDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_game_fig_monster),
                kryvavitsaDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_game_fig_kryvavitsa),
                shadowDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_game_fig_shadow),
                doorDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_game_door)
            )
        }
    }
}
