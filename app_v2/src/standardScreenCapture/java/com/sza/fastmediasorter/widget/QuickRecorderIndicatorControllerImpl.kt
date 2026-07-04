package com.sza.fastmediasorter.widget

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * S0930: floating "Recording.. [Stop]" pill drawn over the current foreground app while the quick
 * audio recorder is active. Reuses [R.layout.view_recording_indicator] (S0774) verbatim so the app
 * never shows two different recording-pill styles; only the timer + Stop button are used here -
 * pause/resume and cancel stay hidden, this indicator only ever stops+saves. Not built on
 * `ScreenGestureOverlayManager` - that class exists to intercept drag gestures
 * (`FLAG_NOT_TOUCH_MODAL` pass-through), the opposite of a clickable pill.
 */
class QuickRecorderIndicatorControllerImpl @Inject constructor(
    @ApplicationContext private val appContext: Context
) : QuickRecorderIndicatorController {

    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var indicatorView: View? = null

    override fun isAvailable(context: Context): Boolean = Settings.canDrawOverlays(context)

    override fun show(context: Context, onStop: () -> Unit) {
        if (indicatorView != null) return
        val view = LayoutInflater.from(appContext).inflate(R.layout.view_recording_indicator, null)
        view.contentDescription = appContext.getString(R.string.quick_recorder_notification_recording)
        view.findViewById<MaterialButton>(R.id.recordingIndicatorPauseResume).visibility = View.GONE
        view.findViewById<MaterialButton>(R.id.recordingIndicatorCancel).visibility = View.GONE
        view.findViewById<MaterialButton>(R.id.recordingIndicatorStop).apply {
            contentDescription = appContext.getString(R.string.quick_recorder_action_stop)
            setOnClickListener { onStop() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            title = "quick_recorder_indicator"
        }
        windowManager.addView(view, params)
        indicatorView = view
    }

    override fun updateElapsed(text: String) {
        indicatorView?.findViewById<TextView>(R.id.recordingIndicatorTimer)?.text = text
    }

    override fun hide() {
        val view = indicatorView ?: return
        windowManager.removeViewImmediate(view)
        indicatorView = null
    }
}
