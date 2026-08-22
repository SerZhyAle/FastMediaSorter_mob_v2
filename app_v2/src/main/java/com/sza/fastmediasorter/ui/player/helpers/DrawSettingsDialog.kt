package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.view.LayoutInflater
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.showBoundToHost

/**
 * Draw editor settings dialog (S0192 Phase 02).
 *
 * Hosts a SeekBar for brush size (1..36) plus two RadioGroups (text size, opacity)
 * and reads/writes their values through [DrawEditorPrefs]. The Cancel button discards
 * any in-dialog edits; OK persists all three values and invokes [onApply] so the
 * active drawing session can pick them up on the next stroke (already true because
 * `DrawCanvasView` reads prefs at action creation time - `onApply` exists as a hook
 * for future cases like refreshing a "current brush" preview indicator).
 */
class DrawSettingsDialog(
    private val activity: Activity,
    private val onApply: () -> Unit = {}
) {

    fun show() {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_draw_settings, null, false)

        // ── Brush size ────────────────────────────────────────────────────
        val seek = view.findViewById<SeekBar>(R.id.seek_brush_size)
        val label = view.findViewById<TextView>(R.id.label_brush_value)
        val initialBrush = DrawEditorPrefs.getBrushSize(activity).coerceIn(1, 36)
        seek.progress = initialBrush - 1 // SeekBar range 0..35 maps to 1..36
        label.text = initialBrush.toString()
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = (progress + 1).toString()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // ── Text size ─────────────────────────────────────────────────────
        val radioText = view.findViewById<RadioGroup>(R.id.radio_text_size)
        when (DrawEditorPrefs.getTextSizeOrdinal(activity)) {
            0 -> radioText.check(R.id.radio_text_small)
            1 -> radioText.check(R.id.radio_text_medium)
            2 -> radioText.check(R.id.radio_text_large)
            else -> radioText.check(R.id.radio_text_medium)
        }

        // ── Opacity ───────────────────────────────────────────────────────
        val radioOpacity = view.findViewById<RadioGroup>(R.id.radio_opacity)
        when (DrawEditorPrefs.getOpacityPct(activity)) {
            0 -> radioOpacity.check(R.id.radio_op_0)
            25 -> radioOpacity.check(R.id.radio_op_25)
            50 -> radioOpacity.check(R.id.radio_op_50)
            75 -> radioOpacity.check(R.id.radio_op_75)
            100 -> radioOpacity.check(R.id.radio_op_100)
            else -> radioOpacity.check(R.id.radio_op_100)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.draw_settings_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Persist brush size (SeekBar 0..35 → 1..36)
                DrawEditorPrefs.setBrushSize(activity, seek.progress + 1)

                // Persist text size
                val textOrdinal = when (radioText.checkedRadioButtonId) {
                    R.id.radio_text_small -> 0
                    R.id.radio_text_medium -> 1
                    R.id.radio_text_large -> 2
                    else -> 1
                }
                DrawEditorPrefs.setTextSizeOrdinal(activity, textOrdinal)

                // Persist opacity
                val opacityPct = when (radioOpacity.checkedRadioButtonId) {
                    R.id.radio_op_0 -> 0
                    R.id.radio_op_25 -> 25
                    R.id.radio_op_50 -> 50
                    R.id.radio_op_75 -> 75
                    R.id.radio_op_100 -> 100
                    else -> 100
                }
                DrawEditorPrefs.setOpacityPct(activity, opacityPct)

                onApply()
            }
            .showBoundToHost(activity)
    }
}
