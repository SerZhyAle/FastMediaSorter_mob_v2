package com.sza.fastmediasorter.ui.player.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.domain.model.StreamViabilityState
import com.sza.fastmediasorter.ui.player.helpers.PrefetchProgress
import timber.log.Timber

/**
 * Semi-transparent pill overlay drawn at the top-centre of the player surface.
 * Shows how many seconds have been pre-cached and the target, plus a thin bar.
 *
 * Variants:
 * - Normal (VIABLE): dark-grey pill, white text, blue bar fill
 * - MARGINAL: amber pill background, adds a "Network is tight" hint line
 * - "Playing from local copy": green pill, no bar, dismisses after [LOCAL_COPY_DISMISS_MS]
 *
 * Auto-dismisses [AUTO_DISMISS_DELAY_MS] after [markPlaybackReady] is called.
 * Dismissible by tap (honoured for rest of session via [setUserDismissed]).
 *
 * Visibility gate: set [isOverlayEnabled] = false (driven by [PrefetchCacheMultiplier.LESS])
 * to suppress all overlay display for users who prefer minimal UI noise.
 *
 * a11y: [contentDescription] is updated on every [update] call.
 */
class PrefetchOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── State ──────────────────────────────────────────────────────────────────

    /** Toggled by PlayerPrefetchManager based on PrefetchCacheMultiplier. False = overlay always hidden. */
    var isOverlayEnabled: Boolean = true

    private var cachedSec: Int = 0
    private var targetSec: Int = 0
    private var sourceLabel: String = ""
    private var isBuffering: Boolean = false
    private var viability: StreamViabilityState = StreamViabilityState.VIABLE

    private var isLocalCopyMode: Boolean = false
    private var localCopySizeLabel: String = ""
    private var isUserDismissed: Boolean = false

    // ── Dismiss timer ──────────────────────────────────────────────────────────

    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    // ── Geometry ───────────────────────────────────────────────────────────────

    private val pillRect = RectF()
    private val barBgRect = RectF()
    private val barFillRect = RectF()

    // ── Paint ──────────────────────────────────────────────────────────────────

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        isFakeBoldText = false
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 200
    }
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FFFFFF")
        style = Paint.Style.FILL
    }
    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── Dimension helpers ──────────────────────────────────────────────────────

    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )
    private fun sp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Update progress. Call from the UI thread (or post from a coroutine collector).
     * Triggers a redraw if the overlay is visible.
     */
    fun update(progress: PrefetchProgress) {
        if (!isOverlayEnabled || isUserDismissed) return
        isLocalCopyMode = false
        cachedSec = progress.cachedSec
        targetSec = progress.targetSec
        sourceLabel = progress.sourceLabel
        isBuffering = progress.isBuffering
        val prevViability = viability
        viability = progress.viability

        val shouldShow = isBuffering || (cachedSec < targetSec && targetSec > 0)
        isVisible = shouldShow

        if (shouldShow) {
            updateA11yDescription()
            // Viability change toggles the hint line → full re-measure required
            if (prevViability != viability) requestLayout() else invalidate()
        }
    }

    /**
     * Switch to "Playing from local copy" mode. Pill turns green,
     * bar is hidden, and it auto-dismisses after [LOCAL_COPY_DISMISS_MS].
     */
    fun showLocalCopyMode(sizeLabelStr: String) {
        if (!isOverlayEnabled || isUserDismissed) return
        isLocalCopyMode = true
        localCopySizeLabel = sizeLabelStr
        isVisible = true
        invalidate()
        scheduleDismiss(LOCAL_COPY_DISMISS_MS)
        Timber.d("PrefetchOverlayView: local copy mode - will dismiss in ${LOCAL_COPY_DISMISS_MS}ms")
    }

    /** Called when ExoPlayer reaches STATE_READY + isPlaying. Starts auto-dismiss timer. */
    fun markPlaybackReady() {
        if (!isLocalCopyMode) {
            scheduleDismiss(AUTO_DISMISS_DELAY_MS)
        }
    }

    /** User tapped the overlay - suppress for the rest of this session. */
    fun setUserDismissed() {
        isUserDismissed = true
        dismissRunnable?.let { handler.removeCallbacks(it) }
        isVisible = false
        Timber.d("PrefetchOverlayView: user dismissed for session")
    }

    /** Reset to initial state for a new media session. */
    fun reset() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        isUserDismissed = false
        isLocalCopyMode = false
        isVisible = false
        cachedSec = 0
        targetSec = 0
    }

    // ── Touch ──────────────────────────────────────────────────────────────────

    override fun performClick(): Boolean {
        super.performClick()
        setUserDismissed()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_UP) performClick()
        return true
    }

    // ── Layout ─────────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val needsHintLine = !isLocalCopyMode && viability == StreamViabilityState.MARGINAL
        val linesCount = if (needsHintLine) 3 else 2   // text + bar (+ hint)
        val h = (dp(12f) + sp(14f) * linesCount + dp(6f) * (linesCount - 1) + dp(8f) + dp(12f)).toInt()
        setMeasuredDimension(w, h)
    }

    // ── Draw ───────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalW = width.toFloat()
        val pillW = totalW.coerceAtMost(dp(280f))
        val pillX = (totalW - pillW) / 2f
        val cornerR = dp(14f)
        val padH = dp(12f)
        val padV = dp(10f)

        // Choose colours by variant
        val pillColor: Int
        val barColor: Int
        when {
            isLocalCopyMode -> {
                pillColor = Color.parseColor("#CC2E7D32")   // green, 80 % opaque
                barColor = Color.parseColor("#4CAF50")
            }
            viability == StreamViabilityState.MARGINAL -> {
                pillColor = Color.parseColor("#CCF57F17")   // amber, 80 % opaque
                barColor = Color.parseColor("#FFB300")
            }
            else -> {
                pillColor = Color.parseColor("#CC1C1C1C")   // dark, 80 % opaque
                barColor = Color.parseColor("#2196F3")
            }
        }
        pillPaint.color = pillColor
        barFillPaint.color = barColor

        // Text sizes
        val mainTextSz = sp(13f)
        val hintTextSz = sp(11f)
        val barH = dp(3f)
        textPaint.textSize = mainTextSz
        hintPaint.textSize = hintTextSz

        val hasHint = !isLocalCopyMode && viability == StreamViabilityState.MARGINAL
        val pillH = padV + mainTextSz + dp(6f) + barH + dp(6f) +
            (if (hasHint) hintTextSz + dp(4f) else 0f) + padV

        pillRect.set(pillX, padH, pillX + pillW, padH + pillH)
        canvas.drawRoundRect(pillRect, cornerR, cornerR, pillPaint)

        // ── Line 1: main label ──────────────────────────────────────────────

        val line1: String
        val fraction = if (targetSec > 0) cachedSec.toFloat() / targetSec else 0f

        line1 = if (isLocalCopyMode) {
            resources.getString(
                com.sza.fastmediasorter.R.string.prefetch_overlay_local_copy_format,
                localCopySizeLabel
            )
        } else {
            resources.getString(
                com.sza.fastmediasorter.R.string.prefetch_overlay_format,
                cachedSec, targetSec, sourceLabel
            )
        }

        val textY = pillRect.top + padV + mainTextSz
        canvas.drawText(line1, pillX + dp(16f), textY, textPaint)

        // ── Progress bar ────────────────────────────────────────────────────

        val barTop = textY + dp(6f)
        val barLeft = pillX + dp(16f)
        val barRight = pillX + pillW - dp(16f)
        barBgRect.set(barLeft, barTop, barRight, barTop + barH)
        canvas.drawRoundRect(barBgRect, barH / 2f, barH / 2f, barBgPaint)

        if (!isLocalCopyMode) {
            val fillRight = (barLeft + (barRight - barLeft) * fraction.coerceIn(0f, 1f))
            barFillRect.set(barLeft, barTop, fillRight, barTop + barH)
            if (fillRight > barLeft) {
                canvas.drawRoundRect(barFillRect, barH / 2f, barH / 2f, barFillPaint)
            }
        } else {
            // Local copy mode: full green bar
            barFillRect.set(barLeft, barTop, barRight, barTop + barH)
            canvas.drawRoundRect(barFillRect, barH / 2f, barH / 2f, barFillPaint)
        }

        // ── Hint line (MARGINAL only) ───────────────────────────────────────

        if (hasHint) {
            val hintY = barTop + barH + dp(6f) + hintTextSz
            val hint = resources.getString(com.sza.fastmediasorter.R.string.prefetch_margin_hint)
            canvas.drawText(hint, pillX + dp(16f), hintY, hintPaint)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun scheduleDismiss(delayMs: Long) {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            dismissRunnable = null
            isVisible = false
        }
        dismissRunnable = r
        handler.postDelayed(r, delayMs)
    }

    private fun updateA11yDescription() {
        val ctx = context
        contentDescription = if (isLocalCopyMode) {
            ctx.getString(
                com.sza.fastmediasorter.R.string.prefetch_overlay_local_copy_format,
                localCopySizeLabel
            )
        } else {
            val marginal = if (viability == StreamViabilityState.MARGINAL)
                " ${ctx.getString(com.sza.fastmediasorter.R.string.prefetch_margin_hint)}"
            else ""
            ctx.getString(
                com.sza.fastmediasorter.R.string.prefetch_a11y_format,
                cachedSec, targetSec, sourceLabel
            ) + marginal
        }
    }

    companion object {
        private const val AUTO_DISMISS_DELAY_MS = 600L
        private const val LOCAL_COPY_DISMISS_MS = 3000L
    }
}
