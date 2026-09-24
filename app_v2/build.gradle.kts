import com.android.build.api.variant.BuildConfigField
import java.io.FileInputStream
import java.io.File
import java.time.Duration
import java.util.Properties
import org.gradle.kotlin.dsl.support.serviceOf
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

// S1783: a debug build carries the list of tickets it contains, so a tester holding the APK can see
// what there is to test without the repository. The selection happens here, once, rather than on the
// settings screen - the screen only renders whatever this task emitted.
@CacheableTask
abstract class GenerateReleasedTicketsTask : DefaultTask() {
    // S3155: Optional, because .gitignore excludes PLAN/ from Git entirely. A CI checkout - or any
    // clean clone - carries neither file, and a non-optional @InputFile makes Gradle refuse the task
    // before its action runs, which kept every CI run of every branch red independently of lint.
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseQueueFile: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseReadyFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val target = outputDir.get().asFile
        target.mkdirs()
        val listing = File(target, "released_tickets.tsv")

        // A build made outside the specification workspace has no tickets to advertise, so the
        // correct answer is an empty listing - the settings screen renders whatever this task
        // emitted. Failing the whole variant over their absence is what kept CI red.
        val queueFile = releaseQueueFile.orNull?.asFile?.takeIf(File::isFile)
        val readyFile = releaseReadyFile.orNull?.asFile?.takeIf(File::isFile)
        if (queueFile == null || readyFile == null) {
            listing.writeText("")
            val missing = listOfNotNull(
                "RELEASE_QUEUE.md".takeIf { queueFile == null },
                "RELEASE_READY.md".takeIf { readyFile == null }
            ).joinToString(separator = ", ")
            logger.lifecycle(
                "released tickets: no listing - PLAN/ is gitignored and this checkout has no $missing"
            )
            return
        }

        val markerRx = Regex("""^\s*current-next-release:\s*(\d+)\s*$""")
        // The marker is the only authority on which package this build carries. The highest package
        // present in RELEASE_READY.md is NOT it: that file legitimately holds rows for the next
        // package too, so reading the maximum would ship a listing of the wrong, tiny package.
        val currentRelease = queueFile.readLines()
            .firstNotNullOfOrNull { markerRx.find(it)?.groupValues?.get(1) }
            ?: throw GradleException(
                "No 'current-next-release:' marker in $queueFile - cannot tell which release " +
                    "package this build carries."
            )

        val rowRx = Regex("""^\s*(\d+)\s+(S\d{4})_(\S+)\s+\d{4}-\d{2}-\d{2}\s+(\S+)\s*$""")
        val rows = readyFile.readLines()
            .mapNotNull { rowRx.find(it)?.groupValues }
            .filter { it[1] == currentRelease }

        // BlockNeedUserTest first - those are the rows a tester actually has to act on. sortedBy is
        // stable, so the owner's order inside each group survives.
        val ordered = rows.sortedBy { if (it[4] == "BlockNeedUserTest") 0 else 1 }

        listing.writeText(
            ordered.joinToString(separator = "\n") { "${it[2]}\t${it[3]}\t${it[4]}" }
        )
        logger.lifecycle(
            "released tickets: ${ordered.size} row(s) for package $currentRelease"
        )
    }
}

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

// S0403: the foss variant is the F-Droid artifact, and F-Droid refuses proprietary libraries. Every
// other guard in this file judges SOURCE; this one judges the resolved dependency graph, which is
// where the failure actually happens - a capability flag set to false leaves the SDK on the
// classpath, so `lite` linked all five cloud SDKs while reporting no cloud support.
@CacheableTask
abstract class VerifyNoProprietaryDepsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val denyListFile: RegularFileProperty

    // The resolved graph root, not a pre-mapped list of strings: a Provider.map lambda written in
    // build.gradle.kts captures the script object, which the configuration cache cannot store.
    // ResolvedComponentResult is a supported input type, so the walk happens in the action below.
    @get:Input
    abstract val rootComponent: Property<ResolvedComponentResult>

    @get:Input
    abstract val configurationName: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val prefixes = denyListFile.asFile.get().readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        val resolved = collectModuleCoordinates(rootComponent.get()).sorted()
        val violations = resolved.filter { coordinate ->
            prefixes.any { prefix -> coordinate == prefix || coordinate.startsWith("$prefix:") }
        }

        val report = reportFile.asFile.get()
        report.parentFile.mkdirs()

        if (violations.isNotEmpty()) {
            report.writeText(violations.joinToString(System.lineSeparator()))
            throw GradleException(
                buildString {
                    appendLine(
                        "Proprietary dependencies on the ${configurationName.get()} classpath - " +
                            "the F-Droid variant may not link them."
                    )
                    appendLine(
                        "Remove the dependency from the foss variant (declare it per-flavor), or " +
                            "review and edit app_v2/compliance/proprietary-deps-denylist.txt."
                    )
                    appendLine()
                    violations.forEach { appendLine(it) }
                }
            )
        }

        report.writeText(
            "OK configuration=${configurationName.get()} components=${resolved.size} " +
                "prefixes=${prefixes.size}${System.lineSeparator()}"
        )
    }

    private fun collectModuleCoordinates(root: ResolvedComponentResult): Set<String> {
        val coordinates = sortedSetOf<String>()
        val seen = mutableSetOf<ResolvedComponentResult>()

        fun walk(component: ResolvedComponentResult) {
            if (!seen.add(component)) return
            (component.id as? ModuleComponentIdentifier)?.let { id ->
                coordinates += "${id.group}:${id.module}"
            }
            component.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { walk(it.selected) }
        }

        walk(root)
        return coordinates
    }
}

// S2879: the build-time native AARs live in gitignored app_v2/libs/, and Gradle resolves an absent
// files("libs/..") path to an EMPTY file collection rather than to an error. So a checkout missing
// one of them configures, compiles and lints without that decoder and says nothing about it -
// measured 2026-09-10 on the CI logs of 2026-08-23 and 2026-08-24, which fetched only the FFmpeg
// AAR, ran twenty minutes to a lint verdict, and never mentioned the absent VP9 one. This task
// turns that silence into a failure for the flavors that declare the AARs; the list is the same
// manifest the CI fetcher and the publisher read, so a third AAR reaches all three channels at once.
//
// Deliberately NOT @CacheableTask, and forced to run on every build by outputs.upToDateWhen { false }
// at the registration site. Its subject is whether a file exists on THIS disk right now, which is
// the one thing neither a cache entry nor an up-to-date check can answer: measured 2026-09-10, with
// only the manifest and the root marker as inputs the task reported UP-TO-DATE after the VP9 AAR was
// deleted, and a build-cache hit would go further and restore a green report produced on a machine
// that had the file. The check is two stat calls, so running it always costs nothing worth saving.
abstract class VerifyPrebuiltNativeAarsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectRootMarker: RegularFileProperty

    @get:Input
    abstract val variantName: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val projectRoot = projectRootMarker.asFile.get().parentFile.canonicalFile
        val entries = manifestFile.asFile.get().readLines()
            .map { it.substringBefore('#').trim() }
            .filter(String::isNotEmpty)

        // An entry present but zero-length is a truncated download, and it fails much later inside
        // an opaque Gradle transform - so it is judged here, where the message still names a cause.
        val missing = entries.filter { entry ->
            val file = File(projectRoot, entry)
            !file.isFile || file.length() == 0L
        }

        val report = reportFile.asFile.get()
        report.parentFile.mkdirs()

        if (missing.isNotEmpty()) {
            report.writeText(missing.joinToString(System.lineSeparator()))
            throw GradleException(
                buildString {
                    appendLine(
                        "Prebuilt native AAR missing or empty - the ${variantName.get()} variant " +
                            "declares it as a hard dependency, and Gradle would otherwise resolve " +
                            "it to an empty file collection and build without that decoder."
                    )
                    appendLine("Fetch them: bash ./scripts/ci/fetch-prebuilt-libs.sh")
                    appendLine("List: ${MANIFEST_RELATIVE_PATH}. Roles: delivery/INVENTORY.md.")
                    appendLine()
                    missing.forEach { appendLine(it) }
                }
            )
        }

        report.writeText("OK variant=${variantName.get()} entries=${entries.size}${System.lineSeparator()}")
    }

    companion object {
        const val MANIFEST_RELATIVE_PATH = "scripts/ci/prebuilt-native-aars.txt"
    }
}

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    // S3371: SBOM producer for the dependency-admission contour. Task only - no runtime effect.
    alias(libs.plugins.cyclonedx)
    id("org.jetbrains.kotlin.plugin.compose")
}

// S1873: the version has three sources, in this order.
//   1. -Pfms.version* passed on the command line. Always wins (ADR-2): the phone and the watch of
//      one release are built by two invocations seconds apart and must agree byte for byte, which
//      only an explicitly shared stamp can guarantee.
//   2. The in-build stamp, applied when THIS invocation packages an artifact and nobody passed a
//      property. Covers the paths no wrapper script reaches - a raw gradlew, CI, the IDE Run button.
//   3. The checked-in constant below. After ADR-4 nothing writes it, so it is a deliberately
//      non-releasable sentinel and reaching it means a packaging path stamped nothing - which
//      scripts/quality/assert-artifact-version-fresh.ps1 refuses on the produced artifact.
// Compile-only work never reaches source 2 and keeps the constant, so its incrementality and its
// configuration-cache entry are untouched. Gate on the pair of constants:
// scripts/quality/assert-module-version-parity.ps1.
apply(from = rootProject.file("gradle/build-version-stamp.gradle.kts"))

val defaultAppVersionCode = 260901214
val defaultAppVersionName = "2.60.9012.140"

// S2585: single source for the unit-test task ceiling, shared with wear through gradle.properties so
// the two modules cannot drift apart. Declared here rather than inside testOptions.unitTests.all
// because that lambda already binds `it` to the Test task, and a nested provider lambda would shadow
// it. The full rationale - and the measurement that picked 20 - is on the property itself.
val unitTestTimeoutMinutes: Long =
    providers.gradleProperty("fms.unitTestTimeoutMinutes").orNull?.toLongOrNull() ?: 20L

// S2851: how many test worker JVMs run at once, shared with wear through gradle.properties for the
// same reason the timeout above is. Declared at the top level, not inside testOptions.unitTests.all,
// because that lambda binds `it` to the Test task and a nested provider lambda would shadow it.
// The measurement that picked the shipped value is on the property itself.
val unitTestMaxParallelForks: Int =
    providers.gradleProperty("fms.unitTestMaxParallelForks").orNull?.toIntOrNull()?.coerceAtLeast(1)
        ?: 1
