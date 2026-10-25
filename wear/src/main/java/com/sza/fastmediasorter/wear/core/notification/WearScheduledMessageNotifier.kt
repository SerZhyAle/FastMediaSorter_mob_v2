package com.sza.fastmediasorter.wear.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.wear.MainActivity
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.writeTo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One message is shown at a time, so the pending open always reuses this slot. */
private const val REQUEST_CODE = 4248

private const val CHANNEL_ID = "wear_scheduled_message"

private const val PICTURE_MIME_TYPE = "image/jpeg"

/**
 * S4248: shows a message the phone's schedule sent as this watch's own notification.
 *
 * The watch never opens itself (S1961): with a picture, a tap opens it full screen through the same
 * [WearLaunchTarget] address the other entrances use; without one there is nothing to open, so the
 * notification carries no content intent and a tap only dismisses it.
 */
@Singleton
class WearScheduledMessageNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * False when POST_NOTIFICATIONS is denied: the post would be a silent no-op, and the phone must not
     * record a message as shown when nobody could see it.
     */
    fun notify(title: String, text: String?, imageFile: File?): Boolean {
        if (!canPostNotification()) {
            return false
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wear_scheduled_message_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.notify(WearNotificationIds.SCHEDULED_MESSAGE, build(title, text, imageFile))
        return true
    }

    private fun build(title: String, text: String?, imageFile: File?) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setAutoCancel(true)
            .also { builder -> imageFile?.let { attachPicture(builder, it, text) } }
            .build()

    private fun attachPicture(builder: NotificationCompat.Builder, imageFile: File, text: String?) {
        // An undecodable file still leaves a readable text notification rather than none at all.
        val bitmap = BitmapFactory.decodeFile(imageFile.path) ?: return
        builder.setStyle(
            NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .setSummaryText(text)
        )
        builder.setContentIntent(pendingIntentFor(WearLaunchTarget.File(imageFile.path, PICTURE_MIME_TYPE)))
    }

    /** FLAG_UPDATE_CURRENT: a newer message reuses the slot, and stale extras must not survive it. */
    private fun pendingIntentFor(target: WearLaunchTarget): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .also(target::writeTo)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun canPostNotification(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
