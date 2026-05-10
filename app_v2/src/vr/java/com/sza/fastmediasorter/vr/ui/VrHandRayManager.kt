package com.sza.fastmediasorter.vr.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.sza.fastmediasorter.vr.openxr.XrHand
import timber.log.Timber

/**
 * Layer E pointer bridge — translates normalised hand-tracking ray coordinates
 * into Android `MotionEvent`s delivered to the VR overlay hierarchy.
 *
 * Design decisions (spec_vr-hand-tracking-tech §5.4):
 *   - NDC → decor-view coordinates: the OpenXR UI composition plane matches the
 *     Activity's decor view, so NDC [-1,1]² maps linearly to [0, decorWidth/Height]
 *     with a Y flip (Android pixel space has +Y down).
 *   - Cursor dot: a small circular overlay rendered via a `FrameLayout` child of
 *     the decor view. Added lazily on first pointer-move and removed on [release]
 *     so non-hand-tracking sessions pay no layout cost.
 *   - Hover throttling: pointer-move dispatches at the XR render cadence (72–120 Hz).
 *     We forward `ACTION_HOVER_MOVE` at [HOVER_THROTTLE_MS] which matches mouse
 *     hover rate expectations and keeps view invalidation cheap.
 *   - Click translation: pinch-down latches [lastDownX]/[lastDownY] and fires
 *     `ACTION_DOWN`; pinch-up fires `ACTION_UP` at the same coordinates so even
 *     if the aim drifts mid-pinch the click lands where the user committed.
 */
