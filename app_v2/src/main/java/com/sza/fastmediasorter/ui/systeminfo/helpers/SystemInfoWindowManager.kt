package com.sza.fastmediasorter.ui.systeminfo.helpers

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import android.content.res.Configuration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.systeminfo.SystemInfoReport
import com.sza.fastmediasorter.core.systeminfo.renderSystemInfo
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.databinding.ActivitySystemInfoBinding
import com.sza.fastmediasorter.ui.systeminfo.adapter.SystemInfoAdapter
import com.sza.fastmediasorter.ui.systeminfo.model.SystemInfoListItem
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
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
    private var systemInfoAdapter: SystemInfoAdapter? = null
    private val expandedSections = mutableSetOf<String>()
    private var currentQuery: String = ""

    suspend fun gather(context: Context): SystemInfoReport = systemInfoDialogManager.gather(context)

    fun bind(activity: AppCompatActivity, binding: ActivitySystemInfoBinding) {
        binding.root.applySystemBarInsetPadding()
        binding.systemInfoToolbar.setUpNavigation(activity)
        binding.systemInfoToolbar.setOnMenuItemClickListener { item -> onAction(activity, binding, item.itemId) }

        systemInfoAdapter = SystemInfoAdapter(
            onToggleSection = { title ->
                if (expandedSections.contains(title)) {
                    expandedSections.remove(title)
                } else {
                    expandedSections.add(title)
                }
                updateExpandCollapseIcon(binding)
                filterAndSubmit(currentQuery)
            },
            onCopySection = { title -> onCopySection(title) },
            onCopyField = { label, value -> onCopyField(label, value) },
        )

        val spanCount = if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 2
        val gridLayoutManager = GridLayoutManager(activity, spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val item = systemInfoAdapter?.currentList?.getOrNull(position) ?: return spanCount
                    return when (item) {
                        is SystemInfoListItem.SectionHeader -> if (item.isExpanded) spanCount else 1
                        is SystemInfoListItem.Field -> spanCount
                        is SystemInfoListItem.EmptyState -> spanCount
                    }
                }
            }
        }

        binding.systemInfoRecyclerView.apply {
            layoutManager = gridLayoutManager
            adapter = systemInfoAdapter
        }

        binding.systemInfoSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentQuery = query.orEmpty()
                filterAndSubmit(currentQuery)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText.orEmpty()
                filterAndSubmit(currentQuery)
                return true
            }
        })
    }

    private fun onAction(activity: AppCompatActivity, binding: ActivitySystemInfoBinding, itemId: Int): Boolean {
        val report = currentReport
        when (itemId) {
            R.id.action_system_info_copy -> report?.let(::copyReport)
            R.id.action_system_info_save -> report?.let { activity.lifecycleScope.launch { saveReport(it) } }
            R.id.action_system_info_share -> report?.let { share(activity, it.fullText) }
            R.id.action_system_info_expand_collapse -> toggleExpandCollapseAll(binding)
            else -> return false
        }
        return true
    }

    fun render(binding: ActivitySystemInfoBinding, report: SystemInfoReport) {
        currentReport = report
        expandedSections.clear()
        expandedSections.addAll(report.sections.filter { it.fields.isNotEmpty() }.map { it.title })
        updateExpandCollapseIcon(binding)
        filterAndSubmit(currentQuery)
    }

    private fun toggleExpandCollapseAll(binding: ActivitySystemInfoBinding) {
        val allTitles = currentReport?.sections?.filter { it.fields.isNotEmpty() }?.map { it.title } ?: return
        val allExpanded = expandedSections.containsAll(allTitles)
        if (allExpanded) {
            expandedSections.clear()
        } else {
            expandedSections.addAll(allTitles)
        }
        updateExpandCollapseIcon(binding)
        filterAndSubmit(currentQuery)
    }

    private fun updateExpandCollapseIcon(binding: ActivitySystemInfoBinding) {
        val allTitles = currentReport?.sections?.filter { it.fields.isNotEmpty() }?.map { it.title } ?: return
        val item = binding.systemInfoToolbar.menu.findItem(R.id.action_system_info_expand_collapse) ?: return
        val allExpanded = expandedSections.containsAll(allTitles)
        if (allExpanded) {
            item.setIcon(R.drawable.ic_expand_less)
            item.setTitle(R.string.sysinfo_action_collapse_all)
        } else {
            item.setIcon(R.drawable.ic_expand_more)
            item.setTitle(R.string.sysinfo_action_expand_all)
        }
    }

    private fun filterAndSubmit(query: String) {
        val report = currentReport ?: return
        val items = mutableListOf<SystemInfoListItem>()
        val trimmedQuery = query.trim()
        val isSearch = trimmedQuery.isNotEmpty()

        val validSections = report.sections.filter { it.fields.isNotEmpty() }

        validSections.forEach { section ->
            val matchingFields = if (isSearch) {
                section.fields.filter { (label, value) ->
                    label.contains(trimmedQuery, ignoreCase = true) ||
                        value.contains(trimmedQuery, ignoreCase = true) ||
                        section.title.contains(trimmedQuery, ignoreCase = true)
                }
            } else {
                section.fields
            }

            if (matchingFields.isNotEmpty()) {
                val isExpanded = if (isSearch) true else expandedSections.contains(section.title)
                items.add(
                    SystemInfoListItem.SectionHeader(
                        title = section.title,
                        count = matchingFields.size,
                        isExpanded = isExpanded,
                    )
                )
                if (isExpanded) {
                    matchingFields.forEach { (label, value) ->
                        items.add(
                            SystemInfoListItem.Field(
                                sectionTitle = section.title,
                                label = label,
                                value = value,
                            )
                        )
                    }
                }
            }
        }

        if (items.isEmpty() && isSearch) {
            items.add(
                SystemInfoListItem.EmptyState(
                    message = appContext.getString(R.string.sysinfo_search_empty),
                )
            )
        }

        systemInfoAdapter?.submitList(items)
    }

    private fun onCopySection(title: String) {
        val section = currentReport?.sections?.firstOrNull { it.title == title } ?: return
        val text = listOf(section).renderSystemInfo()
        appContext.copyTextToClipboard("System info $title", text)
        val message = appContext.getString(R.string.sysinfo_toast_section_copied, title)
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun onCopyField(label: String, value: String) {
        val textToCopy = "$label: $value"
        appContext.copyTextToClipboard(label, textToCopy)
        Toast.makeText(appContext, textToCopy, Toast.LENGTH_SHORT).show()
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
        try {
            withContext(ioDispatcher) { writeReport(name, report.fullText) }
            val message = appContext.getString(R.string.s0116_toast_saved_to_downloads, name)
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
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
}
