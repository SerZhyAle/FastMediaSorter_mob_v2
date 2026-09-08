<#
.SYNOPSIS
    Fast per-module, per-flavor Gradle check - compile, resources, unit tests, instrumented tests on a
    connected device (-Mode ConnectedAndroidTest, the only mode needing one) or assemble. Defaults
    to app_v2. Every module in scripts/utils/gradle-modules.ps1 is accepted, including one with no
    flavor dimension, whose task names carry no variant segment (:watchface:processDebugResources).

.OUTPUTS
    Exit 0 - the check passed.
    Exit 1 - the check found a defect (compile error, red test, truncated suite).
    Exit 2 - the check could not run to a verdict (S1463: the unit-test worker JVM died, twice;
             -Module named a project the registry does not know; -Flavor named one the module does
             not declare, or was passed to a module that declares none; -BuildType named a build type
             the module does not declare, or the module declares none at all (S2123 - lint-rules has
             no Android plugin); -BuildType Release was combined with -Mode Assemble, which is
             refused - see below; or (S2363) the connected-test target could not be resolved to
             exactly one device - no adb, no device online, several online with no -DeviceId, or
             -DeviceId passed to a mode that touches no device at all); or (S2584) this module's
             R.jar for the variant under check is held open by another process - a hung Gradle test
             worker outliving the build that spawned it - so no resource task could run and nothing
             was proven about the code. Reported before the run where the handle is already held,
             and substituted for Gradle's own exit code where the handle is taken mid-run.
    Exit 4 - QUEUED, not failed: nothing was built and nothing is wrong (S2612). A short-hold-class
             check found its build domain busy and refused rather than blocking past the caller's
             120 s foreground timeout, which would have killed it with no verdict at all. The place
             in the queue is taken and survives this exit. Wait for the turn in the BACKGROUND with
             the wait-for-lock-turn.ps1 command the refusal prints, keep doing lock-free work, then
             rerun this check - it adopts the same place. Same code enter-code-lock.ps1 returns for
             a busy CODE domain (CLAUDE.md Rule 23). Pass -BlockThrough, or set FMS_LOCK_BLOCK=1,
             to block instead where there is no 120 s ceiling.
