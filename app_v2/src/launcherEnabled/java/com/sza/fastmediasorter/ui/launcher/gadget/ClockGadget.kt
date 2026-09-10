package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.provider.CalendarContract
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextClock
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherClockBinding
import com.sza.fastmediasorter.domain.model.UnitScale
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.util.resolveActivityCompat
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

/**
 * S0404: time and date on the desktop. Weather is deliberately absent - S0426 extends this gadget
 * through the contract (strategic §6.7), so there is no placeholder to mislead the user meanwhile.
 */
class ClockGadget @Inject constructor(
    private val stateStore: ClockGadgetStateStore,
    private val unitSystemProvider: Lazy<UnitSystemProvider>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_CLOCK

    // S1094: big by default (seeds 4x2) but resizes down to 2x1 - the seed size and the resize floor
    // are decoupled here, so the clock reads as a main element yet still shrinks.
    override val defaultSpanW: Int = 4
    override val defaultSpanH: Int = 2
    override val minSpanW: Int = 2
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_clock
    override val iconRes: Int = R.drawable.ic_schedule

    // S2062: ic_schedule fills white and is invisible on the picker's light surface without a tint.
    override val iconTintable: Boolean = true
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        ClockGadgetView(container.context, stateStore, unitSystemProvider.get())
}

/**
 * Both [android.widget.TextClock]s tick on their own; the only lifecycle work is following the
 * unit-system setting (S2795), which decides the clock length and therefore the patterns they tick with.
 */
private class ClockGadgetView(
    context: Context,
    private val stateStore: ClockGadgetStateStore,
    private val unitSystemProvider: UnitSystemProvider,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherClockBinding.inflate(LayoutInflater.from(context), this)

    private var displayState: ClockGadgetDisplayState? = null

    private var unitSystem: UnitSystem = unitSystemProvider.value

    private val configuration = ViewConfiguration.get(context)

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onFling(
                start: MotionEvent?,
                end: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                isFlingHandled = start?.let { handleFling(it, end, velocityX, velocityY) } ?: false
                return isFlingHandled
            }
        },
    )
    private var isFlingHandled = false

    init {
        contentDescription = context.getString(R.string.launcher_gadget_clock_actions)
        isFocusable = true
        isClickable = true
        setOnClickListener { openSystemClock(context) }
        setOnLongClickListener {
            openCalendar(context)
            true
        }
        setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_UP || !event.isShiftPressed) return@setOnKeyListener false
            handleDirection(
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> ClockSwipeDirection.LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> ClockSwipeDirection.RIGHT
                    KeyEvent.KEYCODE_DPAD_UP -> ClockSwipeDirection.UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> ClockSwipeDirection.DOWN
                    else -> return@setOnKeyListener false
                }
            )
            true
        }
    }

    /**
     * The collection is scoped by [LauncherGadgetView], which already runs this only while attached and
     * STARTED - a settings flip made while the desktop is on screen must reach a clock that is already
     * ticking, and one made while it is not is picked up by the next start.
     */
    override suspend fun CoroutineScope.onActive() {
        val storedState = withContext(Dispatchers.IO) { stateStore.read() }
        unitSystemProvider.current.collect { system ->
            unitSystem = system
            applyDisplayState(displayState ?: storedState)
        }
    }

    /**
     * The two TextClock children own the touch target, so an OnTouchListener on this FrameLayout
     * misses their move and up events. Dispatch sees the complete sequence before either child does;
     * it also keeps the desktop scroll container from cancelling a vertical clock gesture mid-flight.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isFlingHandled = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && isFlingHandled) {
            val cancelEvent = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            val result = super.dispatchTouchEvent(cancelEvent)
            cancelEvent.recycle()
            return result
        }
        return super.dispatchTouchEvent(event)
    }

    private fun handleFling(
        start: MotionEvent,
        end: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        val distanceX = end.x - start.x
        val distanceY = end.y - start.y
        val direction = ClockSwipeDirectionResolver.resolve(
            distanceX = distanceX,
            distanceY = distanceY,
            velocityX = velocityX,
            velocityY = velocityY,
            touchSlop = configuration.scaledPagingTouchSlop.toFloat(),
            minimumFlingVelocity = configuration.scaledMinimumFlingVelocity.toFloat(),
        ) ?: return false
        handleDirection(direction)
        return true
    }

    private fun handleDirection(direction: ClockSwipeDirection) {
        val currentState = stateStore.read()
        when (direction) {
            ClockSwipeDirection.RIGHT -> stateStore.setSecondsVisible(false)
            ClockSwipeDirection.LEFT -> stateStore.setSecondsVisible(true)
            ClockSwipeDirection.UP -> {
                stateStore.setDialColor(randomDialColor())
                stateStore.setDialTypefaceName(randomTypefaceName(currentState.dialTypefaceName))
            }
            ClockSwipeDirection.DOWN -> stateStore.setDialColor(null)
        }
        applyDisplayState(stateStore.read())
    }

    private fun applyDisplayState(state: ClockGadgetDisplayState) {
        displayState = state
        applyPattern(binding.gadgetClockTime, UnitScale.timePattern(unitSystem, state.secondsVisible))
        applyPattern(binding.gadgetClockDate, WEEKDAY_FIELD + UnitScale.shortDatePattern(unitSystem))
        binding.gadgetClockTime.setTextColor(
            state.dialColor ?: MaterialColors.getColor(
                binding.gadgetClockTime,
                com.google.android.material.R.attr.colorOnSurface,
            )
        )
        binding.gadgetClockDate.setTextColor(
            state.dialColor ?: MaterialColors.getColor(
                binding.gadgetClockDate,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
            )
        )
        binding.gadgetClockTime.typeface = ClockDialTypeface
            .fromPersistedName(state.dialTypefaceName)
            .resolveTypeface()
    }

    /**
     * Exactly one attribute carries the pattern and the other is null, because TextClock falls back to
     * whichever one is set when the one matching the DEVICE's 12/24-hour switch is missing. That is what
     * makes the app's unit system, not the device, decide the clock length (S2795); setting both would
     * hand the choice back to the device, which is what this gadget did before.
     */
    private fun applyPattern(clock: TextClock, pattern: String) {
        val imperial = unitSystem == UnitSystem.IMPERIAL
        Timber.d("S2795: desktop clock pattern=%s imperial=%s", pattern, imperial)
        clock.format12Hour = pattern.takeIf { imperial }
        clock.format24Hour = pattern.takeIf { !imperial }
    }

    private fun randomTypefaceName(currentName: String): String = ClockDialTypeface.entries
        .filter { it.persistedName != currentName }
        .random()
        .persistedName

    private fun randomDialColor(): Int {
        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
        val lightness = if (ColorUtils.calculateLuminance(surfaceColor) > SURFACE_LIGHTNESS_THRESHOLD) {
            LIGHT_SURFACE_DIAL_LIGHTNESS
        } else {
            DARK_SURFACE_DIAL_LIGHTNESS
        }
        repeat(MAX_RANDOM_COLOR_ATTEMPTS) {
            val candidate = ColorUtils.HSLToColor(
                floatArrayOf(Random.nextFloat() * HUE_DEGREES, DIAL_SATURATION, lightness)
            )
            if (ColorUtils.calculateContrast(candidate, surfaceColor) >= MINIMUM_DIAL_CONTRAST) {
                return candidate
            }
        }
        return if (ColorUtils.calculateLuminance(surfaceColor) > SURFACE_LIGHTNESS_THRESHOLD) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    /** Long press opens the calendar at now; like the alarm intent, a missing app is a silent no-op. */
    private fun openCalendar(context: Context) {
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .also { ContentUris.appendId(it, System.currentTimeMillis()) }
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivityCompat(intent) == null) {
            Timber.i("Launcher clock gadget: no calendar app to open")
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Launcher clock gadget: calendar app refused to open") }
    }

    private companion object {
        /**
         * The weekday, translated by the locale, in front of the system's own day-month order. The full
         * date pattern is not used here: this line autosizes down to 9sp and has no room for a year.
         */
        const val WEEKDAY_FIELD = "EEE "
        const val HUE_DEGREES = 360f
        const val DIAL_SATURATION = 0.72f
        const val LIGHT_SURFACE_DIAL_LIGHTNESS = 0.25f
        const val DARK_SURFACE_DIAL_LIGHTNESS = 0.80f
        const val SURFACE_LIGHTNESS_THRESHOLD = 0.5
        const val MINIMUM_DIAL_CONTRAST = 4.5
        const val MAX_RANDOM_COLOR_ATTEMPTS = 24
    }
}

