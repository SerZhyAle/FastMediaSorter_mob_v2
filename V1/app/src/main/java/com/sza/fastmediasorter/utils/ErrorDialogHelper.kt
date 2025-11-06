package com.sza.fastmediasorter.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object ErrorDialogHelper {
    fun showErrorWithCopy(
        context: Context,
        title: String,
        message: String,
        onDismiss: (() -> Unit)? = null,
    ): AlertDialog? {
        if (context is android.app.Activity && context.isFinishing) {
            return null
        }

        return AlertDialog
            .Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()
            }.setNeutralButton("Copy") { _, _ ->
                copyToClipboard(context, message)
                onDismiss?.invoke()
            }.setCancelable(onDismiss == null)
            .show()
    }

    fun showErrorWithCopyAndActions(
        context: Context,
        title: String,
        message: String,
        positiveText: String = "OK",
        negativeText: String? = null,
        neutralText: String? = null,
        onPositive: (() -> Unit)? = null,
        onNegative: (() -> Unit)? = null,
        onNeutral: (() -> Unit)? = null,
        cancelable: Boolean = true,
    ): AlertDialog? {
        if (context is android.app.Activity && context.isFinishing) {
            return null
        }

        val builder =
            AlertDialog
                .Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveText) { dialog, _ ->
                    dialog.dismiss()
                    onPositive?.invoke()
                }.setCancelable(cancelable)

        negativeText?.let {
            builder.setNegativeButton(it) { dialog, _ ->
                dialog.dismiss()
                onNegative?.invoke()
            }
        }

        neutralText?.let {
            builder.setNeutralButton(it) { dialog, _ ->
                dialog.dismiss()
                onNeutral?.invoke()
            }
        }

        val dialog = builder.show()

        // Override the positive button to also copy
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnLongClickListener {
            copyToClipboard(context, message)
            true
        }

        return dialog
    }

    private fun copyToClipboard(
        context: Context,
        text: String,
    ) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Error Message", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Error message copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
