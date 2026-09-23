package com.sza.fastmediasorter.wear.ui.common.dimclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.power.WearPowerStateObserver
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private const val SECONDS_CADENCE_MS = 1000L
private const val NORMAL_CADENCE_MS = 30000L
private const val AUTO_FADE_TIMEOUT_MS = 60000L
private const val BURN_IN_SHIFT_INTERVAL_MS = 60000L
private const val BURN_IN_STEP_COUNT = 4
private const val FADE_ANIMATION_MS = 500
private const val NORMAL_ALPHA = 1.0f
private const val DIMMED_ALPHA = 0.35f
private const val MAX_BURN_IN_OFFSET_DP = 4
private const val BATTERY_PERCENT_SCALE = 100
private const val BATTERY_LOW_THRESHOLD = 15
private const val UNKNOWN_BATTERY_FIELD = -1

private val CHIP_PADDING_H = 6.dp
private val CHIP_PADDING_V = 2.dp
private val CHIP_CORNER_RADIUS = 4.dp
private val STATUS_ROW_SPACING = 8.dp
private val STATUS_ICON_SIZE = 12.dp
private val CHIP_ICON_SIZE = 10.dp

// Raw ARGB ints named so MagicNumber's ignoreConstantDeclaration exempts them; Color(Long/Int) is not
// itself a const expression, so the wrapped val below carries no literal of its own to flag.
private const val CHIP_BACKGROUND_ARGB = 0x33FFFFFF
private const val BATTERY_LOW_ARGB = 0xFFFF5252.toInt()
private const val PHONE_CONNECTED_ARGB = 0xFF81C784.toInt()
private const val PHONE_DISCONNECTED_ARGB = 0xFFE57373.toInt()

private val CHIP_BACKGROUND = Color(CHIP_BACKGROUND_ARGB)
private val BATTERY_LOW_COLOR = Color(BATTERY_LOW_ARGB)
private val PHONE_CONNECTED_COLOR = Color(PHONE_CONNECTED_ARGB)
private val PHONE_DISCONNECTED_COLOR = Color(PHONE_DISCONNECTED_ARGB)

/** Battery charge as last read from the sticky/live [Intent.ACTION_BATTERY_CHANGED] broadcast. */
private data class DimClockBatteryState(val percent: Int, val isCharging: Boolean)

/**
 * Centered digital clock with date, battery and phone connection status for dimmed watch screen (S3256).
 *
 * Honors [WearPreferencesRepository.dimClockSecondsVisible] cadence (1s vs 30s),
 * applies burn-in shift every minute to protect OLED screens, auto-fades after 1 minute of idle,
 * and reads battery and phone connectivity via [WearPowerStateObserver] and [WearSystemInfoDataSource].
 * [lastUserActivityMillis] restarts the idle window - the dim sheet forwards its taps (S3361).
 *
 * [powerStateObserver] is currently unused: the caller (`WearDimOverlay`) already wires the singleton
 * in, but this screen still reads charge/charging state from its own broadcast receiver below -
 * [WearPowerStateObserver] exposes only the coarse power-saving policy level, not a raw percent, so it
 * is not a substitute yet. Kept as a parameter rather than dropped so a future charge-state migration
 * is a one-file change here instead of a second call-site edit.
 */
@Composable
@Suppress("UnusedParameter")
fun WearDimClock(
    preferencesRepository: WearPreferencesRepository,
    powerStateObserver: WearPowerStateObserver? = null,
    systemInfoDataSource: WearSystemInfoDataSource? = null,
    lastUserActivityMillis: Long = 0L,
    modifier: Modifier = Modifier
) {
    val secondsVisible by preferencesRepository.dimClockSecondsVisible.collectAsStateWithLifecycle(initialValue = false)
    val unitSystem = LocalWearUnitSystem.current
    val dateTimeFormatter = LocalWearDateTimeFormatter.current
    val context = LocalContext.current

    val nowMillis = rememberDimClockNowMillis(secondsVisible)
    val displayStartTime = remember { System.currentTimeMillis() }
    val burnInOffset = rememberDimClockBurnInOffset(context)
    val alphaAnim = rememberDimClockAlpha(nowMillis, displayStartTime, lastUserActivityMillis)
    val batteryState = rememberDimClockBatteryState(context)
    val isPhoneConnected = rememberDimClockPhoneConnected(systemInfoDataSource)

    val timeText = remember(nowMillis, unitSystem, secondsVisible) {
        dateTimeFormatter.formatTime(nowMillis, unitSystem, withSeconds = secondsVisible)
    }
    val dateText = remember(nowMillis, unitSystem) {
        val baseDate = dateTimeFormatter.formatDate(nowMillis, unitSystem)
        val weekday = dateTimeFormatter.formatWeekday(nowMillis)
        "$baseDate, $weekday"
    }

    val statusContentDescription = stringResource(
        R.string.dim_clock_status_cd,
        timeText,
        batteryState.percent
    )

    DimClockContent(
        modifier = modifier,
        burnInOffset = burnInOffset,
        alpha = alphaAnim,
        frame = DimClockFrame(
            timeText = timeText,
            dateText = dateText,
            statusContentDescription = statusContentDescription,
            batteryState = batteryState,
            phoneConnected = isPhoneConnected
        )
    )
}

/** Cadence loop: 1s if seconds are visible, 30s otherwise. */
@Composable
private fun rememberDimClockNowMillis(secondsVisible: Boolean): Long {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(secondsVisible) {
        val cadence = if (secondsVisible) SECONDS_CADENCE_MS else NORMAL_CADENCE_MS
        while (isActive) {
            nowMillis = System.currentTimeMillis()
            delay(cadence)
        }
    }
    return nowMillis
}

