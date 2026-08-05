package com.sza.fastmediasorter.core.screencapture.gesture

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import timber.log.Timber
import javax.inject.Inject

/**
 * S1038: runs the launch/intent edge-gesture actions (LAUNCH picker group). Every launch is a guarded
 * startActivity that degrades to a safe no-op (Timber.w) when no app resolves the intent - the dispatcher
 * runs inside a Service, so a missing target must never crash it. [handle] returns false for actions it
 * does not own so the dispatcher can try the next handler; [payload] carries the per-slot URL for
 * OPEN_URL (S1038 phase 02) and is empty for the others.
 */
class LaunchActionHandler @Inject constructor() {

    /** Returns true when [action] is a launch/intent action this handler owns (started or degraded). */
    fun handle(context: Context, action: ScreenshotGestureAction, payload: String): Boolean {
        return when (action) {
        ScreenshotGestureAction.OPEN_ASSISTANT -> {
            startGuarded(context, Intent(Intent.ACTION_ASSIST), "assistant")
            true
        }
        ScreenshotGestureAction.OPEN_GEMINI -> {
            launchPackage(context, GEMINI_PACKAGE, "Gemini")
            true
        }
        ScreenshotGestureAction.CREATE_KEEP_NOTE -> {
            createNote(context)
            true
        }
        ScreenshotGestureAction.OPEN_URL -> {
            openUrl(context, payload)
            true
        }
        ScreenshotGestureAction.SET_ALARM -> {
            startGuarded(context, Intent(AlarmClock.ACTION_SET_ALARM), "alarm")
            true
        }
        ScreenshotGestureAction.SET_TIMER -> {
            startGuarded(context, Intent(AlarmClock.ACTION_SET_TIMER), "timer")
            true
        }
        ScreenshotGestureAction.NEW_CALENDAR_EVENT -> {
            startGuarded(
                context,
                Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI),
                "calendar",
            )
            true
        }
        else -> false
        }
    }

    private fun openUrl(context: Context, url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            Timber.w("LaunchActionHandler: OPEN_URL has no address configured, ignoring")
            return
        }
        // Accept a bare host (non-technical users omit the scheme): assume https when none is present.
        val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        startGuarded(context, Intent(Intent.ACTION_VIEW, Uri.parse(normalized)), "url")
    }

    private fun createNote(context: Context) {
        // API 33+ exposes a standard note-creation intent handled by the notes-role app; below that, or
        // when nothing handles it, fall back to simply opening Keep (there is no public Keep new-note intent).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val created = startGuarded(context, Intent(Intent.ACTION_CREATE_NOTE), "note", logFailure = false)
            if (created) return
        }
        launchPackage(context, KEEP_PACKAGE, "Keep")
    }

    private fun launchPackage(context: Context, packageName: String, label: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Timber.w("LaunchActionHandler: %s app (%s) not installed, ignoring", label, packageName)
            return
        }
        startGuarded(context, intent, label)
    }

    // Returns true when the activity actually started, so createNote can fall back on a failed launch.
    private fun startGuarded(
        context: Context,
        intent: Intent,
        label: String,
        logFailure: Boolean = true,
    ): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrElse {
        if (logFailure) Timber.w(it, "LaunchActionHandler: no app to handle %s action", label)
        false
    }

    private companion object {
        // Standalone Google Gemini app (formerly Bard); degrades to a no-op when not installed.
        private const val GEMINI_PACKAGE = "com.google.android.apps.bard"
        private const val KEEP_PACKAGE = "com.google.android.keep"
    }
}
