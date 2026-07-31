import java.io.FileInputStream
import java.io.File
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class VerifyNoPlatformNamesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val denyListFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectRootMarker: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val tokens = denyListFile.asFile.get().readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { token -> token to buildPattern(token) }

        val baseline = baselineFile.asFile.get().readLines()
            .map(String::trimEnd)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { entry ->
                val tabIndex = entry.indexOf('\t')
                require(tabIndex > 0) {
                    "Invalid baseline entry in ${baselineFile.asFile.get()}: $entry"
                }
                val path = entry.substring(0, tabIndex).replace('\\', '/')
                val line = entry.substring(tabIndex + 1)
                path to line
            }
            .toSet()

        val violations = mutableListOf<String>()
        var scannedFilesCount = 0

        val projectRoot = projectRootMarker.asFile.get().parentFile.canonicalFile

        sourceFiles.files
            .filter(File::exists)
            .sortedBy { normalizePath(projectRoot, it) }
            .forEach { file ->
                scannedFilesCount += 1
                val relativePath = normalizePath(projectRoot, file)
                var previousNonEmptyTrimmed = ""

                file.readLines().forEachIndexed { index, rawLine ->
                    val trimmed = rawLine.trim()
                    val suppressed = trimmed.contains(SUPPRESSION_MARKER) ||
                        previousNonEmptyTrimmed.contains(SUPPRESSION_MARKER)
                    val commentOnly = isCommentOnly(file.extension.lowercase(), trimmed)

                    if (!suppressed && !commentOnly) {
                        val matches = tokens.mapNotNull { (token, pattern) ->
                            token.takeIf { pattern.containsMatchIn(rawLine) }
                        }

                        if (matches.isNotEmpty()) {
                            val baselineKey = relativePath to trimmed
                            if (!baseline.contains(baselineKey)) {
                                violations += "$relativePath:${index + 1} -> ${matches.joinToString(", ")} :: $trimmed"
                            }
                        }
                    }

                    if (trimmed.isNotEmpty()) {
                        previousNonEmptyTrimmed = trimmed
                    }
                }
            }

        val report = reportFile.asFile.get()
        report.parentFile.mkdirs()

        if (violations.isNotEmpty()) {
            report.writeText(violations.joinToString(System.lineSeparator()))
            throw GradleException(
                buildString {
                    appendLine("Forbidden platform literals detected in market sources or public FEATURES docs.")
                    appendLine("Remove the literal, add an inline '$SUPPRESSION_MARKER <reason>' marker, or add a reviewed legacy entry to app_v2/compliance/platform-name-baseline.txt.")
                    appendLine()
                    violations.take(MAX_PRINTED_VIOLATIONS).forEach { appendLine(it) }
                    val remaining = violations.size - MAX_PRINTED_VIOLATIONS
                    if (remaining > 0) {
                        appendLine(".. and $remaining more. Full report: ${report.invariantSeparatorsPath}")
                    }
                }
            )
        }

        report.writeText("OK scanned=$scannedFilesCount tokens=${tokens.size}${System.lineSeparator()}")
    }

    private fun buildPattern(token: String): Regex {
        val escaped = Regex.escape(token)
        val options = if (token.contains('.')) {
            setOf(RegexOption.IGNORE_CASE)
        } else {
            emptySet()
        }
        return Regex("(?<![A-Za-z0-9_])$escaped(?![A-Za-z0-9_])", options)
    }

    private fun isCommentOnly(extension: String, trimmed: String): Boolean {
        if (trimmed.isBlank()) {
            return false
        }
        return when (extension) {
            "kt", "java", "kts" -> trimmed.startsWith("//") ||
                trimmed.startsWith("/*") ||
                trimmed.startsWith("*") ||
                trimmed.startsWith("*/")
            "xml" -> trimmed.startsWith("<!--")
            else -> false
        }
    }

    private fun normalizePath(projectRoot: File, file: File): String =
        projectRoot.toPath().relativize(file.canonicalFile.toPath()).toString().replace('\\', '/')

    companion object {
        const val SUPPRESSION_MARKER = "allow-platform-literal:"
        private const val MAX_PRINTED_VIOLATIONS = 20
    }
}

