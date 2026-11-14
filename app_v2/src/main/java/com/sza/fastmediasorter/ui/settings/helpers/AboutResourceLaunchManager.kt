package com.sza.fastmediasorter.ui.settings.helpers

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.DebugToolsBridge
import com.sza.fastmediasorter.core.logging.LogExportHelper
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.common.support.MediaExchangePortalLinks
import com.sza.fastmediasorter.ui.common.support.SupportDestination
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import com.sza.fastmediasorter.ui.delivery.ExtensionsManagerFragment
import com.sza.fastmediasorter.ui.settings.fragments.OpenSourceLicensesFragment
import com.sza.fastmediasorter.ui.settings.fragments.PermissionsManagementFragment
import com.sza.fastmediasorter.ui.systeminfo.SystemInfoActivity
import com.sza.fastmediasorter.ui.welcome.WelcomeActivity
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

/** Which flavor-gated About facilities this build actually has. */
class AboutResourceAvailability(
    val isCompanionAvailable: Boolean,
    val isExtensionsAvailable: Boolean,
)

/**
 * Binds the About page rows to [AboutResourceCatalog] and runs each row's action.
 *
 * Binding performs no network call (SETTINGS-NAVIGATION 2.1 section 3 rule 4): every remote page is
 * opened only by the user's own tap, in the browser.
 */
class AboutResourceLaunchManager(
    private val fragment: Fragment,
    private val availability: AboutResourceAvailability,
) {

    fun bind(root: View, versionView: TextView) {
        AboutResourceCatalog.resources.forEach { resource ->
            val row = root.findViewById<View>(resource.viewId) ?: return@forEach
            row.isVisible = isAvailable(resource.action)
            row.setOnClickListener { launch(resource.action) }
        }
        bindVersion(versionView)
    }

    private fun isAvailable(action: AboutResourceAction): Boolean = when (action) {
        AboutResourceAction.OpenCompanionGuide, AboutResourceAction.OpenCompanionHome ->
            availability.isCompanionAvailable
        AboutResourceAction.OpenExtensions -> availability.isExtensionsAvailable
        else -> true
    }

    private fun launch(action: AboutResourceAction) {
        when (action) {
            is AboutResourceAction.LocalizedUrl ->
                openUrl(action.forLanguage(LocaleHelper.getLanguage(fragment.requireContext())))
            AboutResourceAction.OpenCompanionGuide -> openUrl(SupportIntentFactory.companionPublishGuideUrl())
            AboutResourceAction.OpenCompanionHome -> openUrl(SupportIntentFactory.companionHomeUrl())
            AboutResourceAction.OpenExchangePortal ->
                MediaExchangePortalLinks.open(fragment.requireActivity(), MediaExchangePortalLinks.Page.START)
            AboutResourceAction.OpenLicenses -> replaceContent(OpenSourceLicensesFragment())
            AboutResourceAction.OpenPermissions -> replaceContent(PermissionsManagementFragment())
            AboutResourceAction.OpenExtensions -> openExtensions()
            AboutResourceAction.OpenSystemInfo ->
                fragment.startActivity(SystemInfoActivity.createIntent(fragment.requireContext()))
            AboutResourceAction.OpenWelcomeTour ->
                fragment.startActivity(Intent(fragment.requireContext(), WelcomeActivity::class.java))
            AboutResourceAction.SendSupportEmail -> openEmailClient()
            AboutResourceAction.ExportDiagnostics -> shareLogs()
        }
    }

    private fun bindVersion(versionView: TextView) {
        val versionInfo = "${BuildConfig.VERSION_NAME} | Build ${BuildConfig.VERSION_CODE} | " +
            SupportIntentFactory.SUPPORT_EMAIL
        // A hardware line under the version lets a screenshot-only bug report still identify the device.
        versionView.text = "$versionInfo\n${buildDeviceSummary()}"
        versionView.setOnClickListener { openEmailClient() }
        versionView.setOnLongClickListener {
            val intent = DebugToolsBridge.maybeCreateDebugMenuIntent(fragment.requireContext())
            if (intent != null) fragment.startActivity(intent) else shareLogs()
            true
        }
    }

    // The activity FragmentManager: these screens are full-window overlays over the whole settings pane.
    private fun replaceContent(target: Fragment) {
        fragment.requireActivity().supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, target)
            .addToBackStack(null)
            .commit()
    }

    private fun openExtensions() {
        fragment.requireActivity().supportFragmentManager
            .beginTransaction()
            .add(android.R.id.content, ExtensionsManagerFragment(), ExtensionsManagerFragment.TAG)
            .addToBackStack(null)
            .commit()
    }

    private fun openUrl(url: String) {
        try {
            fragment.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            // No browser on the device: the toast is the whole recovery; the address is logged for support.
            Timber.w(e, "About: no activity to open $url")
            Toast.makeText(fragment.requireContext(), R.string.settings_no_browser_for_docs, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun shareLogs() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val result = LogExportHelper.exportLogs(fragment.requireActivity())
            if (!fragment.isAdded || fragment.view == null) return@launch
            when (result) {
                is LogExportHelper.ExportResult.NoLogs ->
                    Toast.makeText(fragment.requireContext(), R.string.export_logs_no_files, Toast.LENGTH_SHORT).show()
                is LogExportHelper.ExportResult.Error ->
                    Toast.makeText(fragment.requireContext(), result.message, Toast.LENGTH_LONG).show()
                else -> Unit
            }
        }
    }

    private fun openEmailClient() {
        // The "About .. (mobile)" subject prefix stays so the support mailbox filters keep matching.
        val subject = "About FastMediaSorter (mobile) ${BuildConfig.VERSION_NAME}"
        val intent = Intent.createChooser(
            SupportIntentFactory.build(
                context = fragment.requireContext(),
                destination = SupportDestination.REPORT_PROBLEM,
                emailSubject = subject,
            ),
            fragment.getString(R.string.send_email),
        )
        try {
            fragment.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "About: no email client")
            Toast.makeText(fragment.requireContext(), R.string.no_email_client, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildDeviceSummary(): String {
        val model = Build.MODEL.orFallback("unknown")
        val product = Build.PRODUCT.orFallback(Build.DEVICE.orFallback("unknown"))
        val release = Build.VERSION.RELEASE.orFallback(Build.VERSION.SDK_INT.toString())
        return "$model-$product $release(${Build.VERSION.SDK_INT}) RAM:${roundedTotalRamGb()}G"
    }

    private fun String?.orFallback(fallback: String): String = this?.takeIf { it.isNotBlank() } ?: fallback

    private fun roundedTotalRamGb(): Int {
        val activityManager =
            fragment.requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        // Rounded to the marketed size: the label is for quick human diagnostics, not profiling.
        return (memoryInfo.totalMem / BYTES_PER_GB).roundToInt().coerceAtLeast(1)
    }

    private companion object {
        const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0
    }
}
