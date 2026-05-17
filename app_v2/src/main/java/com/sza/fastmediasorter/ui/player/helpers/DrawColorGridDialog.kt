package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.GridLayout
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * 16-color custom palette picker (S0192 Phase 05, strategic §2.3.1).
 *
 * Tap-to-pick — no OK/Cancel buttons; selecting a colour fires [onPicked] and
 * dismisses the dialog immediately.
 */
class DrawColorGridDialog(
    private val activity: Activity,
    private val initialColor: Int,
    private val onPicked: (Int) -> Unit
) {

    fun show() {
        val grid = GridLayout(activity).apply {
            columnCount = 4
            rowCount = 4
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.draw_color_custom_cd)
            .setView(grid)
            .create()

        for (color in PALETTE) {
            val swatch = View(activity).apply {
                val size = dp(40)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(2), 0xFFFFFFFF.toInt())
                }
                setOnClickListener {
                    onPicked(color)
                    dialog.dismiss()
                }
            }
            grid.addView(swatch)
        }

        dialog.show()
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    companion object {
        // Strategic §2.3.1 — exact palette in row-major order
        private val PALETTE = intArrayOf(
            0xFF000000.toInt(), 0xFF424242.toInt(), 0xFF757575.toInt(), 0xFFBDBDBD.toInt(),
            0xFFFFFFFF.toInt(), 0xFFFF1744.toInt(), 0xFFE53935.toInt(), 0xFFFF7043.toInt(),
            0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF00897B.toInt(), 0xFF1E88E5.toInt(),
            0xFF3949AB.toInt(), 0xFF8E24AA.toInt(), 0xFFAD1457.toInt(), 0xFF6D4C41.toInt(),
        )
    }
}
