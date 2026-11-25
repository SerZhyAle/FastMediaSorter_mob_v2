package com.sza.fastmediasorter.core.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.JsonObject
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentPrompter
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import com.sza.fastmediasorter.ui.remotecontrol.RemoteCommandConsentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteCommandConsentPrompt(
    val key: Int,
    val title: String,
    val description: String,
    val kind: String,
    val deadlineElapsedMs: Long,
) {
    fun remainingMillis(): Long = (deadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
}

@StringRes
internal fun RemoteRiskClass.externalControlLabel(): Int = when (this) {
    RemoteRiskClass.SAFE -> R.string.external_control_class_safe
    RemoteRiskClass.READS_USER_DATA -> R.string.external_control_class_reads_user_data
    RemoteRiskClass.WRITES_USER_DATA -> R.string.external_control_class_writes_user_data
    RemoteRiskClass.LEAVES_MACHINE -> R.string.external_control_class_leaves_machine
}

/**
 * Rule 10 has one pending answer shared by the notification and the External control screen. The command
 * decider awaits it serially, and the activity that accepted can stay visible until the verb finishes.
 */
@Singleton
class RemoteCommandConsentNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RemoteConsentPrompter {

    private class Pending(val prompt: RemoteCommandConsentPrompt) {
        val answer = CompletableDeferred<Boolean>()
        val ran = CompletableDeferred<Unit>()
    }

    private val pending = AtomicReference<Pending?>(null)
    private val keys = AtomicInteger(0)
    private val visiblePrompt = MutableStateFlow<RemoteCommandConsentPrompt?>(null)
    val prompt = visiblePrompt.asStateFlow()

    override suspend fun ask(controllerName: String, handler: RemoteVerbHandler, args: JsonObject): Boolean {
        val entry = Pending(createPrompt(controllerName, handler, args))
        Timber.d("S4701: consent asked verb=%s", handler.spec.name)
        pending.set(entry)
        var accepted = false
        try {
            if (post(entry.prompt)) {
                visiblePrompt.value = entry.prompt
                accepted = withTimeoutOrNull(entry.prompt.remainingMillis()) { entry.answer.await() } == true
            }
            return accepted
        } finally {
            visiblePrompt.value = null
            if (!accepted) {
                pending.compareAndSet(entry, null)
                entry.ran.complete(Unit)
            }
            NotificationManagerCompat.from(context).cancel(idOf(entry.prompt.key))
        }
    }

    override fun ran() {
        pending.getAndSet(null)?.ran?.complete(Unit)
    }

    suspend fun answer(key: Int, accepted: Boolean) {
        val entry = pending.get()?.takeIf { it.prompt.key == key } ?: return
        if (entry.prompt.remainingMillis() == 0L || !entry.answer.complete(accepted)) return
        if (accepted) withTimeoutOrNull(RUN_WAIT_MS) { entry.ran.await() }
    }

    private fun createPrompt(
        controllerName: String,
        handler: RemoteVerbHandler,
        args: JsonObject,
    ) = RemoteCommandConsentPrompt(
        key = keys.incrementAndGet(),
        title = context.getString(
            R.string.external_control_prompt_title,
            controllerName,
            context.getString(R.string.app_name)
        ),
        description = handler.describe(context, args),
        kind = context.getString(
            R.string.external_control_prompt_kind,
            context.getString(handler.spec.riskClass.externalControlLabel()),
        ),
        deadlineElapsedMs = SystemClock.elapsedRealtime() + RemoteConsentPrompter.CONSENT_WINDOW_MS,
    )

    @SuppressLint("MissingPermission")
    private fun post(prompt: RemoteCommandConsentPrompt): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled() || !channelEnabled()) return false

        val remainingMs = prompt.remainingMillis()
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs + MILLIS_PER_SECOND - 1)
        val text = listOf(
            prompt.description,
            prompt.kind,
            context.getString(R.string.external_control_prompt_countdown, seconds),
        ).joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(prompt.title)
            .setContentText(prompt.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis() + remainingMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(remainingMs)
            .setOngoing(true)
            .addAction(
                NotificationIcons.STATUS_BAR,
                context.getString(R.string.external_control_decline),
                pendingIntent(prompt.key, accept = false)
            )
            .addAction(
                NotificationIcons.STATUS_BAR,
                context.getString(R.string.external_control_allow),
                pendingIntent(prompt.key, accept = true)
            )
            .build()
        return runCatching { manager.notify(idOf(prompt.key), notification) }
            .onFailure { Timber.i(it, "RemoteCommandConsentNotifier: notification suppressed") }
            .isSuccess
    }

    private fun pendingIntent(key: Int, accept: Boolean): PendingIntent {
        val intent = RemoteCommandConsentActivity.intent(context, key, accept)
        // Distinct request codes keep a queued answer bound to the question and the selected action.
        val requestCode = idOf(key) * ACTION_COUNT + if (accept) 1 else 0
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun idOf(key: Int) = NotificationIds.slotIn(NotificationIds.REMOTE_COMMAND_CONSENT, key)

    private fun channelEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.remote_command_consent_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).also(manager::createNotificationChannel)
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private companion object {
        const val CHANNEL_ID = "remote_command_consent"
        const val RUN_WAIT_MS = 10_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val ACTION_COUNT = 2
    }
}
