package com.sza.fastmediasorter.ui.common.support

import android.app.Dialog
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.common.dialog.applyDialogInsets
import com.sza.fastmediasorter.ui.companionimport.qr.QrCodeEncoder

/**
 * Shown by [DocsPageOpenManager] when a documentation page cannot be opened here: no browser is
 * installed (typical for Android TV boxes) or the device is offline. It gives the address as
 * selectable text and as a QR code, so the page can be opened on a phone instead.
 */
class DocsHelpFallbackDialogFragment : DialogFragment() {

    enum class Reason { NO_BROWSER, OFFLINE }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val url = requireArguments().getString(ARG_URL).orEmpty()
        val reason = runCatching { Reason.valueOf(requireArguments().getString(ARG_REASON).orEmpty()) }
            .getOrDefault(Reason.NO_BROWSER)
        val density = ctx.resources.displayMetrics.density
        val gap = (GAP_DP * density).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(gap)
        }
        val messageRes = when (reason) {
            Reason.OFFLINE -> R.string.docs_help_fallback_offline
            Reason.NO_BROWSER -> R.string.docs_help_fallback_no_browser
        }
        root.addView(
            TextView(ctx).apply {
                setText(messageRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SP)
            }
        )
        root.addView(
            TextView(ctx).apply {
                text = url
                setTextIsSelectable(true)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SP)
                setPadding(0, gap, 0, gap)
            }
        )
        val qrPx = (QR_DP * density).toInt()
        root.addView(
            ImageView(ctx).apply {
                setImageBitmap(QrCodeEncoder.encode(url, qrPx))
                contentDescription = url
            },
            LinearLayout.LayoutParams(qrPx, qrPx)
        )

        return MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.docs_help_fallback_title)
            .setView(ScrollView(ctx).apply { addView(root) })
            .setPositiveButton(R.string.ok) { d, _ -> d.dismiss() }
            .create()
            .apply { applyDialogInsets() }
    }

    companion object {
        private const val TAG = "docs_help_fallback_dialog"
        private const val ARG_URL = "url"
        private const val ARG_REASON = "reason"
        private const val GAP_DP = 16
        private const val QR_DP = 200
        private const val BODY_SP = 14f

        fun show(fm: FragmentManager, url: String, reason: Reason) {
            if (fm.isStateSaved || fm.findFragmentByTag(TAG) != null) return
            DocsHelpFallbackDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                    putString(ARG_REASON, reason.name)
                }
            }.show(fm, TAG)
        }
    }
}
