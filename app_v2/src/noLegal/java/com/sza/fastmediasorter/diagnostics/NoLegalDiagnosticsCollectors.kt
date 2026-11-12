package com.sza.fastmediasorter.diagnostics

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaDrm
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.webkit.WebView
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.systeminfo.ExtendedDiagnosticsField
import com.sza.fastmediasorter.core.systeminfo.ExtendedDiagnosticsSection
import com.sza.fastmediasorter.core.systeminfo.SystemInfoAccessClassifier
import com.sza.fastmediasorter.core.systeminfo.SystemInfoGroup
import com.sza.fastmediasorter.core.xr.XrEnvironmentDetector
import com.sza.fastmediasorter.util.getPackageInfoCompat
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import com.sza.fastmediasorter.util.resolveActivityCompat
import timber.log.Timber
import java.io.File
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.Security
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Builds the seven noLegal diagnostic sections (strategic S0336 §5.1, extended in S4543 with
 * permission-gated sensors, radio/Bluetooth/telephony details, and deep sysfs/SystemProperties
 * metrics). Every value is read defensively: a failing source yields "unknown" / "n/a" rather than
 * throwing, so a single broken field never drops a section. Section titles are localized
 * (`R.string.nolegal_diag_section_*`); field labels stay fixed-English technical keys (strategic
 * §3.2 allowance).
 */