#>
param(
    [ValidateSet("Code", "Resources", "CodeAndResources", "Unit", "AndroidTest", "ConnectedAndroidTest", "Assemble")]
    [string]$Mode = "CodeAndResources",
    # S0826: per-flavor fast compile check. Standard is the default; NoLegal needs its own
    # path because it bundles Python via Chaquopy (see flag handling below).
    # S0404: the capability-gated flavors (Lite / Photos / Legacy) compile the no-op source sets,
    # so a seam change needs a fast check on one of them too.
    # S0989: Vr compiles the src/vr source set (OpenXR immersive host) - needed when a change
    # lives only under src/vr, which the Standard/NoLegal checks never compile.
    # S2121: no ValidateSet. The accepted values differ per module - watchface declares none at all -
    # so the check is a registry lookup below, and left unbound this resolves to the module's own
    # first flavor rather than to a literal "Standard" a flavorless module cannot name.
    [string]$Flavor,
    # S1496: wear is an active Gradle module with no fast check of its own, which is how its jsch
    # pin drifted nine minor versions behind app_v2 unnoticed.
    # S2090: wear now declares its own two-flavor `version` dimension, so -Flavor applies to it too -
    # but only over Standard and NoLegal. The per-module allowed set is checked below.
    # S2121: the module list moved to scripts/utils/gradle-modules.ps1 - one table, read by the
    # closure facade's resource-link gate too. A ValidateSet here was a third copy of it.
    [string]$Module = "app_v2",
    # S1988: which build type the check compiles. Debug is the working default and the only one any
    # caller used before, but it compiles src/debug ALONGSIDE src/main, so it cannot answer the one
    # question a debug-only test seam raises: does src/main still build when the debug class is gone?
    # `.\a.ps1 nl` looks like that proof and is not - release targets delegate to the git worktree at
    # ../FastMediaSorter_release and build main, so they never see the working tree's changes.
    # S2123: no ValidateSet, for the same reason -Flavor lost its one in S2121 - the accepted values
    # differ per module. :benchmark declares neither Debug nor Release: androidx.baselineprofile gives
    # it nonMinifiedRelease and benchmarkRelease, so a two-value ValidateSet made every call to that
    # module name a task gradle has never had. Left unbound this resolves to the module's own default
    # (its first declared build type), which is still Debug for the three ordinary Android modules.
    [string]$BuildType,
    [string]$Tests,
    # S1735: system properties forwarded to the gradle JVM, "name=value" each.
    #
    # Some maintained artifacts are REGENERATED by a test rather than by a script - the settings manifest
    # is written by SettingsManifestExportTest under -Dsettings.manifest.generate=true, and its gate tells
    # you to run exactly that. Without a passthrough the only way to obey the gate was to call gradlew.bat
    # directly, which Rule 23 forbids because it bypasses temp/BUILD.LOCK. The gap made the sanctioned
    # path and the documented path contradict each other.
    [string[]]$SystemProperty,
    # S2363: which device the connected instrumented run goes to. AGP's connected task takes no such
    # argument - left unpinned it installs on EVERY device `adb devices` reports, which on 2026-09-02
    # reached the owner's phone and tried to install the app and uninstall the test APK there. The one
    # lever the task honours is ANDROID_SERIAL, so that is what this parameter sets, and its default is
    # that same variable - the workaround this replaces keeps working unchanged.
    [string]$DeviceId = $env:ANDROID_SERIAL,
    # S2612: block on a busy build domain instead of refusing, for a caller with no 120 s ceiling -
    # a human terminal or a script chain. FMS_LOCK_BLOCK=1 does the same for a whole session and is
    # inherited by child processes. The unattended queue runner is NOT such a caller: it launches
    # `claude -p`, whose agent runs its checks through the same tool with the same timeout.
    [switch]$BlockThrough,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
# S2612: decides whether a foreground-scale check refuses a busy build domain instead of blocking
# in it until the caller's 120 s tool timeout kills it without a verdict.
. "$PSScriptRoot\build-queue-refusal.ps1"
# S2584: tells "another process holds this module's build output" apart from "the change is broken".
. "$PSScriptRoot\build-output-holder.ps1"
. "$PSScriptRoot\filtered-test-report.ps1"
. "$PSScriptRoot\..\utils\gradle-modules.ps1"
. "$PSScriptRoot\gradle-run-verdict.ps1"
. "$PSScriptRoot\gradle-worker-reaper.ps1"

# S2121: validate the module BEFORE taking a lock or launching gradle. An unknown module used to be
# impossible here only because a ValidateSet listed two of the five projects the build declares;
# with the registry it is a real input, and the honest answer is "could not check" rather than a
# task name guessed for a project that may not exist.
if (-not (Test-GradleModuleName -Name $Module)) {
    $knownModules = (Get-GradleModuleNames) -join ', '
    Write-Error "check-standard-fast: -Module '$Module' is not a registered Gradle module - known modules are $knownModules. Add a row in scripts/utils/gradle-modules.ps1 if the build declares it." -ErrorAction Continue
    exit 2
}

# S2090: each module declares its own flavor set, so the allowed values are per-module rather than one
# ValidateSet. Naming what the module DOES accept matters more than naming what it rejected: the wear
# set is a strict subset of the phone's, so the plausible mistake is asking for a phone-only flavor.
# S2121: the sets live in the registry now, and an EMPTY set is a real answer - watchface declares no
# flavor dimension at all, so its task names carry no variant segment. Resolved before the lock for
# the same reason the toolchain check is: a refusal must not first take a place in every sibling's
# queue and then decline to build anything.
$declaredFlavors = @(Get-GradleModuleFlavors -Name $Module)
if ($PSBoundParameters.ContainsKey('Flavor') -and $Flavor) {
    if ($declaredFlavors.Count -eq 0) {
        Write-Error "check-standard-fast: -Flavor '$Flavor' was passed to '$Module', which declares no product flavors - omit -Flavor for this module." -ErrorAction Continue
        exit 2
    }
    if ($declaredFlavors -notcontains $Flavor) {
        Write-Error "check-standard-fast: -Flavor '$Flavor' does not exist in the '$Module' module - it declares $($declaredFlavors -join ', ')." -ErrorAction Continue
        exit 2
    }
}
else {
    # Unbound means "the module's default variant" - its first declared flavor, or none at all. A
    # literal "Standard" default would name a variant a flavorless module has never had.
    $Flavor = if ($declaredFlavors.Count -gt 0) { $declaredFlavors[0] } else { '' }
}

# S2123: the build type is validated against the registry exactly as the flavor is, and for the same
# reason - it is the other half of the variant name, and it was the half left hardcoded. Resolved
# before the lock too: a refusal must not first take a place in every sibling's queue.
$declaredBuildTypes = @(Get-GradleModuleBuildTypes -Name $Module)
if ($PSBoundParameters.ContainsKey('BuildType') -and $BuildType) {
    if ($declaredBuildTypes.Count -eq 0) {
        Write-Error "check-standard-fast: -BuildType '$BuildType' was passed to '$Module', which declares no build types at all - it has no Android plugin, so no variant task exists for it." -ErrorAction Continue
        exit 2
    }
    if ($declaredBuildTypes -notcontains $BuildType) {
        Write-Error "check-standard-fast: -BuildType '$BuildType' does not exist in the '$Module' module - it declares $($declaredBuildTypes -join ', ')." -ErrorAction Continue
        exit 2
    }
}
else {
    if ($declaredBuildTypes.Count -eq 0) {
        Write-Error "check-standard-fast: '$Module' declares no build types - it has no Android plugin, so there is no task for this check to run." -ErrorAction Continue
        exit 2
    }
    $BuildType = Get-GradleModuleDefaultBuildType -Name $Module
}

# S1988/S1873: -Mode Assemble is the only mode that packages an installable artifact, and its version
# stamp below is written for a debug build. A release APK produced here would be unsigned and stamped
# wrong while looking exactly like the real thing, which is the failure S1873 records. Release
# packaging belongs to the release worktree; this script only ever compiles a release variant.
if ($BuildType -eq 'Release' -and $Mode -eq 'Assemble') {
    Write-Error "check-standard-fast: -BuildType Release is refused with -Mode Assemble - use the release worktree (.\a.ps1 r / nl / vr) to package." -ErrorAction Continue
    exit 2
}

# S2363: resolve the device BEFORE the lock, for the reason the module and flavor checks above are
# resolved there - a refusal must not first take a place in every sibling's build queue and then
# decline to build anything. Nothing but ConnectedAndroidTest touches a device, so -DeviceId anywhere
# else is a caller who believes this run goes somewhere it does not, and answering "fine" to that is
# how the belief survives.
$targetDevice = ''
if ($Mode -eq 'ConnectedAndroidTest') {
    # The device is chosen by device-ready.ps1 rather than by a private `adb devices` parse: it is
    # already the one place that knows about a missing adb, an offline device, a device a sibling
    # session leased (S1926) and - the case this ticket exists for - several devices at once. A second
    # copy of that judgement here would drift from it.
    $readyScript = Join-Path $PSScriptRoot '..\devtest\device-ready.ps1'
    # A hashtable, not an array: `& $script @array` splats POSITIONALLY, so '-Json' lands in the
    # probe's first positional parameter and it goes looking for a device serial named '-Json'. That
    # is the same trap a.ps1 documents at its own script table, and it reproduced here on the first run.
    $readyArgs = @{ Json = $true }
    # S2611: name the form factor instead of letting the probe take whatever is attached. -Module
    # here is the DEVICE vocabulary (app_v2|wear), not this script's Gradle module - :watchface
    # builds under every domain and still installs only on a watch, so the table translates.
    $readyArgs['Module'] = Get-GradleModuleDeviceModule -Name $Module
    if ($DeviceId) { $readyArgs['DeviceId'] = $DeviceId }
    $readyRaw = & $readyScript @readyArgs
    $ready = $null
    if ($readyRaw) { $ready = ($readyRaw | Out-String).Trim() | ConvertFrom-Json }
    if (-not $ready) {
        Write-Error "check-standard-fast: the device probe produced no answer, so the target device is unknown - nothing was run. Re-run scripts/devtest/device-ready.ps1 by hand to see why." -ErrorAction Continue
        exit 2
    }
    if (-not $ready.ready) {
        # `multiple-devices` is the state this ticket is about, and the refusal IS the fix: the run
        # used to fan out silently instead, so the reason names every serial and the flag that picks one.
        $attached = @($ready.devices | ForEach-Object { if ($_.id) { $_.id } else { "$_" } }) -join ', '
        $detail = if ($attached) { " Attached: $attached." } else { '' }
        Write-Error "check-standard-fast: -Mode ConnectedAndroidTest needs exactly one target device, and the probe answered '$($ready.state)' - $($ready.reason).$detail Pass -DeviceId <serial> (or export ANDROID_SERIAL) to name it." -ErrorAction Continue
        exit 2
    }
    $targetDevice = [string]$ready.selectedDevice
}
elseif ($PSBoundParameters.ContainsKey('DeviceId') -and $DeviceId) {
    Write-Error "check-standard-fast: -DeviceId is accepted only with -Mode ConnectedAndroidTest - every other mode compiles or runs on this host and reaches no device, so naming one here would claim a targeting this run does not do." -ErrorAction Continue
    exit 2
}

# S2361: initialize log file at startup so interrupted/stalled runs leave an observable trace.
$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $projectRoot
$tempDir = Join-Path $projectRoot "temp"
if (-not (Test-Path -Path $tempDir)) {
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
}
$logTimestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$tempLogPath = Join-Path $tempDir "check_fast_${Module}_${Mode}_$logTimestamp.log"

# S2109: the domain is derived from -Module - ADR-1 wants the domain read off data the call carries,
# not declared a second time and left to drift from it.
# S2121: the derivation moved into the registry, so a module with no domain of its own widens to the
# full build set (ADR-2) instead of silently taking the phone's and serialising against nothing.
$buildDomains = @(Get-GradleModuleBuildDomains -Name $Module)

# S2580: the reason is the ONLY thing a queued session gets to read - it is what the refusal from
# Enter-BuildLockOrExit prints, what `lock-status.ps1 -Queue` lists, and what the agent-chat wait line
# carries. Naming the module alone made all 14 a.ps1 targets that route through this script write one
# identical string, so a 14 s compile and the full unit suite were indistinguishable in the queue:
# measured 2026-09-05 08:52, this lock was held 42 min by -Mode Unit while eleven sessions queued
# behind "check-standard-fast.ps1 (app_v2)", the longest waiting 45 min, unable to tell whether to
# wait or go do lock-free work. What the holder was actually running was recoverable only from the
# Win32_Process command line of its child cmd.exe, which is why the variant is spelled out here.
#
# The hold class comes from the measured table in docs/BUILD_TEST_FAST_PATH.md (the 120 s threshold),
# not from a judgement made here. It is derived from -Mode ALONE and is deliberately NOT downgraded
# when -Tests narrows the run: the filter's breadth is unknown at this point, and that document
# (S2453) chooses the pessimistic wording on purpose because it misleads more cheaply. A hold that
# ends sooner than advertised costs a waiter nothing; a "seconds" promise that runs 40 minutes is the
# exact failure this line exists to prevent.
# S2612 moved the derivation into build-queue-refusal.ps1, because the same classification now also
# decides whether this run may block on a busy domain - two copies of the mode list would let the
# queue reason and the refusal disagree about what this run is.
$holdClass = Get-BuildHoldClassLabel -Mode $Mode
# A flavorless module (watchface) resolves $Flavor to '', so the variant is just its build type -
# which is the real task-name segment for it, exactly as S2121 established for the task list below.
$variantLabel = "$Module $Flavor$BuildType".Trim()
# Only the two modes that accept a filter say whether one was used; on every other mode the word
# would describe a parameter that run ignores. `whole suite` is the case worth naming - it is the one
# that holds the domain for tens of minutes.
$scopeLabel = ''
if ($Mode -in @('Unit', 'ConnectedAndroidTest')) {
    $scopeLabel = if ($Tests) { ", filtered: $Tests" } else { ', whole suite' }
}
$lockReason = "check-standard-fast.ps1 -Mode $Mode ($variantLabel$scopeLabel) - $holdClass"

# S2612: a short-class check is run in the FOREGROUND by Rule 6, so its caller kills it at 120 s -
# and it can spend that whole window queueing, because a target's measured wall clock is the run
# alone on an empty queue. A killed foreground check reports no verdict at all, which reads exactly
# like a check nobody ran. So on a busy domain this takes its place in the queue, says who is in
# front and how to wait for the turn, and exits 4 - the same answer enter-code-lock.ps1 already
# gives for a busy CODE domain. Long-class runs are backgrounded and keep blocking.
#
# The ticket set is taken BEFORE the report and is deliberately not removed: a refusal that drops
# its place turns waiting into starvation, and the rerun after the background wait adopts it
# (New-AgentLockTicket dedups per session; the handoff file carries it where there is no session id).
$queueVerdict = Test-BuildQueueRefusal -HoldClass (Get-BuildHoldClass -Mode $Mode) `
    -DomainState (Get-BuildDomainState -Domains $buildDomains) -BlockThrough:$BlockThrough
if ($queueVerdict.ShouldRefuse) {
    $queueTickets = New-AgentLockTicketSet -Name 'Build' -Reason $lockReason -Domains $buildDomains
    $queueHandoff = Save-AgentLockTicketHandoff -Tickets $queueTickets -Reason $lockReason
    $queueReport = Format-BuildQueueRefusalReport -BlockingDomain $queueVerdict.BlockingDomain `
        -BlockingState $queueVerdict.BlockingState -Reason $lockReason -HandoffPath $queueHandoff
    # A refused run would otherwise leave no trace at all: the log is written after the acquire, so
    # measure-build-lock-wait.ps1 - which reconstructs the whole queue from these files - would count
    # a refusal as a run that never happened. It writes a header with `Queued:` where an acquired run
    # writes `Date:`, so the same filename-stamp arithmetic that measures a wait can measure a
    # refusal, and the tool can tell the two apart by which header line is present.
    @(
        "=== Fast Check Log ($Module / $Mode) ===",
        "Queued: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
        "Build Domains: $($buildDomains -join ', ')",
        "Blocking Domain: $($queueVerdict.BlockingDomain)",
        "Outcome: refused - queued behind another session (exit 4, S2612)",
        ""
    ) + $queueReport | Set-Content -LiteralPath $tempLogPath -Encoding utf8

    # Printed LAST, immediately before the exit, so the reason a caller sees is adjacent to the code
    # it exits with - assert-exit-contract.ps1 reads that adjacency, and it is right to: a refusal
    # whose explanation scrolled past other output is a refusal nobody acts on.
    foreach ($line in $queueReport) { Write-Host $line -ForegroundColor Yellow }
    exit 4
}

