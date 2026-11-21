package com.sza.fastmediasorter.core.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.JsonObject
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentPrompter
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import com.sza.fastmediasorter.ui.remotecontrol.RemoteCommandConsentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The consent question of REMOTE-CONTROL rule 10 as a notification with Allow and Decline. A notification,
 * because the request arrives on a background control stream and a background app may not open a window.
 * The decider asks one question at a time, so one pending answer is held.
 */
@Singleton
class RemoteCommandConsentNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RemoteConsentPrompter {

    private class Pending(val key: Int) {
        val answer = CompletableDeferred<Boolean>()
        val ran = CompletableDeferred<Unit>()
    }

    private val pending = AtomicReference<Pending?>(null)
    private val keys = AtomicInteger(0)

    override suspend fun ask(controllerName: String, handler: RemoteVerbHandler, args: JsonObject): Boolean {
        val entry = Pending(keys.incrementAndGet())
        Timber.d("S4701: consent asked verb=%s", handler.spec.name)
        pending.set(entry)
        val shown = post(entry.key, controllerName, handler.describe(context, args))
        val accepted = shown &&
            withTimeoutOrNull(RemoteConsentPrompter.CONSENT_WINDOW_MS) { entry.answer.await() } == true
        NotificationManagerCompat.from(context).cancel(idOf(entry.key))
        if (!accepted) {
            pending.compareAndSet(entry, null)
            entry.ran.complete(Unit)
        }
        return accepted
    }

    override fun ran() {
        pending.getAndSet(null)?.ran?.complete(Unit)
    }

    /**
     * The person's answer from [RemoteCommandConsentActivity]. An acceptance returns once the verb has run,
     * so the window that carries the tap is still up when the verb starts its work.
     */
    suspend fun answer(key: Int, accepted: Boolean) {
        val entry = pending.get()?.takeIf { it.key == key } ?: return
        entry.answer.complete(accepted)
        if (accepted) withTimeoutOrNull(RUN_WAIT_MS) { entry.ran.await() }
    }

    // S3155 pattern: a POST_NOTIFICATIONS check above and runCatching for a revocation between it and the post.
    @SuppressLint("MissingPermission")
    private fun post(key: Int, controllerName: String, text: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Timber.i("RemoteCommandConsentNotifier: notifications not allowed, the request is declined")
            return false
        }
        ensureChannel()
        val allow = NotificationCompat.Action.Builder(
            NotificationIcons.STATUS_BAR,
            context.getString(R.string.remote_command_consent_allow),
            pendingIntent(key, accept = true),
        ).build()
        val decline = NotificationCompat.Action.Builder(
            NotificationIcons.STATUS_BAR,
            context.getString(R.string.remote_command_consent_decline),
            pendingIntent(key, accept = false),
        ).build()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.remote_command_consent_title, controllerName))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(RemoteConsentPrompter.CONSENT_WINDOW_MS)
            .setOngoing(true)
            .addAction(allow)
            .addAction(decline)
        return runCatching { manager.notify(idOf(key), builder.build()) }
            .onFailure { Timber.i(it, "RemoteCommandConsentNotifier: notification suppressed") }
            .isSuccess
    }

    private fun pendingIntent(key: Int, accept: Boolean): PendingIntent {
        val intent = RemoteCommandConsentActivity.intent(context, key, accept)
        // One request code per question and action, so Decline never reuses Allow's intent.
        val requestCode = idOf(key) * 2 + if (accept) 1 else 0
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun idOf(key: Int) = NotificationIds.slotIn(NotificationIds.REMOTE_COMMAND_CONSENT, key)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.remote_command_consent_channel),
                // HIGH: the controller waits for the answer and gives up after 60 s of confirmation.
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    private companion object {
        const val CHANNEL_ID = "remote_command_consent"
        const val RUN_WAIT_MS = 10_000L
    }
}