@Suppress("LargeClass", "TooManyFunctions", "MagicNumber", "ReturnCount", "TooGenericExceptionCaught")
internal class NoLegalDiagnosticsCollectors(
    private val context: Context,
    private val xrEnvironmentDetector: XrEnvironmentDetector,
) {

    fun osSecurity(): ExtendedDiagnosticsSection = section(
        SystemInfoGroup.OS_SECURITY,
        R.string.nolegal_diag_section_os_security,
        f("Root markers") { rootMarkers() },
        f("SELinux") { selinuxStatus() },
        f("Developer options") { globalFlag("development_settings_enabled") },
        f("ADB enabled") { globalFlag("adb_enabled") },
        f("ADB Wi-Fi & USB state") { adbWifiAndUsbState() },
        f("Xposed/LSPosed") { xposedStatus() },
        f("Build tags") { buildTags() },
        f("Build type") { Build.TYPE },
        f("Bootloader version") { Build.BOOTLOADER.takeIf { it.isNotBlank() } ?: systemProperty("ro.bootloader") },
        f("Verified boot") { systemProperty("ro.boot.verifiedbootstate") },
        f("VBMeta & verity") { vbmetaAndVerity() },
        f("Bootloader locked") { bootloaderLocked() },
        f("OEM ROM / skin") { oemRomSkin() },
        f("Crypto & FBE state") { cryptoState() },
        f("Kernel /proc/version") { kernelProcVersion() },
        f("System uptime") { systemUptime() },
    )

    fun permissions(): ExtendedDiagnosticsSection = section(
        SystemInfoGroup.PERMISSIONS,
        R.string.nolegal_diag_section_permissions,
        f("MANAGE_EXTERNAL_STORAGE") { manageExternalStorage() },
        f("QUERY_ALL_PACKAGES") { permissionState("android.permission.QUERY_ALL_PACKAGES") },
        f("REQUEST_INSTALL_PACKAGES") { canRequestInstalls() },
        f("SYSTEM_ALERT_WINDOW") { yesNo(Settings.canDrawOverlays(context)) },
        f("ACTIVITY_RECOGNITION") { permissionState("android.permission.ACTIVITY_RECOGNITION") },
        f("Step counter (since boot)") { stepCounterReading() },
        f("Step detector & motion") { stepAndMotionSensors() },
        f("Body & health sensors") { bodyAndHealthSensors() },
        f("ACCESS_FINE_LOCATION") { permissionState("android.permission.ACCESS_FINE_LOCATION") },
        f("NEARBY_WIFI_DEVICES") { nearbyWifiPermission() },
        f("BLUETOOTH_CONNECT") { bluetoothConnectPermission() },
        f("READ_PHONE_STATE") { permissionState("android.permission.READ_PHONE_STATE") },
        f("POST_NOTIFICATIONS") { postNotifications() },
        f("Battery optimization ignored") { ignoringBatteryOptimizations() },
        f("Accessibility services") { accessibilityServices() },
        f("Notification listeners") { notificationListeners() },
    )

    fun installerSignature(): ExtendedDiagnosticsSection = section(
        SystemInfoGroup.INSTALLER_SIGNATURE,
        R.string.nolegal_diag_section_installer,
        f("Installer package") { installerPackage() },
        f("Install source details") { installSourceDetails() },
        f("Signature SHA-256", sensitive = true) { signatureSha256() },
        f("Alternative stores") { alternativeStores() },
        f("Visible packages (user/sys)") { visiblePackageCounts() },
        f("Default launcher & browser") { defaultRoleHolders() },
        f("APK & split layout", sensitive = true) { apkAndSplitLayout() },
        f("Native libraries (.so)") { nativeLibInventory() },
        f("App version") { appVersion() },
        f("Target SDK") { context.applicationInfo.targetSdkVersion.toString() },
        f("First install") { installTime(firstInstall = true) },
        f("Last update") { installTime(firstInstall = false) },
    )

    fun runtimes(): ExtendedDiagnosticsSection = section(
        SystemInfoGroup.RUNTIMES,
        R.string.nolegal_diag_section_runtimes,
        f("Python (Chaquopy)") { pythonStatus() },
        f("yt-dlp") { ytDlpStatus() },
        f("ffmpeg/ffprobe") { ffmpegStatus() },
        f("Tesseract data") { tesseractStatus() },
        f("XR runtime") { xrRuntime() },
        f("Supported ABIs") { Build.SUPPORTED_ABIS.joinToString(", ") },
        f("WebView provider") { webViewProvider() },
        f("GPU & EGL / Vulkan driver") { gpuDriverProperties() },
        f("ART VM heap limits") { artVmFlags() },
        f("Widevine DRM level") { widevineDrmSummary() },
        f("TLS security providers") { securityProvidersSummary() },
        f("Private & wake-up sensors") { privateAndWakeupSensors() },
    )

    fun mounts(): ExtendedDiagnosticsSection = section(
        SystemInfoGroup.MOUNTS,
        R.string.nolegal_diag_section_mounts,
        f("Mount entries") { mountCount() },
        f("Mount points", sensitive = true) { mountPoints() },
        f("System / data FS types") { coreFilesystemTypes() },
        f("zram & swap sysfs") { zramAndSwapDetails() },
        f("Block I/O scheduler") { blockIoScheduler() },
        f("/data size") { partitionSize(Environment.getDataDirectory().absolutePath) },
        f("App cache size") { partitionSize(context.cacheDir.absolutePath) },
        f("External storage state") { Environment.getExternalStorageState() },
        f("Direct FS read/write") { directExternalAccess() },
        f("Removable volumes") { removableVolumes() },
    )

    fun network(): ExtendedDiagnosticsSection = section(
        SystemInfoGroup.NETWORK_DIAGNOSTICS,
        R.string.nolegal_diag_section_network,
        f("DNS servers", sensitive = true) { dnsServers() },
        f("VPN interfaces", sensitive = true) { vpnInterfaces() },
        f("Active interfaces & MTU", sensitive = true) { activeInterfacesAndMtu() },
        f("Wi-Fi SSID & BSSID", sensitive = true) { wifiSsidAndBssid() },
        f("Wi-Fi link & gateway", sensitive = true) { wifiLinkAndGateway() },
        f("Bluetooth & bonded devices", sensitive = true) { bluetoothDetails() },
        f("Cellular & SIM state", sensitive = true) { cellularAndSimState() },
        f("HTTP proxy") { httpProxy() },
        f("Active transport") { activeTransport() },
        f("Private DNS") { privateDns() },
        f("Metered") { metered() },
    )

    fun processResources(): ExtendedDiagnosticsSection = section(
        SystemInfoGroup.PROCESS_RESOURCES,
        R.string.nolegal_diag_section_process,
        f("Process PID / UID / 64-bit") { processIdentity() },
        f("SELinux process context") { processSelinuxContext() },
        f("Native heap") { formatBytes(Debug.getNativeHeapAllocatedSize()) },
        f("Java heap (used/max)") { javaHeap() },
        f("Process PSS & VmRSS") { processPssAndVmRss() },
        f("Open file descriptors") { openFdCount() },
        f("Active threads") { Thread.getAllStackTraces().size.toString() },
        f("CPU cores") { Runtime.getRuntime().availableProcessors().toString() },
        f("CPU governor & live freq") { cpuGovernorAndLiveFreq() },
        f("Total RAM") { totalRam() },
        f("Thermal status") { thermalStatus() },
        f("Sysfs thermal zones") { sysfsThermalZones() },
        f("Sysfs battery hardware") { sysfsBatteryHardware() },
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

    // --- Extended noLegal deep metrics (S4543) ---------------------------------------------------

    private fun adbWifiAndUsbState(): String {
        val adbWifi = onOff(Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1)
        val usbState = systemProperty("sys.usb.state")
        return "Wi-Fi ADB: $adbWifi, USB: $usbState"
    }

    private fun vbmetaAndVerity(): String {
        val vbmeta = systemProperty("ro.boot.vbmeta.device_state")
        val verity = systemProperty("ro.boot.veritymode")
        return "vbmeta=$vbmeta, verity=$verity"
    }

    private fun oemRomSkin(): String {
        val candidates = listOf(
            "OneUI" to systemProperty("ro.build.version.oneui"),
            "MIUI/HyperOS" to systemProperty("ro.miui.ui.version.name"),
            "EMUI" to systemProperty("ro.build.version.emui"),
            "OxygenOS" to systemProperty("ro.oxygen.version"),
            "ColorOS" to systemProperty("ro.build.version.opporom"),
        ).filter { (_, v) -> v != "unknown" }
        if (candidates.isNotEmpty()) {
            return candidates.joinToString(", ") { (k, v) -> "$k $v" }
        }
        return Build.DISPLAY?.takeIf { it.isNotBlank() } ?: "AOSP / stock"
    }

    private fun cryptoState(): String {
        val state = systemProperty("ro.crypto.state")
        val type = systemProperty("ro.crypto.type")
        return if (type != "unknown") "$state ($type)" else state
    }

    private fun kernelProcVersion(): String =
        File("/proc/version").takeIf { it.canRead() }?.readLines()?.firstOrNull()?.take(96) ?: "n/a"

    private fun nearbyWifiPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionState("android.permission.NEARBY_WIFI_DEVICES")
        } else {
            "n/a (< API 33)"
        }

    private fun bluetoothConnectPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionState("android.permission.BLUETOOTH_CONNECT")
        } else {
            permissionState("android.permission.BLUETOOTH")
        }

    private fun notificationListeners(): String {
        val raw = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.takeIf { it.isNotBlank() } ?: return "none"
        return raw.split(":").size.toString()
    }

    private fun stepCounterReading(): String {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return "n/a"
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return "unsupported"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.packageManager.checkPermission(
                "android.permission.ACTIVITY_RECOGNITION",
                context.packageName,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "supported (${sensor.name}, permission denied)"
        }
        var steps: Float? = null
        val latch = CountDownLatch(1)
        val thread = HandlerThread("nolegal-step-counter").apply { start() }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val v = event?.values?.firstOrNull()
                if (v != null) {
                    steps = v
                    latch.countDown()
                }
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
        }
        return try {
            val registered = sm.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST,
                Handler(thread.looper),
            )
            if (registered) {
                latch.await(STEP_SAMPLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            steps?.let { String.format(Locale.US, "%.0f steps", it) } ?: "supported (${sensor.name})"
        } finally {
            runCatching { sm.unregisterListener(listener) }
            runCatching { thread.quitSafely() }
        }
    }

    private fun stepAndMotionSensors(): String {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return "n/a"
        val checks = listOf(
            "Step detector" to Sensor.TYPE_STEP_DETECTOR,
            "Significant motion" to Sensor.TYPE_SIGNIFICANT_MOTION,
            "Stationary detect" to SENSOR_TYPE_STATIONARY_DETECT,
            "Motion detect" to SENSOR_TYPE_MOTION_DETECT,
        )
        val present = checks.mapNotNull { (label, type) ->
            label.takeIf { sm.getDefaultSensor(type) != null }
        }
        return if (present.isEmpty()) "none" else present.joinToString(", ")
    }

    private fun bodyAndHealthSensors(): String {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return "n/a"
        val checks = listOf(
            "Heart rate" to Sensor.TYPE_HEART_RATE,
            "Heart beat" to SENSOR_TYPE_HEART_BEAT,
            "Off-body detect" to SENSOR_TYPE_LOW_LATENCY_OFFBODY_DETECT,
        )
        val present = checks.mapNotNull { (label, type) ->
            sm.getDefaultSensor(type)?.let { "$label (${it.name})" }
        }
        return if (present.isEmpty()) "none" else present.joinToString(", ")
    }

    private fun installSourceDetails(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "n/a (< API 30)"
        val info = context.packageManager.getInstallSourceInfo(context.packageName)
        val initiating = info.initiatingPackageName ?: "none"
        val originating = info.originatingPackageName ?: "none"
        return "init=$initiating, orig=$originating"
    }

    private fun visiblePackageCounts(): String {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivitiesCompat(intent)
        val apps = resolved.mapNotNull { it.activityInfo?.applicationInfo }.distinctBy { it.packageName }
        val systemCount = apps.count {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }
        val userCount = (apps.size - systemCount).coerceAtLeast(0)
        return "${apps.size} launchable ($userCount user / $systemCount system)"
    }

    private fun defaultRoleHolders(): String {
        val pm = context.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePkg = pm.resolveActivityCompat(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName ?: "unknown"
        val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
        val browserPkg = pm.resolveActivityCompat(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName ?: "unknown"
        return "Home: $homePkg, Browser: $browserPkg"
    }

    private fun apkAndSplitLayout(): String {
        val appInfo = context.applicationInfo
        val baseSize = formatBytes(File(appInfo.sourceDir).length())
        val splits = appInfo.splitNames
        val splitInfo = if (splits.isNullOrEmpty()) "monolithic APK" else "${splits.size} split(s)"
        return "$baseSize ($splitInfo) @ ${appInfo.sourceDir}"
    }

    private fun nativeLibInventory(): String {
        val dir = context.applicationInfo.nativeLibraryDir?.let(::File) ?: return "n/a"
        val libs = dir.listFiles()?.filter { it.name.endsWith(".so") }.orEmpty()
        val totalBytes = libs.sumOf { it.length() }
        return "${libs.size} .so files (${formatBytes(totalBytes)})"
    }

    private fun gpuDriverProperties(): String {
        val egl = systemProperty("ro.hardware.egl")
        val vulkan = systemProperty("ro.hardware.vulkan")
        return "EGL=$egl, Vulkan=$vulkan"
    }

    private fun artVmFlags(): String {
        val heapSize = systemProperty("dalvik.vm.heapsize")
        val growth = systemProperty("dalvik.vm.heapgrowthlimit")
        val jit = systemProperty("dalvik.vm.usejit")
        return "max=$heapSize, growth=$growth, jit=$jit"
    }

    private fun widevineDrmSummary(): String {
        val widevineUuid = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
        if (!MediaDrm.isCryptoSchemeSupported(widevineUuid)) return "unsupported"
        val drm = MediaDrm(widevineUuid)
        return try {
            val level = runCatching { drm.getPropertyString("securityLevel") }.getOrDefault("unknown")
            val vendor = runCatching { drm.getPropertyString(MediaDrm.PROPERTY_VENDOR) }.getOrDefault("?")
            val version = runCatching { drm.getPropertyString(MediaDrm.PROPERTY_VERSION) }.getOrDefault("?")
            "$level ($vendor $version)"
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { drm.close() }
            } else {
                @Suppress("DEPRECATION")
                runCatching { drm.release() }
            }
        }
    }

    private fun securityProvidersSummary(): String {
        val providers = Security.getProviders().map { "${it.name} ${it.version}" }
        return if (providers.isEmpty()) "none" else providers.take(4).joinToString(", ")
    }

    private fun privateAndWakeupSensors(): String {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return "n/a"
        val all = sm.getSensorList(Sensor.TYPE_ALL)
        val privateCount = all.count { it.type >= Sensor.TYPE_DEVICE_PRIVATE_BASE }
        val wakeCount = all.count { it.isWakeUpSensor }
        val dynamic = yesNo(sm.isDynamicSensorDiscoverySupported)
        return "$privateCount vendor-private, $wakeCount wake-up, dynamic=$dynamic"
    }

    private fun coreFilesystemTypes(): String {
        val targets = setOf("/", "/system", "/vendor", "/data")
        val matched = mountLines().mapNotNull { line ->
            val parts = line.split(" ")
            val mp = parts.getOrNull(1) ?: return@mapNotNull null
            val fs = parts.getOrNull(2) ?: return@mapNotNull null
            val opts = parts.getOrNull(3)?.split(",")?.firstOrNull() ?: ""
            if (mp in targets) "$mp:$fs($opts)" else null
        }.distinct()
        return if (matched.isEmpty()) "n/a" else matched.joinToString(", ")
    }

    private fun zramAndSwapDetails(): String {
        val algo = File("/sys/block/zram0/comp_algorithm")
            .takeIf { it.canRead() }?.readText()?.trim() ?: "n/a"
        val diskBytes = File("/sys/block/zram0/disksize")
            .takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
        val diskText = diskBytes?.let(::formatBytes) ?: "n/a"
        return "zram0=$diskText, algo=$algo"
    }

    private fun blockIoScheduler(): String {
        val candidates = listOf("sda", "sdb", "mmcblk0", "dm-0", "nvme0n1")
        val found = candidates.mapNotNull { dev ->
            val f = File("/sys/block/$dev/queue/scheduler")
            f.takeIf { it.canRead() }?.readText()?.trim()?.let { "$dev: $it" }
        }
        return if (found.isEmpty()) "n/a" else found.joinToString("; ")
    }

    private fun directExternalAccess(): String {
        val root = Environment.getExternalStorageDirectory()
        return "read=${yesNo(root.canRead())}, write=${yesNo(root.canWrite())}"
    }

    private fun activeInterfacesAndMtu(): String {
        val list = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .map { "${it.name} (MTU ${it.mtu})" }
        return if (list.isEmpty()) "none" else list.joinToString(", ")
    }

    @Suppress("DEPRECATION")
    private fun currentWifiInfo(): WifiInfo? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let(cm::getNetworkCapabilities)
        val fromCaps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps?.transportInfo as? WifiInfo
        } else {
            null
        }
        if (fromCaps != null) return fromCaps
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return runCatching { wm?.connectionInfo }.getOrNull()
    }

    private fun wifiSsidAndBssid(): String {
        val info = currentWifiInfo() ?: return "n/a"
        val ssid = info.ssid?.trim('"')?.takeIf { it.isNotBlank() } ?: "<unknown ssid>"
        val bssid = info.bssid ?: "unknown"
        return "SSID=$ssid, BSSID=$bssid"
    }

    @Suppress("DEPRECATION")
    private fun wifiLinkAndGateway(): String {
        val info = currentWifiInfo()
        val speed = info?.linkSpeed ?: -1
        val freq = info?.frequency ?: -1
        val rssi = info?.rssi ?: -127
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val lp = cm?.activeNetwork?.let(cm::getLinkProperties)
        val gateway = lp?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: "n/a"
        return "${speed}Mbps, ${freq}MHz, ${rssi}dBm, gw=$gateway"
    }

    @Suppress("MissingPermission")
    private fun bluetoothDetails(): String {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return "n/a"
        val adapter = bm.adapter ?: return "unsupported"
        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        if (!enabled) return "off"
        val bonded = runCatching {
            adapter.bondedDevices?.mapNotNull { it.name?.takeIf(String::isNotBlank) }.orEmpty()
        }.getOrDefault(emptyList())
        val bondedSummary = if (bonded.isEmpty()) {
            "0 bonded"
        } else {
            val top = bonded.take(3).joinToString(", ")
            "${bonded.size} bonded ($top)"
        }
        return "on, $bondedSummary"
    }

    private fun cellularAndSimState(): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return "n/a"
        val simState = when (tm.simState) {
            TelephonyManager.SIM_STATE_READY -> "Ready"
            TelephonyManager.SIM_STATE_ABSENT -> "Absent"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK required"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked"
            else -> "State(${tm.simState})"
        }
        val carrier = tm.networkOperatorName?.takeIf { it.isNotBlank() } ?: "none"
        val simOp = tm.simOperatorName?.takeIf { it.isNotBlank() } ?: "none"
        val roaming = yesNo(tm.isNetworkRoaming)
        return "SIM=$simState ($simOp), Net=$carrier, roaming=$roaming"
    }

    private fun processIdentity(): String =
        "PID=${Process.myPid()}, UID=${Process.myUid()}, 64-bit=${yesNo(Process.is64Bit())}"

    private fun processSelinuxContext(): String =
        File("/proc/self/attr/current").takeIf { it.canRead() }?.readText()?.trim()?.trim('\u0000') ?: "n/a"

    private fun processPssAndVmRss(): String {
        val pssKb = Debug.getPss()
        val rssLine = File("/proc/self/status").takeIf { it.canRead() }
            ?.readLines()
            ?.firstOrNull { it.startsWith("VmRSS:") }
            ?.removePrefix("VmRSS:")
            ?.trim() ?: "n/a"
        return "PSS=${pssKb / 1024} MB, VmRSS=$rssLine"
    }

    private fun cpuGovernorAndLiveFreq(): String {
        val cores = Runtime.getRuntime().availableProcessors()
        val gov = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
            .takeIf { it.canRead() }?.readText()?.trim() ?: "unknown"
        val curMhz = (0 until cores).mapNotNull { idx ->
            File("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_cur_freq")
                .takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()?.let { "${it / 1000}MHz" }
        }.distinct()
        val curText = if (curMhz.isEmpty()) "n/a" else curMhz.take(4).joinToString(", ")
        return "gov=$gov, cur=$curText"
    }

    private fun sysfsThermalZones(): String {
        val dir = File("/sys/class/thermal")
        val zones = dir.listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.sortedBy { it.name }
            ?.mapNotNull { zone ->
                val type = File(zone, "type").takeIf { it.canRead() }?.readText()?.trim() ?: return@mapNotNull null
                val rawTemp = File(zone, "temp").takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
                    ?: return@mapNotNull null
                val tempC = if (rawTemp > 1000L) rawTemp / 1000.0 else rawTemp.toDouble()
                if (tempC in 1.0..150.0) String.format(Locale.US, "%s:%.1fC", type, tempC) else null
            }.orEmpty()
        return if (zones.isEmpty()) "n/a" else zones.take(6).joinToString(", ")
    }

    private fun sysfsBatteryHardware(): String {
        val base = File("/sys/class/power_supply/battery")
        if (!base.exists()) return "n/a"
        val designUah = File(base, "charge_full_design").takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
        val fullUah = File(base, "charge_full").takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull()
        val cycles = File(base, "cycle_count").takeIf { it.canRead() }?.readText()?.trim()
        val parts = mutableListOf<String>()
        if (designUah != null && designUah > 0) parts += "design=${designUah / 1000}mAh"
        if (fullUah != null && fullUah > 0) parts += "full=${fullUah / 1000}mAh"
        if (!cycles.isNullOrBlank()) parts += "cycles=$cycles"
        return if (parts.isEmpty()) "restricted by SELinux" else parts.joinToString(", ")
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

    private fun section(
        group: SystemInfoGroup,
        @StringRes titleRes: Int,
        vararg fields: ExtendedDiagnosticsField,
    ): ExtendedDiagnosticsSection =
        ExtendedDiagnosticsSection(context.getString(titleRes), fields.toList(), group)

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
        const val STEP_SAMPLE_TIMEOUT_MS = 80L
        const val SENSOR_TYPE_STATIONARY_DETECT = 29
        const val SENSOR_TYPE_MOTION_DETECT = 30
        const val SENSOR_TYPE_HEART_BEAT = 31
        const val SENSOR_TYPE_LOW_LATENCY_OFFBODY_DETECT = 34
    }
}
