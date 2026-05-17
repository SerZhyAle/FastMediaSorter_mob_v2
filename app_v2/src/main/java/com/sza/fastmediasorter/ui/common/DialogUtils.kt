package com.sza.fastmediasorter.ui.common

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import timber.log.Timber

object DialogUtils {

    fun showScrollableDialog(
        context: Context,
        title: String,
        message: String,
        positiveButtonText: String? = context.getString(R.string.ok),
        onPositive: (() -> Unit)? = null,
        negativeButtonText: String? = null,
        onNegative: (() -> Unit)? = null,
        cancelable: Boolean = true
    ) {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            Timber.w("DialogUtils: skipping showScrollableDialog — Activity is finishing/destroyed")
            return
        }
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_scrollable_text, null)
        val tvContent = view.findViewById<TextView>(R.id.tvDialogContent)
        val btnCopy = view.findViewById<MaterialButton>(R.id.btnCopyToClipboard)

        tvContent.text = message

        btnCopy.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            // Read text directly from TextView to avoid closure/scope issues
            val textToCopy = tvContent.text.toString()
            val clip = ClipData.newPlainText("Dialog Content", textToCopy)
            clipboard.setPrimaryClip(clip)
            // Use existing string resource for "Copied to clipboard"
            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }

        val btnShare = view.findViewById<MaterialButton>(R.id.btnShareContent)
        btnShare.setOnClickListener {
            val textToShare = tvContent.text.toString()
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, textToShare)
            }
            val chooser = Intent.createChooser(sendIntent, null)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(view)
            .setCancelable(cancelable)

        if (positiveButtonText != null) {
            builder.setPositiveButton(positiveButtonText) { _, _ ->
                onPositive?.invoke()
            }
        }

        if (negativeButtonText != null) {
            builder.setNegativeButton(negativeButtonText) { _, _ ->
                onNegative?.invoke()
            }
        }

        val dialog = builder.create()
        try {
            dialog.show()
        } catch (e: WindowManager.BadTokenException) {
            Timber.e(e, "DialogUtils: dialog show failed — bad window token")
            return
        }
        // S0230 Phase 04 — TalkBack initial focus on the most-meaningful button.
        com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper.applyInitialFocus(dialog)

        // Resize dialog to 90% of screen width
        val displayMetrics = context.resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun showScrollableDialog(
        context: Context,
        @StringRes titleRes: Int,
        message: String,
        @StringRes positiveButtonTextRes: Int = R.string.ok,
        onPositive: (() -> Unit)? = null
    ) {
        showScrollableDialog(context, context.getString(titleRes), message, context.getString(positiveButtonTextRes), onPositive)
    }
}