val stampedAppVersionCode = extra.properties["fmsStampedAppVersionCode"] as Int?
val stampedAppVersionName = extra.properties["fmsStampedVersionName"] as String?
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
// S1972: ABI splits, requested per invocation rather than per build type - `splits` is an
// `android {}`-level block with no per-buildType form, so the debug builders pass
// -Pfms.abiSplits=true and the release path passes nothing. Read here, at the top with the other
// property gates, because noLegal's ndk.abiFilters below is conditioned on it as well: `splits` can
// only slice what was already packaged.
val abiSplitsRequested =
    (providers.gradleProperty("fms.abiSplits").orNull ?: "false").equals("true", ignoreCase = true)
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
    // S2884: compileSdk 37 - the API level AGP 9.2 tops out at, lifting the minCompileSdk=37
    // ceiling that forced the Glide 5.0.7 and rtsp-server 1.4.1 pins back. API 37 platforms are
    // minor-qualified (android-37.0 / android-37.1 on the workstation), and AGP resolves the
    // highest installed 37.x - the same way compileSdk 36 compiled against installed android-36.1.
    compileSdk = 37
    // NDK r27c required: first NDK release that ships a 16 KB page-size aligned libc++_shared.so
    // (Google Play requirement since Nov 1 2025 for apps targeting Android 15+).
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.sza.fastmediasorter"
        // Minimum supported Android 8.0 (API 26). Legacy flavor covers API 23-25.
        minSdk = 26
        // targetSdk 36 stays one level behind compileSdk 37 on purpose (S2884): compileSdk is a
        // compile-time ceiling only, targetSdk is the runtime-behaviour contract the Play mandate
        // (S1149) governs. minSdk stays 26/23 - device reach unchanged.
        targetSdk = 36
        // Three sources in one order - passed property, in-build stamp when this invocation
        // packages, checked-in sentinel. See the block above the constants for why each exists.
        // versionName format: Y.YM.MDDH.Hmm (e.g., 2.62.0501.151 for 2026/02/05 01:51)
        // versionCode format: YYMMDDHHm (e.g., 260205015 for 2026/02/05 01:51)
        // Note: YYMMDDHHmm overflows Int32, using first digit of minutes only
        versionCode = overrideAppVersionCode ?: stampedAppVersionCode ?: defaultAppVersionCode
        versionName = overrideAppVersionName ?: stampedAppVersionName ?: defaultAppVersionName

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
        // S1666: OWNER_TRIGGER and its local.properties key `sza.owner.trigger` are gone. The trigger
        // guarded an import of credentials that shipped inside every APK regardless of it - with the
        // bundled file withdrawn there is nothing left to guard, and the string itself no longer ends up
        // in the built BuildConfig.

        // Git Hash
        // === STARTUP DEBUG INFO ===
        // Git Hash (Simplified for stability)
        buildConfigField("String", "GIT_HASH", "\"Unknown\"")

        // Build Time (Current)
        buildConfigField("String", "BUILD_TIME", "\"Unknown\"")
        buildConfigField("boolean", "IS_NO_LEGAL_FLAVOR", "false")
        buildConfigField("boolean", "SUPPORT_LAUNCHER", "false")
        // S1436: manifest-composition axis the permission registry filters on. Stripped from the
        // release manifest only (src/release/AndroidManifest.xml), so the default is true and the
        // release build type is the single override. Resolved at compile time, never by reflection -
        // see PermissionRegistryRepositoryImpl.resolveBuildGate and the S0970 incident.
        // The two flavor-and-switch axes are set per variant in androidComponents.onVariants below,
        // beside the manifest injections they mirror, because their value is not a literal.
        buildConfigField("boolean", "DECLARES_BATTERY_OPTIMIZATION", "true")
        // S2742: reach of the src/vr source set, which is mounted by exactly two flavors
        // (noLegal and vr) and so cannot be named by any single existing flag row.
        buildConfigField("boolean", "SUPPORT_IMMERSIVE_XR", "false")
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
            //
            // S1972: skipped entirely under -Pfms.abiSplits=true, because AGP refuses the two
            // mechanisms together - "Conflicting configuration: '..' in ndk abiFilters cannot be
            // present when splits abi filters are set", measured 2026-08-26. So a split build has
            // exactly one filter, `splits.abi.include`, and it names the two ABIs anything here
            // actually runs on. That property is passed only by the debug builders.
            if (!abiSplitsRequested) {
                ndk {
                    abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                }
            }
        }

        // ===== STANDARD (Full Featured) =====
        create("standard") {
            dimension = "version"
            manifestPlaceholders["ossNoticesPayload"] = "@raw/oss_notices_standard"
            isDefault = true
            disableNativeBuild()
            // No applicationIdSuffix = keeps current package names
            // No versionNameSuffix = keeps current version format
            // Full feature set: Videos, Audio, Images, Cloud, Documents, Animations
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_STREAMS", "true")     // S0565: Трансляции entry-point
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "true")
            buildConfigField("boolean", "DECLARES_MIC_RECORDING", "true")
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
            buildConfigField("boolean", "SUPPORT_LAUNCHER", "true")
            buildConfigField("boolean", "SUPPORT_NETWORK_MONITOR", "true")  // S1433: Network Monitor program
            buildConfigField("boolean", "SUPPORT_BROADCAST_SOURCE", "true")
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
            manifestPlaceholders["ossNoticesPayload"] = "@raw/oss_notices_nolegal"
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
            //
            // x86_64 was dropped 2026-08-23 (owner ruling), and S1972 - the ticket this comment
            // named as the owner of restoring it - established 2026-08-26 that it cannot come back
            // while Chaquopy is in the build. Two refusals meet head-on:
            //
            //   AGP:      abiFilters cannot be present when splits abi filters are set;
            //   Chaquopy: "Variant 'noLegalDebug': Chaquopy requires ndk.abiFilters".
            //
            // So noLegal can be filtered or split, never both, and it must be filtered. It stays a
            // single arm64-v8a APK - exactly the shape the owner ruled for, and the reasoning behind
            // that ruling is untouched: AGP merges flavor and buildType abiFilters by UNION, so
            // "x86_64 for debug only" is inexpressible here and the slice would ride inside every
            // noLegal build, costing 93.8 MB of a 256.7 MB APK (libVLC is 43.95 MB per ABI since
            // S1060). No builder passes -Pfms.abiSplits for this flavor.
            //
            // The guard below is still needed, and not for noLegal's own sake: AGP checks the
            // abiFilters-versus-splits conflict across EVERY variant at configuration time, so an
            // unconditional filter here would break a split build of standard. Under the property
            // this flavor is configured filterless and simply never assembled.
            if (!abiSplitsRequested) {
                ndk {
                    abiFilters += listOf("arm64-v8a")
                }
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
            buildConfigField("boolean", "DECLARES_MIC_RECORDING", "true")
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
            buildConfigField("boolean", "SUPPORT_LAUNCHER", "true")
            buildConfigField("boolean", "SUPPORT_NETWORK_MONITOR", "true")  // S1433: Network Monitor program
            buildConfigField("boolean", "SUPPORT_BROADCAST_SOURCE", "true")
            buildConfigField("boolean", "SUPPORT_IMMERSIVE_XR", "true")  // S2742: mounts src/vr
        }

        // ===== LITE (Lightweight, Local Files Only) =====
        create("lite") {
            dimension = "version"
            manifestPlaceholders["ossNoticesPayload"] = "@raw/oss_notices_lite"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-Lite"
            disableNativeBuild()
            // Local files only: No cloud, no heavy features
            // Target: Users with limited storage/bandwidth, older devices
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            buildConfigField("boolean", "SUPPORT_STREAMS", "false")    // S0575: Streams feature UI hidden in lite (streamingDisabled pipeline unchanged)
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "false") // Excluded per S0100 §6
            // S1459: lite excludes the voice-note feature but still records video with audio, so it
            // declares RECORD_AUDIO and must show the row. The two flags disagree only here.
            buildConfigField("boolean", "DECLARES_MIC_RECORDING", "true")
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
            buildConfigField("boolean", "SUPPORT_NETWORK_MONITOR", "false") // S1433: no diagnostic program in lite
            buildConfigField("boolean", "SUPPORT_BROADCAST_SOURCE", "false")
        }

        // ===== PHOTOS (Images Only, with Cloud Support) =====
        create("photos") {
            dimension = "version"
            manifestPlaceholders["ossNoticesPayload"] = "@raw/oss_notices_photos"
            applicationIdSuffix = ".photos"
            versionNameSuffix = "-Photos"
            disableNativeBuild()
            // Images + GIFs only, no video/audio player
            // Target: Photo management, cloud photo backup/sync
            buildConfigField("boolean", "SUPPORT_VIDEO", "false")       // No video player
            buildConfigField("boolean", "SUPPORT_AUDIO", "false")       // No audio player
            buildConfigField("boolean", "SUPPORT_STREAMS", "false")     // S0565: no Трансляции entry-point in photos
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "false") // No audio support
            // S1442 removes RECORD_AUDIO from the photos manifest - no video mode, so no mic path.
            buildConfigField("boolean", "DECLARES_MIC_RECORDING", "false")
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
            buildConfigField("boolean", "SUPPORT_NETWORK_MONITOR", "false") // S1433: no diagnostic program in photos
            buildConfigField("boolean", "SUPPORT_BROADCAST_SOURCE", "false")
        }

        // ===== LEGACY (Full Features, Android 6.0+) =====
        create("legacy") {
            dimension = "version"
            manifestPlaceholders["ossNoticesPayload"] = "@raw/oss_notices_legacy"
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
            buildConfigField("boolean", "DECLARES_MIC_RECORDING", "true")
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
            // S1951: legacy carries applicationIdSuffix ".legacy", so the phone installs as
            // com.sza.fastmediasorter.legacy while the watch app is com.sza.fastmediasorter.
            // Play Services routes the Data Layer by (package name, signing certificate), so the
            // pair can never talk - the companion was declared on a route that cannot exist. The
            // suffix is a frozen anchor of a store-published flavor (see the applicationId policy
            // above), so the claim goes instead of the identity. Owner ruling 2026-08-22, variant A.
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "false")
            // AAR rebuilt with NDK r27c + -Wl,-z,max-page-size=16384 (LOAD Align=0x4000).
            buildConfigField("boolean", "SUPPORT_CAST", "true")
            buildConfigField("boolean", "SUPPORT_NETWORK_MONITOR", "false") // S1433: no diagnostic program in legacy
            buildConfigField("boolean", "SUPPORT_BROADCAST_SOURCE", "true")
        }

        // ===== VR (Full Features + OpenXR Headset Rendering) =====
        create("vr") {
            dimension = "version"
            manifestPlaceholders["ossNoticesPayload"] = "@raw/oss_notices_vr"
            // S0232: no applicationIdSuffix - vr shares com.sza.fastmediasorter with standard
            // for cloud OAuth identity. Re-add a .vr suffix here if/when this flavor lands on
            // Meta Horizon Store (the Store binds the listing identity to applicationId);
            // at that point a dedicated Azure/Google/Dropbox app registration becomes required.
            versionNameSuffix = "-VR"
            // S0555 Phase 01 steps 01.4/01.5: Meta Horizon Store's own SDK contract, which is not the
            // Play one. `publish-mobile-manifest` (read 2026-09-10) bands minSdk at 29-34 for Quest 2 /
            // Pro / 3-family and recommends 32, and requires targetSdk to be EXACTLY 34 for an
            // immersive app created after 2026-03-01 - a ceiling of 34 is the wide-support reading, not
            // the one a new submission is held to. defaultConfig's 26 / 36 sit outside both bands.
            //
            // This cannot be a BuildConfig gate (Rule 14): an SDK level is a build contract the
            // packager reads, not a runtime branch. It is also deliberately NOT a change to
            // defaultConfig - targetSdk 36 there answers the Play mandate (S2884), and `vr` ships to no
            // Play track, so the two requirements do not collide and neither needs to win.
            //
            // minSdk 29, the BOTTOM of Meta's band, not the 32 it recommends. Meta publishes two
            // tables: a 29-34 range for "a wider array of Android devices" and a separate "recommended"
            // row at 32 for best feature support. The owner's ruling of 2026-07-07 chose maximum device
            // reach (all four Quest models), so the range bottom is the value that serves the decision
            // and 32 is the one that quietly contradicts it - docs/VR_EDITION.md puts Quest 2 at
            // Android 10, which is API 29, so 32 could make the store build refuse the very device the
            // four-model list was widened to include. Raising 26 -> 29 removes only Oculus Go and
            // Quest 1, both named Non-goals in section 2 of the strategic spec.
            minSdk = 29
            targetSdk = 34
            // Meta Quest 2/3/Pro use arm64-v8a exclusively; skip 32-bit to halve APK size.
            //
            // S1972: conditional for the same reason as the other flavors, and it has to be. AGP
            // checks abiFilters against splits across EVERY variant at configuration time, not only
            // the one being assembled - measured 2026-08-26, a standard debug build with
            // -Pfms.abiSplits=true failed on `'arm64-v8a' in ndk abiFilters` from this very block.
            // So a single unconditional filter anywhere in the file breaks every split build.
            //
            // The residual case this leaves - a vr APK sliced for x86_64, which would carry no
            // OpenXR native, since the loader AAR ships arm64 only - is kept out by the builders
            // instead: build-debug.PS1 refuses to pass the property for a vr task, and no other
            // caller passes it at all. externalNativeBuild.cmake.abiFilters below stays arm64-only
            // regardless, so nothing tries to compile the XR runtime for an ABI it has no slice for.
            if (!abiSplitsRequested) {
                ndk {
                    abiFilters += listOf("arm64-v8a")
                }
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
            buildConfigField("boolean", "DECLARES_MIC_RECORDING", "true")
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
            buildConfigField("boolean", "SUPPORT_NETWORK_MONITOR", "false") // S1433: no diagnostic program in vr
            buildConfigField("boolean", "SUPPORT_BROADCAST_SOURCE", "false")
            buildConfigField("boolean", "SUPPORT_IMMERSIVE_XR", "true")  // S2742: owns src/vr
        }

        // ===== FOSS (F-Droid catalogue: zero proprietary dependencies) =====
        // S0403: the F-Droid inclusion policy refuses Google Play Services, ML Kit, MSAL and the
        // Dropbox SDK outright, so this flavor is defined by what it does NOT link rather than by a
        // feature tier. Everything reachable on free libraries stays on: local media, SMB/FTP/SFTP
        // shares, documents, EPUB, animations, background audio, the default player.
        create("foss") {
            dimension = "version"
            manifestPlaceholders["ossNoticesPayload"] = "@raw/oss_notices_foss"
            // S0403 research 07: a distinct id lets the F-Droid build coexist with a Play or
            // sideload install and keeps F-Droid's signing key off the store identity. Cloud OAuth
            // is off here, so the shared-id argument that keeps noLegal and vr on the store id
            // (see the applicationId policy above) does not apply.
            applicationIdSuffix = ".fdroid"
            versionNameSuffix = "-FOSS"
            // Owner ruling 2026-07-14: reach over API surface - de-googled ROMs are commonly older
            // devices, so foss matches legacy's floor instead of the API 26 line.
            minSdk = 23
            disableNativeBuild()
            buildConfigField("boolean", "SUPPORT_VIDEO", "true")
            buildConfigField("boolean", "SUPPORT_AUDIO", "true")
            // Owner ruling 2026-07-14: conservative first iteration - no IPTV stream catalogue, so
            // foss mounts streamingDisabled and hides the entry point like lite and photos do.
            buildConfigField("boolean", "SUPPORT_STREAMS", "false")
            buildConfigField("boolean", "SUPPORT_MIC_RECORDING", "false")
            // Video capture still records audio, so RECORD_AUDIO stays declared and the permission
            // row must stay visible - the same split lite carries (S1459).
            buildConfigField("boolean", "DECLARES_MIC_RECORDING", "true")
            buildConfigField("boolean", "SUPPORT_IMAGES", "true")
            buildConfigField("boolean", "SUPPORT_CLOUD", "false")
            // Network shares are the FOSS audience's replacement for cloud storage (strategic goal 4).
            buildConfigField("boolean", "SUPPORT_LOCAL_NETWORK", "true")
            buildConfigField("boolean", "SUPPORT_DOCUMENTS", "true")
            buildConfigField("boolean", "ENABLE_ANIMATIONS", "true")
            buildConfigField("boolean", "ENABLE_EPUB", "true")
            buildConfigField("boolean", "ENABLE_TRANSLATION", "false")
            buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")
            buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")
            buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")
            buildConfigField("boolean", "SUPPORT_WEAR_COMPANION", "false")
            buildConfigField("boolean", "SUPPORT_CAST", "false")
            buildConfigField("boolean", "SUPPORT_NETWORK_MONITOR", "false")
            buildConfigField("boolean", "SUPPORT_BROADCAST_SOURCE", "false")
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
        // S1450: the shared src/test set is compiled for EVERY flavor, so a test for a class that
        // lives in a flavor-scoped set (src/streamingEnabled, src/cloudEnabled) broke unit-test
        // COMPILATION on the flavors that mount the disabled counterpart - no test could run at all
        // on lite, PermissionRegistryManifestParityTest included, which is release-blocking per
        // docs/RELEASE_READINESS_STANDARD.md. These two test sets mirror, one for one, the main
        // source-set mounts below: a test lands here instead of src/test whenever its subject is
        // flavor-scoped. AGP picks up src/test<Flavor>/java by convention (see src/testStandard,
        // src/testNoLegal, src/testVr); only a set shared by SEVERAL flavors needs mounting.
        // S1455: testDocumentsEnabled is the behavioural sibling. Its subject is
        // OfficeDocumentFamilyCatalog - one FQCN with a per-flavor copy each - so a test of it
        // compiles everywhere and fails only where the catalog is empty, rather than breaking
        // compilation the way S1450's cases did.
        // S2446: the criterion for this list is a NON-EMPTY catalog, never the SUPPORT_DOCUMENTS
        // flag. lite, photos and foss declare emptySet()/emptyMap() and stay off it; foss carries
        // SUPPORT_DOCUMENTS = true and still belongs off, because owner ruling 2026-07-14 gave it
        // PDF, EPUB and text while promising no Office family. Adding a flavor here by the flag
        // turns the run red - the test asserts application/msword resolves to MediaFamily.DOCUMENT.
        // The earlier wording named the flag and claimed the set matched SUPPORT_STREAMS one for
        // one; that parity was a coincidence of six flavors and foss broke it. Gated by the
        // capability-keyed rule in scripts/quality/assert-shared-test-flavor-scope.ps1.
        listOf("testStandard", "testNoLegal", "testLegacy", "testVr").forEach { unitTestSet ->
            getByName(unitTestSet) {
                kotlin.directories.add("src/testStreamingEnabled/java")
                kotlin.directories.add("src/testCloudEnabled/java")
                kotlin.directories.add("src/testDocumentsEnabled/java")
            }
        }
        // photos mounts cloudEnabled but streamingDisabled, so it gets the cloud tests only.
        getByName("testPhotos") {
            kotlin.directories.add("src/testCloudEnabled/java")
        }
        // S0403: testCloudSdk mirrors the src/cloudSdk main-set mount one for one - the same six
        // flavors, foss excluded. It is a SEPARATE list from testCloudEnabled above because the two
        // main sets it shadows do not have the same membership: lite mounts cloudDisabled (so it is
        // off the testCloudEnabled lists) yet still links the Dropbox SDK, so DropboxClientUtilsTest
        // must compile there. Merging the two lists would drop lite's coverage or break foss.
        listOf("testStandard", "testNoLegal", "testLegacy", "testVr", "testPhotos", "testLite")
            .forEach { unitTestSet ->
                getByName(unitTestSet) {
                    kotlin.directories.add("src/testCloudSdk/java")
                }
            }
        // S3077: src/castEnabled is mounted by these five flavors; vr and foss mount src/castDisabled.
        // LocalCastProxyServerTest sat in the shared src/test set and broke unit-test COMPILATION on
        // those two, which stops every test there - the S1450 shape, recurring because the test set
        // did not exist yet (the S1498 half of RULE 7). Keep this list identical to the castEnabled
        // mounts below; the gate's mirror rule fails on any drift.
        listOf("testStandard", "testNoLegal", "testLegacy", "testPhotos", "testLite")
            .forEach { unitTestSet ->
                getByName(unitTestSet) {
                    kotlin.directories.add("src/testCastEnabled/java")
                }
            }
        // S3077: src/broadcastSource is mounted by three flavors only - the other four mount
        // src/broadcastSourceDisabled. Same incident, same rule: mirror the main mount list exactly.
        listOf("testStandard", "testNoLegal", "testLegacy").forEach { unitTestSet ->
            getByName(unitTestSet) {
                kotlin.directories.add("src/testBroadcastSource/java")
            }
        }
        // S1433: RadioControlContractImpl lives in src/networkMonitor, which only standard and noLegal
        // mount, so its test cannot live in the shared src/test set - that set compiles for every flavor
        // and the reference would break unit-test compilation on the other four, which is the S1450 shape
        // exactly. Mounted one line per flavor rather than through the loop above, because these are the
        // only two and the pairing is easier to check against the main blocks when it is spelled out.
        // S1498: src/launcherEnabled is mounted by the same two flavors and had no test set at all,
        // which is the half of Rule 7 the S1453 gate cannot see - a set that does not exist has no
        // mount list to drift. Its arithmetic had already been pushed into src/main to stay testable
        // (LauncherSectionMembership), duplicating a constant to get there.
        getByName("testStandard") {
            kotlin.directories.add("src/testNetworkMonitor/java")
            kotlin.directories.add("src/testLauncherEnabled/java")
        }
        getByName("testNoLegal") {
            // S2768: Kotlin does not add this flavor's conventional test directory to the
            // noLegal variant inputs automatically, unlike the shared mounted test sets below.
            kotlin.directories.add("src/testNoLegal/java")
            kotlin.directories.add("src/testNetworkMonitor/java")
            kotlin.directories.add("src/testLauncherEnabled/java")
        }
        // lite mounts streamingDisabled AND cloudDisabled - it mounts neither test set, which is
        // exactly what makes its unit tests compilable again.
        getByName("standard") {
            kotlin.directories.add("src/streamingEnabled/java")
            kotlin.directories.add("src/cloudEnabled/java")
            // S0403: cloud provider clients that import the Dropbox / MSAL / AppAuth / Play Services
            // auth SDKs directly. foss mounts src/cloudNoSdk, whose no-op twins bind the same four
            // contracts. Keep this list in sync with the per-flavor cloud dependency blocks below.
            kotlin.directories.add("src/cloudSdk/java")
            // S0403: Play Core seam (LanguageSplitInstaller, ReviewRequestManager). Both classes
            // used to sit in src/main with an unconditional Play Core import, which made it
            // impossible to take the library off any flavor's classpath. Each source set carries a
            // full copy under the same FQCN - the OfficeDocumentFamilyCatalog pattern - so call
            // sites keep injecting one type and never ask which flavor they are in (Rule 14).
            // Every flavor mounts exactly one of playServicesEnabled / playServicesDisabled.
            kotlin.directories.add("src/playServicesEnabled/java")
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
            } else {
                // S2447: that set also carries the ONLY AccessibilityServiceControl binding standard
                // has, so turning the overlay off used to leave the unconditional @Inject in
                // src/main unbound - the same Dagger/MissingBinding lite failed on. Both branches
                // must supply exactly one binding.
                kotlin.directories.add("src/screenCaptureDisabled/java")
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
            // S1433: Network Monitor program. Flavors without it mount src/networkMonitorDisabled,
            // which binds the no-op capability contract.
            kotlin.directories.add("src/networkMonitor/java")
            kotlin.directories.add("src/broadcastSource/java")
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
            // S0403: cloud provider clients that import the Dropbox / MSAL / AppAuth / Play Services
            // auth SDKs directly. foss mounts src/cloudNoSdk, whose no-op twins bind the same four
            // contracts. Keep this list in sync with the per-flavor cloud dependency blocks below.
            kotlin.directories.add("src/cloudSdk/java")
            kotlin.directories.add("src/playServicesEnabled/java")
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
            // S1433: Network Monitor program - part of the sideload superset.
            kotlin.directories.add("src/networkMonitor/java")
            kotlin.directories.add("src/broadcastSource/java")
        }
        getByName("legacy") {
            kotlin.directories.add("src/streamingEnabled/java")
            kotlin.directories.add("src/cloudEnabled/java")
            // S0403: cloud provider clients that import the Dropbox / MSAL / AppAuth / Play Services
            // auth SDKs directly. foss mounts src/cloudNoSdk, whose no-op twins bind the same four
            // contracts. Keep this list in sync with the per-flavor cloud dependency blocks below.
            kotlin.directories.add("src/cloudSdk/java")
            kotlin.directories.add("src/playServicesEnabled/java")
            // S0403: Google Cast SDK seam impl (CastMediaManagerImpl); foss mounts castDisabled.
            kotlin.directories.add("src/castEnabled/java")
            // S1951: no Wear companion in this flavor - the applicationIdSuffix makes the Data Layer
            // route impossible, so mount the no-op contract instead of the GMS bridge.
            kotlin.directories.add("src/wearStub/java")
            kotlin.directories.add("src/ocrEnabled/java")
            kotlin.directories.add("src/translationEnabled/java")
            kotlin.directories.add("src/translationMlKit/java")
            kotlin.directories.add("src/vrStub/java")
            // S0404: no launcher-mode surface in this flavor - mount the no-op capability contract.
            kotlin.directories.add("src/launcherDisabled/java")
            // S1433: no Network Monitor program in this flavor - mount the no-op capability contract.
            kotlin.directories.add("src/networkMonitorDisabled/java")
            // S2447: no screen-capture suite here - mount the no-op AccessibilityServiceControl.
            kotlin.directories.add("src/screenCaptureDisabled/java")
            kotlin.directories.add("src/broadcastSource/java")
        }
        getByName("vr") {
            kotlin.directories.add("src/streamingEnabled/java")
            kotlin.directories.add("src/cloudEnabled/java")
            // S0403: cloud provider clients that import the Dropbox / MSAL / AppAuth / Play Services
            // auth SDKs directly. foss mounts src/cloudNoSdk, whose no-op twins bind the same four
            // contracts. Keep this list in sync with the per-flavor cloud dependency blocks below.
            kotlin.directories.add("src/cloudSdk/java")
            kotlin.directories.add("src/playServicesEnabled/java")
            // S1439: vr declares SUPPORT_CAST=false because Horizon OS has no Play Services Cast
            // module, so it mounts the no-op seam impl - shipping the real one packaged an SDK the
            // platform cannot back, behind two safeguards nothing recorded as requirements.
            kotlin.directories.add("src/castDisabled/java")
            // S0403: vr has no Wear companion -> mount the wearStub no-op (no Play Services Wearable).
            kotlin.directories.add("src/wearStub/java")
            kotlin.directories.add("src/ocrEnabled/java")
            kotlin.directories.add("src/translationEnabled/java")
            kotlin.directories.add("src/translationMlKit/java")
            kotlin.directories.add("src/vrOnly/java")
            // S0404: vr has its own OpenXR shell and the headset's system launcher - no Android
            // launcher mode here (strategic ADR-1). Mount the no-op capability contract.
            kotlin.directories.add("src/launcherDisabled/java")
            // S1433: no Network Monitor program in this flavor - mount the no-op capability contract.
            kotlin.directories.add("src/networkMonitorDisabled/java")
            // S2447: no screen-capture suite here - mount the no-op AccessibilityServiceControl.
            kotlin.directories.add("src/screenCaptureDisabled/java")
            kotlin.directories.add("src/broadcastSourceDisabled/java")
        }
        getByName("photos") {
            kotlin.directories.add("src/streamingDisabled/java")
            kotlin.directories.add("src/cloudEnabled/java")
            // S0403: cloud provider clients that import the Dropbox / MSAL / AppAuth / Play Services
            // auth SDKs directly. foss mounts src/cloudNoSdk, whose no-op twins bind the same four
            // contracts. Keep this list in sync with the per-flavor cloud dependency blocks below.
            kotlin.directories.add("src/cloudSdk/java")
            kotlin.directories.add("src/playServicesEnabled/java")
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
            // S1433: no Network Monitor program in this flavor - mount the no-op capability contract.
            kotlin.directories.add("src/networkMonitorDisabled/java")
            // S2447: no screen-capture suite here - mount the no-op AccessibilityServiceControl.
            kotlin.directories.add("src/screenCaptureDisabled/java")
            kotlin.directories.add("src/broadcastSourceDisabled/java")
        }
        // S0403: foss subtracts exactly the proprietary nodes. Every "disabled"/"stub" set below is
        // the no-op contract half of a seam whose real half links a Google, Microsoft or Dropbox
        // SDK; documents, EPUB, audio and default-player stay on, which is what separates it from lite.
        getByName("foss") {
            kotlin.directories.add("src/streamingDisabled/java")
            kotlin.directories.add("src/cloudDisabled/java")
            // S0403: the no-op half of the cloud-provider seam. cloudDisabled above covers cloud
            // IDENTITY only - one repository binding - which is why lite mounted it for a year while
            // still linking all five cloud SDKs. This set is what actually keeps them off foss.
            kotlin.directories.add("src/cloudNoSdk/java")
            kotlin.directories.add("src/castDisabled/java")
            kotlin.directories.add("src/wearStub/java")
            kotlin.directories.add("src/ocrDisabled/java")
            kotlin.directories.add("src/vrStub/java")
            kotlin.directories.add("src/launcherDisabled/java")
            kotlin.directories.add("src/networkMonitorDisabled/java")
            kotlin.directories.add("src/playServicesDisabled/java")
            // S2447: no screen-capture suite here - mount the no-op AccessibilityServiceControl.
            kotlin.directories.add("src/screenCaptureDisabled/java")
            kotlin.directories.add("src/broadcastSourceDisabled/java")
        }
        getByName("lite") {
            kotlin.directories.add("src/streamingDisabled/java")
            kotlin.directories.add("src/cloudDisabled/java")
            // S0403: lite drops cloud IDENTITY but still links every cloud SDK, so it mounts the
            // SDK-backed clients like the other five. foss is the only flavor on src/cloudNoSdk.
            kotlin.directories.add("src/cloudSdk/java")
            kotlin.directories.add("src/playServicesEnabled/java")
            // S0403: lite ships Cast (video flavor), so it mounts the GMS-backed castEnabled impl.
            kotlin.directories.add("src/castEnabled/java")
            // S0403: lite has no Wear companion (SUPPORT_WEAR_COMPANION=false) -> wearStub no-op.
            kotlin.directories.add("src/wearStub/java")
            kotlin.directories.add("src/ocrDisabled/java")
            kotlin.directories.add("src/vrStub/java")
            // S0404: no launcher-mode surface in this flavor - mount the no-op capability contract.
            kotlin.directories.add("src/launcherDisabled/java")
            // S1433: no Network Monitor program in this flavor - mount the no-op capability contract.
            kotlin.directories.add("src/networkMonitorDisabled/java")
            // S2447: no screen-capture suite here - mount the no-op AccessibilityServiceControl.
            // This is the flavor whose hiltJavaCompileLiteDebug failure opened the ticket.
            kotlin.directories.add("src/screenCaptureDisabled/java")
            kotlin.directories.add("src/broadcastSourceDisabled/java")
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
                // S2851: how many of those workers run at once. Left at Gradle's default of 1 the
                // suite is strictly serial: measured 2026-09-10 on a 20-processor host, 695 classes
                // and 5033 tests took 429 s of wall clock against 415.6 s of reported test time, so
                // 97% of the run is one worker executing tests one after another and only 13.4 s is
                // Gradle. Each concurrent worker costs another `maxHeapSize` above, which is why the
                // value is a measurement rather than a core count - the two limits above exist
                // because this suite has twice been killed by exactly that memory peak.
                it.maxParallelForks = unitTestMaxParallelForks
                // S2585: bound the task's WALL CLOCK, next to the two limits that bound the worker's
                // heap and lifetime. This one is different in kind and the difference matters: it
                // ends the task, not the worker - Gradle's timeout only interrupts its own
                // daemon-side thread, and the worker that hung on 2026-09-05 was spinning RUNNABLE
                // and ignored every interrupt, including kotlinx-coroutines-test's own 60 s runTest
                // timeout. Its value is that the wrapper stops waiting, reaches its finally,
                // releases Build.Phone and reaps the worker there. Alone it would leave the worker
                // holding R.jar; that half lives in scripts/builders/gradle-worker-reaper.ps1.
                it.timeout.set(Duration.ofMinutes(unitTestTimeoutMinutes))
                // S1657: Gson ships no java.time adapter, so a bare Gson() reflects over java.time's own
                // private fields. The test JVM enforces modules and refuses that read with
                // InaccessibleObjectException, which made every serialization test of a model carrying an
                // Instant fail on a difference the app never sees.
                // S1668 took the app off that path - the injected Gson now registers InstantTypeAdapter - so
                // this open is no longer what lets the model tests run. It is still required by exactly one
                // test, InstantTypeAdapterTest, which serializes with a bare reflective Gson to prove the
                // adapter emits identical bytes to the format already stored on users' devices. Measured:
                // with this line removed that single test fails and the other 13 in the pair of classes pass.
                // Remove the line only together with that compatibility guard.
                it.jvmArgs("--add-opens=java.base/java.time=ALL-UNNAMED")
                // S3441: WaveParticlesContractConstantsTest reads the watch renderer and the site's copy of
                // the WAVE-PARTICLES reference as text. Neither is on this module's classpath, so without
                // declaring them an edit to either left the test UP-TO-DATE and a drifted constant passed.
                it.inputs.files(
                    rootProject.file(
                        "wear/src/main/java/com/sza/fastmediasorter/wear/ui/common/WaveParticleBackground.kt"
                    ),
                    rootProject.file("documentation/assets/wave-particles.js")
                ).withPropertyName("waveParticlesContractSources")
                    .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
                it.systemProperty(
                    "settings.manifest.generate",
                    System.getProperty("settings.manifest.generate") ?: "false"
                )
                it.systemProperty(
                    "icon.inventory.generate",
                    System.getProperty("icon.inventory.generate") ?: "false"
                )
                // S1789: Redirect XML and HTML reports for filtered test runs (--tests) to a -filtered subdirectory
                // so filtered runs do not clear the full unit test suite XML reports in build/test-results/test<Variant>UnitTest/.
                val defaultXmlDir = layout.buildDirectory.dir("test-results/${it.name}")
                val filteredXmlDir = layout.buildDirectory.dir("test-results/${it.name}-filtered")
                val defaultHtmlDir = layout.buildDirectory.dir("reports/tests/${it.name}")
                val filteredHtmlDir = layout.buildDirectory.dir("reports/tests/${it.name}-filtered")

                it.reports.junitXml.outputLocation.set(
                    provider {
                        val filterObj = it.filter
                        val hasCmdFilter = try {
                            val method = filterObj.javaClass.getMethod("getCommandLineIncludePatterns")
                            (method.invoke(filterObj) as? Set<*>)?.isNotEmpty() == true
                        } catch (_: Exception) {
                            false
                        }
                        if (filterObj.includePatterns.isNotEmpty() || hasCmdFilter) {
                            filteredXmlDir.get()
                        } else {
                            defaultXmlDir.get()
                        }
                    }
                )

                it.reports.html.outputLocation.set(
                    provider {
                        val filterObj = it.filter
                        val hasCmdFilter = try {
                            val method = filterObj.javaClass.getMethod("getCommandLineIncludePatterns")
                            (method.invoke(filterObj) as? Set<*>)?.isNotEmpty() == true
                        } catch (_: Exception) {
                            false
                        }
                        if (filterObj.includePatterns.isNotEmpty() || hasCmdFilter) {
                            filteredHtmlDir.get()
                        } else {
                            defaultHtmlDir.get()
                        }
                    }
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
            // S1436: src/release/AndroidManifest.xml removes REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            // so no registry row and no in-feature prompt for it may appear in a release build.
            buildConfigField("boolean", "DECLARES_BATTERY_OPTIMIZATION", "false")
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
            // S1436: initWith copies the release value, but the manifest removal is keyed on the
            // src/release source set, which this build type does not use - it still declares the
            // permission, so the axis is restored explicitly.
            buildConfigField("boolean", "DECLARES_BATTERY_OPTIMIZATION", "true")
        }
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            versionNameSuffix = "-BENCHMARK"
            matchingFallbacks += listOf("release")
            // S1436: same as staging - src/release does not apply to this build type.
            buildConfigField("boolean", "DECLARES_BATTERY_OPTIMIZATION", "true")
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
            // S3041: MINA SSHD's client file-system providers; the embedded server uses none of them.
            excludes += "META-INF/services/java.nio.file.spi.FileSystemProvider"
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

            // S1971: libVLC is the one native set that goes the other way. Its two libraries are 44 MB
            // of a 162.8 MB noLegal artifact, and the S0971 reason above does not reach them - noLegal
            // is sideload-only and never enters Play, so no Play policy applies. The dependency stays
            // `noLegalImplementation`, so classes.jar remains on the compile path and VlcPlaybackEngine
            // still builds; only the native part leaves the package and is fetched at runtime. No other
            // flavor declares the dependency, so these two lines are a no-op everywhere else.
            // `libc++_shared.so` is deliberately NOT excluded: the copy that ships comes from another
            // dependency via pickFirsts, and this ticket leaves that pairing exactly as it is.
            excludes += "**/libvlc.so"
            excludes += "**/libvlcjni.so"

            // Forces 16 KB page alignment for every native library, which Android 15+ devices need
            // and Google Play rejects the APK without. This comment sat above the `splits` block
            // until S1972, where it read as an explanation of a setting it has nothing to do with.
            useLegacyPackaging = false
        }
    }
    
    // S1190: no locale filter here on purpose. The package carries every locale declared in
    // res/xml/locales_config.xml; the store channel trims it back through Play language splits, the
    // direct APK and the non-Play editions deliberately carry all of them.

    // S1972: `splits` is an `android {}`-level block - the DSL offers no per-build-type form of it,
    // so "slice debug builds, leave the release path universal" cannot be written as a buildType
    // setting and is gated by a Gradle property instead. The debug builders pass
    // -Pfms.abiSplits=true; nothing on the release path does, so a release still emits one
    // all-architecture APK per flavor and the direct-download channel keeps the device set it
    // reaches (canon hard invariant 2, strategic §3.2).
    //
    // Play is untouched either way: `android.splits` is ignored when building a bundle, and
    // `bundle.abi.enableSplit` already defaults to true, so the AAB was always sliced per ABI.
    //
    // isUniversalApk stays at its default (false): with the property off there is one output and a
    // universal APK would be a duplicate of it; with the property on, a third output nobody asked for.
    splits {
        abi {
            isEnable = abiSplitsRequested
            reset()
            // The two architectures anything in this project actually runs on: the owner's phone and
            // the Quest are arm64-v8a, every emulator here is x86_64. When splits are on this list
            // is the ONLY ABI filter in the build - every flavor's ndk.abiFilters is skipped,
            // because AGP refuses the two together - so it decides what gets packaged outright
            // rather than narrowing something already chosen.
            include("arm64-v8a", "x86_64")
        }
    }

    lint {
        checkAllWarnings = false
        // Fail CI on lint ERRORs; warnings only produce report
        abortOnError = true
        checkReleaseBuilds = false
        disable += "InvalidPackage"
        // S3155: MissingTranslation is reported but not fatal. All 36 findings named the same ten
        // unauthored locales against keys authored in en/ru/uk - which CLAUDE.md Rule 30 declares
        // legal until the release boundary, where scripts/quality/assert-new-lexemes-translated.ps1
        // refuses them at /spec-prerelease step 0.8. A per-commit gate failing on them contradicts
        // that loop. Debug-only strings still carry translatable="false", and post-change.ps1's
        // strings audit still sweeps locale parity on every key.
        warning += "MissingTranslation"
        disable += "NewApi"
        disable += "UnsafeOptInUsageError"
        // ExperimentalDetector also handles UnsafeExperimentalUsageWarning; with both disabled the
        // detector is skipped entirely, preventing a K2 restoreSymbolOrThrowIfDisposed crash that
        // blocks local baseline regeneration (Kotlin 2.2.10 + AGP 9.2.1).
        disable += "UnsafeExperimentalUsageWarning"
        // False positive: 0dp with layout_weight in LinearLayout or as ConstraintLayout child
        disable += "Suspicious0dp"
        // S3155: UseAppTint asks for app:tint over android:tint, which matters only below API 21 -
        // the floor here is 23 (legacy) and 26 everywhere else, so android:tint is honoured on every
        // device this ships to. Six of the seven findings are also RemoteViews widget layouts, which
        // the framework inflater renders: an AppCompat app: attribute is inert there, so taking the
        // advice would break the tint rather than fix it.
        disable += "UseAppTint"
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
    "src/foss",
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
        rootProject.layout.projectDirectory.file("docs/FEATURES-ru.md"),
        rootProject.layout.projectDirectory.file("docs/FEATURES-uk.md"),
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

        // S1783: the released-tickets listing is bound to the debug build type, not to a flag inside
        // the code, so a release build cannot carry it even by mistake.
        if (buildType == "debug") {
            val generateReleasedTickets = tasks.register<GenerateReleasedTicketsTask>(
                "generateReleasedTickets${variant.name.replaceFirstChar { it.uppercase() }}"
            ) {
                // S3155: set only what exists. @Optional excuses an UNSET property; it does not
                // excuse one set to an absent path, which Gradle still validates and refuses. PLAN/
                // is gitignored, so on CI and on any clean clone both of these are absent and the
                // task emits an empty listing instead of failing the variant.
                val queue = rootProject.layout.projectDirectory.file("PLAN/RELEASE_QUEUE.md")
                val ready = rootProject.layout.projectDirectory.file("PLAN/RELEASE_READY.md")
                if (queue.asFile.isFile) releaseQueueFile.set(queue)
                if (ready.asFile.isFile) releaseReadyFile.set(ready)
            }
            variant.sources.assets?.addGeneratedSourceDirectory(
                generateReleasedTickets,
                GenerateReleasedTicketsTask::outputDir
            )
        }

        variant.outputs.forEach { output ->
            // S1972: the ABI token is not cosmetic. This name is per-output, and every slice of a
            // split build reaches this block with the same flavor, build type and version - so
            // without the token all of them resolve to ONE file name and each packaging step
            // overwrites the previous slice. Measured 2026-08-26: a split standard debug wrote an
            // output-metadata.json listing two elements, arm64-v8a and x86_64, both naming the
            // identical .apk, with exactly one 88 MB file on disk. The resolver could then hand a
            // caller the arm64 element and the caller would install the x86_64 bytes.
            //
            // Empty for a non-split build, where the single output carries no ABI filter - so the
            // release name and today's debug name are unchanged, byte for byte.
            val abiToken = (output as? com.android.build.api.variant.VariantOutput)
                ?.filters
                ?.firstOrNull { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?.let { "_$it" }
                ?: ""
            output.outputFileName.set(
                output.versionName.map { vn ->
                    val v = vn ?: "unknown"
                    if (buildType == "release") "FastMediaSorter_${flavorName}${abiToken}_v${v}.apk"
                    else "FastMediaSorter_${flavorName}_${buildType}${abiToken}_v${v}.apk"
                }
            )
        }

        // S0403: the F-Droid artifact is proved clean per variant, because the runtime classpath is
        // per variant - there is no single "the foss dependencies" to check once. Only foss carries
        // the gate: the other six flavors link these coordinates on purpose.
        if (flavorName == "foss") {
            val verifyTaskName = "verifyNoProprietaryDeps${variant.name.replaceFirstChar { it.uppercase() }}"
            val runtimeClasspath = "${variant.name}RuntimeClasspath"
            val verifyNoProprietaryDeps = tasks.register<VerifyNoProprietaryDepsTask>(verifyTaskName) {
                group = "verification"
                description = "Fails the build when the $runtimeClasspath graph links a proprietary coordinate."
                denyListFile.set(layout.projectDirectory.file("compliance/proprietary-deps-denylist.txt"))
                configurationName.set(runtimeClasspath)
                rootComponent.set(
                    configurations.named(runtimeClasspath)
                        .flatMap { it.incoming.resolutionResult.rootComponent }
                )
                reportFile.set(
                    layout.buildDirectory.file("reports/compliance/$verifyTaskName.txt")
                )
            }
            // S0403: onVariants runs before AGP registers the pre<Variant>Build anchors, so
            // tasks.named() here fails configuration with UnknownTaskException. Match by name
            // through configureEach, which is evaluated when the anchor is actually created.
            val preBuildTaskName = "pre${variant.name.replaceFirstChar { it.uppercase() }}Build"
            tasks.configureEach {
                if (name == preBuildTaskName) {
                    dependsOn(verifyNoProprietaryDeps)
                }
            }
        }

        // S2879: exactly the flavors that declare files("libs/*.aar") in dependencies below. lite,
        // photos and foss legitimately build without those AARs, so the check is bound to the
        // variant rather than to the project - a configuration-time check would refuse them too.
        val prebuiltNativeAarFlavors = setOf("standard", "noLegal", "legacy", "vr")
        if (flavorName in prebuiltNativeAarFlavors) {
            val verifyAarsTaskName = "verifyPrebuiltNativeAars${variant.name.replaceFirstChar { it.uppercase() }}"
            val verifyPrebuiltNativeAars = tasks.register<VerifyPrebuiltNativeAarsTask>(verifyAarsTaskName) {
                group = "verification"
                description = "Fails the build when a declared build-time native AAR is missing or empty."
                manifestFile.set(
                    rootProject.layout.projectDirectory.file(
                        VerifyPrebuiltNativeAarsTask.MANIFEST_RELATIVE_PATH
                    )
                )
                projectRootMarker.set(rootProject.layout.projectDirectory.file("settings.gradle.kts"))
                variantName.set(variant.name)
                reportFile.set(layout.buildDirectory.file("reports/compliance/$verifyAarsTaskName.txt"))
                outputs.upToDateWhen { false }
            }
            // S0403: onVariants runs before AGP registers the pre<Variant>Build anchors, so
            // tasks.named() here fails configuration with UnknownTaskException. Match by name
            // through configureEach, which is evaluated when the anchor is actually created.
            val preBuildAnchorName = "pre${variant.name.replaceFirstChar { it.uppercase() }}Build"
            tasks.configureEach {
                if (name == preBuildAnchorName) {
                    dependsOn(verifyPrebuiltNativeAars)
                }
            }
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
        // S1439: vr is off this list for the same reason - it mounts castDisabled, and registering a
        // provider for an impl the flavor does not ship is what made the two halves disagree.
        val castFlavors = setOf("standard", "noLegal", "lite", "photos", "legacy")
        if (flavorName in castFlavors) {
            variant.sources.manifests.addStaticManifestFile("src/castEnabled/AndroidManifest.xml")
        }

        // S0403: the wearGms source set (GMS WearableListenerService) is mounted by directory only,
        // which does not pull in its AndroidManifest automatically. Inject the Wear Data Layer
        // <service> overlay for every Wear-capable flavor. addStaticManifestFile is additive, so it
        // coexists with noLegal's manifest.srcFile(src/vr) override. foss / non-Wear flavors mount
        // wearStub (no manifest), so they never register the service.
        // S1951: legacy is off this list - it mounts wearStub, so registering the GMS listener
        // service would declare a receiver for an impl the flavor does not ship.
        val wearFlavors = setOf("standard", "noLegal")
        if (flavorName in wearFlavors) {
            variant.sources.manifests.addStaticManifestFile("src/wearGms/AndroidManifest.xml")
        }

        // S0404: the launcherEnabled source set is mounted by directory only, so its manifest (the
        // HOME-filter activity, shipped disabled) is injected explicitly. addStaticManifestFile is
        // additive, so it coexists with noLegal's manifest.srcFile(src/vr) override.
        val launcherFlavors = setOf("standard", "noLegal")
        if (flavorName in launcherFlavors) {
            variant.sources.manifests.addStaticManifestFile("src/launcherEnabled/AndroidManifest.xml")
            // S1433: same reason - src/networkMonitor is mounted by directory, so its permission
            // manifest needs its own injection or the Monitor's grants never reach the merge.
            variant.sources.manifests.addStaticManifestFile("src/networkMonitor/AndroidManifest.xml")
        }

        // S2793: src/broadcastSource is mounted by directory only, so its manifest - which declares
        // BroadcastCaptureService and the video spike activity - never reached the merge and the
        // service was undeclared in every shipped APK. startForegroundService then no-ops silently,
        // which is the whole of the "Live Broadcast does nothing" report. Same class as S0403/S1433.
        val broadcastFlavors = setOf("standard", "noLegal", "legacy")
        if (flavorName in broadcastFlavors) {
            variant.sources.manifests.addStaticManifestFile("src/broadcastSource/AndroidManifest.xml")
            // S3154: FOREGROUND_SERVICE_CAMERA blocks the Play commit until a background-camera use
            // case is declared, so only the sideload flavor keeps the camera type on the service.
            val videoServiceDir = if (flavorName == "noLegal") "broadcastVideoBackground" else "broadcastVideoForeground"
            variant.sources.manifests.addStaticManifestFile("src/$videoServiceDir/AndroidManifest.xml")
        }

        // S2726: the vr-only permission overlay. It cannot go in src/vr/AndroidManifest.xml - noLegal
        // mounts that same file via manifest.srcFile and legitimately holds READ_CONTACTS and
        // READ_PHONE_STATE, so the removals need a file only vr reads.
        if (flavorName == "vr") {
            variant.sources.manifests.addStaticManifestFile("src/vrOnly/AndroidManifest.xml")
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

        // S1436: the two manifest-composition axes whose value is not a literal. Set here rather
        // than in productFlavors so each sits beside the injection condition it mirrors and cannot
        // drift from it: DECLARES_SCREEN_CAPTURE repeats injectSharedCaptureManifest above, and
        // DECLARES_OVERLAY_PERMISSION covers the SPECIAL_USE overlay host plus noLegal's own
        // SYSTEM_ALERT_WINDOW declaration. The permission registry reads both to decide whether a
        // row may appear; a build that does not declare the permission must not offer to grant it.
        variant.buildConfigFields?.put(
            "DECLARES_SCREEN_CAPTURE",
            BuildConfigField("boolean", injectSharedCaptureManifest, "S1436: MediaProjection capture manifest is merged into this variant"),
        )
        val declaresOverlayPermission =
            flavorName == "noLegal" || (flavorName == "standard" && edgeGestureOverlayStandardEnabled)
        variant.buildConfigFields?.put(
            "DECLARES_OVERLAY_PERMISSION",
            BuildConfigField("boolean", declaresOverlayPermission, "S1436: SYSTEM_ALERT_WINDOW is declared in this variant"),
        )

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
                    // 2026-08-12 (pre-release refresh): bumped 2026.07.23.234303 → 2026.08.04.234419.
                    // Still on nightly - PyPI stable remains 2026.7.4, older than the pinned nightly
                    // date, so it does not supersede. Freshest nightly at ship time; needs an on-device
                    // link-download to verify extraction.
                    // 2026-08-20 (pre-release refresh): back to the stable channel, 2026.08.04.234419
                    // → 2026.8.19. PyPI stable finally moved past the pinned nightly date, which is the
                    // documented condition for leaving nightly: stable carries the same Instagram
                    // Rework lineage plus two weeks of maintenance, and is the better-tested of the two
                    // for every other site. Needs an on-device link-download to verify extraction -
                    // a pip resolve alone proves nothing.
                    install("yt-dlp==2026.8.19")
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    
    // Security (EncryptedSharedPreferences for cloud credentials)
    implementation(libs.androidx.security.crypto)

    // S0200 - Credential Manager (Google identity binding) + Chrome Custom Tabs.
    // Credential Manager replaces the deprecated Google Sign-In SDK; googleid supplies GetGoogleIdOption.
    // androidx.browser is consumed by Phase 03 CCT routing - added here to keep all S0200 deps colocated.
    implementation(libs.androidx.credentials.credentials)
    // S0403: the -play-services-auth provider is the Credential Manager half that pulls GMS in, so
    // it is scoped away from foss. The core artifact above stays global - it is plain androidx and
    // the system Credential Manager works without Play Services.
    "standardImplementation"(libs.androidx.credentials.play.services.auth)
    "noLegalImplementation"(libs.androidx.credentials.play.services.auth)
    "liteImplementation"(libs.androidx.credentials.play.services.auth)
    "photosImplementation"(libs.androidx.credentials.play.services.auth)
    "legacyImplementation"(libs.androidx.credentials.play.services.auth)
    "vrImplementation"(libs.androidx.credentials.play.services.auth)
    // S0385: googleid is consumed only by src/cloudEnabled (CredentialManagerGoogleIdentityRepository),
    // which is mounted into every flavor EXCEPT lite (lite mounts cloudDisabled). Scope it per-flavor
    // so the lite APK stops packaging an unused Google-identity dependency.
    "standardImplementation"(libs.google.googleid)
    "noLegalImplementation"(libs.google.googleid)
    "legacyImplementation"(libs.google.googleid)
    "vrImplementation"(libs.google.googleid)
    "photosImplementation"(libs.google.googleid)
    implementation(libs.androidx.browser)

    // Jetpack Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // S0385: material-icons-extended is NOT dead - Icons.Filled.Pause / SkipNext / SkipPrevious
    // (media-control icons in WearSyncSettingsFragment + widget config) live only in the extended
    // set, not in material-icons-core. Removing it breaks compilation. Kept intentionally.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    
    // Material Design 3
    implementation(libs.google.material)

    // Google Play In-App Review (S0135)
    // S0403: Play Core is unavailable on F-Droid, so both artifacts are scoped away from foss. A
    // catalogue install has no Play store page to review and carries every locale in the APK, so
    // neither library has anything to do there. Consumers live behind the src/playServices* seam.
    "standardImplementation"(libs.google.play.review.ktx)
    "noLegalImplementation"(libs.google.play.review.ktx)
    "liteImplementation"(libs.google.play.review.ktx)
    "photosImplementation"(libs.google.play.review.ktx)
    "legacyImplementation"(libs.google.play.review.ktx)
    "vrImplementation"(libs.google.play.review.ktx)
    // Google Play language splits (S1190). Brought back for on-demand locale delivery only - the
    // dynamic-feature module this library once served was deleted with S0423 and stays deleted.
    "standardImplementation"(libs.google.play.feature.delivery.ktx)
    "noLegalImplementation"(libs.google.play.feature.delivery.ktx)
    "liteImplementation"(libs.google.play.feature.delivery.ktx)
    "photosImplementation"(libs.google.play.feature.delivery.ktx)
    "legacyImplementation"(libs.google.play.feature.delivery.ktx)
    "vrImplementation"(libs.google.play.feature.delivery.ktx)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process) // For ProcessLifecycleOwner
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    
    // Hilt
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.android.compiler)
    
    // WorkManager - 2.10.x: SystemForegroundService handles Service.onTimeout() for
    // FOREGROUND_SERVICE_TYPE_DATA_SYNC on Android 14+, preventing
    // ForegroundServiceDidNotStopInTimeException fatals (S0709). Keep in sync with work-multiprocess below.
    implementation(libs.androidx.work.runtime.ktx)

    // Baseline Profiles runtime installer
    implementation(libs.androidx.profileinstaller)
    
    // Hilt WorkManager integration
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Paging 3
    implementation(libs.androidx.paging.runtime.ktx)
    
    // AppFunctions requires minSdk 24, so API-23 FOSS and Legacy variants must not resolve it.
    "standardImplementation"(libs.androidx.appfunctions)
    "noLegalImplementation"(libs.androidx.appfunctions)
    "liteImplementation"(libs.androidx.appfunctions)
    "photosImplementation"(libs.androidx.appfunctions)
    "vrImplementation"(libs.androidx.appfunctions)
    "kspStandard"(libs.androidx.appfunctions.compiler)
    "kspNoLegal"(libs.androidx.appfunctions.compiler)
    "kspLite"(libs.androidx.appfunctions.compiler)
    "kspPhotos"(libs.androidx.appfunctions.compiler)
    "kspVr"(libs.androidx.appfunctions.compiler)
    
    // DataStore - 1.1.x or newer is required: 1.0.0 persists via File.renameTo, which cannot
    // replace an existing file on Windows, so every write after the first one fails (S1449).
    implementation(libs.androidx.datastore.preferences)
    
    // DocumentFile for SAF support
    implementation(libs.androidx.documentfile)

    // Print support
    implementation(libs.androidx.print)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    // S0403: this artifact pulls play-services-basement and play-services-tasks transitively, which
    // the F-Droid denylist refuses. Its only consumers are the GMS-backed source sets (castEnabled,
    // cloudEnabled, cloudSdk, playServicesEnabled, translationMlKit, wearGms) - foss mounts none of
    // them, so it is declared per-flavor for the six that do.
    "standardImplementation"(libs.kotlinx.coroutines.play.services)
    "noLegalImplementation"(libs.kotlinx.coroutines.play.services)
    "liteImplementation"(libs.kotlinx.coroutines.play.services)
    "photosImplementation"(libs.kotlinx.coroutines.play.services)
    "legacyImplementation"(libs.kotlinx.coroutines.play.services)
    "vrImplementation"(libs.kotlinx.coroutines.play.services)
    
    // ExoPlayer (HLS/DASH re-enabled per-flavor below for S0116; SmoothStreaming stays excluded)
    implementation(libs.androidx.media3.exoplayer) {
        // Exclude SmoothStreaming - not used by url-download or playback
        exclude(group = "androidx.media3", module = "media3-exoplayer-smoothstreaming")
    }
    // S0116: HLS/DASH offline downloader is wired only into video-supporting market flavors.
    // lite/photos stay without these modules to preserve their APK size budget.
    "standardImplementation"(libs.androidx.media3.exoplayer.hls)
    "standardImplementation"(libs.androidx.media3.exoplayer.dash)
    "noLegalImplementation"(libs.androidx.media3.exoplayer.hls)
    "noLegalImplementation"(libs.androidx.media3.exoplayer.dash)
    "legacyImplementation"(libs.androidx.media3.exoplayer.hls)
    "legacyImplementation"(libs.androidx.media3.exoplayer.dash)
    "vrImplementation"(libs.androidx.media3.exoplayer.hls)
    "vrImplementation"(libs.androidx.media3.exoplayer.dash)
    // S0565: RTSP playback (rtsp:// internet streams) is wired only into streaming-capable flavors,
    // matching the HLS/DASH flavor split; lite/photos stay RTSP-free to preserve their APK budget.
    "standardImplementation"(libs.androidx.media3.exoplayer.rtsp)
    "noLegalImplementation"(libs.androidx.media3.exoplayer.rtsp)
    "legacyImplementation"(libs.androidx.media3.exoplayer.rtsp)
    "vrImplementation"(libs.androidx.media3.exoplayer.rtsp)
    // S2662: RTSP SERVER on the device (broadcast source, pillar B) - the mirror of the media3
    // client above, which only receives. Restricted to the three flavors that carry
    // SUPPORT_BROADCAST_SOURCE: a plain implementation() would push the native encoder .so into
    // lite/photos/vr/foss, which cannot broadcast at all. Owner accepted the JitPack source
    // 2026-09-06; the repository is already declared for PhotoView in settings.gradle.kts.
    // S2884: 1.4.3, the newest - un-pinned when the project moved to compileSdk 37 (its AAR
    // declares minCompileSdk=37, re-measured 2026-09-10; the 1.4.1 pin existed for that alone).
    // The `whip` sibling module (WebRTC-HTTP publishing) is excluded: nothing here publishes over
    // WebRTC, and it is the only path that drags BouncyCastle 1.84 in against the 1.75 pin below,
    // which the version guard rejected on the first resolution attempt (measured 2026-09-06).
    "standardImplementation"(libs.rtsp.server) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    "noLegalImplementation"(libs.rtsp.server) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    "legacyImplementation"(libs.rtsp.server) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    // RootEncoder carries the base classes (`Camera2Base`, `ConnectChecker`) that the server type
    // above extends and exposes, but JitPack generates its POM with every dependency at runtime
    // scope, so they reach the runtime classpath and not the compile one - the compiler reported
    // "Cannot access 'Camera2Base' which is a supertype of 'RtspServerCamera2'" until these two were
    // declared here. Versions match what RTSP-Server 1.4.3 resolves to (library 2.8.1, its POM);
    // bump them together with it.
    // S2884: since library 2.8.1 the whip module also arrives through THIS pair (Gradle module
    // metadata, not the POM) and drags BouncyCastle 1.84 against the 1.75 pin - the same whip
    // exclusion as the server block above is repeated on every line here.
    "standardImplementation"(libs.rootencoder.library) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    "standardImplementation"(libs.rootencoder.common) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    "noLegalImplementation"(libs.rootencoder.library) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    "noLegalImplementation"(libs.rootencoder.common) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    "legacyImplementation"(libs.rootencoder.library) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    "legacyImplementation"(libs.rootencoder.common) {
        exclude(group = "com.github.pedroSG94.RootEncoder", module = "whip")
    }
    // S1060: libVLC software decoding of patented codecs + DVD/BD ISO playback. noLegal ONLY -
    // the flavor boundary is the ticket's legal premise (patents/DMCA), so this must never move
    // to implementation(). Ships prebuilt .so per ABI; noLegal abiFilters govern which are packaged.
    // 3.7.5, not the spec's 3.6.0 pin: 3.6.0's libvlc.so is 4 KB-aligned and fails the repo's
    // 16 KB page-alignment rule; 3.7.5 ships 16 KB-aligned .so for arm64-v8a and x86_64 (measured).
    "noLegalImplementation"(libs.libvlc.libvlc.all)
    // S0305: MIDI playback is available only in flavors that support audio.
    "standardImplementation"(libs.androidx.media3.exoplayer.midi)
    "noLegalImplementation"(libs.androidx.media3.exoplayer.midi)
    "liteImplementation"(libs.androidx.media3.exoplayer.midi)
    "legacyImplementation"(libs.androidx.media3.exoplayer.midi)
    "vrImplementation"(libs.androidx.media3.exoplayer.midi)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.decoder) // Audio decoders for WAV and other formats
    implementation(libs.androidx.media3.session) // MediaSession for audio background playback
    // S2876: media3 1.11.0 deleted androidx.media3.exoplayer.MetadataRetriever; the class lives on
    // in this artifact as androidx.media3.inspector.MetadataRetriever. Not flavor-scoped, because
    // its one caller (AudioMetadataLoader) sits in src/main and compiles into all seven flavors.
    implementation(libs.androidx.media3.inspector)
    implementation(libs.androidx.media3.effect)  // GlEffect API for SBS stereo crop rendering (Phase 2)
    // S1066: post-record re-encode that bakes the in-app digital zoom into the camera MP4 (all flavors -
    // the camera lives in src/main and compiles into every flavor, so the dep cannot be flavor-scoped).
    implementation(libs.androidx.media3.transformer)

    // Image Loading - Glide
    implementation(libs.glide.glide)
    ksp(libs.glide.ksp)
    implementation(libs.glide.okhttp3.integration)
    
    // PhotoView for pinch-to-zoom and rotation support
    implementation(libs.photoview)
    
    // ExifInterface for image metadata (width, height, camera, GPS, etc.)
    implementation(libs.androidx.exifinterface)
    
    // ML Kit - Translation and Text Recognition (OCR)
    // S0423: ML Kit Translate is bundled in every translation-capable flavor. The on-demand
    // :translate_feature DFM was removed (it shipped empty and broke the release bundle), so no
    // Play Core SplitInstall dependency is needed.
    "noLegalImplementation"(libs.mlkit.translate)
    "noLegalImplementation"(libs.mlkit.language.id)
    "vrImplementation"(libs.mlkit.translate)
    "vrImplementation"(libs.mlkit.language.id)
    "standardImplementation"(libs.mlkit.translate)
    "standardImplementation"(libs.mlkit.language.id)
    "legacyImplementation"(libs.mlkit.translate)
    "legacyImplementation"(libs.mlkit.language.id)

    // S0386: com.google.mlkit:text-recognition is completely removed from all builds.

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // S0545: in-app video recording (unified capture host); replaces external ACTION_VIDEO_CAPTURE.
    implementation(libs.androidx.camera.video)
    // S0753: OEM NIGHT extension for the camera night mode (device-gated via ExtensionsManager).
    implementation(libs.androidx.camera.extensions)

    // S0988: pure-JVM QR decoder for the companion-config scan (no native model, no GMS, all flavors).
    // Only the core decoder - NOT zxing-android-embedded, which drags in a legacy camera1 stack.
    implementation(libs.zxing.core)

    // Tesseract OCR (Offline, better Cyrillic support)
    // S0386: cz.adaptech:tesseract4android is flavor-specific (compiled only for OCR-supporting flavors)
    "standardImplementation"(libs.tesseract.tesseract4android) {
        exclude(group = "cz.adaptech.tesseract4android", module = "tesseract4android-openmp")
    }
    "legacyImplementation"(libs.tesseract.tesseract4android) {
        exclude(group = "cz.adaptech.tesseract4android", module = "tesseract4android-openmp")
    }
    "noLegalImplementation"(libs.tesseract.tesseract4android) {
        exclude(group = "cz.adaptech.tesseract4android", module = "tesseract4android-openmp")
    }
    "vrImplementation"(libs.tesseract.tesseract4android) {
        exclude(group = "cz.adaptech.tesseract4android", module = "tesseract4android-openmp")
    }
    
    // Network - SMB. Pulls org.bouncycastle:bcprov-jdk18on transitively; the version that arrives
    // is asserted at configuration time below (S1496), not forced.
    implementation(libs.smbj)

    // Crypto - BouncyCastle, declared rather than inherited from SMBJ (S3382). The FD-SEC container
    // reader needs Argon2id with a settable parallelism, natively keyed BLAKE2b and raw RFC 8439
    // ChaCha20, and it ships on every flavor - including `lite`, where SUPPORT_LOCAL_NETWORK is off
    // and SMBJ is therefore absent. Same coordinate and same version as the transitive edge, so the
    // drift assertion below still sees one version across every runtime classpath.
    implementation(libs.bouncycastle.bcprov)
    
    // Network - SFTP (JSch for Android - better KEX support than SSHJ)
    implementation(libs.jsch)

    // S3041: embedded SFTP server. MINA SSHD keeps Bouncy Castle and EdDSA as optional POM
    // dependencies, so neither arrives transitively and the BC drift assertion below stays untouched.
    implementation(libs.apache.sshd.core)
    implementation(libs.apache.sshd.sftp)
    
    // Network - FTP
    implementation(libs.commons.net)
    
    // Wearable Data Layer - phone-side bridge to Wear OS companion.
    // S0403: consumed only by src/wearGms (WearableDataLayerRepositoryImpl + PhoneWearListenerService),
    // mounted into the Wear-capable flavors only. Scoped per-flavor so the FOSS APK (and non-Wear
    // flavors) never package the proprietary Play Services Wearable SDK. Keep this list in sync with
    // the wearGms sourceSets mounts above.
    "standardImplementation"(libs.google.gms.play.services.wearable)
    "noLegalImplementation"(libs.google.gms.play.services.wearable)
    // S1951: legacy dropped - it mounts wearStub, so the SDK was weight with no reachable route,
    // on the one flavor whose whole purpose is old and weak devices (minSdk 23).

    // Cloud Storage - Google Drive (REST API + Google Sign-In)
    // S0403: both are reachable only through src/cloudEnabled, and play-services-auth is proprietary
    // outright. appauth is Apache-2.0 but rides the same OAuth path, so a flavor mounting
    // cloudDisabled has no consumer for either. Keep this list in sync with the cloudEnabled mounts.
    "standardImplementation"(libs.google.gms.play.services.auth)
    "noLegalImplementation"(libs.google.gms.play.services.auth)
    "liteImplementation"(libs.google.gms.play.services.auth)
    "photosImplementation"(libs.google.gms.play.services.auth)
    "legacyImplementation"(libs.google.gms.play.services.auth)
    "vrImplementation"(libs.google.gms.play.services.auth)
    // S2101: Block Store carries the sign-in state across a device migration. Unlike the line above
    // this list omits `lite`, and deliberately: the only consumer is BlockStoreTransferableSignInStore
    // in src/cloudEnabled, which `lite` does not mount - it binds the no-op instead - so shipping a
    // proprietary library there would add weight no code in that flavor can reach.
    "standardImplementation"(libs.google.gms.play.services.auth.blockstore)
    "noLegalImplementation"(libs.google.gms.play.services.auth.blockstore)
    "photosImplementation"(libs.google.gms.play.services.auth.blockstore)
    "legacyImplementation"(libs.google.gms.play.services.auth.blockstore)
    "vrImplementation"(libs.google.gms.play.services.auth.blockstore)
    "standardImplementation"(libs.appauth)
    "noLegalImplementation"(libs.appauth)
    "liteImplementation"(libs.appauth)
    "photosImplementation"(libs.appauth)
    "legacyImplementation"(libs.appauth)
    "vrImplementation"(libs.appauth)

    // Network - Retrofit for iTunes Search API
    implementation(libs.retrofit.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.okhttp)
    debugImplementation(libs.chucker.library)
    "benchmarkImplementation"(libs.chucker.library.no.op)
    releaseImplementation(libs.chucker.library.no.op)
    
    // Cloud Storage - Dropbox
    // S0403: F-Droid refuses the Dropbox SDK, and cloudDisabled leaves it with no call site anyway.
    "standardImplementation"(libs.dropbox.dropbox.core.sdk)
    "noLegalImplementation"(libs.dropbox.dropbox.core.sdk)
    "liteImplementation"(libs.dropbox.dropbox.core.sdk)
    "photosImplementation"(libs.dropbox.dropbox.core.sdk)
    "legacyImplementation"(libs.dropbox.dropbox.core.sdk)
    "vrImplementation"(libs.dropbox.dropbox.core.sdk)

    // Cloud Storage - OneDrive (REST API + MSAL OAuth)
    // S0403: MSAL is proprietary Microsoft code - same exclusion as the Dropbox SDK above.
    "standardImplementation"(libs.msal)
    "noLegalImplementation"(libs.msal)
    "liteImplementation"(libs.msal)
    "photosImplementation"(libs.msal)
    "legacyImplementation"(libs.msal)
    "vrImplementation"(libs.msal)

    // Google Cast SDK + MediaRouter (Chromecast output from player) + NanoHTTPD proxy.
    // S0403: consumed only by src/castEnabled (CastMediaManagerImpl / LocalCastProxyServer). Scoped
    // per-flavor so a flavor mounting castDisabled never packages the proprietary Google Cast SDK.
    // S1439: vr is off all three lists - it mounts castDisabled, and none of the three has any other
    // consumer in the tree, so leaving them would ship an SDK, a router and an HTTP server for code
    // that is not in the APK. Keep these lists in sync with the castEnabled sourceSets mounts above.
    "standardImplementation"(libs.google.gms.play.services.cast.framework)
    "noLegalImplementation"(libs.google.gms.play.services.cast.framework)
    "liteImplementation"(libs.google.gms.play.services.cast.framework)
    "photosImplementation"(libs.google.gms.play.services.cast.framework)
    "legacyImplementation"(libs.google.gms.play.services.cast.framework)
    "standardImplementation"(libs.androidx.mediarouter)
    "noLegalImplementation"(libs.androidx.mediarouter)
    "liteImplementation"(libs.androidx.mediarouter)
    "photosImplementation"(libs.androidx.mediarouter)
    "legacyImplementation"(libs.androidx.mediarouter)
    "standardImplementation"(libs.nanohttpd)
    "noLegalImplementation"(libs.nanohttpd)
    "liteImplementation"(libs.nanohttpd)
    "photosImplementation"(libs.nanohttpd)
    "legacyImplementation"(libs.nanohttpd)

    // Logging
    implementation(libs.timber)
    debugImplementation(libs.leakcanary.android)
    // Required by LeakCanary for background heap analysis (RemoteListenableWorker)
    debugImplementation(libs.androidx.work.multiprocess) // Keep in sync with work-runtime-ktx (S0709)
    
    // Document Support - EPUB
    implementation(libs.epub4j.epub4j.core) {
        exclude(group = "xmlpull", module = "xmlpull")
        exclude(group = "net.sf.kxml", module = "kxml2")
    }
    implementation(libs.jsoup)

    // Document Support - encrypted ZIP archives
    implementation(libs.zip4j)
    // S0117: GPL extractor is linked only into the sideload-only noLegal flavor.
    // S0175: bumped v0.24.0 -> v0.26.1; no wrapper changes needed (breaking changes in v0.25/v0.26 don't touch our API surface).
    "noLegalImplementation"(libs.newpipe.extractor)

    // Markdown Rendering (for .md text files)
    implementation(libs.markwon.core)
    
    // Document Support - PDF extraction via built-in PdfRenderer (API 21+)
    // No external dependencies needed - metadata extraction removed to avoid conflicts
    
    // OpenXR loader - vr and noLegal (headset XR rendering).
    // noLegal ships the same arm64-v8a OpenXR slice; non-Quest devices simply never
    // exercise VrPlayerActivity because the graceful fallback fires first.
    "vrImplementation"(libs.openxr.loader)
    "noLegalImplementation"(libs.openxr.loader)

    // SW AV1 decoder (libgav1) - source-only extension. androidx.media3 publishes NO decoder
    // extension artifact at all (its Google Maven group index lists media3-decoder and nothing
    // else), so no coordinate for it can resolve at any version. S1126 §3.1 defers it behind VP9.
    // S2876 removed the matching `androidx-media3-decoder-av1` entry from the version catalog and
    // the three commented `implementation` lines that referenced it: the catalog entry made the
    // coordinate look one uncomment away from working, when enabling AV1 in fact starts with
    // building the extension from source, the same pipeline as fms-vpx.aar below.

    // ── Custom libvpx VP9 AAR (software video decode backstop) ────────────────────────────────
    // S1126: software VP9 renderer as the target of media3's decoder fallback. With
    // EXTENSION_RENDERER_MODE_ON (see PlaybackRenderersFactory) media3 reflectively loads
    // androidx.media3.decoder.vp9.LibvpxVideoRenderer and appends it AFTER MediaCodecVideoRenderer,
    // so the platform decoder stays first and libvpx is reached only when it fails or is absent -
    // a backstop, not the default path. No Kotlin change is needed; the classpath IS the wiring.
    //
    // Build script: scripts/builders/build-libvpx-vp9.sh (+ compile-vp9-classes.ps1 for classes.jar)
    // Built from media3 1.2.1 sources + libvpx v1.8.0, NDK r25c, four ABIs,
    // -Wl,-z,max-page-size=16384. readelf LOAD Align=0x4000 (16 KB). Play-safe.
    // S2876 moved the media3 pin to 1.11.0 and left this AAR on its 1.2.1 build, against the
    // blanket "raising the pin invalidates this AAR" that stood here before. The reason is
    // measured, not assumed: the AAR carries only the six androidx.media3.decoder.vp9 classes and
    // inherits SimpleDecoder / DecoderVideoRenderer from the runtime classpath, and a javap
    // comparison against media3 1.11.0 found every abstract member still implemented, every used
    // constructor still present, and no new abstract member on either base. What that check cannot
    // reach is the JNI side - vpxGetFrame and friends touch VideoDecoderOutputBuffer fields by name
    // from C - so the runtime verdict is a device test, not a build. Full evidence:
    // PLAN/S2876_media3-1-11-upgrade/research/03__prebuilt-native-aar-compatibility.md.
    // A device test that shows NoSuchFieldError / UnsatisfiedLinkError or a silent fall back to the
    // platform decoder means this AAR did need the rebuild - do it from the matching source tree.
    //
    // noLegal bundles it like the other three rather than delivering it on demand: VP9 is
    // royalty-free, and on-demand delivery exists for payloads that cannot be bundled, not for
    // every native payload - fms-ffmpeg-dts.aar is already bundled into noLegal a few lines below.
    "standardImplementation"(files("libs/fms-vpx.aar"))
    "noLegalImplementation"(files("libs/fms-vpx.aar"))
    "legacyImplementation"(files("libs/fms-vpx.aar"))
    "vrImplementation"(files("libs/fms-vpx.aar"))

    // ── Custom FFmpeg AAR (DTS + APE/WMA/WavPack/TTA/DSD) ─────────────────────────────────────
    // DTS/extended codec decoder via custom FFmpeg AAR - built from media3 1.2.1 sources, kept on
    // that build across S2876's move to media3 1.11.0 for the reason recorded above fms-vpx.aar:
    // the AAR subclasses DecoderAudioRenderer / SimpleDecoder, and both contracts survive the move.
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
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric) // For Android framework in JVM tests
    
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.leakcanary.android.instrumentation)
    // S0116 Phase 07 step 0: MockWebServer for graceful-degradation instrumentation tests.
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.dagger.hilt.android.testing)
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    kspAndroidTest(libs.dagger.hilt.android.compiler)
}