plugins {
    id("com.android.application")
    id("com.android.legacy-kapt")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val defaultAppVersionCode = 260726210
val defaultAppVersionName = "2.60.7262.102"
val overrideAppVersionCode = providers.gradleProperty("fms.versionCode").orNull?.let { raw ->
    raw.toIntOrNull() ?: throw GradleException("Invalid -Pfms.versionCode value: '$raw'")
}
val overrideAppVersionName = providers.gradleProperty("fms.versionName").orNull
// S0630/S0671: the standard flavor now splits screen capture into two independent gates.
// fms.screenCapture controls the Play-shippable MediaProjection capture suite (consent activity,
// capture service, notification, post-processing). fms.edgeGestureOverlay controls only the
// standard edge-overlay launcher (SYSTEM_ALERT_WINDOW + specialUse FGS), deferred to S0672.
val screenCaptureStandardEnabled =
    (providers.gradleProperty("fms.screenCapture").orNull ?: "on").lowercase() != "off"
val edgeGestureOverlayStandardEnabled =
    (providers.gradleProperty("fms.edgeGestureOverlay").orNull ?: "off").lowercase() != "off"
// S0672: independent QS-tile fallback trigger (no specialUse / SYSTEM_ALERT_WINDOW), enabled instead of
// the strip if Play rejects the specialUse declaration. Standard only.
val edgeGestureTileStandardEnabled =
    (providers.gradleProperty("fms.edgeGestureTile").orNull ?: "off").lowercase() != "off"
val isXrNativeBuildRequested = providers.gradleProperty("fms.xrNative").orNull?.let { raw ->
    when {
        raw.equals("true", ignoreCase = true) -> true
        raw.equals("false", ignoreCase = true) -> false
        else -> throw GradleException("Invalid -Pfms.xrNative value: '$raw'")
    }
} ?: gradle.startParameter.taskNames.any { taskName ->
    val t = taskName.lowercase()
    t.contains("nolegal") || t.contains("vr")
}

fun findRootSecretFile(vararg relativePaths: String): File? =
    relativePaths
        .asSequence()
        .map(rootProject::file)
        .firstOrNull(File::exists)

fun resolveSiblingPath(baseFile: File, rawPath: String): File {
    val direct = File(rawPath)
    return if (direct.isAbsolute) direct else File(baseFile.parentFile, rawPath).normalize()
}

android {
    val releaseKeystorePropertiesFile = findRootSecretFile(".secrets/keystore.properties", "keystore.properties")
    val hasReleaseKeystore = releaseKeystorePropertiesFile != null
    val debugKeystorePropertiesFile = rootProject.file("debug.keystore.properties")
    val hasCustomDebugKeystore = debugKeystorePropertiesFile.exists()
    val requestedTasks = gradle.startParameter.taskNames
    val requiresReleaseSigning = requestedTasks.any {
        val t = it.lowercase()
        t.contains("release") && (t.contains("bundle") || t.contains("sign") || t.contains("assemble"))
    }

    namespace = "com.sza.fastmediasorter"
    // CRITICAL: Play Store target-API mandate (deadline 2026-08-31, S1149) - Android 16 / API 36.
    // Compiles against installed base SDK android-36.1; do not downgrade.
    compileSdk = 36
    // NDK r27c required: first NDK release that ships a 16 KB page-size aligned libc++_shared.so
    // (Google Play requirement since Nov 1 2025 for apps targeting Android 15+).
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.sza.fastmediasorter"
        // Minimum supported Android 8.0 (API 26). Legacy flavor covers API 23-25.
        minSdk = 26
        // Keep targetSdk aligned with compileSdk
        // CRITICAL: Play Store compliance (Android 16 / API 36, S1149); minSdk stays 26/23 - device reach unchanged
        targetSdk = 36
        // Local fast checks keep these defaults stable so configuration-cache reuse survives
        // across repeated debug builds. Artifact-oriented helper scripts can override them
        // via -Pfms.versionCode / -Pfms.versionName when a timestamped package is needed.
        // versionName format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151 for 2026/02/05 01:51)
        // versionCode format: YYMMDDHHm (e.g., 260205015 for 2026/02/05 01:51)
        // Note: YYMMDDHHmm overflows Int32, using first digit of minutes only
        versionCode = overrideAppVersionCode ?: defaultAppVersionCode
        versionName = overrideAppVersionName ?: defaultAppVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Note: locale filtering moved to androidResources.localeFilters (resourceConfigurations deprecated)
        
        // Screen size support (Android automatically selects resources)
        // - values-sw480dp: Compact screens (480x480+), smartwatches, small tablets
        // - values: Default/normal screens
        // - values-sw600dp: Tablets (600dp width+)
        // - values-sw720dp: Large tablets (720dp width+)
        
        // Dropbox App Key - User must provide a valid key
        manifestPlaceholders["dropboxAppKey"] = "dpy64e70kqobr6x"

        // === STARTUP DEBUG INFO ===
        // Owner trigger - read from local.properties (excluded from VCS)
        // If local.properties is absent or the key is missing, field is empty → no special behavior
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localProps.load(FileInputStream(localPropsFile))
        }
        buildConfigField("String", "OWNER_TRIGGER", "\"${localProps.getProperty("sza.owner.trigger", "")}\"")

        // Git Hash
        // === STARTUP DEBUG INFO ===
        // Git Hash (Simplified for stability)
        buildConfigField("String", "GIT_HASH", "\"Unknown\"")

        // Build Time (Current)
        buildConfigField("String", "BUILD_TIME", "\"Unknown\"")
        buildConfigField("boolean", "IS_NO_LEGAL_FLAVOR", "false")
    }
    
    // Product Flavors: Different app versions for different use cases.
    //
    // S0232 applicationId policy: cloud-enabled flavors that are NOT published to a store
    // (noLegal, vr) share applicationId = com.sza.fastmediasorter with `standard`. They
    // are alternate builds of the same product, not separately distributed apps. A single
    // set of OAuth / MSAL / Dropbox registrations covers all of them. (S0250: vrUnlicensed
    // archived; noLegal now owns the sideload-VR distribution channel.)
    // Store-published flavors (photos, legacy) keep their applicationIdSuffix because the
    // Store binds the listing identity to it. lite has no cloud surface and is unaffected.
    // Any new signing keystore additionally requires:
    //   (a) a new <intent-filter> path in src/main/AndroidManifest.xml BrowserTabActivity, and
    //   (b) a matching redirect URI registered in Azure (OneDrive), Google Cloud (Drive) and
    //       Dropbox app consoles.
    flavorDimensions += listOf("version")

    productFlavors {
        // XR native build is enabled only for vr/noLegal task graphs. Standard/lite/photos/legacy
        // leave the entire CMake pipeline disabled so local debug loops avoid per-ABI no-op work.
        // ABI selection is handled per-flavor (not per-buildType) because AGP merges
        // flavor+buildType ndk.abiFilters via UNION, not intersection. Setting abiFilters
        // on a buildType would leak extra slices (e.g. x86) into VR AABs. Keeping ABI
        // configuration flavor-local gives each flavor exactly what Play delivers to users.
        fun com.android.build.api.dsl.ProductFlavor.disableNativeBuild() {
            // Distribution ABIs for non-VR flavors: all four production ABIs.
            // Covers Android 8+ phones/tablets (arm64-v8a + armeabi-v7a), Chromebooks
            // and emulators (x86/x86_64). AAB per-device delivery keeps user download size
            // unchanged vs single-ABI. See PLAN/spec_ffmpeg-dts-multi-abi.md.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
        }

        // ===== STANDARD (Full Featured) =====
        create("standard") {
            dimension = "version"
            isDefault = true
            disableNativeBuild()
            // No applicationIdSuffix = keeps current package names
            // No versionNameSuffix = keeps current version format
            // Full feature set: Videos, Audio, Images, Cloud, Documents, Animations
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_STREAMS", "true")     // S0565: Трансляции entry-point
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")
            buildConfigField("boolean", "SUPPORT_LOCAL_NETWORK", "true")
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
            buildConfigField("boolean", "ENABLE_EPUB", "true")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "true")
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "true")
            // AAR rebuilt with NDK r27c + -Wl,-z,max-page-size=16384 (LOAD Align=0x4000).
            // 16 KB compatible - safe for Google Play.
            buildConfigField("boolean", "SUPPORT_CAST", "true")
        }

        // ===== NO-LEGAL (Sideload-only full build: standard + VR + GPL extractors) =====
        // S0156 ADR-8: single APK covers both Quest (arm64 + OpenXR) and phones (all ABIs).
        // ndk.abiFilters = all 4 ABIs → APK installs on any device.
        // cmake.abiFilters = arm64-v8a only → diagnostic XR native runtime compiles for Quest
        //   slice only; non-arm64 slices simply omit libfms_diagnostic_xr.so - VR entry points
        //   must graceful-fallback to PlayerActivity when System.loadLibrary("fms_diagnostic_xr")
        //   throws UnsatisfiedLinkError.
        create("noLegal") {
            dimension = "version"
            // S0232: no applicationIdSuffix - noLegal shares com.sza.fastmediasorter with
            // standard so cloud OAuth/MSAL/Dropbox registrations cover it without per-flavor
            // setup. See policy comment above productFlavors block.
            versionNameSuffix = "-NoLegal"
            // NewPipeExtractor (noLegal-only dep) drags Rhino + jsoup, which reference
            // optional JVM classes absent on Android (java.beans.*, com.google.re2j.*).
            // Flavor-scoped -dontwarn rules keep these warnings out of standard/vr R8.
            proguardFiles("proguard-nolegal.pro")
            // S0174: Chaquopy 17.x Python 3.12 ships wheels only for arm64-v8a and x86_64.
            // armeabi-v7a and x86 are excluded - 32-bit ARMv7 devices (pre-2017) and x86
            // emulators are not supported for the Python runtime. noLegal is a sideload-only
            // flavor targeting modern devices (arm64) and Quest headsets (arm64).
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
            if (isXrNativeBuildRequested) {
                externalNativeBuild {
                    cmake {
                        // S0249 Phase 02: diagnostic XR native runtime (fms_diagnostic_xr) - same
                        // JNI bridge as vr flavor. OpenXR loader AAR ships arm64-v8a only.
                        targets += listOf("fms_diagnostic_xr")
                        // Restrict CMake configure to arm64-v8a so AGP does not attempt to build
                        // fms_diagnostic_xr for armeabi-v7a/x86/x86_64 where the OpenXR slice is absent.
                        abiFilters += listOf("arm64-v8a")
                        cppFlags += listOf("-std=c++17", "-Wall", "-Werror")
                        arguments += listOf(
                            "-DANDROID_STL=c++_shared",
                            "-DANDROID_PLATFORM=android-26",
                            // S0249 Phase 02: gates the fms_diagnostic_xr SHARED target in
                            // src/vr/cpp/CMakeLists.txt. Without this flag CMake emits no targets
                            // and AGP fails with "Unexpected native build target …".
                            "-DFMS_BUILD_XR_RUNTIME=ON",
                            // Revision 4: invalidates stale .tmp cmake cache from prior vr runs.
                            "-DFMS_BUILD_REVISION=4"
                        )
                    }
                }
            }
            // S0117: keep the full standard capability surface while isolating
            // site-specific/GPL code behind a dedicated sideload-only flavor.
            // S0250: noLegal owns the sideload VR-capable surface (replaces archived
            // vrUnlicensed flavor). VR feature UI is present in the binary; individual
            // VR controls are gated at runtime by XrRuntimeAvailability so non-XR
            // devices see them disabled with the standard "device unsupported" hint.
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_STREAMS", "true")     // S0565: Трансляции entry-point
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")
            buildConfigField("boolean", "SUPPORT_LOCAL_NETWORK", "true")
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
            buildConfigField("boolean", "ENABLE_EPUB", "true")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "true")
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "true")
            buildConfigField("boolean", "VR_UI_COMPOSITION_LAYER_ENABLED", "true")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "true")
            buildConfigField("boolean", "SUPPORT_CAST", "true")
            buildConfigField("boolean", "IS_NO_LEGAL_FLAVOR", "true")
        }

        // ===== LITE (Lightweight, Local Files Only) =====
        create("lite") {
            dimension = "version"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-Lite"
            disableNativeBuild()
            // Local files only: No cloud, no heavy features
            // Target: Users with limited storage/bandwidth, older devices
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_STREAMS", "false")    // S0575: Streams feature UI hidden in lite (streamingDisabled pipeline unchanged)
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "false") // Excluded per S0100 §6
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "false")        // No cloud providers
            buildConfigField("boolean", "SUPPORT_LOCAL_NETWORK", "false") // S0448: local-files-only, no SMB/SFTP/FTP
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "false")    // No PDF/EPUB/Text
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "false")    // No animations for speed
            buildConfigField("boolean", "ENABLE_EPUB", "false")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "false")   // No ML Kit
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "false")  // No background audio in lite
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "false")  // No default player in lite
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "false")  // No wearable in lite
            buildConfigField("boolean", "SUPPORT_CAST", "true")
        }

        // ===== PHOTOS (Images Only, with Cloud Support) =====
        create("photos") {
            dimension = "version"
            applicationIdSuffix = ".photos"
            versionNameSuffix = "-Photos"
            disableNativeBuild()
            // Images + GIFs only, no video/audio player
            // Target: Photo management, cloud photo backup/sync
            buildConfigField("boolean", "SUPPORT_VIDEO", "false")       // No video player
            buildConfigField("boolean", "SUPPORT_AUDIO", "false")       // No audio player
            buildConfigField("boolean", "SUPPORT_STREAMS", "false")     // S0565: no Трансляции entry-point in photos
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "false") // No audio support
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")       // Full image support
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")        // Cloud for photo backup
            buildConfigField("boolean", "SUPPORT_LOCAL_NETWORK", "true") // Network photo shares (SMB/SFTP/FTP)
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "false")   // No documents
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")    // Keep animations for UI
            buildConfigField("boolean", "ENABLE_EPUB", "false")         // No EPUB
            buildConfigField("boolean", "ENABLE_TRANSLATION", "false")  // No translation needed
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "false")  // No audio support
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")  // Image-only default player
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "false")  // No wearable in photos
            buildConfigField("boolean", "SUPPORT_CAST", "true")
        }

        // ===== LEGACY (Full Features, Android 6.0+) =====
        create("legacy") {
            dimension = "version"
            // CRITICAL: Do not change - legacy flavor for Android 6/7 devices (API 23-25)
            // Standard flavor covers API 26+ (Android 8+); legacy covers the remaining API 23-25 gap.
            minSdk = 23  // Android 6.0 (Marshmallow)
            applicationIdSuffix = ".legacy"
            versionNameSuffix = "-Legacy"
            disableNativeBuild()
            // Full feature set but compatible with older Android versions
            // Target: Users with older Android devices (API 23-25)
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_STREAMS", "true")     // S0565: Трансляции entry-point
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")
            buildConfigField("boolean", "SUPPORT_LOCAL_NETWORK", "true")
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
            buildConfigField("boolean", "ENABLE_EPUB", "true")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "true")
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "true")
            // AAR rebuilt with NDK r27c + -Wl,-z,max-page-size=16384 (LOAD Align=0x4000).
            buildConfigField("boolean", "SUPPORT_CAST", "true")
        }

        // ===== VR (Full Features + OpenXR Headset Rendering) =====
        create("vr") {
            dimension = "version"
            // S0232: no applicationIdSuffix - vr shares com.sza.fastmediasorter with standard
            // for cloud OAuth identity. Re-add a .vr suffix here if/when this flavor lands on
            // Meta Horizon Store (the Store binds the listing identity to applicationId);
            // at that point a dedicated Azure/Google/Dropbox app registration becomes required.
            versionNameSuffix = "-VR"
            // Meta Quest 2/3/Pro use arm64-v8a exclusively; skip 32-bit to halve APK size.
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            if (isXrNativeBuildRequested) {
                externalNativeBuild {
                    cmake {
                        // S0249 Phase 02: build the diagnostic XR native runtime (fms_diagnostic_xr).
                        // OpenXR loader ships prebuilt in the Khronos AAR via prefab.
                        targets += listOf("fms_diagnostic_xr")
                        // OpenXR loader AAR ships only arm64-v8a - restrict CMake config to match,
                        // otherwise AGP tries to build fms_diagnostic_xr for every ABI in the buildType
                        // filter (armeabi-v7a/x86/x86_64 inherited from release buildType) and fails
                        // because those OpenXR slices do not exist. ndk.abiFilters above only
                        // governs packaging; externalNativeBuild.cmake.abiFilters governs configure.
                        abiFilters += listOf("arm64-v8a")
                        cppFlags += listOf("-std=c++17", "-Wall", "-Werror")
                        arguments += listOf(
                            "-DANDROID_STL=c++_shared",
                            "-DANDROID_PLATFORM=android-26",
                            // Gate fms_diagnostic_xr target in src/vr/cpp/CMakeLists.txt: non-vr
                            // flavors omit this flag so CMake configure succeeds without the
                            // Khronos OpenXR AAR on the prefab classpath.
                            "-DFMS_BUILD_XR_RUNTIME=ON",
                            // Force new cmake config hash to avoid stale .tmp file lock (2026-04-21)
                            "-DFMS_BUILD_REVISION=3"
                        )
                    }
                }
            }
            // S0241: keep the VR visual shell/source-set overlay buildable while routing the
            // shared runtime through the same player path as standard until the rewrite lands.
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_STREAMS", "true")     // S0565: Трансляции entry-point
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "true")
            buildConfigField("boolean", "SUPPORT_LOCAL_NETWORK", "true")
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
            buildConfigField("boolean", "ENABLE_EPUB", "true")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "true")
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")
            buildConfigField("boolean", "VR_UI_COMPOSITION_LAYER_ENABLED", "false")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "false")  // Headset has no paired watch
            // AAR rebuilt with NDK r27c + -Wl,-z,max-page-size=16384 (LOAD Align=0x4000).
            buildConfigField("boolean", "SUPPORT_CAST", "false") // Horizon OS lacks Google Play Services Cast module
        }

        // S0250: flavor `vrUnlicensed` was archived (2026-05-19). Its role - sideload-only
        // VR-capable build - is now fulfilled by `noLegal` (full VR feature surface, runtime
        // XR-gated via XrDetectionFacade). The `vr` flavor remains as the Store-published
        // (Meta Horizon Store / Google Play AAB) channel, kept Store-clean (no GPL extractors,
        // no Python runtime). See PLAN/S0250_nolegal-vr-unification.md.
    }

    // AGP does not inherit flavor source sets automatically, so each flavor explicitly maps
    // to one of the shared streaming/cloud source-sets below.
    //
    // S0116 §3.2: streamingEnabled - Media3 HLS/DASH + MediaMuxer; streamingDisabled - NoOp pipeline for lite/photos.
    // S0200: cloudEnabled - Credential Manager Google identity + Drive auth; cloudDisabled - no-op identity for lite.
    // Both shared source-sets are mounted into every flavor that needs them; AGP does not
    // expose pseudo-flavor inheritance, so each flavor explicitly maps to one of the two.
    sourceSets {
        // S1009: expose the exported Room schemas as androidTest assets so MigrationTestHelper can
        // load <db-fqcn>/<version>.json at runtime and validate the 43 -> 44 migration on device.
        getByName("androidTest") {
            assets.directories.add("schemas")
        }
        getByName("standard") {
            kotlin.directories.add("src/streamingEnabled/java")
            kotlin.directories.add("src/cloudEnabled/java")
            // S0403: Google Cast SDK seam impl (CastMediaManagerImpl); foss mounts castDisabled.
            kotlin.directories.add("src/castEnabled/java")
            // S0403: GMS-backed Wear Data Layer bridge; foss / non-Wear flavors mount wearStub.
            kotlin.directories.add("src/wearGms/java")
            kotlin.directories.add("src/ocrEnabled/java")
            kotlin.directories.add("src/translationEnabled/java")
            kotlin.directories.add("src/translationMlKit/java")
            // S0250 / S0245 wiring closure: NoOp XR Hilt bindings live in src/vrStub/java.
            // Without this mount, any @Inject of XrEnvironmentDetector / XrDetectionFacade /
            // XrEntryGateway in src/main/java/** would fail to resolve in this flavor.
            kotlin.directories.add("src/vrStub/java")
            // S0559: the confirmable MediaProjection capture engine (ScreenCaptureConsentActivity +
            // ScreenCaptureService) is now shared with the store flavor via a menu-triggered path.
            // Only the engine moves here; the overlay-strip launcher + accessibility silent capture
            // (SYSTEM_ALERT_WINDOW / specialUse / a11y) stay noLegal-only in src/noLegal.
            // S0671: keep the Play-safe MediaProjection suite independent from the standard-only edge
            // overlay launcher so standard can ship capture while S0672 keeps the overlay OFF.
            if (screenCaptureStandardEnabled) {
                kotlin.directories.add("src/screenCapture/java")
                res.directories.add("src/screenCapture/res")
            }
            if (edgeGestureOverlayStandardEnabled) {
                // Standard-only edge-gesture overlay controller + its @IntoSet binding, relocated from
                // src/standard so the overlay can stay disabled independently from the capture suite.
                kotlin.directories.add("src/standardScreenCapture/java")
            }
            if (screenCaptureStandardEnabled && edgeGestureTileStandardEnabled) {
                // S0672: standard-only QS-tile fallback trigger. Needs BOTH flags because the tile
                // launches ScreenCaptureConsentActivity from src/screenCapture (present only when the
                // capture suite is on).
                kotlin.directories.add("src/standardEdgeTile/java")
            }
            // S0404: launcher-mode home surface (HOME-role activity, desktop grid, taskbar). Flavors
            // without it mount src/launcherDisabled, which binds the no-op capability contract.
            kotlin.directories.add("src/launcherEnabled/java")
            res.directories.add("src/launcherEnabled/res")
        }
        getByName("noLegal") {
            // S0156: noLegal = standard + VR + sideload-only capabilities.
            // S0250: noLegal owns the sideload VR-capable surface (replaces vrUnlicensed).
            // Mount vr source set so VrPlayerActivity, OpenXR bridge, and XR Hilt bindings
            // are available.
            kotlin.directories.add("src/vr/java")
            res.directories.add("src/vr/res")
            manifest.srcFile("src/vr/AndroidManifest.xml")
            kotlin.directories.add("src/streamingEnabled/java")
            kotlin.directories.add("src/cloudEnabled/java")
            // S0403: Google Cast SDK seam impl (castEnabled manifest injected via addStaticManifestFile
            // below - manifest.srcFile above is a set, so it cannot also mount the cast overlay).
            kotlin.directories.add("src/castEnabled/java")
            // S0403: GMS-backed Wear Data Layer bridge (wearGms manifest injected via
            // addStaticManifestFile below, same reason as castEnabled - the manifest.srcFile above
            // is a set). foss / non-Wear flavors mount wearStub instead.
            kotlin.directories.add("src/wearGms/java")
            kotlin.directories.add("src/ocrEnabled/java")
            kotlin.directories.add("src/translationEnabled/java")
            kotlin.directories.add("src/translationMlKit/java")
            // S0418: shared screencapture machinery (moved out of src/noLegal). noLegal keeps its own
            // accessibility capture path + a11y-aware controller in src/noLegal/java.
            kotlin.directories.add("src/screenCapture/java")
            res.directories.add("src/screenCapture/res")
            // S0404: launcher-mode home surface - noLegal is the all-inclusive sideload superset.
            kotlin.directories.add("src/launcherEnabled/java")
            res.directories.add("src/launcherEnabled/res")
        }
        getByName("legacy") {
            kotlin.directories.add("src/streamingEnabled/java")
            kotlin.directories.add("src/cloudEnabled/java")
            // S0403: Google Cast SDK seam impl (CastMediaManagerImpl); foss mounts castDisabled.
            kotlin.directories.add("src/castEnabled/java")
            // S0403: GMS-backed Wear Data Layer bridge; foss / non-Wear flavors mount wearStub.
            kotlin.directories.add("src/wearGms/java")
            kotlin.directories.add("src/ocrEnabled/java")
            kotlin.directories.add("src/translationEnabled/java")
            kotlin.directories.add("src/translationMlKit/java")
            kotlin.directories.add("src/vrStub/java")
            // S0404: no launcher-mode surface in this flavor - mount the no-op capability contract.
            kotlin.directories.add("src/launcherDisabled/java")
        }
        getByName("vr") {
            kotlin.directories.add("src/streamingEnabled/java")
            kotlin.directories.add("src/cloudEnabled/java")
            // S0403: Google Cast SDK seam impl (CastMediaManagerImpl); foss mounts castDisabled.
            kotlin.directories.add("src/castEnabled/java")
            // S0403: vr has no Wear companion -> mount the wearStub no-op (no Play Services Wearable).
            kotlin.directories.add("src/wearStub/java")
            kotlin.directories.add("src/ocrEnabled/java")
            kotlin.directories.add("src/translationEnabled/java")
            kotlin.directories.add("src/translationMlKit/java")
            kotlin.directories.add("src/vrOnly/java")
            // S0404: vr has its own OpenXR shell and the headset's system launcher - no Android
            // launcher mode here (strategic ADR-1). Mount the no-op capability contract.
            kotlin.directories.add("src/launcherDisabled/java")
        }
        getByName("photos") {
            kotlin.directories.add("src/streamingDisabled/java")
            kotlin.directories.add("src/cloudEnabled/java")
            // S0403: Google Cast SDK seam impl (CastMediaManagerImpl); foss mounts castDisabled.
            kotlin.directories.add("src/castEnabled/java")
            // S0403: photos has no Wear companion -> mount the wearStub no-op.
            kotlin.directories.add("src/wearStub/java")
            kotlin.directories.add("src/ocrDisabled/java")
            kotlin.directories.add("src/vrStub/java")
            // S0423 release scope: S0418 screencapture stays noLegal-only for now (Play review risk
            // from SPECIAL_USE/SYSTEM_ALERT_WINDOW); not mounted into the photos store flavor.
            // S0404: no launcher-mode surface in this flavor - mount the no-op capability contract.
            kotlin.directories.add("src/launcherDisabled/java")
        }
        getByName("lite") {
            kotlin.directories.add("src/streamingDisabled/java")
            kotlin.directories.add("src/cloudDisabled/java")
            // S0403: lite ships Cast (video flavor), so it mounts the GMS-backed castEnabled impl.
            kotlin.directories.add("src/castEnabled/java")
            // S0403: lite has no Wear companion (SUPPORT_WEAR_COMPANION=false) -> wearStub no-op.
            kotlin.directories.add("src/wearStub/java")
            kotlin.directories.add("src/ocrDisabled/java")
            kotlin.directories.add("src/vrStub/java")
            // S0404: no launcher-mode surface in this flavor - mount the no-op capability contract.
            kotlin.directories.add("src/launcherDisabled/java")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Required for Robolectric
            isReturnDefaultValues = true
            // Forward the doc-export toggles (S0440 settings manifest, S0815 icon inventory) to the
            // test JVM; Gradle does not propagate -D system properties to test workers by default.
            all {
                // S1244: the test worker is a SEPARATE process. It inherits neither
                // org.gradle.jvmargs (-Xmx6g, the daemon) nor kotlin.daemon.jvm.options (-Xmx4g,
                // the compile daemon) - with nothing set here it ran on Gradle's 512 MB default.
                // ~200 Robolectric classes in one 512 MB JVM exhausted the heap around
                // `data.remote.ftp.*`, killing the worker; Gradle still printed a normal-looking
                // "N tests completed" line, so the `domain`/`ui`/`util` packages silently never ran.
                it.maxHeapSize = "2g"
                // S1253: bound the worker's lifetime, not just its heap. Robolectric keeps a
                // sandbox classloader per test class (metaspace + native, invisible to -Xmx);
                // past ~350 of 409 classes in one process the peak exceeds what the host can
                // commit whenever emulators or a sibling build are live, and the JVM aborts
                // natively - exit value 10, no Java-level OOM, truncated suite. Recycling the
                // worker every 100 classes caps that peak; cost is a few JVM warmups per run.
                it.forkEvery = 100L
                it.systemProperty(
                    "settings.manifest.generate",
                    System.getProperty("settings.manifest.generate") ?: "false"
                )
                it.systemProperty(
                    "icon.inventory.generate",
                    System.getProperty("icon.inventory.generate") ?: "false"
                )
            }
        }
    }

    signingConfigs {
        create("debugCustom") {
            if (hasCustomDebugKeystore) {
                val debugProps = Properties()
                FileInputStream(debugKeystorePropertiesFile).use { inputStream ->
                    debugProps.load(inputStream)
                }

                val debugKeyAlias = debugProps.getProperty("keyAlias")
                val debugKeyPassword = debugProps.getProperty("keyPassword")
                val debugStorePassword = debugProps.getProperty("storePassword")
                val debugStorePath = debugProps.getProperty("storeFile")

                if (debugKeyAlias.isNullOrBlank() || debugKeyPassword.isNullOrBlank() ||
                    debugStorePassword.isNullOrBlank() || debugStorePath.isNullOrBlank()) {
                    throw GradleException(
                        "debug.keystore.properties is incomplete. Required keys: keyAlias, keyPassword, storePassword, storeFile"
                    )
                }

                val resolvedDebugStore = file(debugStorePath)
                if (!resolvedDebugStore.exists()) {
                    throw GradleException(
                        "Debug keystore file not found: ${resolvedDebugStore.absolutePath}. " +
                        "Fix storeFile in debug.keystore.properties."
                    )
                }

                keyAlias = debugKeyAlias
                keyPassword = debugKeyPassword
                storeFile = resolvedDebugStore
                storePassword = debugStorePassword
            }
        }

        create("release") {
            val keystorePropertiesFile = releaseKeystorePropertiesFile
            if (keystorePropertiesFile != null) {
                val keystoreProperties = Properties()
                FileInputStream(keystorePropertiesFile).use { inputStream ->
                    keystoreProperties.load(inputStream)
                }

                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = resolveSiblingPath(
                    keystorePropertiesFile,
                    keystoreProperties["storeFile"] as String
                )
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // Debug uses dedicated package/applicationId for separate OAuth client and signing setup.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            isDebuggable = true
            isMinifyEnabled = false
            // ABI selection is flavor-local (see productFlavors block) - not set here because
            // AGP merges buildType+flavor abiFilters as UNION, not intersection, which would
            // leak non-VR ABIs into VR debug AABs.
            if (hasCustomDebugKeystore) {
                signingConfig = signingConfigs.getByName("debugCustom")
            }
            buildConfigField("boolean", "LOG_SMB_IO", "false")
            buildConfigField("boolean", "LOG_NETWORK_THUMBNAILS", "true")
            buildConfigField("boolean", "LOG_LINK_DOWNLOAD", "true")
            buildConfigField("boolean", "ENABLE_LEAKCANARY", "false")
            buildConfigField("boolean", "ENABLE_SCHEDULED_OPERATIONS", "true")
            buildConfigField("boolean", "ENABLE_BACKGROUND_AUDIO", "true")
            // Dedicated Dropbox app key for debug (com.sza.fastmediasorter.debug).
            // Prevents the "Security alert" triggered when debug + release are both installed
            // and both register for the same db-<appKey>:// URI scheme.
            manifestPlaceholders["dropboxAppKey"] = "u43ocp6pqvwaiu1"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "LOG_SMB_IO", "false")
            buildConfigField("boolean", "LOG_NETWORK_THUMBNAILS", "false")
            buildConfigField("boolean", "LOG_LINK_DOWNLOAD", "false")
            buildConfigField("boolean", "ENABLE_SCHEDULED_OPERATIONS", "true")
            buildConfigField("boolean", "ENABLE_BACKGROUND_AUDIO", "true")
            ndk {
                debugSymbolLevel = "FULL"
                // ABI selection is flavor-local (see productFlavors block) - AGP merges
                // buildType+flavor ndk.abiFilters as UNION, so a buildType-level list
                // would leak non-VR ABIs into VR AABs. Each flavor declares its own ABIs.
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else if (requiresReleaseSigning) {
                throw GradleException(
                    "Release signing is requested, but .secrets/keystore.properties is missing " +
                    "(root keystore.properties is still accepted as a fallback). " +
                    "Create keystore.properties with keyAlias/keyPassword/storeFile/storePassword and ensure storeFile exists."
                )
            }
        }
        create("staging") {
            initWith(getByName("release"))
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-STAGING"
            matchingFallbacks += listOf("release")
        }
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            versionNameSuffix = "-BENCHMARK"
            matchingFallbacks += listOf("release")
            signingConfig = if (hasCustomDebugKeystore) {
                signingConfigs.getByName("debugCustom")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        // Required for java.time.* on API 23-25 (legacy flavor). API 26+ has native support.
        isCoreLibraryDesugaringEnabled = true
        // CRITICAL: Do not change - Java 17 required for Kotlin 1.9.24 and modern Android libraries
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
        // Prefab is needed only when the XR native bridge is part of the requested task graph.
        prefab = isXrNativeBuildRequested
    }

    if (isXrNativeBuildRequested) {
        // Native build (vr/noLegal only; standard/lite/photos/legacy skip the entire pipeline).
        // CMake glues Kotlin JNI calls to the OpenXR loader shipped in the AAR.
        externalNativeBuild {
            cmake {
                path = file("src/vr/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/versions/*/OSGI-INF/MANIFEST.MF" // BC & JSch conflict
            // Исключаем дубликаты нативных библиотек BouncyCastle
            pickFirsts += "**/*.so"
            
            // APK Size Optimization (S0385): drop unused BouncyCastle post-quantum PICNIC
            // data tables (~1.22 MB of lowmcL1/L3/L5 .bin.properties) and the German locale of
            // the X.509 cert-path reviewer messages. No code references org.bouncycastle.pqc;
            // SMB/SFTP use only classical BC crypto, so these data resources are never loaded.
            excludes += "org/bouncycastle/pqc/crypto/picnic/**"
            excludes += "org/bouncycastle/x509/CertPathReviewerMessages_de.properties"
        }
        
        jniLibs {
            // 16 KB page size alignment for Android 15+ compatibility (required for Google Play since Nov 1, 2025)
            // Ensures all native libraries (.so) have LOAD segments aligned to 16 KB boundaries
            // Affects Tesseract OCR libraries: libjpeg.so, libleptonica.so, libpng.so, libtesseract.so
            // Windows NDK linker can leave locked sibling temp files (*.tmp) next to the
            // final shared library in intermediates/cxx; mergeNativeLibs must ignore them.
            excludes += "**/*.tmp"

            // S0971 (2026-07-06): re-bundle the native sets back into the base artifact. The S0386
            // Phase 05 de-bundle stripped the OCR (Tesseract/PaddleOCR) and FFmpeg-DTS `.so` and relied
            // on a runtime GitHub download, but Google Play policy (Device & Network Abuse) forbids
            // fetching executable `.so` from a non-Play source, so on a Play install those modules were
            // permanently unavailable (S0401's Play-compliant delivery was never built). Owner decision:
            // ship the `.so` in the APK/AAB again (store AABs stay lean via per-ABI delivery). The `.so`
            // ride the AAR native libs on the compile path, so simply not excluding them re-packages
            // them; lite/photos have no OCR/DTS deps, so this is a no-op there. The sets are now declared
            // in each flavor's *BundledDeliverableSetsModule.bundledSets(), so the delivery runtime
            // treats them as installed and never offers a (Play-forbidden) download.

            useLegacyPackaging = false
        }
    }
    
    // S1190: no locale filter here on purpose. The package carries every locale declared in
    // res/xml/locales_config.xml; the store channel trims it back through Play language splits, the
    // direct APK and the non-Play editions deliberately carry all of them.

    // Force 16 KB page alignment for all native libraries
    // This is critical for Android 15+ devices with 16 KB page size
    // Without this, Google Play will reject the APK
    splits {
        abi {
            isEnable = false
        }
    }

    lint {
        checkAllWarnings = false
        // Fail CI on lint ERRORs; warnings only produce report
        abortOnError = true
        checkReleaseBuilds = false
        disable += "InvalidPackage"
        // MissingTranslation is on: debug-only strings carry translatable="false", and post-change.ps1's
        // strings audit sweeps locale parity on every key.
        disable += "NewApi"
        disable += "UnsafeOptInUsageError"
        // False positive: 0dp with layout_weight in LinearLayout or as ConstraintLayout child
        disable += "Suspicious0dp"
        baseline = file("lint-baseline.xml")
        // HTML report for CI artifact upload
        htmlReport = true
        htmlOutput = file("build/reports/lint-results.html")
        xmlReport = true
        xmlOutput = file("build/reports/lint-results.xml")
    }
}

// Gradle 9.4.1 on Windows can try to hash linker temp files like
// libfms_diagnostic_xr.so<hash>.tmp before the native toolchain releases them.
tasks.configureEach {
    if (name.startsWith("buildCMake")) {
        doNotTrackState("Windows native linker temp outputs can remain unreadable during Gradle output hashing")
    }
}

val complianceSourceRoots = listOf(
    "src/main",
    "src/legacy",
    "src/lite",
    "src/photos",
    "src/vr",
)

val verifyNoPlatformNames = tasks.register<VerifyNoPlatformNamesTask>("verifyNoPlatformNames") {
    group = "verification"
    description = "Fails the build when a new forbidden platform literal appears in market sources or public FEATURES docs."
    denyListFile.set(layout.projectDirectory.file("compliance/platform-name-denylist.txt"))
    baselineFile.set(layout.projectDirectory.file("compliance/platform-name-baseline.txt"))
    projectRootMarker.set(rootProject.layout.projectDirectory.file("settings.gradle.kts"))
    sourceFiles.from(
        complianceSourceRoots
            .map { layout.projectDirectory.dir(it).asFile }
            .filter(File::exists)
            .map { root ->
                project.fileTree(root) {
                    include("**/*.kt")
                    include("**/*.java")
                    include("**/*.xml")
                    include("**/*.kts")
                }
            }
    )
    sourceFiles.from(
        rootProject.layout.projectDirectory.file("docs/FEATURES.md"),
        rootProject.layout.projectDirectory.file("docs/FEATURES_RU.md"),
        rootProject.layout.projectDirectory.file("docs/FEATURES_UK.md"),
    )
    reportFile.set(layout.buildDirectory.file("reports/compliance/verifyNoPlatformNames.txt"))
}

tasks.named("preBuild").configure {
    dependsOn(verifyNoPlatformNames)
}

// Replaces the legacy applicationVariants.all { } block (removed in AGP 10.0).
// outputFileName wired lazily so versionName resolves after all variant merges.
androidComponents {
    beforeVariants(selector().withBuildType("benchmark")) { variantBuilder ->
        val flavorName = variantBuilder.flavorName ?: ""
        if (flavorName != "standard") {
            variantBuilder.enable = false
        }
    }

    onVariants { variant ->
        val buildType = variant.buildType ?: ""
        val flavorName = variant.flavorName ?: ""
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { vn ->
                    val v = vn ?: "unknown"
                    if (buildType == "release") "FastMediaSorter_${flavorName}_v${v}.apk"
                    else "FastMediaSorter_${flavorName}_${buildType}_v${v}.apk"
                }
            )
        }

        // S0183: noLegal flavor source set sets manifest.srcFile to src/vr/AndroidManifest.xml
        // (VR overlay). That call REPLACES the auto-detected src/noLegal/AndroidManifest.xml,
        // so noLegal-specific manifest entries (e.g. REQUEST_INSTALL_PACKAGES) were silently
        // dropped. addStaticManifestFile injects an additional manifest file into the merger
        // input list without conflicting with the flavor srcFile override.
        if (flavorName == "noLegal") {
            variant.sources.manifests.addStaticManifestFile("src/noLegal/AndroidManifest.xml")
        }

        // S0403: the castEnabled source set is mounted by directory only, which does not pull in its
        // AndroidManifest automatically. Inject the Cast OPTIONS_PROVIDER meta-data overlay for every
        // cast-capable flavor. addStaticManifestFile is additive, so it coexists with noLegal's
        // manifest.srcFile(src/vr) override. foss never mounts castEnabled, so it never registers it.
        val castFlavors = setOf("standard", "noLegal", "lite", "photos", "legacy", "vr")
        if (flavorName in castFlavors) {
            variant.sources.manifests.addStaticManifestFile("src/castEnabled/AndroidManifest.xml")
        }

        // S0403: the wearGms source set (GMS WearableListenerService) is mounted by directory only,
        // which does not pull in its AndroidManifest automatically. Inject the Wear Data Layer
        // <service> overlay for every Wear-capable flavor. addStaticManifestFile is additive, so it
        // coexists with noLegal's manifest.srcFile(src/vr) override. foss / non-Wear flavors mount
        // wearStub (no manifest), so they never register the service.
        val wearFlavors = setOf("standard", "noLegal", "legacy")
        if (flavorName in wearFlavors) {
            variant.sources.manifests.addStaticManifestFile("src/wearGms/AndroidManifest.xml")
        }

        // S0404: the launcherEnabled source set is mounted by directory only, so its manifest (the
        // HOME-filter activity, shipped disabled) is injected explicitly. addStaticManifestFile is
        // additive, so it coexists with noLegal's manifest.srcFile(src/vr) override.
        val launcherFlavors = setOf("standard", "noLegal")
        if (flavorName in launcherFlavors) {
            variant.sources.manifests.addStaticManifestFile("src/launcherEnabled/AndroidManifest.xml")
        }

        // S0559: the shared confirmable-capture engine manifest (consent activity + mediaProjection
        // service + FOREGROUND_SERVICE_MEDIA_PROJECTION) is injected into both the store flavor and
        // noLegal. The src/screenCapture source set is mounted by directory only, which does not pull
        // in its AndroidManifest automatically, so it is added explicitly here.
        // S0671: standard injects the shared MediaProjection manifest when the capture suite is ON;
        // the overlay manifest stays behind its own gate. noLegal always mounts the shared capture path.
        val injectSharedCaptureManifest =
            flavorName == "noLegal" || (flavorName == "standard" && screenCaptureStandardEnabled)
        if (injectSharedCaptureManifest) {
            variant.sources.manifests.addStaticManifestFile("src/screenCapture/AndroidManifest.xml")
        }
        if (flavorName == "standard" && edgeGestureOverlayStandardEnabled) {
            // SPECIAL_USE overlay host, relocated from the auto-detected src/standard manifest so it
            // can stay OFF for standard while the MediaProjection capture suite ships (S0671/S0672).
            variant.sources.manifests.addStaticManifestFile("src/standardScreenCapture/AndroidManifest.xml")
        }
        if (flavorName == "standard" && edgeGestureTileStandardEnabled) {
            // S0672: QS-tile fallback manifest (TileService declaration, no specialUse / SYSTEM_ALERT_WINDOW).
            variant.sources.manifests.addStaticManifestFile("src/standardEdgeTile/AndroidManifest.xml")
        }

        // S0386: keep native payloads bundled until per-set descriptors and ABI-complete hosting
        // are ready. The delivery UI/runtime remains wired, but stripping these artifacts here
        // would leave OCR/DTS in a half-migrated state.

        // S0423: ML Kit translate is bundled in every translation-capable flavor (no on-demand DFM),
        // so the engine `.so` must stay in the base for standard/legacy too - no exclusion here.
    }
}

// CRITICAL: Do not change - must match compileOptions.targetCompatibility

// S0174: Chaquopy is applied conditionally - only when a noLegal build is in progress.
// Reason: Chaquopy 17.x requires minSdk >= 24 for every variant it processes, and the
// `legacy` flavor has minSdk=23 (intentional - covers API 23-25 devices). There is no
// Kotlin-DSL variantFilter in Chaquopy (that API is Groovy-only / Chaquopy ≤14), so we
// must avoid applying the plugin at all unless noLegal is actually being built.
//
// Activation sources (first match wins):
//   1. Explicit -Pchaquopy.enabled=true|false (CLI / helper scripts) - hard override.
//   2. Auto-detect: any task in gradle.startParameter.taskNames contains "noLegal"
//      (case-insensitive). Covers Android Studio's debug/run button which schedules
//      :app_v2:assembleNoLegalDebug / :installNoLegalDebug for the active build variant.
//      IDE sync runs no assemble* task, so this path stays false during sync and the
//      Build Variants dropdown keeps showing every flavor (beforeVariants stays inactive).
//
// S0276: the old local.properties fallback was removed before re-enabling the Gradle
// configuration cache globally. A machine-local `chaquopy.enabled=true` line would make
// IDE sync and unrelated Gradle invocations apply Chaquopy even when no noLegal task is
// in scope, which defeats the non-noLegal fast path and reintroduces CC instability.
//
// CLI examples:
//   ./gradlew :app_v2:assembleNoLegalDebug                          # auto-enabled
//   ./gradlew :app_v2:assembleNoLegalDebug -Pchaquopy.enabled=true  # explicit
//   ./gradlew :app_v2:assembleStandardDebug -Pchaquopy.enabled=false # force-off override
//
val _gradleChaquopyPropRaw = providers.gradleProperty("chaquopy.enabled").orNull
val _noLegalTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("noLegal", ignoreCase = true)
}
val isNoLegalBuild = when {
    _gradleChaquopyPropRaw != null -> _gradleChaquopyPropRaw.equals("true", ignoreCase = true)
    _noLegalTaskRequested -> true
    else -> false
}
if (isNoLegalBuild) {
    // Chaquopy 17.x validates all variants at configuration time. Constraints:
    //   - legacy has minSdk=23 (< Chaquopy's minimum of 24)
    //   - Python 3.10+ ships wheels only for arm64-v8a and x86_64; standard/lite/photos/legacy
    //     include armeabi-v7a in their abiFilters
    // The only escape: disable every non-noLegal variant via AGP beforeVariants so that
    // Chaquopy's onVariants() is never invoked for them. This is safe when building noLegal -
    // those flavors are not requested and produce no APK in a noLegal invocation.
    androidComponents {
        beforeVariants { variantBuilder ->
            val flavor = variantBuilder.flavorName ?: ""
            if (flavor != "noLegal") {
                variantBuilder.enable = false
            }
        }
    }
    apply(plugin = "com.chaquo.python")
    configure<com.chaquo.python.ChaquopyExtension> {
        defaultConfig {
            // Python 3.12: Chaquopy 17.x supports arm64-v8a + x86_64 only for 3.11+.
            // noLegal abiFilters is restricted to those two ABIs in productFlavors block.
            version = "3.12"
            // Windows: 'python3' is not available; use 'py' launcher with -3.12 flag.
            // yt-dlp is pure-Python - buildPython version only needs to match the device version
            // for packages that ship native extensions (yt-dlp does not).
            buildPython("py", "-3.12")
        }
        productFlavors {
            // Only noLegal installs yt-dlp - all other flavors get no Python packages.
            // Use getByName because Chaquopy registers PythonExtension per-flavor automatically.
            getByName("noLegal") {
                pip {
                    // S0190: bumped from 2025.4.30 → 2026.3.17 (latest non-dev release on PyPI
                    // at spec time). Brings 2025-H2 + early-2026 YouTube player.js handling
                    // plus extractor_args.youtube.player_client support, used in ytdlp_utils.py
                    // to prefer Android client which typically bypasses PoToken requirements.
                    // 2026-06-17: bumped 2026.3.17 → 2026.6.9 (latest stable on PyPI) for
                    // continued YouTube extractor maintenance.
                    // 2026-07-03: stable channel had no newer release than 2026.6.9, but the
                    // Instagram extractor was returning "empty media response" for reels/video
                    // (photos still worked via the HTML/structured sniffer, which does not use
                    // yt-dlp). The nightly channel 2026.07.02.234458 ships "Instagram: Rework
                    // extractor" (#17075) which fixes this. Nightly is NOT on PyPI, so we pin the
                    // GitHub sdist tarball directly. Trade-off: nightly is less battle-tested for
                    // other sites than a stable release - revisit on the next stable bump.
                    // 2026-07-05 (S0950): bumped 2026.07.02.234458 → 2026.07.04.221833. The prior
                    // pin still 404'd/500'd on reels (S0935 device test); this nightly adds the
                    // follow-up fix to #17075 (commit 8b8e3e3) plus "Instagram: Detect when cookies
                    // are invalidated" (#17126), targeting exactly the reel extraction + stale-
                    // session failure modes (ref S0822).
                    // 2026-07-19 (pre-release refresh): bumped 2026.07.04.221833 → 2026.07.14.233956.
                    // Stayed on nightly - PyPI stable is still 2026.7.4 (same day as the prior pin,
                    // lacks the nightly-only Instagram Rework #17075 fixes). This nightly carries the
                    // named Instagram fixes plus ~10 days of upstream extractor maintenance. Server-
                    // side extractor rot means the freshest nightly is the best bet at ship time.
                    // 2026-07-22 (pre-release refresh): bumped 2026.07.14.233956 → 2026.07.21.234255.
                    // Still on nightly - PyPI stable unchanged at 2026.7.4, which predates the pinned
                    // nightly date and so does not supersede it. Freshest nightly adds ~7 days of
                    // upstream extractor maintenance at ship time. Needs an on-device link-download to
                    // verify extraction - pip resolve alone proves nothing (BlockNeedUserTest-shaped).
                    // 2026-07-26 (pre-release refresh): bumped 2026.07.21.234255 → 2026.07.23.234303.
                    // Still on nightly - PyPI stable remains 2026.7.4, older than the pinned nightly
                    // date, so it does not supersede. Freshest nightly at ship time; needs an on-device
                    // link-download to verify extraction.
                    install(
                        "yt-dlp @ https://github.com/yt-dlp/yt-dlp-nightly-builds/" +
                            "releases/download/2026.07.23.234303/yt-dlp.tar.gz",
                    )
                }
            }
        }
    }
}


