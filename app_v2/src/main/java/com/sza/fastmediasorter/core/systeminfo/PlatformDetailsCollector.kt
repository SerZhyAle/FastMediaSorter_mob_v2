package com.sza.fastmediasorter.core.systeminfo

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.nfc.NfcManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.UserManager
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.accessibility.AccessibilityManager
import android.webkit.WebView
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.getPackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.hypot

/**
 * Supplies extended metrics for the 15 base System info sections (`DEVICE`, `USER`, `OS`, `APP`,
 * `HARDWARE`, `MEMORY`, `STORAGE`, `NETWORK`, `DISPLAY`, `LOCALE`, `TIME`, `SYSTEM`) so
 * `GatherSystemInfoUseCase` stays under detekt's function-count ceiling.
 */
@Suppress("TooManyFunctions", "MagicNumber", "ReturnCount", "TooGenericExceptionCaught")
class PlatformDetailsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun extraDeviceFields(): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_brand) to safe { sanitize(Build.BRAND) },
        label(R.string.sysinfo_field_board) to safe { sanitize(Build.BOARD) },
        label(R.string.sysinfo_field_hardware_name) to safe { sanitize(Build.HARDWARE) },
        label(R.string.sysinfo_field_baseband) to safe { sanitize(Build.getRadioVersion()) },
        label(R.string.sysinfo_field_sku) to safe { skuInfo() },
        label(R.string.sysinfo_field_form_factor) to safe { formFactor() },
    )

    fun extraUserFields(): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_user_system) to safe {
            val um = context.getSystemService(Context.USER_SERVICE) as UserManager
            yesNo(um.isSystemUser)
        },
        label(R.string.sysinfo_field_user_work_profile) to safe {
            val um = context.getSystemService(Context.USER_SERVICE) as UserManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) yesNo(um.isManagedProfile) else UNKNOWN
        },
        label(R.string.sysinfo_field_user_unlocked) to safe {
            val um = context.getSystemService(Context.USER_SERVICE) as UserManager
            yesNo(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) um.isUserUnlocked else true)
        },
    )

    fun extraOsFields(): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_os_codename) to safe { sanitize(Build.VERSION.CODENAME) },
        label(R.string.sysinfo_field_os_incremental) to safe { sanitize(Build.VERSION.INCREMENTAL) },
        label(R.string.sysinfo_field_media_perf_class) to safe { mediaPerformanceClass() },
        label(R.string.sysinfo_field_jvm_version) to safe { sanitize(System.getProperty("java.vm.version")) },
        label(R.string.sysinfo_field_play_services) to safe { playServicesVersion() },
        label(R.string.sysinfo_field_vulkan) to safe { vulkanSupport() },
    )

    fun extraAppFields(): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_package_name) to safe { context.packageName },
        label(R.string.sysinfo_field_sdk_target_min) to safe { targetAndMinSdk() },
        label(R.string.sysinfo_field_standby_bucket) to safe { standbyBucket() },
        label(R.string.sysinfo_field_last_exit_reason) to safe { lastExitReason() },
        label(R.string.sysinfo_field_app_storage_size) to safe { appStorageSizes() },
    )

    fun extraHardwareFields(): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_cpu_frequencies) to safe { cpuFrequencies() },
        label(R.string.sysinfo_field_thermal_status) to safe { thermalStatus() },
        label(R.string.sysinfo_field_thermal_headroom) to safe { thermalHeadroom() },
        label(R.string.sysinfo_field_hw_features) to safe { hardwareFeatures() },
    )

    fun extraBatteryFields(details: BatteryDetails?, unavailable: String): List<Pair<String, String>> {
        val currentAvg = details?.averageCurrentMicroamps?.let {
            String.format(Locale.getDefault(), "%.1f mA", it / MICROAMPS_PER_MILLIAMP)
        } ?: unavailable
        val chargeCounter = details?.chargeCounterMilliampHours?.let { "$it mAh" } ?: unavailable
        val cycles = details?.cycleCount?.toString() ?: unavailable
        val policy = details?.chargingPolicy ?: unavailable
        return listOf(
            label(R.string.sysinfo_field_battery_current_avg) to currentAvg,
            label(R.string.sysinfo_field_battery_charge_counter) to chargeCounter,
            label(R.string.sysinfo_field_battery_cycles) to cycles,
            label(R.string.sysinfo_field_battery_charging_policy) to policy,
        )
    }

    fun extraMemoryFields(): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_ram_used) to safe { ramUsedAndPercent() },
        label(R.string.sysinfo_field_ram_threshold) to safe { ramThresholdAndLowFlag() },
        label(R.string.sysinfo_field_swap_zram) to safe { swapFromProcMeminfo() },
        label(R.string.sysinfo_field_jvm_heap_used) to safe { jvmHeapUsage() },
        label(R.string.sysinfo_field_native_heap) to safe { formatMegabytes(Debug.getNativeHeapAllocatedSize()) },
        label(R.string.sysinfo_field_memory_class) to safe { memoryClassSummary() },
    )

    fun extraStorageFields(): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_storage_used) to safe { externalStorageUsedAndPercent() },
        label(R.string.sysinfo_field_storage_data_partition) to safe { dataPartitionSummary() },
        label(R.string.sysinfo_field_storage_volumes) to safe { storageVolumesSummary() },
        label(R.string.sysinfo_field_storage_encryption) to safe { fileEncryptionStatus() },
    )

    fun extraNetworkFields(): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_net_ipv6) to safe { localIpv6Addresses() },
        label(R.string.sysinfo_field_net_bandwidth) to safe { linkBandwidth() },
        label(R.string.sysinfo_field_net_validated) to safe { validatedAndPortal() },
        label(R.string.sysinfo_field_net_dns) to safe { dnsSummary() },
        label(R.string.sysinfo_field_net_wifi_details) to safe { wifiLinkSummary() },
        label(R.string.sysinfo_field_net_wifi_capabilities) to safe { wifiRadioCapabilities() },
        label(R.string.sysinfo_field_net_bt_nfc) to safe { bluetoothAndNfcState() },
    )

    fun extraDisplayFields(display: Display, metrics: DisplayMetrics): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_display_diagonal) to safe { physicalDiagonal(metrics) },
        label(R.string.sysinfo_field_refresh_rate_range) to safe { refreshRateRange(display) },
        label(R.string.sysinfo_field_hdr_types) to safe { hdrTypesAndLuminance(display) },
        label(R.string.sysinfo_field_display_cutout) to safe { cutoutSummary(display) },
        label(R.string.sysinfo_field_brightness) to safe { brightnessSummary() },
        label(R.string.sysinfo_field_screen_timeout) to safe { screenTimeoutSummary() },
        label(R.string.sysinfo_field_navigation_mode) to safe { navigationMode() },
    )

    fun extraLocaleFields(): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_first_day_of_week) to safe { firstDayOfWeek() },
        label(R.string.sysinfo_field_measurement_system) to safe { measurementSystem() },
    )

    fun extraTimeFields(): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_uptime) to safe { formatUptime(SystemClock.elapsedRealtime()) },
        label(R.string.sysinfo_field_awake_deep_sleep) to safe { awakeAndDeepSleep() },
        label(R.string.sysinfo_field_auto_time) to safe { autoTimeAndZone() },
    )

    fun extraSystemFields(): List<Pair<String, String>> = listOf(
        label(R.string.sysinfo_field_webview) to safe { webViewSummary() },
        label(R.string.sysinfo_field_accessibility) to safe { accessibilitySummary() },
        label(R.string.sysinfo_field_animation_scales) to safe { animationScales() },
        label(R.string.sysinfo_field_dev_adb) to safe { devOptionsAndAdb() },
    )

    private fun skuInfo(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return UNKNOWN
        val parts = listOfNotNull(
            Build.SKU.takeIf { it.isNotBlank() && it != UNKNOWN },
            Build.ODM_SKU.takeIf { it.isNotBlank() && it != UNKNOWN && it != Build.SKU },
        )
        return if (parts.isEmpty()) UNKNOWN else parts.joinToString(" / ")
    }

    private fun formFactor(): String {
        val pm = context.packageManager
        val config = context.resources.configuration
        return when {
            pm.hasSystemFeature(PackageManager.FEATURE_WATCH) -> "Watch"
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) -> "TV"
            pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) -> "Automotive"
            pm.hasSystemFeature(PackageManager.FEATURE_PC) -> "PC / Chromebook"
            config.smallestScreenWidthDp >= 600 -> "Tablet"
            else -> "Phone"
        }
    }

    private fun mediaPerformanceClass(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return UNKNOWN
        val mpc = Build.VERSION.MEDIA_PERFORMANCE_CLASS
        return if (mpc > 0) "API $mpc" else "none"
    }

    private fun playServicesVersion(): String = try {
        val info = context.packageManager.getPackageInfoCompat("com.google.android.gms")
        sanitize(info.versionName)
    } catch (_: Exception) {
        "not installed"
    }

    private fun vulkanSupport(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return UNKNOWN
        val features = context.packageManager.systemAvailableFeatures
        val versionFeature = features.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
        val levelFeature = features.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL }
        if (versionFeature == null) return "none"
        val v = versionFeature.version
        val major = v ushr 22
        val minor = (v ushr 12) and 0x3ff
        val patch = v and 0xfff
        val level = levelFeature?.version?.let { ", level $it" }.orEmpty()
        return "$major.$minor.$patch$level"
    }

    private fun targetAndMinSdk(): String {
        val appInfo = context.applicationInfo
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion.toString() else "?"
        return "${appInfo.targetSdkVersion} / $min"
    }

    private fun standbyBucket(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return UNKNOWN
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return UNKNOWN
        return when (usm.appStandbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "Active"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "Working set"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "Frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "Rare"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "Restricted"
            else -> "Bucket(${usm.appStandbyBucket})"
        }
    }

    private fun lastExitReason(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return UNKNOWN
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return UNKNOWN
        val exit = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return "none"
        return "${exitReasonName(exit.reason)} (status ${exit.status})"
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        1 -> "Exit self"
        2 -> "Signaled"
        3 -> "Low memory"
        4 -> "Crash"
        5 -> "Native crash"
        6 -> "ANR"
        10 -> "User requested"
        11 -> "User stopped"
        else -> "Reason($reason)"
    }

    private fun appStorageSizes(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            val stats = ssm?.queryStatsForUid(StorageManager.UUID_DEFAULT, Process.myUid())
            if (stats != null) {
                return "${formatMegabytes(stats.appBytes)} / " +
                    "${formatMegabytes(stats.dataBytes)} / " +
                    formatMegabytes(stats.cacheBytes)
            }
        }
        val apkBytes = runCatching { File(context.applicationInfo.sourceDir).length() }.getOrDefault(0L)
        return "${formatMegabytes(apkBytes)} / unknown / unknown"
    }

    private fun cpuFrequencies(): String {
        val cores = Runtime.getRuntime().availableProcessors()
        val ranges = (0 until cores).mapNotNull { core ->
            val base = "/sys/devices/system/cpu/cpu$core/cpufreq"
            val maxKhz = readLongFile("$base/cpuinfo_max_freq") ?: return@mapNotNull null
            val minKhz = readLongFile("$base/cpuinfo_min_freq") ?: 0L
            "${minKhz / 1000}-${maxKhz / 1000} MHz"
        }.distinct()
        return if (ranges.isEmpty()) UNKNOWN else ranges.joinToString(", ")
    }

    private fun readLongFile(path: String): Long? =
        runCatching { File(path).takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull() }.getOrNull()

    private fun thermalStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return UNKNOWN
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return UNKNOWN
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "None"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> UNKNOWN
        }
    }

    private fun thermalHeadroom(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return UNKNOWN
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return UNKNOWN
        val headroom = pm.getThermalHeadroom(0)
        return if (headroom.isNaN() || headroom <= 0f) UNKNOWN else String.format(Locale.US, "%.2f", headroom)
    }

    private fun hardwareFeatures(): String {
        val pm = context.packageManager
        val checks = listOf(
            "NFC" to PackageManager.FEATURE_NFC,
            "BLE" to PackageManager.FEATURE_BLUETOOTH_LE,
            "USB Host" to PackageManager.FEATURE_USB_HOST,
            "Wi-Fi Direct" to PackageManager.FEATURE_WIFI_DIRECT,
            "Fingerprint" to PackageManager.FEATURE_FINGERPRINT,
            "UWB" to "android.hardware.uwb",
            "StrongBox" to "android.hardware.strongbox_keystore",
        )
        val supported = checks.mapNotNull { (label, feature) ->
            label.takeIf { pm.hasSystemFeature(feature) }
        }
        return if (supported.isEmpty()) "none" else supported.joinToString(", ")
    }

    private fun ramUsedAndPercent(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val used = (info.totalMem - info.availMem).coerceAtLeast(0L)
        val pct = if (info.totalMem > 0L) used * 100L / info.totalMem else 0L
        return "${formatGigabytes(used)} ($pct%)"
    }

    private fun ramThresholdAndLowFlag(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val flag = if (info.lowMemory) "low!" else "normal"
        return "${formatMegabytes(info.threshold)} ($flag)"
    }

    private fun swapFromProcMeminfo(): String {
        val lines = File("/proc/meminfo").takeIf { it.canRead() }?.readLines().orEmpty()
        val totalKb = parseMeminfoKb(lines, "SwapTotal:") ?: return UNKNOWN
        val freeKb = parseMeminfoKb(lines, "SwapFree:") ?: 0L
        if (totalKb <= 0L) return "none"
        val usedKb = (totalKb - freeKb).coerceAtLeast(0L)
        return "${formatMegabytes(usedKb * 1024L)} / ${formatMegabytes(totalKb * 1024L)}"
    }

    private fun parseMeminfoKb(lines: List<String>, prefix: String): Long? =
        lines.firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.toLongOrNull()

    private fun jvmHeapUsage(): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()).coerceAtLeast(0L)
        return "${formatMegabytes(used)} / ${formatMegabytes(rt.maxMemory())}"
    }

    private fun memoryClassSummary(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lowRam = if (am.isLowRamDevice) ", low-RAM device" else ""
        return "${am.memoryClass} MB (large ${am.largeMemoryClass} MB$lowRam)"
    }

    private fun externalStorageUsedAndPercent(): String {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val avail = stat.availableBlocksLong * stat.blockSizeLong
        val used = (total - avail).coerceAtLeast(0L)
        val pct = if (total > 0L) used * 100L / total else 0L
        return "${formatGigabytes(used)} ($pct%)"
    }

    private fun dataPartitionSummary(): String {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val avail = stat.availableBlocksLong * stat.blockSizeLong
        return "${formatGigabytes(avail)} free / ${formatGigabytes(total)}"
    }

    private fun storageVolumesSummary(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return UNKNOWN
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return UNKNOWN
        val volumes = sm.storageVolumes.map { vol ->
            val kind = when {
                vol.isPrimary -> "Primary"
                vol.isRemovable -> "Removable"
                else -> "Volume"
            }
            "$kind (${vol.state})"
        }
        return if (volumes.isEmpty()) UNKNOWN else volumes.joinToString(", ")
    }

    private fun fileEncryptionStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return UNKNOWN
        // The static StorageManager.isFileEncrypted() is not a public API; the public probe is the
        // instance isEncrypted(File), available since the same API 24 the guard above enforces.
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return UNKNOWN
        return yesNo(sm.isEncrypted(Environment.getDataDirectory()))
    }

    private fun localIpv6Addresses(): String {
        val addresses = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet6Address>()
            .mapNotNull { it.hostAddress?.substringBefore('%') }
            .distinct()
        return if (addresses.isEmpty()) "none" else addresses.take(2).joinToString(", ")
    }

    private fun linkBandwidth(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return UNKNOWN
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return "offline") ?: return UNKNOWN
        return "${caps.linkDownstreamBandwidthKbps} / ${caps.linkUpstreamBandwidthKbps} kbps"
    }

    private fun validatedAndPortal(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return UNKNOWN
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return "offline") ?: return UNKNOWN
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val portal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        return "Validated: ${yesNo(validated)}, Portal: ${yesNo(portal)}"
    }

    private fun dnsSummary(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return UNKNOWN
        val lp = cm.getLinkProperties(cm.activeNetwork ?: return "offline") ?: return UNKNOWN
        val servers = lp.dnsServers.mapNotNull { it.hostAddress }.distinct()
        val privateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (lp.isPrivateDnsActive) "Private(${lp.privateDnsServerName ?: "auto"})" else "Standard"
        } else {
            "Standard"
        }
        val list = if (servers.isEmpty()) "none" else servers.take(3).joinToString(", ")
        return "$list [$privateDns]"
    }

    @Suppress("DEPRECATION")
    private fun wifiLinkSummary(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let(cm::getNetworkCapabilities)
        val transportInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps?.transportInfo as? WifiInfo
        } else {
            null
        }
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val info = transportInfo ?: runCatching { wm?.connectionInfo }.getOrNull() ?: return "n/a"
        val speed = info.linkSpeed
        val freq = info.frequency
        val rssi = info.rssi
        if (speed <= 0 && freq <= 0) return "not connected"
        return "$speed Mbps, $freq MHz ($rssi dBm)"
    }

    private fun wifiRadioCapabilities(): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return UNKNOWN
        val bands = mutableListOf<String>()
        if (wm.is5GHzBandSupported) bands += "5 GHz"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm.is6GHzBandSupported) bands += "6 GHz"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (wm.isWifiStandardSupported(ScanResultWifiStandards.WIFI_STANDARD_11AX)) bands += "Wi-Fi 6"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                wm.isWifiStandardSupported(ScanResultWifiStandards.WIFI_STANDARD_11BE)
            ) {
                bands += "Wi-Fi 7"
            }
        }
        return if (bands.isEmpty()) "2.4 GHz" else (listOf("2.4 GHz") + bands).joinToString(", ")
    }

    private fun bluetoothAndNfcState(): String {
        val pm = context.packageManager
        val btState = when {
            pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) -> "supported (BLE)"
            pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) -> "supported"
            else -> "n/a"
        }
        val nfc = (context.getSystemService(Context.NFC_SERVICE) as? NfcManager)?.defaultAdapter
        val nfcState = when (nfc?.isEnabled) {
            true -> "on"
            false -> "off"
            null -> "n/a"
        }
        return "BT: $btState, NFC: $nfcState"
    }

    private fun physicalDiagonal(metrics: DisplayMetrics): String {
        if (metrics.xdpi <= 0f || metrics.ydpi <= 0f) return UNKNOWN
        val widthIn = metrics.widthPixels / metrics.xdpi.toDouble()
        val heightIn = metrics.heightPixels / metrics.ydpi.toDouble()
        val diag = hypot(widthIn, heightIn)
        return if (diag in 1.0..100.0) String.format(Locale.US, "%.2f in", diag) else UNKNOWN
    }

    private fun refreshRateRange(display: Display): String {
        val rates = display.supportedModes.map { it.refreshRate }
        val min = rates.minOrNull() ?: display.refreshRate
        val max = rates.maxOrNull() ?: display.refreshRate
        return String.format(Locale.US, "%.0f - %.0f Hz", min, max)
    }

    @Suppress("DEPRECATION")
    private fun hdrTypesAndLuminance(display: Display): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return UNKNOWN
        val caps = display.hdrCapabilities ?: return "none"
        val types = caps.supportedHdrTypes?.map { hdrTypeName(it) }.orEmpty()
        if (types.isEmpty()) return "none"
        val maxLum = String.format(Locale.US, "%.0f nits", caps.desiredMaxLuminance)
        return "${types.joinToString(", ")} (max $maxLum)"
    }

    private fun hdrTypeName(type: Int): String = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        else -> "HDR($type)"
    }

    private fun cutoutSummary(display: Display): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return UNKNOWN
        val cutout = display.cutout ?: return "none"
        return "${cutout.boundingRects.size} rect(s)"
    }

    private fun brightnessSummary(): String {
        val cr = context.contentResolver
        val level = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, -1)
        val mode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)
        val modeText = if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "auto" else "manual"
        return if (level >= 0) "$level / 255 ($modeText)" else modeText
    }

    private fun screenTimeoutSummary(): String {
        val ms = Settings.System.getLong(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1L)
        return if (ms > 0L) "${ms / 1000L} s" else UNKNOWN
    }

    private fun navigationMode(): String {
        val resId = context.resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
        if (resId == 0) return UNKNOWN
        return when (context.resources.getInteger(resId)) {
            0 -> "3-button"
            1 -> "2-button"
            2 -> "Gesture"
            else -> UNKNOWN
        }
    }

    private fun firstDayOfWeek(): String = when (Calendar.getInstance(Locale.getDefault()).firstDayOfWeek) {
        Calendar.MONDAY -> "Monday"
        Calendar.SUNDAY -> "Sunday"
        Calendar.SATURDAY -> "Saturday"
        else -> UNKNOWN
    }

    private fun measurementSystem(): String {
        val country = Locale.getDefault().country.uppercase(Locale.US)
        return when (country) {
            "US" -> "US Customary"
            "LR", "MM" -> "Imperial"
            else -> "Metric (SI)"
        }
    }

    private fun formatUptime(millis: Long): String {
        val days = TimeUnit.MILLISECONDS.toDays(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis) % 24
        val mins = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return "${days}d ${hours}h ${mins}m"
    }

    private fun awakeAndDeepSleep(): String {
        val elapsed = SystemClock.elapsedRealtime().coerceAtLeast(1L)
        val awake = SystemClock.uptimeMillis().coerceAtLeast(0L)
        val sleep = (elapsed - awake).coerceAtLeast(0L)
        val sleepPct = sleep * 100L / elapsed
        return "${formatUptime(awake)} / ${formatUptime(sleep)} ($sleepPct%)"
    }

    private fun autoTimeAndZone(): String {
        val cr = context.contentResolver
        val autoTime = onOff(Settings.Global.getInt(cr, Settings.Global.AUTO_TIME, 0) == 1)
        val autoZone = onOff(Settings.Global.getInt(cr, Settings.Global.AUTO_TIME_ZONE, 0) == 1)
        return "$autoTime / $autoZone"
    }

    private fun webViewSummary(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return UNKNOWN
        val pkg = WebView.getCurrentWebViewPackage() ?: return "none"
        return "${pkg.packageName} (${pkg.versionName})"
    }

    private fun accessibilitySummary(): String {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return UNKNOWN
        return "Enabled: ${yesNo(am.isEnabled)}, Touch exploration: ${yesNo(am.isTouchExplorationEnabled)}"
    }

    private fun animationScales(): String {
        val cr = context.contentResolver
        val win = Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
        val trans = Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        val anim = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        return String.format(Locale.US, "%.1fx / %.1fx / %.1fx", win, trans, anim)
    }

    private fun devOptionsAndAdb(): String {
        val cr = context.contentResolver
        val dev = onOff(Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1)
        val adb = onOff(Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1)
        return "$dev / $adb"
    }

    private fun formatGigabytes(bytes: Long): String =
        String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))

    private fun formatMegabytes(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))

    private fun sanitize(value: String?): String = value?.takeIf { it.isNotBlank() } ?: UNKNOWN
    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
    private fun onOff(value: Boolean): String = if (value) "on" else "off"
    private fun label(@StringRes resId: Int): String = context.getString(resId)

    private inline fun safe(block: () -> String): String = try {
        block()
    } catch (e: Exception) {
        if (SystemInfoAccessClassifier.isExpectedAccessDenial(e)) {
            Timber.i("System info: platform field unavailable (${e.javaClass.simpleName})")
        } else {
            Timber.w(e, "System info: failed to read platform field")
        }
        UNKNOWN
    }

    private object ScanResultWifiStandards {
        const val WIFI_STANDARD_11AX = 6
        const val WIFI_STANDARD_11BE = 8
    }

    private companion object {
        const val UNKNOWN = "unknown"
        const val MICROAMPS_PER_MILLIAMP = 1_000.0
    }
}
