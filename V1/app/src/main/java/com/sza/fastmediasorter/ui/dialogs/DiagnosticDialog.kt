package com.sza.fastmediasorter.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.sza.fastmediasorter.R

/**
 * Utility class for displaying diagnostic information dialogs.
 * Shows detailed error messages, connection diagnostics, and system information
 * with copy-to-clipboard functionality for sharing diagnostic data.
 */
class DiagnosticDialog {
    companion object {
        fun show(
            context: Context,
            title: String,
            diagnosticText: String,
            isSuccess: Boolean = false,
        ): AlertDialog? {
            // Don't show dialog if context is finishing
            if (context is android.app.Activity && context.isFinishing) {
                return null
            }

            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_diagnostic, null)

            val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
            val textView = dialogView.findViewById<TextView>(R.id.diagnosticText)
            val copyButton = dialogView.findViewById<Button>(R.id.copyButton)
            val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

            titleView.text = title
            textView.text = diagnosticText

            // Set title color based on success/failure
            titleView.setTextColor(
                context.getColor(
                    if (isSuccess) {
                        android.R.color.holo_green_light
                    } else {
                        android.R.color.holo_red_light
                    },
                ),
            )

            val dialog =
                AlertDialog
                    .Builder(context)
                    .setView(dialogView)
                    .setCancelable(true)
                    .create()

            copyButton.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("SMB Test Results", diagnosticText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }

            closeButton.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()

            // Make dialog larger
            dialog.window?.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
                (context.resources.displayMetrics.heightPixels * 0.7).toInt(),
            )

            return dialog
        }
    }
}
