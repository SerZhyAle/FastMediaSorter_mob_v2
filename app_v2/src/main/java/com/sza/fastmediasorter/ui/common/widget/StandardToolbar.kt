package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.util.AttributeSet
import androidx.activity.ComponentActivity
import com.google.android.material.appbar.MaterialToolbar

/**
 * S3248: the app's single toolbar entry point, styled by
 * `Widget.FastMediaSorter.Toolbar.Primary` or `.Flat`.
 *
 * Back navigation had three independent idioms - `setSupportActionBar` plus
 * `setDisplayHomeAsUpEnabled`, a hand-written `setNavigationOnClickListener`, and a bespoke back
 * button - so a predictive-back or dispatcher change had to be repeated in every host. [setUpNavigation]
 * routes them all through `onBackPressedDispatcher`, which is the only path that honours the
 * callbacks a host already registered.
 */
class StandardToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.toolbarStyle,
) : MaterialToolbar(context, attrs, defStyleAttr) {

    fun setUpNavigation(activity: ComponentActivity) {
        setNavigationOnClickListener { activity.onBackPressedDispatcher.onBackPressed() }
    }
}
