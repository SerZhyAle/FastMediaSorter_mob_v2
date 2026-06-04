package com.sza.fastmediasorter.core.debug

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

object DebugNotificationCenter : Application.ActivityLifecycleCallbacks {

    private val registered = AtomicBoolean(false)
    private var resumedActivityRef: WeakReference<Activity>? = null

    fun register(application: Application) {
        if (registered.compareAndSet(false, true)) {
            application.registerActivityLifecycleCallbacks(this)
        }
    }

    fun showError(message: String, throwable: Throwable? = null) {
        show(message, throwable, true)
    }

    fun showWarning(message: String, throwable: Throwable? = null) {
        show(message, throwable, false)
    }

    private fun show(message: String, throwable: Throwable?, isError: Boolean) {
        val activity = resumedActivityRef?.get() ?: return
        activity.runOnUiThread {
            val label = if (isError) {
                activity.getString(R.string.debug_error_prefix)
            } else {
                activity.getString(R.string.debug_warning_prefix)
            }
            val text = "$label $message"
            // The host Activity may use a non-Material theme (e.g. the fullscreen XR diagnostic),
            // under which a Material Snackbar cannot inflate its Button and would crash the app.
            // Try the themed Snackbar first; on ANY failure degrade to a Toast. The ERROR is
            // already in the log, so the log stays the guaranteed lower bound.
            val shown = runCatching { showSnackbar(activity, text, throwable, isError) }.getOrDefault(false)
            if (!shown) {
                runCatching { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun showSnackbar(activity: Activity, text: String, throwable: Throwable?, isError: Boolean): Boolean {
        val root = activity.findViewById<View>(android.R.id.content) ?: return false
        // Inflate the Snackbar under a guaranteed Material-capable theme so its Material Button
        // resolves required attributes regardless of the host Activity theme. The Snackbar still
        // attaches to the host content view; only the inflation context theme is overridden.
        val materialContext = ContextThemeWrapper(activity, com.google.android.material.R.style.Theme_MaterialComponents_DayNight)
        val snackbar = Snackbar.make(materialContext, root, text, Snackbar.LENGTH_LONG)
        val bgColor = if (isError) android.R.color.holo_red_dark else android.R.color.holo_orange_dark
        snackbar.setBackgroundTint(ContextCompat.getColor(activity, bgColor))
        snackbar.setTextColor(ContextCompat.getColor(activity, android.R.color.white))
        snackbar.setAction(R.string.debug_action_copy) {
            val details = if (throwable != null) {
                "$text\n\n${android.util.Log.getStackTraceString(throwable)}"
            } else {
                text
            }
            copyToClipboard(activity, details)
        }
        snackbar.show()
        return true
    }

    private fun copyToClipboard(context: Context, text: String) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("debug_log", text))
        }.onFailure { Timber.w(it, "DebugNotificationCenter copy failed") }
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        val current = resumedActivityRef?.get()
        if (current === activity) {
            resumedActivityRef = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

class UiNotificationTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority >= android.util.Log.ERROR) {
            DebugNotificationCenter.showError(message, t)
        }
    }
}