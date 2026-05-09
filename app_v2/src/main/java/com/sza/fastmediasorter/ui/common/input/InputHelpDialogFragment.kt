package com.sza.fastmediasorter.ui.common.input

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced

/**
 * Shared F1 help dialog.
 *
 * Shows only the shortcut entries relevant to the current [InputSurface]
 * and appends a single clickable link to the full website documentation
 * resolved by [InputHelpLinkResolver].
 *
 * Surfaces register their own [InputHelpEntry.Section] lists via
 * [InputHelpRegistry] so the same table is consumed by in-app help and
 * future documentation generation without duplication.
 */
class InputHelpDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val surfaceName = arguments?.getString(ARG_SURFACE) ?: InputSurface.MAIN.name
        val surface = runCatching { InputSurface.valueOf(surfaceName) }.getOrDefault(InputSurface.MAIN)
        val sections = InputHelpRegistry.get(surface)
        val density = ctx.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad)
        }

        val scroll = ScrollView(ctx).apply {
            addView(root)
        }

        if (sections.isEmpty()) {
            root.addView(TextView(ctx).apply {
                text = ctx.getString(R.string.kbm_help_empty)
            })
        } else {
            for (section in sections) {
                val header = TextView(ctx).apply {
                    setText(section.titleRes)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
                }
                root.addView(header)
                for (entry in section.entries) {
                    root.addView(buildRow(entry, density))
                }
            }
        }

        val link = TextView(ctx).apply {
            setPadding(0, (16 * density).toInt(), 0, 0)
            setTextColor(Color.parseColor("#2196F3"))
            text = ctx.getString(R.string.kbm_help_full_docs_link)
            setOnClickListenerDebounced {
                // S0118: route through SupportIntentFactory so help launches share the
                // factory's intent shape across surfaces, while InputHelpLinkResolver
                // continues to own the per-surface anchor.
                val url = InputHelpLinkResolver.urlFor(surface)
                runCatching {
                    startActivity(SupportIntentFactory.openUrl(url))
                }
            }
        }
        root.addView(link)

        return AlertDialog.Builder(ctx)
            .setTitle(R.string.kbm_help_title)
            .setView(scroll)
            .setPositiveButton(R.string.ok) { d, _ -> d.dismiss() }
            .create()
    }

    private fun buildRow(entry: InputHelpEntry, density: Float): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
        }
        val keys = TextView(ctx).apply {
            text = entry.keys
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            minWidth = (120 * density).toInt()
        }
        val desc = TextView(ctx).apply {
            setText(entry.descriptionRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding((12 * density).toInt(), 0, 0, 0)
        }
        row.addView(keys)
        row.addView(desc)
        return row
    }

    companion object {
        private const val TAG = "input_help_dialog"
        private const val ARG_SURFACE = "surface"

        /** Show the F1 help dialog for [surface]. */
        fun show(fm: FragmentManager, surface: InputSurface) {
            if (fm.findFragmentByTag(TAG) != null) return
            InputHelpDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_SURFACE, surface.name) }
            }.show(fm, TAG)
        }
    }
}