internal enum class ClockSwipeDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

internal object ClockSwipeDirectionResolver {

    fun resolve(
        distanceX: Float,
        distanceY: Float,
        velocityX: Float,
        velocityY: Float,
        touchSlop: Float,
        minimumFlingVelocity: Float,
    ): ClockSwipeDirection? = when {
        abs(distanceX) > abs(distanceY) -> horizontalDirection(
            distance = distanceX,
            velocity = velocityX,
            touchSlop = touchSlop,
            minimumFlingVelocity = minimumFlingVelocity,
        )

        abs(distanceY) > abs(distanceX) -> verticalDirection(
            distance = distanceY,
            velocity = velocityY,
            touchSlop = touchSlop,
            minimumFlingVelocity = minimumFlingVelocity,
        )

        else -> null
    }

    private fun horizontalDirection(
        distance: Float,
        velocity: Float,
        touchSlop: Float,
        minimumFlingVelocity: Float,
    ): ClockSwipeDirection? = when {
        distance > touchSlop && velocity > minimumFlingVelocity -> ClockSwipeDirection.RIGHT
        distance < -touchSlop && velocity < -minimumFlingVelocity -> ClockSwipeDirection.LEFT
        else -> null
    }

    private fun verticalDirection(
        distance: Float,
        velocity: Float,
        touchSlop: Float,
        minimumFlingVelocity: Float,
    ): ClockSwipeDirection? = when {
        distance < -touchSlop && velocity < -minimumFlingVelocity -> ClockSwipeDirection.UP
        distance > touchSlop && velocity > minimumFlingVelocity -> ClockSwipeDirection.DOWN
        else -> null
    }
}

private enum class ClockDialTypeface(
    val persistedName: String,
    private val familyName: String?,
) {
    DEFAULT(ClockGadgetStateStore.DEFAULT_DIAL_TYPEFACE, null),
    CONDENSED("condensed", "sans-serif-condensed"),
    SERIF("serif", "serif"),
    MONOSPACE("monospace", "monospace"),
    CASUAL("casual", "casual"),
    ;

    fun resolveTypeface(): Typeface = familyName?.let { Typeface.create(it, Typeface.BOLD) }
        ?: Typeface.DEFAULT_BOLD

    companion object {
        fun fromPersistedName(name: String): ClockDialTypeface =
            entries.firstOrNull { it.persistedName == name } ?: DEFAULT
    }
}
