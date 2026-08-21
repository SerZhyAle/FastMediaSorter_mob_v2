package com.sza.fastmediasorter.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import android.webkit.WebView
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.getPackageInfoCompat
import com.sza.fastmediasorter.core.systeminfo.ExtendedDiagnosticsField
import com.sza.fastmediasorter.core.systeminfo.ExtendedDiagnosticsSection
import com.sza.fastmediasorter.core.systeminfo.SystemInfoAccessClassifier
import com.sza.fastmediasorter.core.xr.XrEnvironmentDetector
import timber.log.Timber
import java.io.File
import java.net.NetworkInterface
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Builds the seven noLegal diagnostic sections (strategic S0336 §5.1). Every value is read
 * defensively: a failing source yields "unknown" / "n/a" rather than throwing, so a single broken
 * field never drops a section. Section titles are localized (`R.string.nolegal_diag_section_*`);
 * field labels stay fixed-English technical keys (strategic §3.2 allowance).
 */
internal class NoLegalDiagnosticsCollectors(
    private val context: Context,
    private val xrEnvironmentDetector: XrEnvironmentDetector,
) {

    fun osSecurity(): ExtendedDiagnosticsSection = section(
        R.string.nolegal_diag_section_os_security,
        f("Root markers") { rootMarkers() },
        f("SELinux") { selinuxStatus() },
        f("Developer options") { globalFlag("development_settings_enabled") },
        f("ADB enabled") { globalFlag("adb_enabled") },
        f("Xposed/LSPosed") { xposedStatus() },
        f("Build tags") { buildTags() },
        f("Build type") { Build.TYPE },
        f("Verified boot") { systemProperty("ro.boot.verifiedbootstate") },
        f("Bootloader locked") { bootloaderLocked() },
        f("System uptime") { systemUptime() },
    )

    fun permissions(): ExtendedDiagnosticsSection = section(
        R.string.nolegal_diag_section_permissions,
        f("MANAGE_EXTERNAL_STORAGE") { manageExternalStorage() },
        f("QUERY_ALL_PACKAGES") { permissionState("android.permission.QUERY_ALL_PACKAGES") },
        f("REQUEST_INSTALL_PACKAGES") { canRequestInstalls() },
        f("SYSTEM_ALERT_WINDOW") { yesNo(Settings.canDrawOverlays(context)) },
        f("POST_NOTIFICATIONS") { postNotifications() },
        f("Battery optimization ignored") { ignoringBatteryOptimizations() },
        f("Accessibility services") { accessibilityServices() },
    )

    fun installerSignature(): ExtendedDiagnosticsSection = section(
        R.string.nolegal_diag_section_installer,
        f("Installer package") { installerPackage() },
        f("Signature SHA-256", sensitive = true) { signatureSha256() },
        f("Alternative stores") { alternativeStores() },
        f("App version") { appVersion() },
        f("Target SDK") { context.applicationInfo.targetSdkVersion.toString() },
        f("First install") { installTime(firstInstall = true) },
        f("Last update") { installTime(firstInstall = false) },
    )

    fun runtimes(): ExtendedDiagnosticsSection = section(
        R.string.nolegal_diag_section_runtimes,
        f("Python (Chaquopy)") { pythonStatus() },
        f("yt-dlp") { ytDlpStatus() },
        f("ffmpeg/ffprobe") { ffmpegStatus() },
        f("Tesseract data") { tesseractStatus() },
        f("XR runtime") { xrRuntime() },
        f("Supported ABIs") { Build.SUPPORTED_ABIS.joinToString(", ") },
        f("WebView provider") { webViewProvider() },
    )

    fun mounts(): ExtendedDiagnosticsSection = section(
        R.string.nolegal_diag_section_mounts,
        f("Mount entries") { mountCount() },
        f("Mount points", sensitive = true) { mountPoints() },
        f("/data size") { partitionSize(Environment.getDataDirectory().absolutePath) },
        f("App cache size") { partitionSize(context.cacheDir.absolutePath) },
        f("External storage state") { Environment.getExternalStorageState() },
        f("Removable volumes") { removableVolumes() },
    )

    fun network(): ExtendedDiagnosticsSection = section(
        R.string.nolegal_diag_section_network,
        f("DNS servers", sensitive = true) { dnsServers() },
        f("VPN interfaces", sensitive = true) { vpnInterfaces() },
        f("HTTP proxy") { httpProxy() },
        f("Active transport") { activeTransport() },
        f("Private DNS") { privateDns() },
        f("Metered") { metered() },
    )

    fun processResources(): ExtendedDiagnosticsSection = section(
        R.string.nolegal_diag_section_process,
        f("Native heap") { formatBytes(Debug.getNativeHeapAllocatedSize()) },
        f("Java heap (used/max)") { javaHeap() },
        f("Open file descriptors") { openFdCount() },
        f("Active threads") { Thread.getAllStackTraces().size.toString() },
        f("CPU cores") { Runtime.getRuntime().availableProcessors().toString() },
        f("Total RAM") { totalRam() },
        f("Thermal status") { thermalStatus() },
    )

    // --- OS & Security ---------------------------------------------------------------------------

    private fun rootMarkers(): String {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/bin/busybox", "/system/xbin/busybox",
            "/data/adb/magisk", "/data/adb/ksu", "/data/adb/ap",
        )
        val files = paths.filter { File(it).exists() }
        val packages = listOf(
            "com.topjohnwu.magisk", "me.weishu.kernelsu", "me.bmax.apatch",
        ).filter { isPackageInstalled(it) }
        val found = files + packages
        return if (found.isEmpty()) "none detected" else found.joinToString(", ")
    }

    private fun selinuxStatus(): String {
        val file = File("/sys/fs/selinux/enforce")
        if (!file.exists()) return "n/a"
        return when (file.readText().trim()) {
            "1" -> "Enforcing"
            "0" -> "Permissive"
            else -> "unknown"
        }
    }

    private fun globalFlag(key: String): String =
        onOff(Settings.Global.getInt(context.contentResolver, key, 0) == 1)

    private fun xposedStatus(): String = try {
        Class.forName("de.robv.android.xposed.XposedBridge")
        "present"
    } catch (e: ClassNotFoundException) {
        "not present"
    }

    private fun buildTags(): String {
        val tags = Build.TAGS ?: return "unknown"
        return if (tags.contains("test-keys")) "$tags (custom/AOSP)" else tags
    }

    private fun bootloaderLocked(): String = when (systemProperty("ro.boot.flash.locked")) {
        "1" -> "locked"
        "0" -> "unlocked"
        else -> "unknown"
    }

    private fun systemUptime(): String {
        val millis = SystemClock.elapsedRealtime()
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return "${hours}h ${minutes}m"
    }

    /** Reads a system property via reflection on the hidden `android.os.SystemProperties` API. */
    private fun systemProperty(key: String): String {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        val value = get.invoke(null, key) as? String
        return value?.takeIf { it.isNotBlank() } ?: "unknown"
    }

    // --- Permissions -----------------------------------------------------------------------------

    private fun postNotifications(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            permissionState("android.permission.POST_NOTIFICATIONS")
        else "n/a"

    private fun ignoringBatteryOptimizations(): String {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return yesNo(pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    private fun accessibilityServices(): String {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )?.takeIf { it.isNotBlank() } ?: return "none"
        return enabled.split(":").size.toString()
    }

    private fun manageExternalStorage(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) yesNo(Environment.isExternalStorageManager()) else "n/a"

    private fun permissionState(permission: String): String =
        granted(context.packageManager.checkPermission(permission, context.packageName) == PackageManager.PERMISSION_GRANTED)

    private fun canRequestInstalls(): String = yesNo(context.packageManager.canRequestPackageInstalls())

    // --- Installer & Signature -------------------------------------------------------------------

    private fun installerPackage(): String {
        val pm = context.packageManager
        val pkg = context.packageName
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg)
        }
        return name?.takeIf { it.isNotBlank() } ?: "sideloaded / unknown"
    }

    @Suppress("DEPRECATION", "PackageManagerGetSignatures")
    private fun signatureSha256(): String {
        val pm = context.packageManager
        val pkg = context.packageName
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfoCompat(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfoCompat(pkg, PackageManager.GET_SIGNATURES).signatures
        }
        val first = signatures?.firstOrNull() ?: return "unknown"
        val digest = MessageDigest.getInstance("SHA-256").digest(first.toByteArray())
        return digest.joinToString(":") { String.format(Locale.US, "%02X", it) }
    }

    private fun alternativeStores(): String {
        val stores = mapOf(
            "F-Droid" to "org.fdroid.fdroid",
            "Aurora" to "com.aurora.store",
            "Amazon" to "com.amazon.venezia",
            "Huawei AppGallery" to "com.huawei.appmarket",
        )
        val present = stores.filterValues { isPackageInstalled(it) }.keys
        return if (present.isEmpty()) "none" else present.joinToString(", ")
    }

    private fun appVersion(): String {
        val info = context.packageManager.getPackageInfoCompat(context.packageName)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
        return "${info.versionName} ($code)"
    }

    private fun installTime(firstInstall: Boolean): String {
        val info = context.packageManager.getPackageInfoCompat(context.packageName)
        val millis = if (firstInstall) info.firstInstallTime else info.lastUpdateTime
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
    }

    // --- Runtimes --------------------------------------------------------------------------------

    private fun webViewProvider(): String {
        val pkg = WebView.getCurrentWebViewPackage() ?: return "n/a"
        return "${pkg.packageName} ${pkg.versionName}"
    }

    private fun pythonStatus(): String = if (isClassPresent("com.chaquo.python.Python")) "present (Chaquopy)" else "n/a"

    private fun ytDlpStatus(): String =
        if (isClassPresent("com.chaquo.python.Python")) "bundled (via Python)" else "n/a"

    private fun ffmpegStatus(): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir?.let { File(it) } ?: return "n/a"
        val names = nativeDir.listFiles()?.map { it.name.lowercase(Locale.US) } ?: return "n/a"
        val present = listOf("ffmpeg", "ffprobe").filter { bin -> names.any { it.contains(bin) } }
        return if (present.isEmpty()) "not bundled" else present.joinToString(", ")
    }

    private fun tesseractStatus(): String {
        val tessdata = File(context.filesDir, "tessdata")
        val files = tessdata.listFiles()?.filter { it.name.endsWith(".traineddata") }?.map { it.name }
        return if (files.isNullOrEmpty()) "not loaded" else files.joinToString(", ")
    }

    private fun xrRuntime(): String = xrEnvironmentDetector.detect().name

    // --- Mounts & File System --------------------------------------------------------------------

    private fun mountLines(): List<String> = File("/proc/mounts").takeIf { it.canRead() }?.readLines().orEmpty()

    private fun mountCount(): String = mountLines().size.takeIf { it > 0 }?.toString() ?: "n/a"

    private fun mountPoints(): String {
        val points = mountLines().mapNotNull { it.split(" ").getOrNull(1) }.distinct()
        if (points.isEmpty()) return "n/a"
        val shown = points.take(MAX_MOUNT_POINTS).joinToString(", ")
        return if (points.size > MAX_MOUNT_POINTS) "$shown, .. (+${points.size - MAX_MOUNT_POINTS})" else shown
    }

    private fun partitionSize(path: String): String {
        val stat = StatFs(path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val available = stat.availableBlocksLong * stat.blockSizeLong
        return "${formatBytes(available)} free / ${formatBytes(total)}"
    }

    private fun removableVolumes(): String {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val removable = sm.storageVolumes.count { it.isRemovable }
        return "$removable of ${sm.storageVolumes.size}"
    }

    // --- Network ---------------------------------------------------------------------------------

    private fun dnsServers(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return "n/a"
        val dns = cm.getLinkProperties(active)?.dnsServers?.mapNotNull { it.hostAddress }?.distinct().orEmpty()
        return if (dns.isEmpty()) "n/a" else dns.joinToString(", ")
    }

    private fun vpnInterfaces(): String {
        val vpn = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && (it.name.startsWith("tun") || it.name.startsWith("ppp")) }
            .map { it.name }
        return if (vpn.isEmpty()) "none" else vpn.joinToString(", ")
    }

    private fun httpProxy(): String {
        val host = System.getProperty("http.proxyHost")?.takeIf { it.isNotBlank() } ?: return "none"
        val port = System.getProperty("http.proxyPort")?.takeIf { it.isNotBlank() } ?: "?"
        return "$host:$port"
    }

    private fun activeTransport(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return "none") ?: return "none"
        val transports = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Cellular")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("Bluetooth")
        }
        return if (transports.isEmpty()) "other" else transports.joinToString(", ")
    }

    private fun privateDns(): String =
        Settings.Global.getString(context.contentResolver, "private_dns_mode")?.takeIf { it.isNotBlank() } ?: "unknown"

    private fun metered(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return yesNo(cm.isActiveNetworkMetered)
    }

    // --- Process ---------------------------------------------------------------------------------

    private fun javaHeap(): String {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return "${formatBytes(used)} / ${formatBytes(runtime.maxMemory())}"
    }

    private fun openFdCount(): String =
        File("/proc/self/fd").listFiles()?.size?.toString() ?: "n/a"

    private fun totalRam(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val low = if (info.lowMemory) ", low!" else ""
        return "${formatBytes(info.totalMem)} (avail ${formatBytes(info.availMem)}$low)"
    }

    private fun thermalStatus(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "n/a"
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "unknown"
        }
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun isPackageInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfoCompat(pkg)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun isClassPresent(name: String): Boolean = try {
        Class.forName(name)
        true
    } catch (e: ClassNotFoundException) {
        false
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0)
        else String.format(Locale.US, "%.1f MB", mb)
    }

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
    private fun onOff(value: Boolean): String = if (value) "on" else "off"
    private fun granted(value: Boolean): String = if (value) "granted" else "denied"

    private fun section(@StringRes titleRes: Int, vararg fields: ExtendedDiagnosticsField): ExtendedDiagnosticsSection =
        ExtendedDiagnosticsSection(context.getString(titleRes), fields.toList())

    private fun f(label: String, sensitive: Boolean = false, block: () -> String): ExtendedDiagnosticsField =
        ExtendedDiagnosticsField(label, safe(block), sensitive)

    private inline fun safe(block: () -> String): String = try {
        block()
    } catch (e: Exception) {
        if (SystemInfoAccessClassifier.isExpectedAccessDenial(e)) {
            Timber.i("noLegal diagnostics: field unavailable on this device (${e.javaClass.simpleName})")
        } else {
            Timber.w(e, "noLegal diagnostics: failed to read a field")
        }
        "unknown"
    }

    private companion object {
        const val MAX_MOUNT_POINTS = 12
    }
}
