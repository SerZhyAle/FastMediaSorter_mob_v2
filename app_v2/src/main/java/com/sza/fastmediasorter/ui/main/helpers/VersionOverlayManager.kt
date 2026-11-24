package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import timber.log.Timber
import java.io.IOException

object VersionOverlayManager {

    private const val VERSION_TEXT_SIZE_SP = 8F
    private const val VERSION_TEXT_ALPHA = 0.75F

    // Written by GenerateBuildStampTask into a stable debug build only: that build keeps the checked-in
    // BuildConfig version on purpose, so the packaging minute travels in this asset instead.
    private const val BUILD_STAMP_ASSET = "build_stamp.txt"

    fun attachIfEnabled(activity: Activity) {
        if (!activity.resources.getBoolean(R.bool.debug_version_overlay_enabled)) return

        val contentRoot = activity.findViewById<FrameLayout>(android.R.id.content)
        val versionView = TextView(activity).apply {
            text = "v${readBuildStamp(activity) ?: BuildConfig.VERSION_NAME}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, VERSION_TEXT_SIZE_SP)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE))
            alpha = VERSION_TEXT_ALPHA
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        contentRoot.addView(
            versionView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END,
            ),
        )
        // S4837: the app draws edge to edge, so in landscape the content frame runs under the side bar
        // and an end-gravity label straddles the boundary. Only the horizontal inset is applied: the
        // portrait label sits on the bottom bar strip and has never been cut there.
        ViewCompat.setOnApplyWindowInsetsListener(versionView) { view, insets ->
            applyEndInset(view, insets)
            insets
        }
        versionView.doOnAttach { view ->
            ViewCompat.getRootWindowInsets(view)?.let { applyEndInset(view, it) }
        }
    }

    private fun applyEndInset(view: View, insets: WindowInsetsCompat) {
        val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val endInset = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) safe.left else safe.right
        Timber.d("S4837: version label end inset=$endInset")
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.marginEnd == endInset) return
        params.marginEnd = endInset
        view.layoutParams = params
    }

    // An absent asset is the normal case for a stamped build, where BuildConfig already carries the
    // build minute, so the fallback is the answer there and not a failure worth a log line.
    private fun readBuildStamp(activity: Activity): String? =
        try {
            activity.assets.open(BUILD_STAMP_ASSET).bufferedReader().use { it.readText().trim() }
                .takeIf { it.isNotEmpty() }
        } catch (expected: IOException) {
            null
        }
}