class VrHandRayManager(
    private val activity: Activity,
    private val audioManager: AudioManager? = null,
) {

    private var cursorView: View? = null
    private var rootLayout: FrameLayout? = null

    @Volatile private var currentX = Float.NaN
    @Volatile private var currentY = Float.NaN
    @Volatile private var lastDownX = Float.NaN
    @Volatile private var lastDownY = Float.NaN
    private var isDown = false
    // True while the cursor is positioned over an interactive View. Toggled by
    // dispatchMotion based on whether the decor view consumed the hover event.
    private var isHoveringInteractive = false
    private var lastHoverMs = 0L
    @Volatile private var released = false

    /** Receive a pointer move from the OpenXR layer. NDC ∈ [-1,1]², off-plane > 1. */
    fun onPointerMove(hand: Int, ndcX: Float, ndcY: Float) {
        if (released) return
        if (!ndcX.isFinite() || !ndcY.isFinite()) return
        val decor = ensureRoot() ?: return
        // Off-plane: hide cursor and skip dispatch to avoid phantom hover at edges.
        if (ndcX < -1f || ndcX > 1f || ndcY < -1f || ndcY > 1f) {
            if (cursorView?.visibility == View.VISIBLE) {
                Timber.d("VR_AUDIT/1: hand-ray cursor HIDDEN (off-plane) hand=%d ndc=(%.3f,%.3f)", hand, ndcX, ndcY)
            }
            cursorView?.visibility = View.GONE
            currentX = Float.NaN
            currentY = Float.NaN
            return
        }
        val w = decor.width.toFloat()
        val h = decor.height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Map NDC → decor pixels. Android pixel Y points down so invert ndcY.
        val px = (ndcX * 0.5f + 0.5f) * w
        val py = (-ndcY * 0.5f + 0.5f) * h
        currentX = px
        currentY = py

        val cursor = ensureCursor()
        if (cursor.visibility != View.VISIBLE) {
            Timber.d("VR_AUDIT/1: hand-ray cursor VISIBLE hand=%d ndc=(%.3f,%.3f) px=(%.1f,%.1f) source=hand-tracking", hand, ndcX, ndcY, px, py)
        }
        cursor.visibility = View.VISIBLE
        // Use the intrinsic CURSOR_PX constant instead of measured width/height —
        // on the first few frames the view has not been laid out yet, so width is 0
        // and the cursor would be anchored at the top-left corner of the dot rather
        // than its centre.
        val halfSide = CURSOR_PX * 0.5f
        cursor.translationX = px - halfSide
        cursor.translationY = py - halfSide
        // The hand-tracking overlay must render above every other decor child
        // (VrControlOverlayManager / VrFileOpsOverlayManager are added later, so
        // they end up on top of us by default). Touch dispatch is unaffected
        // because our root is not clickable — events fall through to the real UI.
        rootLayout?.bringToFront()

        val now = SystemClock.uptimeMillis()
        if (now - lastHoverMs >= HOVER_THROTTLE_MS) {
            lastHoverMs = now
            dispatchMotion(MotionEvent.ACTION_HOVER_MOVE, px, py)
        }
    }

    /** Receive a pinch click transition from the OpenXR layer. */
    fun onPointerClick(@Suppress("UNUSED_PARAMETER") hand: Int, down: Boolean) {
        if (released) return
        val x = currentX
        val y = currentY
        if (!x.isFinite() || !y.isFinite()) {
            Timber.v("VrHandRay: click without valid position — ignored")
            return
        }
        if (down) {
            lastDownX = x
            lastDownY = y
            isDown = true
            dispatchMotion(MotionEvent.ACTION_DOWN, x, y)
        } else {
            if (isDown) {
                dispatchMotion(MotionEvent.ACTION_UP, lastDownX, lastDownY)
                isDown = false
            }
        }
    }

    fun release() {
        released = true
        val root = rootLayout
        if (root != null) {
            (root.parent as? ViewGroup)?.removeView(root)
        }
        rootLayout = null
        cursorView = null
        isDown = false
        isHoveringInteractive = false
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private fun ensureRoot(): FrameLayout? {
        val existing = rootLayout
        if (existing != null) return existing
        val decor = activity.window?.decorView as? ViewGroup ?: return null
        val root = FrameLayout(activity).apply {
            isClickable = false
            isFocusable = false
        }
        decor.addView(
            root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        rootLayout = root
        return root
    }

    private fun ensureCursor(): View {
        val existing = cursorView
        if (existing != null) return existing
        val root = ensureRoot() ?: error("decor view unavailable")
        val dot = View(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(220, 255, 255, 255))
                setStroke(3, Color.argb(220, 0, 160, 255))
            }
        }
        root.addView(dot, FrameLayout.LayoutParams(CURSOR_PX, CURSOR_PX))
        cursorView = dot
        return dot
    }

    private fun dispatchMotion(action: Int, x: Float, y: Float) {
        val decor = activity.window?.decorView ?: return
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0).apply {
            source = android.view.InputDevice.SOURCE_CLASS_POINTER or android.view.InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            // dispatchGenericMotionEvent returns true only when a View in the hierarchy
            // consumed the hover — a lightweight proxy for "cursor is over an interactive
            // element" without walking the View tree manually.
            val consumed = when (action) {
                MotionEvent.ACTION_HOVER_MOVE -> decor.dispatchGenericMotionEvent(event)
                else -> { decor.dispatchTouchEvent(event); false }
            }
            if (action == MotionEvent.ACTION_HOVER_MOVE && consumed != isHoveringInteractive) {
                isHoveringInteractive = consumed
                updateCursorAppearance()
            }
        } finally {
            event.recycle()
        }
    }

    private fun updateCursorAppearance() {
        val dot = cursorView ?: return
        val bg = dot.background as? GradientDrawable ?: return
        if (isHoveringInteractive) {
            bg.setColor(Color.argb(220, 0, 160, 255))
            bg.setStroke(3, Color.argb(220, 255, 255, 255))
            // Hover-enter SFX — fires only on the false→true transition (caller guarantees
            // updateCursorAppearance() is invoked only when the consumed-state changes).
            audioManager?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP)
        } else {
            bg.setColor(Color.argb(220, 255, 255, 255))
            bg.setStroke(3, Color.argb(220, 0, 160, 255))
        }
    }

    companion object {
        /** Minimum interval between successive hover dispatches (~60 Hz). */
        const val HOVER_THROTTLE_MS = 16L

        /** Cursor dot side length in pixels. */
        private const val CURSOR_PX = 28

        @Suppress("unused")
        private const val UNUSED_LEFT = XrHand.LEFT
    }
}