/** Burn-in shift state: periodic small offset (max [MAX_BURN_IN_OFFSET_DP] dp in x and y). */
@Composable
private fun rememberDimClockBurnInOffset(context: Context): IntOffset {
    var burnInOffset by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(Unit) {
        val density = context.resources.displayMetrics.density
        val maxPx = (MAX_BURN_IN_OFFSET_DP * density).roundToInt()
        var step = 0
        while (isActive) {
            delay(BURN_IN_SHIFT_INTERVAL_MS)
            step = (step + 1) % BURN_IN_STEP_COUNT
            burnInOffset = when (step) {
                0 -> IntOffset(0, 0)
                1 -> IntOffset(maxPx, 0)
                2 -> IntOffset(maxPx, maxPx)
                else -> IntOffset(0, maxPx)
            }
        }
    }
    return burnInOffset
}

/** Auto-fade after [AUTO_FADE_TIMEOUT_MS] of idle; a tap restarts the idle window. */
@Composable
private fun rememberDimClockAlpha(
    nowMillis: Long,
    displayStartTime: Long,
    lastUserActivityMillis: Long
): Float {
    val activityBase = maxOf(displayStartTime, lastUserActivityMillis)
    val isFaded = (nowMillis - activityBase) >= AUTO_FADE_TIMEOUT_MS
    val targetAlpha = if (isFaded) DIMMED_ALPHA else NORMAL_ALPHA
    val alphaAnim by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(FADE_ANIMATION_MS),
        label = "wearDimClockAlpha"
    )
    return alphaAnim
}

/** Battery observation: charge percent & charging indicator, read from the sticky/live intent alike. */
@Composable
private fun rememberDimClockBatteryState(context: Context): DimClockBatteryState {
    var batteryPercent by remember { mutableIntStateOf(BATTERY_PERCENT_SCALE) }
    var isCharging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val onPercent: (Int) -> Unit = { batteryPercent = it }
        val onCharging: (Boolean) -> Unit = { isCharging = it }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let { applyBatteryIntent(it, onPercent, onCharging) }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)
        sticky?.let { applyBatteryIntent(it, onPercent, onCharging) }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return DimClockBatteryState(batteryPercent, isCharging)
}

private fun applyBatteryIntent(intent: Intent, onPercent: (Int) -> Unit, onCharging: (Boolean) -> Unit) {
    val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, UNKNOWN_BATTERY_FIELD)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN_BATTERY_FIELD)
    if (rawLevel >= 0 && scale > 0) {
        onPercent(rawLevel * BATTERY_PERCENT_SCALE / scale)
    }
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, UNKNOWN_BATTERY_FIELD)
    onCharging(status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
}

/** Phone connection observation via the Data Layer node list. */
@Composable
private fun rememberDimClockPhoneConnected(systemInfoDataSource: WearSystemInfoDataSource?): Boolean? {
    var isPhoneConnected by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(systemInfoDataSource) {
        if (systemInfoDataSource != null) {
            val nodes = systemInfoDataSource.connectedNodes()
            isPhoneConnected = !nodes.isNullOrEmpty()
        }
    }
    return isPhoneConnected
}

/** Everything [DimClockContent] renders, bundled so the composable stays under the parameter-count gate. */
private data class DimClockFrame(
    val timeText: String,
    val dateText: String,
    val statusContentDescription: String,
    val batteryState: DimClockBatteryState,
    val phoneConnected: Boolean?
)

@Composable
private fun DimClockContent(
    modifier: Modifier,
    burnInOffset: IntOffset,
    alpha: Float,
    frame: DimClockFrame
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { burnInOffset }
            .alpha(alpha)
            .semantics { contentDescription = frame.statusContentDescription },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Time text
            Text(
                text = frame.timeText,
                style = MaterialTheme.typography.display2,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )

            // Date & Weekday text
            Text(
                text = frame.dateText,
                style = MaterialTheme.typography.caption2,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )

            // Status row: Battery + Connection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(STATUS_ROW_SPACING)
            ) {
                BatteryChip(frame.batteryState)
                val phoneConnected = frame.phoneConnected
                if (phoneConnected != null) {
                    PhoneConnectionChip(connected = phoneConnected)
                }
            }
        }
    }
}

@Composable
private fun BatteryChip(batteryState: DimClockBatteryState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(CHIP_CORNER_RADIUS))
            .background(CHIP_BACKGROUND)
            .padding(horizontal = CHIP_PADDING_H, vertical = CHIP_PADDING_V)
    ) {
        if (batteryState.isCharging) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = Color.Yellow,
                modifier = Modifier.size(STATUS_ICON_SIZE)
            )
        }
        val percentColor = if (batteryState.percent <= BATTERY_LOW_THRESHOLD && !batteryState.isCharging) {
            BATTERY_LOW_COLOR
        } else {
            Color.White
        }
        Text(
            text = "${batteryState.percent}%",
            style = MaterialTheme.typography.caption3,
            color = percentColor
        )
    }
}

@Composable
private fun PhoneConnectionChip(connected: Boolean) {
    val icon = if (connected) Icons.Default.PhoneAndroid else Icons.Default.PhoneDisabled
    val tint = if (connected) PHONE_CONNECTED_COLOR else PHONE_DISCONNECTED_COLOR
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(CHIP_CORNER_RADIUS))
            .background(CHIP_BACKGROUND)
            .padding(horizontal = CHIP_PADDING_H, vertical = CHIP_PADDING_V)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(CHIP_ICON_SIZE)
        )
    }
}
