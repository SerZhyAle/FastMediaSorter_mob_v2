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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.power.WearPowerStateObserver
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val SECONDS_CADENCE_MS = 1000L
private const val NORMAL_CADENCE_MS = 30000L
private const val AUTO_FADE_TIMEOUT_MS = 60000L
private const val BURN_IN_SHIFT_INTERVAL_MS = 60000L
private const val FADE_ANIMATION_MS = 500
private const val NORMAL_ALPHA = 1.0f
private const val DIMMED_ALPHA = 0.35f
private const val MAX_BURN_IN_OFFSET_DP = 4
private const val BATTERY_PERCENT_SCALE = 100
private const val BATTERY_LOW_THRESHOLD = 15

private val CHIP_PADDING_H = 6.dp
private val CHIP_PADDING_V = 2.dp
private val CHIP_CORNER_RADIUS = 4.dp
private val STATUS_ROW_SPACING = 8.dp
private val STATUS_ICON_SIZE = 12.dp
private val CHIP_ICON_SIZE = 10.dp
private val TIME_FONT_SIZE = 34.sp
private val DATE_FONT_SIZE = 12.sp
private val STATUS_FONT_SIZE = 11.sp

/**
 * Centered digital clock with date, battery and phone connection status for dimmed watch screen (S3256).
 *
 * Honors [WearPreferencesRepository.dimClockSecondsVisible] cadence (1s vs 30s),
 * applies burn-in shift every minute to protect OLED screens, auto-fades after 1 minute of idle,
 * and reads battery and phone connectivity via [WearPowerStateObserver] and [WearSystemInfoDataSource].
 */
@Composable
fun WearDimClock(
    preferencesRepository: WearPreferencesRepository,
    powerStateObserver: WearPowerStateObserver? = null,
    systemInfoDataSource: WearSystemInfoDataSource? = null,
    modifier: Modifier = Modifier
) {
    val secondsVisible by preferencesRepository.dimClockSecondsVisible.collectAsStateWithLifecycle(initialValue = false)
    val unitSystem = LocalWearUnitSystem.current
    val dateTimeFormatter = LocalWearDateTimeFormatter.current
    val context = LocalContext.current

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val displayStartTime = remember { System.currentTimeMillis() }

    // Cadence loop: 1s if seconds are visible, 30s otherwise
    LaunchedEffect(secondsVisible) {
        val cadence = if (secondsVisible) SECONDS_CADENCE_MS else NORMAL_CADENCE_MS
        while (isActive) {
            nowMillis = System.currentTimeMillis()
            delay(cadence)
        }
    }

    // Burn-in shift state: periodic small offset (max 4dp in x and y)
    var burnInOffset by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(Unit) {
        val density = context.resources.displayMetrics.density
        val maxPx = (MAX_BURN_IN_OFFSET_DP * density).roundToInt()
        var step = 0
        while (isActive) {
            delay(BURN_IN_SHIFT_INTERVAL_MS)
            step = (step + 1) % 4
            burnInOffset = when (step) {
                0 -> IntOffset(0, 0)
                1 -> IntOffset(maxPx, 0)
                2 -> IntOffset(maxPx, maxPx)
                else -> IntOffset(0, maxPx)
            }
        }
    }

    // Auto-fade after 1 minute idle
    val isFaded = (nowMillis - displayStartTime) >= AUTO_FADE_TIMEOUT_MS
    val targetAlpha = if (isFaded) DIMMED_ALPHA else NORMAL_ALPHA
    val alphaAnim by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(FADE_ANIMATION_MS),
        label = "wearDimClockAlpha"
    )

    // Battery observation: charge percent & charging indicator
    var batteryPercent by remember { mutableIntStateOf(BATTERY_PERCENT_SCALE) }
    var isCharging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    val rawLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (rawLevel >= 0 && scale > 0) {
                        batteryPercent = rawLevel * BATTERY_PERCENT_SCALE / scale
                    }
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)
        sticky?.let {
            val rawLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (rawLevel >= 0 && scale > 0) {
                batteryPercent = rawLevel * BATTERY_PERCENT_SCALE / scale
            }
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // Phone connection observation
    var isPhoneConnected by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(systemInfoDataSource) {
        if (systemInfoDataSource != null) {
            val nodes = systemInfoDataSource.connectedNodes()
            isPhoneConnected = !nodes.isNullOrEmpty()
        }
    }

    val timeText = remember(nowMillis, unitSystem, secondsVisible) {
        dateTimeFormatter.formatTime(nowMillis, unitSystem, withSeconds = secondsVisible)
    }
    val dateText = remember(nowMillis, unitSystem) {
        val baseDate = dateTimeFormatter.formatDate(nowMillis, unitSystem)
        val weekday = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(nowMillis))
        "$baseDate, $weekday"
    }

    val statusContentDescription = stringResource(
        R.string.dim_clock_status_cd,
        timeText,
        batteryPercent
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { burnInOffset }
            .alpha(alphaAnim)
            .semantics { contentDescription = statusContentDescription },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Time text
            Text(
                text = timeText,
                fontSize = TIME_FONT_SIZE,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )

            // Date & Weekday text
            Text(
                text = dateText,
                fontSize = DATE_FONT_SIZE,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )

            // Status row: Battery + Connection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(STATUS_ROW_SPACING)
            ) {
                // Battery chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(CHIP_CORNER_RADIUS))
                        .background(Color(0x33FFFFFF))
                        .padding(horizontal = CHIP_PADDING_H, vertical = CHIP_PADDING_V)
                ) {
                    if (isCharging) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color.Yellow,
                            modifier = Modifier.size(STATUS_ICON_SIZE)
                        )
                    }
                    Text(
                        text = "$batteryPercent%",
                        fontSize = STATUS_FONT_SIZE,
                        color = if (batteryPercent <= BATTERY_LOW_THRESHOLD && !isCharging) {
                            Color(0xFFFF5252)
                        } else {
                            Color.White
                        }
                    )
                }

                // Phone connection chip
                val phoneConnected = isPhoneConnected
                if (phoneConnected != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(CHIP_CORNER_RADIUS))
                            .background(Color(0x33FFFFFF))
                            .padding(horizontal = CHIP_PADDING_H, vertical = CHIP_PADDING_V)
                    ) {
                        Icon(
                            imageVector = if (phoneConnected) Icons.Default.PhoneAndroid else Icons.Default.PhoneDisabled,
                            contentDescription = null,
                            tint = if (phoneConnected) Color(0xFF81C784) else Color(0xFFE57373),
                            modifier = Modifier.size(CHIP_ICON_SIZE)
                        )
                    }
                }
            }
        }
    }
}
