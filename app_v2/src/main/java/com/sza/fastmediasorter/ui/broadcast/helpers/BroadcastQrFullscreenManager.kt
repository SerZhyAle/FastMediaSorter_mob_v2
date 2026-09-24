package com.sza.fastmediasorter.ui.broadcast.helpers

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.WriterException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.companionimport.qr.QrCodeEncoder
import com.sza.fastmediasorter.util.showBoundTo
import timber.log.Timber
import kotlin.math.min

/**
 * The broadcast QR code across the whole shorter side of the display, so a viewer can scan it from
 * across the room. One tap, or Back, returns to the control screen.
 */
class BroadcastQrFullscreenManager {

    private var dialog: Dialog? = null
    private var imageView: ImageView? = null
    private var shownPayload: String? = null

    fun show(activity: AppCompatActivity, payload: String) {
        if (dialog?.isShowing == true) return
        Timber.d("S3518: broadcast QR opened full screen")
        val image = ImageView(activity).apply {
            // The quiet zone and the dark modules need a light field in every theme to scan.
            setBackgroundColor(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = activity.getString(R.string.broadcast_share_qr_fullscreen_cd)
            isFocusable = true
            setOnClickListener { dismiss() }
        }
        val fullscreen = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen).apply {
            setContentView(
                image,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            window?.let { window ->
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                }
            }
            setOnDismissListener { forget() }
        }
        imageView = image
        dialog = fullscreen
        if (!render(activity, payload)) return
        fullscreen.showBoundTo(activity)
        image.requestFocus()
    }

    /** A mode switch re-issues the link, so an open code follows it instead of showing the dead one. */
    fun update(activity: AppCompatActivity, payload: String) {
        if (dialog?.isShowing == true && payload != shownPayload) render(activity, payload)
    }

    fun dismiss() {
        dialog?.dismiss()
        forget()
    }

    private fun forget() {
        dialog = null
        imageView = null
        shownPayload = null
    }

    private fun render(activity: AppCompatActivity, payload: String): Boolean {
        val image = imageView ?: return false
        val metrics = activity.resources.displayMetrics
        val bitmap = encodeOrNull(payload, min(metrics.widthPixels, metrics.heightPixels))
        if (bitmap == null) {
            dismiss()
        } else {
            image.setImageBitmap(bitmap)
            shownPayload = payload
        }
        return bitmap != null
    }

    private fun encodeOrNull(payload: String, sizePx: Int): Bitmap? = try {
        QrCodeEncoder.encode(payload, sizePx)
    } catch (e: WriterException) {
        Timber.w(e, "Full-screen broadcast QR could not be encoded")
        null
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Full-screen broadcast QR got an unusable payload")
        null
    }
}
