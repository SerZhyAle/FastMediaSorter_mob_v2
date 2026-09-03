// S1873 - the in-build safety net.
//
// A packaging invocation that arrived with no -Pfms.version* stamps itself here, so a path no
// wrapper script covers - a raw `gradlew assembleStandardDebug`, a CI job, the IDE's Run button -
// stops packaging the checked-in constant. That constant is a deliberately non-releasable sentinel
// after ADR-4: nothing writes it any more, so an artifact carrying it is an artifact nobody
// stamped. Applied by app_v2 and wear; it registers nothing and configures nothing on its own.
//
// Why a ValueSource rather than reading the clock where the constants are declared: measured on
// this build 2026-09-03 (Step 05.1), a configuration-time `System.currentTimeMillis()` was
// serialised into the configuration-cache entry and replayed unchanged seventy seconds later -
// same value, "Reusing configuration cache", no warning. A ValueSource is re-obtained before an
// entry is reused and invalidates it when the value differs, which is the only mechanism that
// survives the cache. Reading the clock directly here would reproduce the exact defect this
// ticket closes, minus the symptom that made it findable.
//
// Note the import: inside these build files `java.time.LocalDateTime` does not resolve by fully
// qualified name, because the Kotlin DSL `java` extension accessor shadows the package name.

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// The single instant of this invocation, as yyMMddHHmm. Both fields below are derived from it, so
// they cannot disagree with each other the way two independently written formulas would.
abstract class BuildClockValueSource : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmm"))
}

// Kept byte-compatible with Get-BuildVersionStamp in scripts/utils/build-version-stamp.ps1, which
// is the other half of the one formula: the orchestrator passes a shared stamp for a multi-module
// release (ADR-2), this net only covers the invocations nobody passed one to.
//   versionName      Y.YM.MDDH.Hmm
//   app versionCode  yyMMddHH + the first digit of the minute (9 digits)
//   wear versionCode yyMMddHH (8 digits) = floor(app / 10)
fun stampedVersionName(raw: String): String {
    val yy = raw.substring(0, 2)
    val mon = raw.substring(2, 4)
    val dd = raw.substring(4, 6)
    val hh = raw.substring(6, 8)
    val mm = raw.substring(8, 10)
    return "${yy[0]}.${yy[1]}${mon[0]}.${mon[1]}$dd${hh[0]}.${hh[1]}$mm"
}

fun stampedAppVersionCode(raw: String): Int = raw.substring(0, 9).toInt()

fun stampedWearVersionCode(raw: String): Int = raw.substring(0, 8).toInt()

// startParameter.taskNames is a flat list of everything after the options - it carries task
// OPTIONS and their VALUES too, not only task names. `--tests "*ApkInstallFailureTest*"` therefore
// puts a string containing "install" in this list, and matching it raw makes a unit-test-only run
// look like a packaging run: it would pay a per-invocation cache invalidation on exactly the
// compile-only path strategic §3.2 protects. A task name never contains '*', '.' or '#', and never
// starts with '-', so those shapes are dropped before the predicate looks at anything.
val fmsRequestedTaskNames: List<String> = gradle.startParameter.taskNames.filter { requested ->
    !requested.startsWith("-") &&
        !requested.contains("*") && !requested.contains(".") && !requested.contains("#")
}

// Pillar 1's predicate: does the requested work package an artifact? Read from the requested task
// names, which are part of the configuration-cache key, so a compile-only invocation and a
// packaging one can never share an entry and the compile-only path keeps the frozen constant
// along with its incrementality.
//
// "connected" and "baselineprofile" are here because those tasks INSTALL what they build on a real
// device - `connectedStandardDebugAndroidTest` (.\a.ps1 fam / fwm) and
// `collectNonMinifiedReleaseBaselineProfile` package an APK and push it, so leaving them out put
// the sentinel version on the owner's phone, which is the literal 2026-08-21 incident this ticket
// exists to close. "uninstall" is excluded before the "install" test: it removes an artifact and
// produces none, so stamping for it would be pure cost.
val fmsPackagingRequested: Boolean = fmsRequestedTaskNames.any { requested ->
    val name = requested.lowercase()
    if (name.contains("uninstall")) {
        false
    } else {
        name.contains("assemble") || name.contains("bundle") || name.contains("install") ||
            name.contains("package") || name.contains("connected") ||
            name.contains("baselineprofile")
    }
}

// Half a stamp is worse than none: the two fields would come from different sources - the name
// from the caller, the code from this invocation's minute - and the pair would silently stop
// satisfying the relationship assert-module-version-parity.ps1 documents. Nothing in the
// repository passes one without the other, so this refuses a shape that only arrives by mistake.
val fmsPassedVersionName = providers.gradleProperty("fms.versionName").orNull
val fmsPassedVersionCode = providers.gradleProperty("fms.versionCode").orNull
if ((fmsPassedVersionName == null) != (fmsPassedVersionCode == null)) {
    throw GradleException(
        "S1873: pass -Pfms.versionName and -Pfms.versionCode together or neither. " +
            "Got versionName=${fmsPassedVersionName ?: "<absent>"}, " +
            "versionCode=${fmsPassedVersionCode ?: "<absent>"} - the missing half would be " +
            "filled from this invocation's clock and the pair would no longer agree."
    )
}

// The clock is obtained only when this invocation packages something. A compile-only run never
// reaches .get(), so it never gains a per-invocation input and its cache entry stays reusable -
// which is the whole reason the predicate is read from the requested tasks rather than assumed.
//
// The raw stamp is memoised on the ROOT project, because this script is applied per module and
// providers.of() hands back a fresh provider with its own memoisation each time. Two obtain()
// calls in one invocation is two readings of the clock, and one invocation building both modules -
// `gradlew :app_v2:assembleStandardRelease :wear:assembleStandardRelease`, which is what CI does
// with no property at all - would straddle a minute boundary sooner or later and ship a phone and
// a watch whose versionName differs. Strategic goal 4 requires those to be byte-identical.
if (fmsPackagingRequested) {
    val raw = if (rootProject.extra.has("fmsRawBuildStamp")) {
        rootProject.extra["fmsRawBuildStamp"] as String
    } else {
        val obtained = providers.of(BuildClockValueSource::class.java) { }.get()
        rootProject.extra["fmsRawBuildStamp"] = obtained
        obtained
    }
    extra["fmsStampedVersionName"] = stampedVersionName(raw)
    extra["fmsStampedAppVersionCode"] = stampedAppVersionCode(raw)
    extra["fmsStampedWearVersionCode"] = stampedWearVersionCode(raw)
}