// S1496: assert the BouncyCastle version instead of forcing it - a force would silently block the
// security updates that ride along with an SMBJ bump, while an unasserted edge lets the crypto
// library move unnoticed. S3382 declared bcprov-jdk18on directly (the FD-SEC reader needs it on
// every flavor, SMBJ-less `lite` included); the assertion is what keeps the declared edge and the
// transitive one from resolving to two different versions.
// Test configurations are excluded on purpose: Robolectric 4.16.1 requests bcprov-jdk18on:1.81 on
// the unit-test classpath, and nothing on a test classpath reaches the APK, so asserting there
// would break the suite over a version that never ships.
//
// S1636: the scope is a WHITELIST of variant runtime classpaths, not "everything except test".
// The guard's own justification is "what ships in the APK", and a variant's `*RuntimeClasspath`
// is exactly that set. Excluding by the substring "test" left every TOOL classpath inside the
// guard, and one of them - `androidLintTool`, lint's own runtime - pulls bcpkix/bcprov 1.79
// through com.android.tools.lint. So every lint task died during dependency resolution while
// the APK built green: `.\a.ps1 ch` and `lint-baseline.xml` regeneration were unusable, which
// is what blocked S1329 step 06.2. Naming lint in a blacklist would have left the next tool
// configuration (ksp, kapt, detekt, lintChecks) to trip the same wire.
val expectedBouncyCastleVersion = "1.75"