Enter-BuildLockOrExit -Reason $lockReason -Domain $buildDomains
try {

# Write log header immediately after lock acquisition
$logHeader = @(
    "=== Fast Check Log ($Module / $Mode) ===",
    "Date: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "PID: $PID",
    "Module: $Module",
    "Mode: $Mode",
    "Flavor: $Flavor",
    "BuildType: $BuildType",
    "Build Domains: $($buildDomains -join ', ')",
    "Device: $(if ($targetDevice) { $targetDevice } else { 'n/a - this mode reaches no device' })",
    "========================================"
)
[System.IO.File]::WriteAllLines($tempLogPath, [string[]]$logHeader)

# S2361: check if sibling build domain locks are currently active (e.g. Build.Phone while running wear)
$allBuildDomains = @(Get-AgentLockDomainTable | Where-Object { $_.Type -eq 'Build' } | ForEach-Object { $_.Domain })
$siblingHolders = @()
foreach ($domain in $allBuildDomains) {
    if ($buildDomains -contains $domain) { continue }
    $lockStatus = Get-AgentLockStatus -Name $domain
    if ($lockStatus.Exists -and -not $lockStatus.Stale) {
        $siblingHolders += "$domain (PID $($lockStatus.Pid): $($lockStatus.Reason))"
    }
}
if ($siblingHolders.Count -gt 0) {
    $notice = "[S2361 Notice] Active sibling build lock(s) detected: $($siblingHolders -join '; '). Gradle root project execution may queue behind them."
    Write-Host $notice -ForegroundColor DarkYellow
    [System.IO.File]::AppendAllLines($tempLogPath, [string[]]@($notice))
}

# S2090: both app modules carry a flavor dimension, so their task names get the segment. Before this,
# wear substituted an empty segment - a task name gradle stopped having the moment the dimension existed.
# S2121: a module that declares NO dimension is the mirror case and needs the empty segment back -
# :watchface:processDebugResources is the real task name, and interpolating any variant into it names
# a task gradle has never had.
$variant = $Flavor

# S2585: the finally block reaps this run's own test workers, and it must know the task directory to
# find them. Computed here rather than there because the finally also runs for a failure BEFORE this
# line, where $variant does not exist yet - reading it there would replace the real error with a
# missing-variable one. Null means "the run never got far enough to fork a worker", which is exactly
# when there is nothing to reap.
$script:unitTaskDir = if ($Mode -eq 'Unit') { "test${variant}${BuildType}UnitTest" } else { $null }
$script:runRegistryPath = $null

# S2584: a hung test worker from an earlier run keeps R.jar open, and every mode below except Code
# starts by rewriting it - so the run dies on `IOException: Couldn't delete .. R.jar`, which reads as
# a defect in the change being checked. The lock cannot warn about this and is not asked to: it
# tracks the wrapper pid, and the holder is a worker that wrapper spawned, so Build.* legitimately
# reports FREE while the directory is occupied. Probing the file is the only signal that worked in
# both 2026-09-05 incidents. Code is exempt because compile*Kotlin never rewrites the jar, which
# makes fk/fkn/fw the one check that still returns a verdict during such an incident - refusing them
# would take away the last working fallback.
if ($Mode -ne 'Code') {
    $rJarVariantDir = Get-BuildOutputVariantDir -Variant $variant -BuildType $BuildType
    $heldJars = @(Get-BuildOutputRJarPath -ProjectRoot $projectRoot -Module $Module -VariantDir $rJarVariantDir |
            Where-Object { Test-BuildOutputLocked -Path $_ })
    if ($heldJars.Count -gt 0) {
        Write-BuildOutputHolderDiagnosis -Module $Module -LockedPaths $heldJars -LogPath $tempLogPath
        # Exit 2, not 1, for S1463's reason below: nothing was proven about the code either way.
        # Inside the try, so the finally releases the build domain exactly as any other outcome does.
        exit 2
    }
}

function Get-GradleTaskList {
    switch ($Mode) {
        "Code" { return @(":${Module}:compile${variant}${BuildType}Kotlin") }
        "Resources" { return @(":${Module}:process${variant}${BuildType}Resources") }
        "CodeAndResources" { return @(":${Module}:compile${variant}${BuildType}Kotlin", ":${Module}:process${variant}${BuildType}Resources") }
        "Unit" { return @(":${Module}:test${variant}${BuildType}UnitTest") }
        # S1832: nothing compiled the instrumented source set, which is how
        # AppDatabaseMigration50To51Test shipped referencing an undeclared constant. Compile only -
        # running these needs a device, but a test that cannot build is never going to run anywhere.
        "AndroidTest" { return @(":${Module}:compile${variant}${BuildType}AndroidTestKotlin") }
        # S2306: the only mode that RUNS the instrumented set, which is the only way a Room migration
        # is ever executed against real SQLite before a user's phone does it. Compiling these proves
        # they parse; runMigrationsAndValidate is the same schema comparison the device performs on
        # the first launch after an update, and until this mode existed nothing in the repository ever
        # performed it - S2251 shipped a migration whose column the entity did not declare, and the
        # first execution of that comparison anywhere was on the owner's phone, which then reset the
        # database. Needs a connected device or emulator.
        "ConnectedAndroidTest" { return @(":${Module}:connected${variant}${BuildType}AndroidTest") }
        "Assemble" { return @(":${Module}:assemble${variant}Debug") }
        default { throw "Unsupported mode: $Mode" }
    }
}

$gradleArgs = New-Object System.Collections.Generic.List[string]
Get-GradleTaskList | ForEach-Object { $null = $gradleArgs.Add($_) }
# Flavors without Python: disable Chaquopy and use the configuration cache for speed.
# NoLegal bundles Python via Chaquopy, whose API must stay on the compile classpath and
# whose tasks are not configuration-cache serialisable - so keep it enabled and skip the cache.
if ($Flavor -ne "NoLegal") {
    $null = $gradleArgs.Add("-Pchaquopy.enabled=false")
    $null = $gradleArgs.Add("--configuration-cache")
}

foreach ($property in $SystemProperty) {
    $trimmed = $property.Trim()
    if (-not $trimmed) { continue }
    if ($trimmed -notmatch '^[^=\s]+=.*$') {
        throw "-SystemProperty expects 'name=value', got '$property'"
    }
    $null = $gradleArgs.Add("-D$trimmed")
}

if ($Mode -eq "Assemble") {
    # -Mode Assemble is the only mode stamped HERE. Left unstamped it ships the checked-in constant,
    # so the artifact reports the version of whatever release last touched build.gradle.kts - on
    # 2026-08-21 that installed a watch build reading six days older than the one it replaced (S1873).
    # Every compile-only mode keeps the frozen value on purpose.
    #
    # S2377: ConnectedAndroidTest installs an APK too and STILL does not belong here - do not "fix"
    # this condition by adding it. Since S1873 Phase 05 the in-build stamp in
    # gradle/build-version-stamp.gradle.kts already covers it, matched from the task name
    # ("connected"), and because that predicate is derived from the task graph the connected run keeps
    # a stable configuration-cache entry. Passing the property from here as well changes no version
    # and costs one: measured 2026-09-05, adding it made every fwm run reconfigure with "configuration
    # cache cannot be reused because Gradle property 'fms.versionName' has changed", while the same
    # run without it stored its entry and installed the identical stamped code 26090502.
    . "$PSScriptRoot\..\utils\build-version-stamp.ps1"
    $stamp = Get-BuildVersionStamp
    $assembleVersionCode = if ($Module -eq 'wear') { $stamp.WearVersionCode } else { $stamp.AppVersionCode }
    $null = $gradleArgs.Add("-Pfms.versionCode=$assembleVersionCode")
    $null = $gradleArgs.Add("-Pfms.versionName=$($stamp.VersionName)")
    Write-Host "Version override: $($stamp.VersionName) (code: $assembleVersionCode)" -ForegroundColor Green
}

if ($Tests -and $Mode -eq "ConnectedAndroidTest") {
    # S2306: the connected task takes no `--tests` - filtering an instrumented run is the runner's
    # job, not Gradle's, so the value is forwarded as an AndroidJUnitRunner argument. Passing a Gradle
    # glob here would be accepted silently and run the WHOLE instrumented suite on the device, which
    # reads as a much stronger verdict than it is.
    #
    # The runner has TWO arguments and they are not interchangeable: `class` takes a fully qualified
    # class (or Class#method), `package` takes a package. Handing a package to `class` matches nothing -
    # and "no tests found" is the worst possible outcome here, because it is a run that proved nothing
    # while looking like one that ran. They are told apart by the Java convention every name in this
    # repository follows: a final segment starting lower-case is a package, upper-case is a class.
    $tokens = @($Tests -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $packages = @($tokens | Where-Object { ($_ -split '\.')[-1] -cmatch '^[a-z]' })
    $classes = @($tokens | Where-Object { $packages -notcontains $_ })
    if ($packages.Count -gt 0 -and $classes.Count -gt 0) {
        throw "-Tests mixes package names ($($packages -join ', ')) with class names ($($classes -join ', ')). The instrumentation runner takes one or the other, so name only packages or only classes."
    }
    if ($packages.Count -gt 0) {
        $null = $gradleArgs.Add("-Pandroid.testInstrumentationRunnerArguments.package=$($packages -join ',')")
    }
    else {
        $null = $gradleArgs.Add("-Pandroid.testInstrumentationRunnerArguments.class=$($classes -join ',')")
    }
}
elseif ($Tests) {
    if ($Mode -ne "Unit") {
        throw "-Tests is supported only with -Mode Unit or -Mode ConnectedAndroidTest"
    }
    # Gradle takes ONE pattern per --tests flag and does not split on commas: passing
    # "*ATest,*BTest" as a single argument makes it look for a class literally named that, and it
    # answers "No tests found for given includes" - which reads as "your tests are gone" rather
    # than "your filter was malformed" (S1449). Split here so a comma-separated list means what
    # every caller assumes it means.
    foreach ($pattern in ($Tests -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
        $null = $gradleArgs.Add("--tests")
        $null = $gradleArgs.Add($pattern)
    }
}

# S1807: the label names the module, not only the flavor. This banner is what gets pasted into a
# step log as proof, and with two active modules a phone verdict quoted under a wear ticket has to
# read as foreign rather than as confirmation.
# S2090: it now names the flavor for BOTH modules. wear used to print a bare 'wear' because it had one
# variant; with two, a bare module name states half of what was checked.
# S2121: a module with no flavor dimension prints its bare name - "watchface/" would read as a
# variant whose name got lost, which is the opposite of what the banner is for.
$checkLabel = if ($Flavor) { "$Module/$Flavor" } else { $Module }
Write-Host "Fast $checkLabel check.." -ForegroundColor Cyan
Write-Host "Mode: $Mode" -ForegroundColor Yellow
Write-Host "Build type: $BuildType" -ForegroundColor Yellow
if ($targetDevice) {
    # Printed beside the mode because this line is what a step-1.4 log quotes as proof of WHICH device
    # answered - a green connected run naming no device is the state S2363 was opened over.
    Write-Host "Device: $targetDevice" -ForegroundColor Yellow
}
if ($Tests) {
    Write-Host "Tests filter: $Tests" -ForegroundColor Yellow
}
Write-Host "Command: .\\gradlew.bat $($gradleArgs -join ' ')" -ForegroundColor DarkGray

# S2127: set by the repair block below, read by the runner on the retry. A closure rather than a
# rebuilt argument list so the first attempt's command line - the one printed above as evidence - is
# the one that actually ran.
$script:kotlinIncrementalDisabled = $false

$runOnce = {
    param([int]$Attempt)

    $attemptArgs = @($gradleArgs)
    if ($script:kotlinIncrementalDisabled) {
        $attemptArgs += Get-KotlinStaleIncrementalRepairArgs
    }

    $collected = New-Object System.Collections.Generic.List[string]
    & "$projectRoot\gradlew.bat" @attemptArgs 2>&1 | ForEach-Object {
        $line = [string]$_
        $collected.Add($line)
        [System.IO.File]::AppendAllLines($tempLogPath, [string[]]@($line))
        if ($Quiet -and ($line -match " UP-TO-DATE$" -or $line -match " NO-SOURCE$" -or $line -match " FROM-CACHE$")) {
            return
        }
        Write-Host $line
    }
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Lines = $collected.ToArray() }
}

# S1463: only a unit run can lose its worker, and only there is a repeat the right answer - repeating
# a failed compile would just double the wait for an error that is not going to change.
# S2127: one failed compile IS worth repeating - the one whose error names a class the incremental
# state lost rather than a class the sources lack. Both branches go through the wrapper now, so every
# mode gets that repair; the retry policy stays bound to its two signatures and to nothing else, which
# is what keeps an ordinary red compile a single attempt on either branch.
$repairStaleState = { $script:kotlinIncrementalDisabled = $true }

# S2363: ANDROID_SERIAL is the only narrowing AGP's connected task honours - it takes no device
# argument of its own - so the pin is set here, around the one call that reaches a device, and the
# previous value is restored in the finally below rather than left behind for whatever runs next in
# this process.
$previousAndroidSerial = $env:ANDROID_SERIAL
$script:androidSerialPinned = $false
if ($targetDevice) {
    $env:ANDROID_SERIAL = $targetDevice
    $script:androidSerialPinned = $true
}

if ($Mode -eq "Unit" -and -not $Tests) {
    # S2465: wipe stale unit-test reports before the run. If Gradle fails at compilation time,
    # leaving old XML reports on disk makes assert-test-suite-complete evaluate a stale/partial
    # set and misreport compilation errors as a truncated test suite.
    $unitTestReportDir = Join-Path $projectRoot "$Module\build\test-results\test${variant}${BuildType}UnitTest"
    if (Test-Path -LiteralPath $unitTestReportDir) {
        Remove-Item -LiteralPath $unitTestReportDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# S2585: register this run before gradle starts, drop the record in the finally. The finally covers
# every exit this process can observe - success, red build, exception, Ctrl-C - but NOT being killed
# outright, which is the suspected shape of the 2026-09-05 incident (an agent's background-task cap;
# the wrapper was gone while its client had been alive 83 minutes). A record left behind whose pid no
# longer exists is an unambiguous orphaned run: no age threshold, no CPU sample, no guess about what
# counts as build machinery, which is what agent-watchdog.ps1 has to fall back on and why it cannot
# act for 25 minutes. Best-effort on purpose - a filesystem refusal must not change this run's verdict.
try {
    $runRegistryDir = Join-Path $projectRoot 'temp\GRADLE-RUN'
    if (-not (Test-Path -LiteralPath $runRegistryDir)) {
        $null = New-Item -ItemType Directory -Path $runRegistryDir -Force
    }
    # Named by the wrapper pid so two concurrent wrappers never contend for one record.
    $script:runRegistryPath = Join-Path $runRegistryDir "run-$PID.json"
    [pscustomobject]@{
        wrapperPid = $PID
        module     = $Module
        mode       = $Mode
        taskDir    = $script:unitTaskDir
        variant    = $variant
        buildType  = $BuildType
        startedAt  = (Get-Date).ToString('o')
        logPath    = $tempLogPath
    } | ConvertTo-Json | Set-Content -LiteralPath $script:runRegistryPath -Encoding UTF8
}
catch {
    Write-Host "Could not register this gradle run: $($_.Exception.Message)" -ForegroundColor DarkYellow
    $script:runRegistryPath = $null
}

# S2588: the mark that separates this run's test reports from the previous holder's. Taken here, on
# the last line before gradle starts, because a report written even a second earlier belongs to
# whoever held the build domain before this run did.
$script:gradleStartedAt = Get-Date

$run = Invoke-GradleRunWithRetry -RunOnce $runOnce -MaxAttempts 2 `
    -RepairStaleIncrementalState $repairStaleState

$gradleExit = $run.ExitCode

# S1244: a truncated unit run ALWAYS ends non-zero - the worker process dies. So the completeness
# check has to run BEFORE the exit-code bail-out below; placed after it, the one run it exists to
# catch would exit first and never reach it. Skipped for a --tests filter, where a handful of
# reports is the correct outcome rather than a truncation.
if ($Mode -eq "Unit" -and -not $Tests) {
    & "$PSScriptRoot\..\quality\assert-test-suite-complete.ps1" -Module $Module -TaskDir "test${variant}${BuildType}UnitTest"
    $gateExit = $LASTEXITCODE
    if ($gateExit -eq 1 -and -not $run.WorkerDeath) {
        Write-Host "`nFast check failed - the unit run did not cover the whole suite." -ForegroundColor Red
        [System.IO.File]::AppendAllLines($tempLogPath, [string[]]@("Fast check failed - unit run truncated."))
        exit 1
    }
    # Exit 2 is "could not check" (no reports yet). Say so, but let a real Gradle failure below own
    # the verdict rather than masking it with a missing-reports message.
    if ($gateExit -eq 2) {
        Write-Host "Suite-completeness check could not run; coverage is unverified." -ForegroundColor Yellow
    }
}

# S1463: the worker JVM dying is not a test result. Reported as exit 1 it is indistinguishable from a
# red test, and the reader spends the next hour hunting a failure that never happened. Exit 2 is this
# repo's "could not verify" (S1338), and it is the honest answer: nothing was proven either way.
if ($run.WorkerDeath) {
    $msg = "check-standard-fast: the Gradle test worker died on both attempts - this run produced NO " +
    "verdict. Nothing was proven about the tests; do not read it as a failure. Usual cause is host " +
    "load (concurrent Gradle daemons or emulators). Re-run on a quiet host; see PLAN/S1463."
    [System.IO.File]::AppendAllLines($tempLogPath, [string[]]@($msg))
    Write-Error $msg -ErrorAction Continue
    exit 2
}

if ($gradleExit -ne 0) {
    # S2584: the pre-flight above clears the run to start, but the holder can appear WHILE it runs -
    # which is exactly how the 2026-09-05 incident looked, each claimant taking a free domain and then
    # dying on the first resource task. Gradle's own message names a file and no cause, so the reader
    # attributes it to their own change. Re-probe before the digest and, if the jar really is held,
    # replace the verdict rather than dressing up a failure that says nothing about the code.
    if ($Mode -ne 'Code' -and (Test-Path -LiteralPath $tempLogPath)) {
        $runLog = Get-Content -LiteralPath $tempLogPath -Raw -ErrorAction SilentlyContinue
        if ($runLog -and $runLog -match "Couldn't delete" -and $runLog -match 'R\.jar') {
            $stillHeld = @(Get-BuildOutputRJarPath -ProjectRoot $projectRoot -Module $Module `
                    -VariantDir (Get-BuildOutputVariantDir -Variant $variant -BuildType $BuildType) |
                    Where-Object { Test-BuildOutputLocked -Path $_ })
            if ($stillHeld.Count -gt 0) {
                Write-BuildOutputHolderDiagnosis -Module $Module -LockedPaths $stillHeld -LogPath $tempLogPath
                exit 2
            }
        }
    }
    Write-Host "`nFast check failed." -ForegroundColor Red
    [System.IO.File]::AppendAllLines($tempLogPath, [string[]]@("Fast check failed with exit code $gradleExit."))
    # S2743: harvest the FAILING reports before this exit, while the build domain is still held. The
    # green harvest below never runs on this path, so until now a red suite kept its evidence only in
    # the shared build/test-results directory, which the next session's run wipes on entry (S2465).
    # The console line is one frame; the XML carries the stack and its suppressed causes, and for a
    # cross-test failure such as kotlinx-coroutines-test's UncaughtExceptionsBeforeTest that
    # suppressed cause is the only place the real culprit is named.
    if ($Mode -eq 'Unit' -and $script:unitTaskDir) {
        $failHarvest = Save-FailedTestReport -ProjectRoot $projectRoot -Module $Module `
            -TaskDir $script:unitTaskDir -Since $script:gradleStartedAt `
            -RunId "$Module-$variant$BuildType-$logTimestamp-$PID-failed"
        if ($failHarvest.Outcome -eq 'Harvested') {
            $failMsg = "Failing test reports ($($failHarvest.Files) file(s)): $($failHarvest.Path)"
        }
        else {
            $failMsg = $failHarvest.Message
        }
        Write-Host $failMsg -ForegroundColor Yellow
        [System.IO.File]::AppendAllLines($tempLogPath, [string[]]@($failMsg))
    }
    # S1786: auto-emit structured failure digest from the current run's log
    $bfdScript = Join-Path $PSScriptRoot "build-failure-digest.ps1"
    if (Test-Path $bfdScript) {
        if (Test-Path $tempLogPath) {
            Write-Host "`n--- Build Failure Digest ---" -ForegroundColor Yellow
            & $bfdScript -LogPath $tempLogPath
        }
    }
    exit $gradleExit
}

Write-Host "`nFast check passed." -ForegroundColor Green
[System.IO.File]::AppendAllLines($tempLogPath, [string[]]@("Fast check passed successfully."))



# S1920: name the directory Gradle actually wrote to. A --tests run may report into a directory of its
# own (`<task>-filtered`) while the plain one keeps whatever the last FULL run left there, so a reader
# who checks the obvious path sees another run's counts under this run's green line - three sessions
# have now read the wrong one and concluded a passing test never ran.
# S1946: which of the two receives them is not fixed - the same call filtered into `-filtered` on
# app_v2 and into the plain directory on wear - so this reports the newest match instead of naming a
# path from a rule. A guess here would recreate the very failure the line exists to prevent.
# S2588: naming the right directory was still not enough, because the directory is shared and the
# reader arrives after the domain is released. Both fixes above pointed at a path a sibling can
# overwrite in the meantime; this copies the reports out while the domain is still held and prints
# the copy. Runs BEFORE the finally that releases the lock - that ordering is the safety argument.
if ($Tests -and $Mode -eq "Unit" -and $script:unitTaskDir) {
    $harvest = Save-FilteredTestReport -ProjectRoot $projectRoot -Module $Module `
        -TaskDir $script:unitTaskDir -Since $script:gradleStartedAt `
        -RunId "$Module-$variant$BuildType-$logTimestamp-$PID"
    switch ($harvest.Outcome) {
        'Harvested' {
            Write-Host "Reports (this filtered run, $($harvest.Files) file(s)): $($harvest.Path)" -ForegroundColor Yellow
        }
        default {
            # Both remaining outcomes describe evidence this run does NOT have, so both are printed
            # in a colour that does not read as a result. Neither changes the exit code: the check
            # answered its question about the code, and where the reports went is a different one.
            Write-Host $harvest.Message -ForegroundColor DarkYellow
            [System.IO.File]::AppendAllLines($tempLogPath, [string[]]@($harvest.Message))
        }
    }
}

}
finally {
    if ($script:androidSerialPinned) { $env:ANDROID_SERIAL = $previousAndroidSerial }

    # S2585: reap this run's own test workers BEFORE releasing the domain. Order is the whole safety
    # argument: while Build.* is still held, this run owns the module's build directory and only one
    # task can be running in it, so a worker under that task's tmpdir is this run's own. Gradle has
    # already returned by now, so a worker still alive is an orphan rather than work in progress -
    # and left alone it holds the variant's R.jar open and fails every later build on the machine
    # with `IOException: Couldn't delete`, which reads as a defect in the next session's change.
    # Released first, the same kill would be aimed at a directory a sibling may have just acquired.
    if ($script:unitTaskDir) {
        $survivors = Get-GradleWorkerIds -ProjectRoot $projectRoot -Module $Module -TaskDir $script:unitTaskDir
        foreach ($workerId in $survivors) {
            $null = Stop-GradleWorkerOrphan -Id $workerId `
                -Why "gradle returned but the worker is still alive; it would hold $Module's R.jar open for every later build"
        }
    }

    if ($script:runRegistryPath) {
        Remove-Item -LiteralPath $script:runRegistryPath -Force -ErrorAction SilentlyContinue
    }

    # Release exactly the domain taken above - a bare Build here would free the other module's
    # domain, which this run never held and a sibling may be building in right now.
    Exit-AgentLock -Name 'Build' -Domains $buildDomains
}
