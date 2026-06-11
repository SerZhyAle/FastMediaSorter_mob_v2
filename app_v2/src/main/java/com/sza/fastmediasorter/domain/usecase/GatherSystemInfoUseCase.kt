package com.sza.fastmediasorter.domain.usecase

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.LocaleList
import android.os.UserManager
import android.provider.Settings
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.annotation.StringRes
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.systeminfo.ExtendedDiagnosticsContributor
import com.sza.fastmediasorter.core.systeminfo.ExtendedDiagnosticsSection
import com.sza.fastmediasorter.core.systeminfo.SystemInfoBenchmark
import com.sza.fastmediasorter.core.systeminfo.SystemInfoAccessClassifier
import com.sza.fastmediasorter.core.systeminfo.SystemInfoReport
import com.sza.fastmediasorter.core.systeminfo.SystemInfoSection
import com.sza.fastmediasorter.core.systeminfo.renderSystemInfo
import com.sza.fastmediasorter.core.systeminfo.toSystemInfoSections
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * Collects easy-to-obtain device, OS, application, memory, storage, display, locale and time
 * facts into one grouped, human-readable string for the Settings "System info" dialog.
 *
 * Section and field labels are localized (S0337). Each system source is read defensively: a
 * failing source yields "unknown" instead of throwing, so the dialog always shows a complete
 * report. Called off the main thread by the dialog opener.
 */
class GatherSystemInfoUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extendedDiagnosticsContributors: Set<@JvmSuppressWildcards ExtendedDiagnosticsContributor>,
) {

    operator fun invoke(): SystemInfoReport {
        val base = buildSections()
        // Collect flavor-contributed sections defensively: a throwing contributor degrades the
        // extended part to empty, never breaking the base report (matches safe/safeList style).
        val extended: List<ExtendedDiagnosticsSection> = try {
            extendedDiagnosticsContributors.flatMap { it.sections() }
        } catch (e: Exception) {
            Timber.w(e, "System info: failed to collect extended diagnostics")
            emptyList()
        }
        val maskedText = (base + extended.toSystemInfoSections(reveal = false)).renderSystemInfo()
        val fullText = (base + extended.toSystemInfoSections(reveal = true)).renderSystemInfo()
        val hasSensitive = extended.any { section -> section.fields.any { it.sensitive } }
        return SystemInfoReport(maskedText = maskedText, fullText = fullText, hasSensitive = hasSensitive)
    }

    private fun buildSections(): List<SystemInfoSection> = listOf(
        SystemInfoSection(
            label(R.string.sysinfo_section_device),
            listOf(
                label(R.string.sysinfo_field_manufacturer) to safe { sanitize(Build.MANUFACTURER) },
                label(R.string.sysinfo_field_model) to safe { sanitize(Build.MODEL) },
                label(R.string.sysinfo_field_product) to safe { sanitize(Build.PRODUCT, sanitize(Build.DEVICE)) },
            ),
        ),
        SystemInfoSection(
            label(R.string.sysinfo_section_user),
            listOf(
                label(R.string.sysinfo_field_user_name) to safe { userName() },
            ),
        ),
        SystemInfoSection(
            label(R.string.sysinfo_section_os),
            listOf(
                label(R.string.sysinfo_field_android_version) to safe { sanitize(Build.VERSION.RELEASE, Build.VERSION.SDK_INT.toString()) },
                label(R.string.sysinfo_field_api_level) to safe { Build.VERSION.SDK_INT.toString() },
            ),
        ),
        SystemInfoSection(
            label(R.string.sysinfo_section_app),
            listOf(
                label(R.string.sysinfo_field_app_version) to safe { BuildConfig.VERSION_NAME },
                label(R.string.sysinfo_field_build) to safe { BuildConfig.VERSION_CODE.toString() },
                label(R.string.sysinfo_field_flavor) to safe { BuildConfig.FLAVOR },
            ),
        ),
        SystemInfoSection(label(R.string.sysinfo_section_hardware), hardwareFields()),
        SystemInfoSection(label(R.string.sysinfo_section_battery), batteryFields()),
        SystemInfoSection(label(R.string.sysinfo_section_memory), memoryFields()),
        SystemInfoSection(label(R.string.sysinfo_section_storage), storageFields()),
        SystemInfoSection(label(R.string.sysinfo_section_network), networkFields()),
        SystemInfoSection(label(R.string.sysinfo_section_display), displayFields()),
        SystemInfoSection(
            label(R.string.sysinfo_section_locale),
            listOf(
                label(R.string.sysinfo_field_locale_current) to safe { Locale.getDefault().toLanguageTag() },
            ),
        ),
        SystemInfoSection(
            label(R.string.sysinfo_section_time),
            listOf(
                label(R.string.sysinfo_field_time_current) to safe { currentTime() },
                label(R.string.sysinfo_field_timezone) to safe { timezone() },
            ),
        ),
        SystemInfoSection(label(R.string.sysinfo_section_system), systemFields()),
        SystemInfoSection(label(R.string.sysinfo_section_benchmark), benchmarkFields()),
    )

    private fun benchmarkFields(): List<Pair<String, String>> = safeList {
        val mem = SystemInfoBenchmark.measureMemoryThroughputMbps()
        val (write, read) = SystemInfoBenchmark.measureStorageThroughputMbps(context.cacheDir)
        listOf(
            label(R.string.sysinfo_field_bench_memory) to formatMbps(mem),
            label(R.string.sysinfo_field_bench_storage_write) to formatMbps(write),
            label(R.string.sysinfo_field_bench_storage_read) to formatMbps(read),
        )
    } ?: listOf(label(R.string.sysinfo_field_bench_memory) to UNKNOWN)

    private fun formatMbps(value: Double): String =
        if (value < 0) UNKNOWN else String.format(Locale.US, "%.0f MB/s (%s)", value, "~")

    private fun systemFields(): List<Pair<String, String>> = safeList {
        listOf(
            label(R.string.sysinfo_field_locales) to safe { localesList() },
            label(R.string.sysinfo_field_hour_format) to safe { if (DateFormat.is24HourFormat(context)) "24h" else "12h" },
            label(R.string.sysinfo_field_boot_count) to safe { bootCount() },
            label(R.string.sysinfo_field_security_patch) to safe { sanitize(Build.VERSION.SECURITY_PATCH) },
            label(R.string.sysinfo_field_fingerprint) to safe { sanitize(Build.FINGERPRINT) },
            label(R.string.sysinfo_field_tags) to safe { sanitize(Build.TAGS) },
            label(R.string.sysinfo_field_build_type) to safe { sanitize(Build.TYPE) },
            label(R.string.sysinfo_field_installer) to safe { installerName() },
            label(R.string.sysinfo_field_first_install) to safe { formatTime(packageInfo().firstInstallTime) },
            label(R.string.sysinfo_field_last_update) to safe { formatTime(packageInfo().lastUpdateTime) },
            label(R.string.sysinfo_field_device_name) to safe { deviceName() },
            label(R.string.sysinfo_field_input_method) to safe { inputMethodId() },
        )
    } ?: listOf(label(R.string.sysinfo_field_fingerprint) to UNKNOWN)

    private fun localesList(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val list = LocaleList.getDefault()
            (0 until list.size()).joinToString(", ") { list.get(it).toLanguageTag() }
        } else {
            Locale.getDefault().toLanguageTag()
        }

    private fun bootCount(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
                .takeIf { it >= 0 }?.toString() ?: UNKNOWN
        } else {
            UNKNOWN
        }

    private fun installerName(): String {
        val pm = context.packageManager
        val pkg = context.packageName
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg)
        }
        return sanitize(name)
    }

    private fun deviceName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            sanitize(Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME))
        } else {
            UNKNOWN
        }

    private fun inputMethodId(): String =
        sanitize(Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))

    private fun packageInfo() = context.packageManager.getPackageInfo(context.packageName, 0)

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

    private fun hardwareFields(): List<Pair<String, String>> = safeList {
        listOf(
            label(R.string.sysinfo_field_cpu_cores) to Runtime.getRuntime().availableProcessors().toString(),
            label(R.string.sysinfo_field_abis) to safe { Build.SUPPORTED_ABIS.joinToString(", ") },
            label(R.string.sysinfo_field_soc) to safe { socName() },
            label(R.string.sysinfo_field_kernel) to safe { sanitize(System.getProperty("os.version")) },
            label(R.string.sysinfo_field_gles) to safe { glEsVersion() },
            label(R.string.sysinfo_field_max_heap) to formatBytes(Runtime.getRuntime().maxMemory()),
            label(R.string.sysinfo_field_process_bits) to safe { if (Process.is64Bit()) "64-bit" else "32-bit" },
        )
    } ?: listOf(label(R.string.sysinfo_field_cpu_cores) to UNKNOWN)

    private fun socName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${sanitize(Build.SOC_MANUFACTURER)} ${sanitize(Build.SOC_MODEL)}".trim()
        } else {
            sanitize(Build.HARDWARE)
        }

    private fun glEsVersion(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return sanitize(am.deviceConfigurationInfo.glEsVersion)
    }

    private fun batteryFields(): List<Pair<String, String>> = safeList {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return@safeList listOf(label(R.string.sysinfo_field_battery_level) to UNKNOWN)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else UNKNOWN
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val tempStr = if (temp != Int.MIN_VALUE) String.format(Locale.US, "%.1f °C", temp / 10.0) else UNKNOWN
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        listOf(
            label(R.string.sysinfo_field_battery_level) to pct,
            label(R.string.sysinfo_field_battery_status) to batteryStatus(intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)),
            label(R.string.sysinfo_field_battery_plug) to batteryPlug(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)),
            label(R.string.sysinfo_field_battery_health) to batteryHealth(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)),
            label(R.string.sysinfo_field_battery_temperature) to tempStr,
            label(R.string.sysinfo_field_battery_voltage) to (if (voltage >= 0) "$voltage mV" else UNKNOWN),
            label(R.string.sysinfo_field_battery_technology) to sanitize(intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)),
        )
    } ?: listOf(label(R.string.sysinfo_field_battery_level) to UNKNOWN)

    private fun batteryStatus(value: Int): String = when (value) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        else -> UNKNOWN
    }

    private fun batteryPlug(value: Int): String = when (value) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        0 -> "Unplugged"
        else -> UNKNOWN
    }

    private fun batteryHealth(value: Int): String = when (value) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> UNKNOWN
    }

    private fun networkFields(): List<Pair<String, String>> = safeList {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "other"
        }
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        listOf(
            label(R.string.sysinfo_field_net_transport) to transport,
            label(R.string.sysinfo_field_net_metered) to yesNo(cm.isActiveNetworkMetered),
            label(R.string.sysinfo_field_net_vpn) to yesNo(vpn),
            label(R.string.sysinfo_field_net_airplane) to safe { onOff(Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1) },
            label(R.string.sysinfo_field_net_ip) to safe { localIpAddresses() },
        )
    } ?: listOf(label(R.string.sysinfo_field_net_transport) to UNKNOWN)

    private fun localIpAddresses(): String {
        val addresses = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .distinct()
        return if (addresses.isEmpty()) UNKNOWN else addresses.joinToString(", ")
    }

    private fun onOff(value: Boolean): String = if (value) "on" else "off"

    private fun memoryFields(): List<Pair<String, String>> = safeList {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        listOf(
            label(R.string.sysinfo_field_ram_total) to formatBytes(info.totalMem),
            label(R.string.sysinfo_field_ram_available) to formatBytes(info.availMem),
        )
    } ?: listOf(label(R.string.sysinfo_field_ram_total) to UNKNOWN)

    private fun storageFields(): List<Pair<String, String>> = safeList {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val available = stat.availableBlocksLong * stat.blockSizeLong
        listOf(
            label(R.string.sysinfo_field_storage_total) to formatBytes(total),
            label(R.string.sysinfo_field_storage_available) to formatBytes(available),
        )
    } ?: listOf(label(R.string.sysinfo_field_storage_total) to UNKNOWN)

    private fun displayFields(): List<Pair<String, String>> = safeList {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        @Suppress("DEPRECATION")
        display.getMetrics(metrics)
        val config = context.resources.configuration
        listOf(
            label(R.string.sysinfo_field_resolution) to "${metrics.widthPixels}x${metrics.heightPixels} px",
            label(R.string.sysinfo_field_density) to "${metrics.density} (${metrics.densityDpi} dpi)",
            label(R.string.sysinfo_field_refresh_rate) to safe { String.format(Locale.US, "%.0f Hz", display.refreshRate) },
            label(R.string.sysinfo_field_hdr) to safe { hdrSupport(display) },
            label(R.string.sysinfo_field_wide_gamut) to safe { wideGamut(display) },
            label(R.string.sysinfo_field_dark_mode) to safe { yesNo((config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) },
            label(R.string.sysinfo_field_font_scale) to safe { config.fontScale.toString() },
            label(R.string.sysinfo_field_orientation) to safe { orientation(config.orientation) },
            label(R.string.sysinfo_field_smallest_width) to safe { "${config.smallestScreenWidthDp} dp" },
            label(R.string.sysinfo_field_display_count) to safe { displayCount() },
        )
    } ?: listOf(label(R.string.sysinfo_field_resolution) to UNKNOWN)

    @Suppress("DEPRECATION")
    private fun hdrSupport(display: android.view.Display): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val types = display.hdrCapabilities?.supportedHdrTypes
            if (types != null && types.isNotEmpty()) "${types.size} type(s)" else "none"
        } else {
            UNKNOWN
        }

    private fun wideGamut(display: android.view.Display): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) yesNo(display.isWideColorGamut) else UNKNOWN

    private fun orientation(value: Int): String = when (value) {
        Configuration.ORIENTATION_PORTRAIT -> "Portrait"
        Configuration.ORIENTATION_LANDSCAPE -> "Landscape"
        else -> UNKNOWN
    }

    private fun displayCount(): String {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.displays.size.toString()
    }

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

    private fun userName(): String {
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager
        return sanitize(um.userName)
    }

    private fun currentTime(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun timezone(): String {
        val tz = TimeZone.getDefault()
        val offsetMinutes = tz.getOffset(System.currentTimeMillis()) / 60000
        val sign = if (offsetMinutes >= 0) "+" else "-"
        val abs = kotlin.math.abs(offsetMinutes)
        val offset = String.format(Locale.US, "%s%02d:%02d", sign, abs / 60, abs % 60)
        return "${tz.id} (UTC$offset)"
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.2f GB", gb)
    }

    private fun sanitize(value: String?, fallback: String = UNKNOWN): String =
        value?.takeIf { it.isNotBlank() } ?: fallback

    private fun label(@StringRes resId: Int): String = context.getString(resId)

    private inline fun safe(block: () -> String): String = try {
        block()
    } catch (e: Exception) {
        if (SystemInfoAccessClassifier.isExpectedAccessDenial(e)) {
            Timber.d("S0345: system info field unavailable on this device")
            Timber.i("System info: device field unavailable on this device (${e.javaClass.simpleName})")
        } else {
            Timber.w(e, "System info: failed to read a device field")
        }
        UNKNOWN
    }

    private inline fun safeList(block: () -> List<Pair<String, String>>): List<Pair<String, String>>? = try {
        block()
    } catch (e: Exception) {
        if (SystemInfoAccessClassifier.isExpectedAccessDenial(e)) {
            Timber.i("System info: device section unavailable on this device (${e.javaClass.simpleName})")
        } else {
            Timber.w(e, "System info: failed to read a device section")
        }
        null
    }

    private companion object {
        const val UNKNOWN = "unknown"
    }
}
