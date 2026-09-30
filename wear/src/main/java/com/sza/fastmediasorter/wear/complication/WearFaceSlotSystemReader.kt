package com.sza.fastmediasorter.wear.complication

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import com.sza.fastmediasorter.wear.domain.files.WearSendToReachability
import com.sza.fastmediasorter.wear.domain.model.WearFaceSystemItem
import com.sza.fastmediasorter.wear.util.queryIntentActivitiesCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject

/**
 * S3558: the device reads behind the `sys:` face-slot choices. None of them needs a permission: the
 * battery comes from the sticky broadcast, the alarm from [AlarmManager.getNextAlarmClock], and the
 * system screens are only resolved, never started here.
 */
class WearFaceSlotSystemReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** The value text [item] shows; empty for the screen-only items, null when it cannot be read. */
    fun valueText(item: WearFaceSystemItem): String? = when (item) {
        WearFaceSystemItem.BATTERY -> batteryPercent()?.let { "$it%" }
        WearFaceSystemItem.DATE -> LocalDate.now().dayOfMonth.toString()
        WearFaceSystemItem.NEXT_ALARM -> nextAlarmText()
        WearFaceSystemItem.ALARMS, WearFaceSystemItem.TIMER -> ""
    }

    /**
     * An explicit intent for the activity that answers [action], or null when nothing real does.
     *
     * The Wear OS framework stub answers many implicit actions only to say "not available", and the
     * Samsung clock is not known to answer ACTION_SHOW_TIMERS at all (strategic section 7), so the
     * stub counts as no handler - a slot that opens a refusal is worse than an empty one.
     */
    fun handlerIntentFor(action: String): Intent? {
        val probe = Intent(action)
        val activity = context.packageManager.queryIntentActivitiesCompat(probe)
            .mapNotNull { it.activityInfo }
            .firstOrNull { it.packageName != WearSendToReachability.STUB_PACKAGE }
            ?: return null
        return probe
            .setComponent(ComponentName(activity.packageName, activity.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun batteryPercent(): Int? {
        val status: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, EXTRA_ABSENT) ?: EXTRA_ABSENT
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, EXTRA_ABSENT) ?: EXTRA_ABSENT
        return if (level < 0 || scale <= 0) null else level * PERCENT / scale
    }

    /** The device's own 12/24-hour choice, as the system clock shows it; empty when no alarm is set. */
    private fun nextAlarmText(): String {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val next = alarmManager?.nextAlarmClock ?: return ""
        return DateFormat.getTimeFormat(context).format(Date(next.triggerTime))
    }

    private companion object {
        const val EXTRA_ABSENT = -1
        const val PERCENT = 100
    }
}