configurations.matching {
    it.name.endsWith("RuntimeClasspath") && !it.name.contains("test", ignoreCase = true)
}.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.bouncycastle" && requested.version != expectedBouncyCastleVersion) {
            throw GradleException(
                "BouncyCastle version drift: ${requested.group}:${requested.name} resolved to " +
                    "${requested.version}, expected $expectedBouncyCastleVersion. Re-check the " +
                    "org/bouncycastle/** entries in packagingOptions against the new layout, then " +
                    "raise expectedBouncyCastleVersion in app_v2/build.gradle.kts."
            )
        }
    }
}

ksp {
    // Export Room schema JSON into a committed dir so future migrations are validatable (S0731).
    arg("room.schemaLocation", "$projectDir/schemas")
}

// S3041: MINA SSHD's sshd-sftp registers org.apache.sshd.sftp.client.fs.SftpFileSystemProvider as a
// java.nio.file service. With that registration on the unit-test runtime classpath Robolectric's native
// SQLite never loads, and every Robolectric test fails with UnsatisfiedLinkError at
// SQLiteConnectionNatives.nativeOpen (measured 2026-09-24: QuantityFormatterTest 9/9 red with the
// registration present, 9/9 green with the same jar minus that one file). The embedded server needs none
// of MINA's client file systems, so unit tests get sshd-sftp rebuilt without the service file; the APK
// drops the same file in packaging.
val sshdSftpOriginal: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    sshdSftpOriginal(libs.apache.sshd.sftp)
}

val sshdArchiveOperations: ArchiveOperations = serviceOf<ArchiveOperations>()

val sshdSftpWithoutNioProviders = tasks.register<Jar>("sshdSftpWithoutNioProviders") {
    val archives = sshdArchiveOperations
    from(sshdSftpOriginal.elements.map { jars -> jars.map { archives.zipTree(it.asFile) } }) {
        exclude("META-INF/services/java.nio.file.spi.FileSystemProvider")
    }
    archiveFileName.set("sshd-sftp-no-nio-providers.jar")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates/sshd-no-nio-providers"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "org.apache.sshd", module = "sshd-sftp")
}

dependencies {
    testRuntimeOnly(
        files(sshdSftpWithoutNioProviders.flatMap { it.archiveFile }).builtBy(sshdSftpWithoutNioProviders),
    )
}
