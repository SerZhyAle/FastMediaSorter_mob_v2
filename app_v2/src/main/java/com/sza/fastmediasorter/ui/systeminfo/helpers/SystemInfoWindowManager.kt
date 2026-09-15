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
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard
import com.sza.fastmediasorter.core.systeminfo.SystemInfoReport
import com.sza.fastmediasorter.databinding.ActivitySystemInfoBinding
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Keeps report gathering, rendering and export behaviour out of the screen host. */
class SystemInfoWindowManager @Inject constructor(
    private val systemInfoDialogManager: SystemInfoDialogManager,
    @ApplicationContext private val appContext: Context,
) {
    private var currentReport: SystemInfoReport? = null

    suspend fun gather(context: Context): SystemInfoReport = systemInfoDialogManager.gather(context)

    fun bind(activity: AppCompatActivity, binding: ActivitySystemInfoBinding) {
        binding.systemInfoToolbar.setNavigationOnClickListener { activity.finish() }
        binding.systemInfoCopy.setOnClickListener { currentReport?.let(::copyReport) }
        binding.systemInfoShare.setOnClickListener { currentReport?.let { share(activity, it.fullText) } }
        binding.systemInfoSave.setOnClickListener { currentReport?.let(::saveReport) }
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

    private fun saveReport(report: SystemInfoReport) {
        val name = "fms_system_info_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                }
                appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                    appContext.contentResolver.openOutputStream(uri)?.use { it.write(report.fullText.toByteArray()) }
                }
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name)
                    .writeText(report.fullText)
            }
        }.onSuccess { Toast.makeText(appContext, R.string.save, Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val PADDING = 24
    }
}
