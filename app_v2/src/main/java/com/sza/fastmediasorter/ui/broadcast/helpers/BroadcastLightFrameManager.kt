package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sza.fastmediasorter.R
import java.lang.ref.WeakReference

/**
 * S4281: the bright frame of the broadcast control screen - a white border over the screen edges, with the
 * window brightness raised, that lights the face of a presenter filming themselves in the dark. It is the
 * border form of the Mirror's glow field, so the preview, the buttons and the QR code under it stay usable.
 *
 * Only this window's brightness is raised, never the system setting (ADR-2 of S1796 and S1924): a program
 * that raised the system brightness would leave the device on maximum after it closed. The raise ends with
 * the screen being looked at (Rule 18), and the frame's on/off flag outlives the activity so a rotation
 * keeps it lit - the owner of this object is the singleton control manager, hence every UI reference here
 * is weak and [detach] drops them.
 */
class BroadcastLightFrameManager {

    /** Survives a rotation; the control manager turns it off when the session ends or loses its camera. */
    var isOn: Boolean = false
        private set

    private var activityRef: WeakReference<AppCompatActivity>? = null
    private var hostRoot: WeakReference<View>? = null
    private var frameView: WeakReference<View>? = null

    // Per window: the value that was there before the raise, so the restore gives back the window's own
    // brightness and not the platform default - the black-screen layer reads the same attribute.
    private var brightnessBefore: Float? = null

    /**
     * The lifecycle observer is owned by [activity]'s own lifecycle and is deliberately not stored here:
     * a singleton holding it would hold the activity too.
     */
    fun attach(activity: AppCompatActivity, screenRoot: View) {
        activityRef = WeakReference(activity)
        hostRoot = WeakReference(screenRoot)
        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> if (isOn) light(activity)
                    Lifecycle.Event.ON_PAUSE -> restoreBrightness(activity)
                    else -> Unit
                }
            }
        )
    }

    fun toggle() {
        if (isOn) turnOff() else turnOn()
    }

    fun turnOn() {
        val activity = activityRef?.get() ?: return
        isOn = true
        light(activity)
    }

    fun turnOff() {
        isOn = false
        removeFrame()
        activityRef?.get()?.let(::restoreBrightness)
    }

    /** The window is going away: the references go with it, the flag stays for the next window. */
    fun detach() {
        removeFrame()
        activityRef = null
        hostRoot = null
        brightnessBefore = null
    }

    private fun light(activity: AppCompatActivity) {
        showFrame(activity)
        if (brightnessBefore == null) brightnessBefore = activity.window.attributes.screenBrightness
        setBrightness(activity, MAX_BRIGHTNESS)
    }

    private fun showFrame(activity: AppCompatActivity) {
        if (frameView?.get() != null) return
        // Added above the screen's root rather than inside either orientation layout: the border must cover
        // the inset padding those layouts apply, and both orientations then need no duplicate view.
        val content = hostRoot?.get()?.parent as? ViewGroup ?: return
        // A plain View is neither clickable nor focusable, so every touch falls through to the screen under it.
        val view = View(activity).apply {
            background = ContextCompat.getDrawable(activity, R.drawable.bg_broadcast_light_frame)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        content.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        frameView = WeakReference(view)
    }

    private fun removeFrame() {
        frameView?.get()?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        frameView = null
    }

    private fun restoreBrightness(activity: AppCompatActivity) {
        val before = brightnessBefore ?: return
        setBrightness(activity, before)
        brightnessBefore = null
    }

    private fun setBrightness(activity: AppCompatActivity, value: Float) {
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
    }

    private companion object {
        const val MAX_BRIGHTNESS = 1.0f
    }
}