// Built-in Kotlin inherits compileOptions.targetCompatibility by default.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    lintChecks(project(":lint-rules"))
    // Core Library Desugaring: java.time.* and other Java 8+ APIs on API 23-25 (legacy flavor)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    // Security (EncryptedSharedPreferences for cloud credentials)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // S0200 - Credential Manager (Google identity binding) + Chrome Custom Tabs.
    // Credential Manager replaces the deprecated Google Sign-In SDK; googleid supplies GetGoogleIdOption.
    // androidx.browser is consumed by Phase 03 CCT routing - added here to keep all S0200 deps colocated.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    // S0385: googleid is consumed only by src/cloudEnabled (CredentialManagerGoogleIdentityRepository),
    // which is mounted into every flavor EXCEPT lite (lite mounts cloudDisabled). Scope it per-flavor
    // so the lite APK stops packaging an unused Google-identity dependency.
    "standardImplementation"("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    "noLegalImplementation"("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    "legacyImplementation"("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    "vrImplementation"("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    "photosImplementation"("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.browser:browser:1.8.0")

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // S0385: material-icons-extended is NOT dead - Icons.Filled.Pause / SkipNext / SkipPrevious
    // (media-control icons in WearSyncSettingsFragment + widget config) live only in the extended
    // set, not in material-icons-core. Removing it breaks compilation. Kept intentionally.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    // Material Design 3
    implementation("com.google.android.material:material:1.14.0")

    // Google Play In-App Review (S0135)
    implementation("com.google.android.play:review-ktx:2.0.2")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0") // For ProcessLifecycleOwner
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.59")
    kapt("com.google.dagger:hilt-android-compiler:2.59")
    
    // WorkManager - 2.10.x: SystemForegroundService handles Service.onTimeout() for
    // FOREGROUND_SERVICE_TYPE_DATA_SYNC on Android 14+, preventing
    // ForegroundServiceDidNotStopInTimeException fatals (S0709). Keep in sync with work-multiprocess below.
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Baseline Profiles runtime installer
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    
    // Hilt WorkManager integration
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")
    
    // Room
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    kapt("androidx.room:room-compiler:2.7.0")
    
    // Paging 3
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // DocumentFile for SAF support
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Print support
    implementation("androidx.print:print:1.0.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // ExoPlayer (HLS/DASH re-enabled per-flavor below for S0116; SmoothStreaming stays excluded)
    implementation("androidx.media3:media3-exoplayer:1.2.1") {
        // Exclude SmoothStreaming - not used by url-download or playback
        exclude(group = "androidx.media3", module = "media3-exoplayer-smoothstreaming")
    }
    // S0116: HLS/DASH offline downloader is wired only into video-supporting market flavors.
    // lite/photos stay without these modules to preserve their APK size budget.
    "standardImplementation"("androidx.media3:media3-exoplayer-hls:1.2.1")
    "standardImplementation"("androidx.media3:media3-exoplayer-dash:1.2.1")
    "noLegalImplementation"("androidx.media3:media3-exoplayer-hls:1.2.1")
    "noLegalImplementation"("androidx.media3:media3-exoplayer-dash:1.2.1")
    "legacyImplementation"("androidx.media3:media3-exoplayer-hls:1.2.1")
    "legacyImplementation"("androidx.media3:media3-exoplayer-dash:1.2.1")
    "vrImplementation"("androidx.media3:media3-exoplayer-hls:1.2.1")
    "vrImplementation"("androidx.media3:media3-exoplayer-dash:1.2.1")
    // S0565: RTSP playback (rtsp:// internet streams) is wired only into streaming-capable flavors,
    // matching the HLS/DASH flavor split; lite/photos stay RTSP-free to preserve their APK budget.
    "standardImplementation"("androidx.media3:media3-exoplayer-rtsp:1.2.1")
    "noLegalImplementation"("androidx.media3:media3-exoplayer-rtsp:1.2.1")
    "legacyImplementation"("androidx.media3:media3-exoplayer-rtsp:1.2.1")
    "vrImplementation"("androidx.media3:media3-exoplayer-rtsp:1.2.1")
    // S0305: MIDI playback is available only in flavors that support audio.
    "standardImplementation"("androidx.media3:media3-exoplayer-midi:1.2.1")
    "noLegalImplementation"("androidx.media3:media3-exoplayer-midi:1.2.1")
    "liteImplementation"("androidx.media3:media3-exoplayer-midi:1.2.1")
    "legacyImplementation"("androidx.media3:media3-exoplayer-midi:1.2.1")
    "vrImplementation"("androidx.media3:media3-exoplayer-midi:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-common:1.2.1")
    implementation("androidx.media3:media3-decoder:1.2.1") // Audio decoders for WAV and other formats
    implementation("androidx.media3:media3-session:1.2.1") // MediaSession for audio background playback
    implementation("androidx.media3:media3-effect:1.2.1")  // GlEffect API for SBS stereo crop rendering (Phase 2)
    // S1066: post-record re-encode that bakes the in-app digital zoom into the camera MP4 (all flavors -
    // the camera lives in src/main and compiles into every flavor, so the dep cannot be flavor-scoped).
    implementation("androidx.media3:media3-transformer:1.2.1")

    // Image Loading - Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
    
    // PhotoView for pinch-to-zoom and rotation support
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    
    // ExifInterface for image metadata (width, height, camera, GPS, etc.)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    
    // ML Kit - Translation and Text Recognition (OCR)
    // S0423: ML Kit Translate is bundled in every translation-capable flavor. The on-demand
    // :translate_feature DFM was removed (it shipped empty and broke the release bundle), so no
    // Play Core SplitInstall dependency is needed.
    "noLegalImplementation"("com.google.mlkit:translate:17.0.3")
    "noLegalImplementation"("com.google.mlkit:language-id:17.0.6")
    "vrImplementation"("com.google.mlkit:translate:17.0.3")
    "vrImplementation"("com.google.mlkit:language-id:17.0.6")
    "standardImplementation"("com.google.mlkit:translate:17.0.3")
    "standardImplementation"("com.google.mlkit:language-id:17.0.6")
    "legacyImplementation"("com.google.mlkit:translate:17.0.3")
    "legacyImplementation"("com.google.mlkit:language-id:17.0.6")

    // S0386: com.google.mlkit:text-recognition is completely removed from all builds.

    implementation("androidx.camera:camera-core:1.5.3")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    // S0545: in-app video recording (unified capture host); replaces external ACTION_VIDEO_CAPTURE.
    implementation("androidx.camera:camera-video:1.5.3")
    // S0753: OEM NIGHT extension for the camera night mode (device-gated via ExtensionsManager).
    implementation("androidx.camera:camera-extensions:1.5.3")

    // S0988: pure-JVM QR decoder for the companion-config scan (no native model, no GMS, all flavors).
    // Only the core decoder - NOT zxing-android-embedded, which drags in a legacy camera1 stack.
    implementation("com.google.zxing:core:3.5.3")

    // Tesseract OCR (Offline, better Cyrillic support)
    // S0386: cz.adaptech:tesseract4android is flavor-specific (compiled only for OCR-supporting flavors)
    "standardImplementation"("cz.adaptech:tesseract4android:4.8.0") {
        exclude(group = "cz.adaptech.tesseract4android", module = "tesseract4android-openmp")
    }
    "legacyImplementation"("cz.adaptech:tesseract4android:4.8.0") {
        exclude(group = "cz.adaptech.tesseract4android", module = "tesseract4android-openmp")
    }
    "noLegalImplementation"("cz.adaptech:tesseract4android:4.8.0") {
        exclude(group = "cz.adaptech.tesseract4android", module = "tesseract4android-openmp")
    }
    "vrImplementation"("cz.adaptech:tesseract4android:4.8.0") {
        exclude(group = "cz.adaptech.tesseract4android", module = "tesseract4android-openmp")
    }
    
    // Network - SMB (uses BouncyCastle jdk15to18:1.72 via resolutionStrategy)
    implementation("com.hierynomus:smbj:0.12.1")
    
    // Network - SFTP (JSch for Android - better KEX support than SSHJ)
    implementation("com.github.mwiede:jsch:0.2.26")
    
    // Network - FTP
    implementation("commons-net:commons-net:3.10.0")
    
    // Wearable Data Layer - phone-side bridge to Wear OS companion.
    // S0403: consumed only by src/wearGms (WearableDataLayerRepositoryImpl + PhoneWearListenerService),
    // mounted into the Wear-capable flavors only. Scoped per-flavor so the FOSS APK (and non-Wear
    // flavors) never package the proprietary Play Services Wearable SDK. Keep this list in sync with
    // the wearGms sourceSets mounts above.
    "standardImplementation"("com.google.android.gms:play-services-wearable:18.1.0")
    "noLegalImplementation"("com.google.android.gms:play-services-wearable:18.1.0")
    "legacyImplementation"("com.google.android.gms:play-services-wearable:18.1.0")

    // Cloud Storage - Google Drive (REST API + Google Sign-In)
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("net.openid:appauth:0.11.1")
    
    // Network - Retrofit for iTunes Search API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("com.github.chuckerteam.chucker:library:4.0.0")
    "benchmarkImplementation"("com.github.chuckerteam.chucker:library-no-op:4.0.0")
    releaseImplementation("com.github.chuckerteam.chucker:library-no-op:4.0.0")
    
    // Cloud Storage - Dropbox
    implementation("com.dropbox.core:dropbox-core-sdk:5.4.5")
    
    // Cloud Storage - OneDrive (REST API + MSAL OAuth)
    implementation("com.microsoft.identity.client:msal:6.0.1")
    
    // Google Cast SDK + MediaRouter (Chromecast output from player) + NanoHTTPD proxy.
    // S0403: consumed only by src/castEnabled (CastMediaManagerImpl / LocalCastProxyServer), mounted
    // into every flavor EXCEPT foss (which mounts castDisabled). Scoped per-flavor so the FOSS APK
    // never packages the proprietary Google Cast SDK. Keep this list in sync with the castEnabled
    // sourceSets mounts above.
    "standardImplementation"("com.google.android.gms:play-services-cast-framework:21.4.0")
    "noLegalImplementation"("com.google.android.gms:play-services-cast-framework:21.4.0")
    "liteImplementation"("com.google.android.gms:play-services-cast-framework:21.4.0")
    "photosImplementation"("com.google.android.gms:play-services-cast-framework:21.4.0")
    "legacyImplementation"("com.google.android.gms:play-services-cast-framework:21.4.0")
    "vrImplementation"("com.google.android.gms:play-services-cast-framework:21.4.0")
    "standardImplementation"("androidx.mediarouter:mediarouter:1.7.0")
    "noLegalImplementation"("androidx.mediarouter:mediarouter:1.7.0")
    "liteImplementation"("androidx.mediarouter:mediarouter:1.7.0")
    "photosImplementation"("androidx.mediarouter:mediarouter:1.7.0")
    "legacyImplementation"("androidx.mediarouter:mediarouter:1.7.0")
    "vrImplementation"("androidx.mediarouter:mediarouter:1.7.0")
    "standardImplementation"("org.nanohttpd:nanohttpd:2.3.1")
    "noLegalImplementation"("org.nanohttpd:nanohttpd:2.3.1")
    "liteImplementation"("org.nanohttpd:nanohttpd:2.3.1")
    "photosImplementation"("org.nanohttpd:nanohttpd:2.3.1")
    "legacyImplementation"("org.nanohttpd:nanohttpd:2.3.1")
    "vrImplementation"("org.nanohttpd:nanohttpd:2.3.1")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
    // Required by LeakCanary for background heap analysis (RemoteListenableWorker)
    debugImplementation("androidx.work:work-multiprocess:2.10.1") // Keep in sync with work-runtime-ktx (S0709)
    
    // Document Support - EPUB
    implementation("io.documentnode:epub4j-core:4.2") {
        exclude(group = "xmlpull", module = "xmlpull")
        exclude(group = "net.sf.kxml", module = "kxml2")
    }
    implementation("org.jsoup:jsoup:1.17.2")

    // Document Support - encrypted ZIP archives
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    // S0117: GPL extractor is linked only into the sideload-only noLegal flavor.
    // S0175: bumped v0.24.0 -> v0.26.1; no wrapper changes needed (breaking changes in v0.25/v0.26 don't touch our API surface).
    "noLegalImplementation"("com.github.TeamNewPipe:NewPipeExtractor:v0.26.1")

    // Markdown Rendering (for .md text files)
    implementation("io.noties.markwon:core:4.6.2")
    
    // Document Support - PDF extraction via built-in PdfRenderer (API 21+)
    // No external dependencies needed - metadata extraction removed to avoid conflicts
    
    // OpenXR loader - vr and noLegal (headset XR rendering).
    // noLegal ships the same arm64-v8a OpenXR slice; non-Quest devices simply never
    // exercise VrPlayerActivity because the graceful fallback fires first.
    "vrImplementation"("org.khronos.openxr:openxr_loader_for_android:1.1.48")
    "noLegalImplementation"("org.khronos.openxr:openxr_loader_for_android:1.1.48")

    // SW AV1 decoder (libgav1) - source-only extension, not published on Google Maven.
    // TODO: build from source (same pipeline as fms-ffmpeg-dts.aar) before enabling.
    // "standardImplementation"("androidx.media3:media3-decoder-av1:1.2.1")
    // "legacyImplementation"("androidx.media3:media3-decoder-av1:1.2.1")
    // "vrImplementation"("androidx.media3:media3-decoder-av1:1.2.1")

    // SW VP9 decoder (libvpx, incl. Profile 2 10-bit HDR) - source-only extension, not on Maven.
    // TODO: build from source before enabling.
    // "standardImplementation"("androidx.media3:media3-decoder-vpx:1.2.1")
    // "legacyImplementation"("androidx.media3:media3-decoder-vpx:1.2.1")
    // "vrImplementation"("androidx.media3:media3-decoder-vpx:1.2.1")

    // ── Custom FFmpeg AAR (DTS + APE/WMA/WavPack/TTA/DSD) ─────────────────────────────────────
    // DTS/extended codec decoder via custom FFmpeg AAR - built from media3 1.2.1 sources.
    // Build script: scripts/builders/build-ffmpeg-dts.sh
    // Spec: PLAN/spec_ffmpeg-custom-build-dts.md §7, Phase 3
    //
    // AAR built: app_v2/libs/fms-ffmpeg-dts.aar (libffmpegJNI.so arm64-v8a + classes.jar)
    // Rebuilt with NDK r25c + -Wl,-z,max-page-size=16384. readelf LOAD Align=0x4000 (16 KB). ✓ Play-safe.
    "standardImplementation"(files("libs/fms-ffmpeg-dts.aar"))
    "noLegalImplementation"(files("libs/fms-ffmpeg-dts.aar"))
    "legacyImplementation"(files("libs/fms-ffmpeg-dts.aar"))
    "vrImplementation"(files("libs/fms-ffmpeg-dts.aar"))

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.robolectric:robolectric:4.11.1") // For Android framework in JVM tests
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("com.squareup.leakcanary:leakcanary-android-instrumentation:2.12")
    // S0116 Phase 07 step 0: MockWebServer for graceful-degradation instrumentation tests.
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.59")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.room:room-testing:2.7.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.59")
}

// TEMPORARILY DISABLED: BouncyCastle resolutionStrategy (was needed for PDFBox)
// configurations.all {
//     resolutionStrategy {
//         force("org.bouncycastle:bcprov-jdk15to18:1.72")
//         force("org.bouncycastle:bcpkix-jdk15to18:1.72")
//         force("org.bouncycastle:bcutil-jdk15to18:1.72")
//     }
// }

kapt {
    correctErrorTypes = true
    arguments {
        // Export Room schema JSON into a committed dir so future migrations are validatable (S0731).
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    javacOptions {
        option("-Xlint:-processing")
    }
}
