package com.sza.fastmediasorter.ui.common.input

import android.app.Dialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.common.support.DocsPageOpenManager
import com.sza.fastmediasorter.ui.common.support.MediaExchangePortalLinks
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced

/**
 * Shared F1 help dialog.
 *
 * Shows only the shortcut entries relevant to the current [UiSurface]
 * and appends a link to the documentation-portal page that describes this
 * screen, resolved by [InputHelpLinkResolver] and opened by [DocsPageOpenManager].
 *
 * Surfaces register their own [InputHelpEntry.Section] lists via
 * [InputHelpRegistry] so the same table is consumed by in-app help and
 * future documentation generation without duplication.
 */
class InputHelpDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val surfaceName = arguments?.getString(ARG_SURFACE) ?: UiSurface.MAIN.name
        val surface = runCatching { UiSurface.valueOf(surfaceName) }.getOrDefault(UiSurface.MAIN)
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

        root.addView(
            buildLink(R.string.docs_help_open_page, topPaddingDp = LINK_TOP_PADDING_DP, density = density) {
                val url = InputHelpLinkResolver.urlFor(surface, LocaleHelper.getLanguage(ctx))
                DocsPageOpenManager.open(requireActivity(), url)
            }
        )
        // S4143: the surfaces that ask the user to choose HOW to reach a source also get the portal page
        // that compares the ways; the recipe link above stays, it answers the how-to question.
        MediaExchangePortalLinks.pageFor(surface)?.let { page ->
            val portalLink = buildLink(
                R.string.media_exchange_portal_open,
                topPaddingDp = PORTAL_LINK_TOP_PADDING_DP,
                density = density,
            ) {
                MediaExchangePortalLinks.open(requireActivity(), page)
            }
            root.addView(portalLink)
        }

        return MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.kbm_help_title)
            .setView(scroll)
            .setPositiveButton(R.string.ok) { d, _ -> d.dismiss() }
            .create()
    }

    private fun buildLink(
        @StringRes textRes: Int,
        topPaddingDp: Int,
        density: Float,
        onClick: () -> Unit,
    ): TextView = TextView(requireContext()).apply {
        setPadding(0, (topPaddingDp * density).toInt(), 0, 0)
        setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
        setText(textRes)
        isFocusable = true
        setBackgroundResource(selectableBackgroundRes())
        setOnClickListenerDebounced { onClick() }
    }

    private fun selectableBackgroundRes(): Int {
        val outValue = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return outValue.resourceId
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
        private const val LINK_TOP_PADDING_DP = 16
        private const val PORTAL_LINK_TOP_PADDING_DP = 8

        /** Show the F1 help dialog for [surface]. */
        fun show(fm: FragmentManager, surface: UiSurface) {
            if (fm.findFragmentByTag(TAG) != null) return
            InputHelpDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_SURFACE, surface.name) }
            }.show(fm, TAG)
        }
    }
}
