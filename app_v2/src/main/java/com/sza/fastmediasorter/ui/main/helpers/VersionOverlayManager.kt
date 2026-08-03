package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R

object VersionOverlayManager {

    private const val VERSION_TEXT_SIZE_SP = 8F
    private const val VERSION_TEXT_ALPHA = 0.75F

    fun attachIfEnabled(activity: Activity) {
        if (!activity.resources.getBoolean(R.bool.debug_version_overlay_enabled)) return

        val contentRoot = activity.findViewById<FrameLayout>(android.R.id.content)
        val versionView = TextView(activity).apply {
            text = "v${BuildConfig.VERSION_NAME}"
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
    }
}
