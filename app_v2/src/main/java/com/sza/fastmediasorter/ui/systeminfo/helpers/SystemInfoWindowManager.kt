package com.sza.fastmediasorter.ui.systeminfo.helpers

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.systeminfo.SystemInfoReport
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.databinding.ActivitySystemInfoBinding
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Keeps report gathering, rendering and export behaviour out of the screen host. */
class SystemInfoWindowManager @Inject constructor(
    private val systemInfoDialogManager: SystemInfoDialogManager,
    @ApplicationContext private val appContext: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private var currentReport: SystemInfoReport? = null

    suspend fun gather(context: Context): SystemInfoReport = systemInfoDialogManager.gather(context)

    fun bind(activity: AppCompatActivity, binding: ActivitySystemInfoBinding) {
        binding.systemInfoToolbar.setUpNavigation(activity)
        binding.systemInfoCopy.setOnClickListener { currentReport?.let(::copyReport) }
        binding.systemInfoShare.setOnClickListener { currentReport?.let { share(activity, it.fullText) } }
        binding.systemInfoSave.setOnClickListener {
            val report = currentReport ?: return@setOnClickListener
            activity.lifecycleScope.launch { saveReport(report) }
        }
    }

    fun render(container: LinearLayout, report: SystemInfoReport) {
        currentReport = report
        container.removeAllViews()
        report.sections.filter { it.fields.isNotEmpty() }.forEach { section ->
            val header = TextView(container.context).apply {
                text = section.title
                isFocusable = true
                isClickable = true
                foreground = ContextCompat.getDrawable(container.context, R.drawable.focus_button_background)
                setPadding(PADDING, PADDING, PADDING, PADDING)
            }
            val fields = LinearLayout(container.context).apply { orientation = LinearLayout.VERTICAL }
            section.fields.forEach { (label, value) ->
                fields.addView(
                    TextView(container.context).apply {
                        text = "$label: $value"
                        isFocusable = true
                        isClickable = true
                        foreground = ContextCompat.getDrawable(container.context, R.drawable.focus_button_background)
                        setPadding(PADDING * 2, PADDING / 2, PADDING, PADDING / 2)
                        setOnLongClickListener {
                            container.context.copyTextToClipboard(label, value)
                            Toast.makeText(container.context, R.string.copy_to_clipboard, Toast.LENGTH_SHORT).show()
                            true
                        }
                    }
                )
            }
            header.setOnClickListener {
                fields.visibility = if (fields.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
            container.addView(header)
            container.addView(fields)
        }
    }

    private fun copyReport(report: SystemInfoReport) = appContext.copyTextToClipboard("System info", report.fullText)

    private fun share(activity: AppCompatActivity, text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        if (activity.packageManager.queryIntentActivitiesCompat(shareIntent, 0).isEmpty()) {
            currentReport?.let(::copyReport)
            Toast.makeText(activity, R.string.export_logs_no_share_target, Toast.LENGTH_LONG).show()
        } else {
            activity.startActivity(
                Intent.createChooser(
                    shareIntent,
                    activity.getString(R.string.share)
                )
            )
        }
    }

    private suspend fun saveReport(report: SystemInfoReport) {
        val name = "fms_system_info_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"
        // The MediaStore insert and the stream write are disk I/O; on the click thread they block the frame.
        try {
            withContext(ioDispatcher) { writeReport(name, report.fullText) }
            val message = appContext.getString(R.string.s0116_toast_saved_to_downloads, name)
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Storage refusals arrive as IOException, SecurityException or IllegalStateException alike.
            e.rethrowIfCancellation()
            Timber.w(e, "SystemInfoWindowManager: saving the report to Downloads failed")
            Toast.makeText(appContext, R.string.system_info_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun writeReport(name: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }
            val resolver = appContext.contentResolver
            // A null here is a refusal, not a success: it must reach the failure toast, not the "saved" one.
            val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
                "MediaStore refused the Downloads entry"
            }
            checkNotNull(resolver.openOutputStream(uri)) { "No output stream for $uri" }
                .use { it.write(text.toByteArray()) }
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name)
                .writeText(text)
        }
    }

    private companion object {
        const val PADDING = 24
    }
}
