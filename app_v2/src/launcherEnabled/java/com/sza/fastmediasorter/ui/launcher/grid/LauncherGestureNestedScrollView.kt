package com.sza.fastmediasorter.ui.launcher.grid

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

/**
 * Delivers every desktop touch to the gesture recognizer before NestedScrollView claims a fling.
 *
 * The observer is deliberately non-consuming, so scrolling and child-cell interaction keep their normal
 * Android dispatch behaviour.
 */
class LauncherGestureNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : NestedScrollView(context, attrs, defStyleAttr) {

    var onRawDesktopTouchEvent: ((MotionEvent) -> Unit)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        onRawDesktopTouchEvent?.invoke(event)
        return super.dispatchTouchEvent(event)
    }
}
