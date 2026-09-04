#!/usr/bin/env pwsh
<#
.SYNOPSIS
    FastMediaSorter project scripts launcher
.DESCRIPTION
    Quick alias launcher for common project scripts
.PARAMETER Command
    Script command to execute:
    r    - Build AAB Release
    vr   - Build VR Release APK
    cc   - Commit without push
    id   - Install Standard Debug APK on device (no launch)
    ind  - Install noLegal Debug APK on device (no launch)
    ivn  - Install noLegal Debug APK on device (no launch)
    dc   - Build Debug Clean
    d    - Fast reusable debug build
    db   - Fast reusable debug build (without zip)
    dav  - Debug build with timestamped app version
    fk   - Fast Kotlin compile check (standard; -Flavor picks another)
    fkn  - Fast Kotlin compile check (noLegal)
    fr   - Fast resources/manifest check (-Flavor applies)
    fc   - Fast code + resources check (-Flavor applies)
           Every flavor is reachable on fk/fr/fc without a dedicated letter:
           -Flavor Standard|NoLegal|Lite|Photos|Legacy|Vr|Foss, e.g. `.\a.ps1 fc -Flavor Lite`.
           This is how "build every affected variant" is satisfied - each call takes
           BUILD.LOCK, so no direct gradlew invocation is needed.
    fu   - Fast full unit-test suite (app_v2)
    fa   - Fast instrumented-test COMPILE check (app_v2 androidTest; does not run them)
    fam  - RUN the Room migration tests on a connected device (the database-upgrade proof)
           Takes -DeviceId <serial>. With several devices attached it now REFUSES and lists
           them instead of installing on each one, the owner's phone included (S2363).
    fw   - Fast Kotlin compile check, wear module (standard flavor)
    fwn  - Fast Kotlin compile check, wear module (noLegal flavor)
    fwr  - Fast resources/manifest check, wear module
    fwu  - Fast unit-test suite, wear module
           fk/fkn/fr/fc/fu all check app_v2. A change under wear/ needs fw/fwr/fwu -
           the phone target exits 0 without looking at the watch module at all.
           fw covers only the flavor the module declares first, standard. Since S2486 the
           two wear flavors compile different code, so a change under wear/src/standard or
           wear/src/noLegal needs fwn as well - fw alone passes on a binding declared once.
    flr  - Fast lint-rules detector test suite (:lint-rules:test)
    fg   - Fast static gates batch (neuroslop+pm+listener+flavor+ticket-log; -IncludeDetekt opt-in)
    fs   - Script regression suites (bare = full sweep, background it; -ChangedFiles "<paths>", -ListOnly)
    mb   - Run standard macrobenchmark suite
    gbp  - Generate standard baseline profile
    cd   - Clean + Debug + Zip
    cdb  - Clean + Debug (without zip)
    cls  - Clean Gradle caches
    c    - Commit & Push
    ch   - Check Typo/Lint
    s    - Setup Test Media
    bp   - Build and Push All
    ss   - Show unresolved specs (alias: sca-specs)
    bf   - Show last build failure block
    bfd  - Build failure digest (structured JSON + verdict)
    nl   - Build noLegal Release
    nd   - Build noLegal Debug
    wd   - Build Wear OS Debug and distribute APK
    iw   - Build and install noLegal Wear OS Debug APK on a selected watch
    r1   - Run the release queue unattended, instance A (one fresh claude process per ticket)
    r2   - Same, instance B - the second parallel stream, staggered so it does not race A
    r3   - Same, instance C - the third parallel stream, staggered further so it does not race A or B
           Order comes from PLAN/RELEASE_QUEUE.md; the model is picked per ticket (Opus where a
           decision is left, Sonnet for Implemented and tier 1-2). Options forward through, e.g.
           `.\a.ps1 r1 -MaxTickets 5 -TimeoutMinutes 45`.
    rs   - Stop the runners: each finishes the ticket it is on, then exits.
           `.\a.ps1 rs -Instance b` stops one; `.\a.ps1 rs -Kill` also kills the children.
    rm   - Monitor: running children, claimed tickets, locks, and what each instance finished
           (-Watch to refresh, -Json for the snapshot object).
    rmw  - Monitor page: start the detached writer and open temp/monitor/index.html in the browser;
           refreshes every 3 s from the same snapshot as rm (-Stop, -Status).
           `.\a.ps1 rm -Watch` refreshes until Ctrl+C.
    chat - Agent chat (S2372): what sibling sessions are doing and what they measured; verb and
           options ride in via $Rest, e.g. `.\a.ps1 chat -Verb Status`, `.\a.ps1 chat -Verb Find -Topic "check:*"`.
    ub   - Unlock build: clear EVERY build domain (Build.Phone + Build.Wear) and its queue when
           the holder is stale or dead. Per domain: ubp (Build.Phone), ubw (Build.Wear).
    uc   - Unlock code: the same across EVERY code domain (Code.Phone + Code.Wear + Code.Scripts).
           Per domain: ucp (Code.Phone), ucw (Code.Wear), ucs (Code.Scripts).
           All REFUSE a lock whose holder is still live and print who holds it; add -Force
           once the holder is confirmed gone - that also drops that domain's whole queue.
    uqb  - Withdraw this session's own place in EVERY build domain queue; locks untouched.
           Per domain: uqbp (Build.Phone), uqbw (Build.Wear).
    uqc  - The same across every code domain queue; per domain: uqcp (Code.Phone),
           uqcw (Code.Wear), uqcs (Code.Scripts). Use after abandoning a queued intent: the
           eviction sweep will not clear it, since it judges the owning session and that
           session is alive.
    ul   - Unlock leases: drop the ticket leases a killed flow left behind, keeping every one a
           running child or a held lock still vouches for. Prints why each went or stayed.
           `.\a.ps1 ul -Force` drops the lot without asking anything.
    adb  - adb swiss-army passthrough (scripts\devtest\adb.ps1); verb + options ride in via $Rest
    adb-devices / adb-shot / adb-log / adb-current / adb-launch / adb-logcat-clear - fixed-verb shortcuts
.EXAMPLE
    .\a.ps1 d
    .\a d
.EXAMPLE
    .\a.ps1 r1
    .\a.ps1 r2
    .\a.ps1 r3
    .\a.ps1 rm
    .\a.ps1 rs
.EXAMPLE
    .\a.ps1 adb devices
    .\a.ps1 adb log -Tail 400 -Grep S0035
    .\a.ps1 adb-shot -DeviceId emulator-5554
#>

param(
    [string]$Command = ''
)

# Everything after <Command> is forwarded verbatim to the target script (after its preset
# Args), e.g. `.\a.ps1 adb log -Tail 400 -Grep S0035` or `.\a.ps1 adb shot -DeviceId X`.
# Reading the automatic $args (simple-script mode) rather than a declared
# ValueFromRemainingArguments parameter is deliberate: $args captures -flag tokens too,
# which an advanced-binding param would reject ("a positional parameter cannot be found").
$Rest = $args

$ErrorActionPreference = "Stop"

# Get project root directory. This one file keeps the literal answer: a.ps1 IS one of the three
# marker files the resolver walks for, so asking the resolver here would make the marker depend on
# what it identifies (S2326 step 04.2).
$ProjectRoot = $PSScriptRoot

# Sibling directories (the release worktree below) resolve through the shared resolver.
. "$PSScriptRoot\scripts\utils\project-paths.ps1"

# --- S2412: keep a Gradle daemon away from the caller's pipe ------------------------------------
#
# A daemon born under an agent's tool call inherits that call's stdout handle and outlives the
# build, so the call never sees EOF and hangs until the tool's own timeout - measured 2026-09-03 at
# 9 min 11 s past the point the script itself had finished, with every lock that session held still
# taken. Running the target in a child whose streams are FILES removes the pipe from what the daemon
# can inherit. a.ps1 is the seam because it is the one entry point an agent is obliged to use
# (CLAUDE.md Rule 25), which beats patching 99 gradlew call sites in 50 scripts.

function Test-GradleBackedScript {
    param([string]$Path)

    # Read from the target's own text, never from a list of target names: there are 73 targets here
    # and the gradle-backed subset changes whenever one is added, so a list would go stale silently
    # and the hang would come back for exactly the target nobody remembered to add.
    # `gradlew` is the invocation itself; `Enter-BuildLockOrExit` is what Rule 23 obliges every
    # gradle entry point to call, and catches a target that assembles its command line elsewhere.
    $text = Get-Content -LiteralPath $Path -Raw -ErrorAction SilentlyContinue
    if (-not $text) { return $false }
    return ($text -match 'gradlew') -or ($text -match 'Enter-BuildLockOrExit')
}

function Test-StdoutIsolationWanted {
    param([string]$Path)

    # FMS_ISOLATE_STDOUT is the escape hatch for the transitive case the text test cannot see - a
    # target that reaches gradle only through another script, `fg -IncludeDetekt` being the one such
    # target today. 1 forces isolation on, 0 forces it off.
    switch ($env:FMS_ISOLATE_STDOUT) {
        '1' { return $true }
        '0' { return $false }
    }
    # Redirected stdout means a pipe or a file, which is the agent; a console is the owner in a
    # terminal, who has never reproduced this and would only lose the colour of the output.
    return [Console]::IsOutputRedirected -and (Test-GradleBackedScript -Path $Path)
}

function ConvertTo-ChildArgumentList {
    param($Preset, $Extra)

    # A hashtable splat binds by name in-process but cannot cross a process boundary, so the preset
    # is flattened to tokens here. Switches carry no value; everything else becomes two tokens.
    $flat = @()
    if ($Preset -is [hashtable]) {
        foreach ($key in $Preset.Keys) {
            $value = $Preset[$key]
            if ($value -is [bool]) {
                if ($value) { $flat += "-$key" }
            }
            else {
                $flat += "-$key"
                $flat += "$value"
            }
        }
    }
    elseif ($Preset) {
        $flat += @($Preset | ForEach-Object { "$_" })
    }
    foreach ($token in $Extra) { $flat += "$token" }
    return , $flat
}

function Invoke-LauncherTarget {
    param([string]$Path, $PresetArgs, $ExtraArgs, [string]$WorkingDirectory)

    if (-not (Test-StdoutIsolationWanted -Path $Path)) {
        & $Path @PresetArgs @ExtraArgs
        return
    }
    $isolator = Join-Path $PSScriptRoot 'scripts\utils\invoke-isolated-stdout.ps1'
    $childArgs = ConvertTo-ChildArgumentList -Preset $PresetArgs -Extra $ExtraArgs
    if ($WorkingDirectory) {
        & $isolator -ScriptPath $Path -Arguments $childArgs -WorkingDirectory $WorkingDirectory
    }
    else {
        & $isolator -ScriptPath $Path -Arguments $childArgs
    }
}

# Script mapping.
#
# Args MUST be a hashtable, not a string array. Reason: `& $script @arrayArgs` splats
# positionally - strings starting with `-` are treated as values, not flags, and
# leak into the first positional [string] parameter. With `& $script @hashtableArgs`
# PowerShell always binds by name, no matter how the keys order. Empty hashtable
# is fine and means "no args".
#
# Each value is `$true` for a switch, or a string/int for a typed param.
$scripts = @{
    'r'         = @{ Path = 'scripts\builders\build-aab-release.ps1'; Args = @{} }
    'vr'        = @{ Path = 'scripts\builders\build-vr-release.ps1'; Args = @{} }
    'cc'        = @{ Path = 'scripts\utils\commit-push.ps1'; Args = @{ NoPush = $true } }
    'id'        = @{ Path = 'scripts\builders\install-standard-debug-to-device.ps1'; Args = @{} }
    'ind'       = @{ Path = 'scripts\builders\install-nolegal-debug-to-device.ps1'; Args = @{} }
    'ivn'       = @{ Path = 'scripts\builders\install-nolegal-debug-to-device.ps1'; Args = @{} }
    'dc'        = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @{} }
    'd'         = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @{} }
    'db'        = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @{ SkipZip = $true } }
    'dav'       = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @{ AutoVersion = $true } }
    'bd'        = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @{ SkipZip = $true } }  # typo-tolerant alias for 'db'
    'dq'        = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @{ SkipZip = $true; Quiet = $true } }  # quiet debug: filters known noise
    'cd'        = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @{} }
    'cdb'       = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @{ SkipZip = $true } }
    'fk'        = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'Code' } }
    'fkn'       = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'Code'; Flavor = 'NoLegal' } }  # S0826: fast noLegal Kotlin compile
    'fr'        = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'Resources' } }
    'fc'        = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'CodeAndResources' } }
    'fu'        = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'Unit' } }
    'fa'        = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'AndroidTest' } }  # S1844: compile the instrumented set - no other target does
    # S2306: RUN the Room migration tests on a connected device. `fa` proves they compile; only this
    # executes runMigrationsAndValidate, which is the same schema comparison a user's phone performs on
    # the first launch after an update - and the comparison that reset the owner's database on
    # 2026-09-01. Needs a device; long, so background it (CLAUDE.md section 6).
    'fam'       = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'ConnectedAndroidTest'; Tests = 'com.sza.fastmediasorter.data.local.db' } }
    'fw'        = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'Code'; Module = 'wear' } }  # S1496: fast Kotlin compile for the wear module
    'fwn'       = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'Code'; Module = 'wear'; Flavor = 'NoLegal' } }  # S2486: fw resolves to the module's first flavor, standard - this is the other one
    'fwr'       = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'Resources'; Module = 'wear' } }  # S1807: fast resources/manifest check for the wear module
    'fwu'       = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'Unit'; Module = 'wear' } }  # S1807: fast unit-test suite for the wear module
    # S2355: compile the WATCH instrumented set. `fa` compiles app_v2 only, so quoting it under a
    # wear change records a verdict about the other module - the miss S1807 measured five times.
    # The flavor is named rather than defaulted: S2090 gave the watch a standard/noLegal dimension.
    'faw'       = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'AndroidTest'; Module = 'wear'; Flavor = 'Standard' } }
    # S2355: RUN the watch Room migration tests on a connected device. `fam` runs the phone package only.
    # The flavor is named rather than defaulted: S2090 gave the watch a standard/noLegal dimension.
    'fwm'       = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'ConnectedAndroidTest'; Module = 'wear'; Flavor = 'Standard'; Tests = 'com.sza.fastmediasorter.wear.data.db' } }
    'flr'       = @{ Path = 'scripts\builders\check-lint-rules.ps1'; Args = @{} }  # S1195: custom lint detectors' own test suite
    'fg'        = @{ Path = 'scripts\quality\assert-fast-gates.ps1'; Args = @{} }  # S0826: batch fast static gates in one process
    # S2122: the repository's *.tests/Run-Tests.ps1 suites, by hand. Bare = the full sweep (measured
    # over 120 s, so background it); `-ChangedFiles "<paths>"` runs only the suites guarding those
    # files; `-ListOnly` shows the selection and which subject each suite claims, running nothing.
    'fs'        = @{ Path = 'scripts\quality\run-script-suites.ps1'; Args = @{} }
    'mb'        = @{ Path = 'scripts\builders\run-standard-macrobenchmark.ps1'; Args = @{} }
    'gbp'       = @{ Path = 'scripts\builders\generate-standard-baseline-profile.ps1'; Args = @{} }
    'cls'       = @{ Path = 'scripts\builders\clean-gradle-caches.ps1'; Args = @{} }
    'c'         = @{ Path = 'scripts\utils\commit-push.ps1'; Args = @{} }
    'ch'        = @{ Path = 'scripts\utils\check-typo-lint.ps1'; Args = @{} }
    's'         = @{ Path = 'scripts\utils\setup_test_media.ps1'; Args = @{} }
    'b'         = @{ Path = 'scripts\builders\build-and-push-all.ps1'; Args = @{} }
    'bp'        = @{ Path = 'scripts\builders\build-and-push-all.ps1'; Args = @{} }
    'ss'        = @{ Path = 'scripts\spec_catalog\sca-specs.ps1'; Args = @{} }
    'sca-specs' = @{ Path = 'scripts\spec_catalog\sca-specs.ps1'; Args = @{} }
    'bf'        = @{ Path = 'scripts\builders\get-last-build-failure.ps1'; Args = @{} }
    'bfd'       = @{ Path = 'scripts\builders\build-failure-digest.ps1'; Args = @{} }
    'nl'        = @{ Path = 'scripts\builders\build-nolegal-release.ps1'; Args = @{} }
    'nd'        = @{ Path = 'scripts\builders\build-nolegal-debug.ps1'; Args = @{} }
    'wd'        = @{ Path = 'scripts\builders\build-wear-debug.PS1'; Args = @{} }
    'iw'        = @{ Path = 'scripts\builders\build-wear-debug.PS1'; Args = @{ Flavor = 'noLegal'; Install = $true } }
    # Unattended queue runners. Each ticket gets its own claude process, so the context resets
    # between tickets instead of growing all session. r1, r2 and r3 are the parallel instances -
    # r2 and r3 stagger their first ranking, each by a wider window than the last, so no pair
    # ranks on the same instant and races for the same ticket. Long-running by design: start them
    # in their own windows.
    'r1'        = @{ Path = 'scripts\utils\run-spec-queue.ps1'; Args = @{ Instance = 'a' } }
    'r2'        = @{ Path = 'scripts\utils\run-spec-queue.ps1'; Args = @{ Instance = 'b'; StartDelaySeconds = 20 } }
    'r3'        = @{ Path = 'scripts\utils\run-spec-queue.ps1'; Args = @{ Instance = 'c'; StartDelaySeconds = 40 } }
    'rs'        = @{ Path = 'scripts\utils\run-spec-queue.ps1'; Args = @{ Stop = $true } }
    'rm'        = @{ Path = 'scripts\utils\monitor-spec-queue.ps1'; Args = @{} }
    # S2406: the monitor page - a detached writer keeps temp/monitor/index.html current every 3 s
    # from the same snapshot `rm` prints; `-Stop` ends it, `-Status` asks.
    'rmw'       = @{ Path = 'scripts\utils\dev-monitor-writer.ps1'; Args = @{} }
    # S2372: the descriptive layer beside the locks - read at a refusal, written by the scripts.
    'chat'      = @{ Path = 'scripts\utils\agent-chat.ps1'; Args = @{} }
    # Lock releasers. Conservative by design: a lock whose owner is still alive is REFUSED and its
    # holder printed, so the shortcut cannot silently drop a sibling's turn mid-edit. `-Force` rides
    # through $Rest for the case the operator has confirmed the holder is gone.
    # S2109: the four short names keep meaning the WHOLE set of their type, so an operator who
    # learned them before the split keeps getting what they expect; the domain arrives as extra
    # targets rather than as a second vocabulary.
    'ub'        = @{ Path = 'scripts\utils\clear-agent-lock.ps1'; Args = @{ Name = 'Build' } }
    'uc'        = @{ Path = 'scripts\utils\clear-agent-lock.ps1'; Args = @{ Name = 'Code' } }
    'ubp'       = @{ Path = 'scripts\utils\clear-agent-lock.ps1'; Args = @{ Name = 'Build.Phone' } }
    'ubw'       = @{ Path = 'scripts\utils\clear-agent-lock.ps1'; Args = @{ Name = 'Build.Wear' } }
    'ucp'       = @{ Path = 'scripts\utils\clear-agent-lock.ps1'; Args = @{ Name = 'Code.Phone' } }
    'ucw'       = @{ Path = 'scripts\utils\clear-agent-lock.ps1'; Args = @{ Name = 'Code.Wear' } }
    'ucs'       = @{ Path = 'scripts\utils\clear-agent-lock.ps1'; Args = @{ Name = 'Code.Scripts' } }
    # Queue withdrawers (S2098), a different operation from the two above: ub/uc clear a LOCK, while
    # uqb/uqc drop this session's own place in a QUEUE and never touch a lock file. Reach for these
    # when a queued intent was abandoned - the eviction sweep will not, since it judges the owning
    # session, and that session is alive; it is the intent that was dropped.
    'uqb'       = @{ Path = 'scripts\utils\withdraw-lock-ticket.ps1'; Args = @{ Name = 'Build' } }
    'uqc'       = @{ Path = 'scripts\utils\withdraw-lock-ticket.ps1'; Args = @{ Name = 'Code' } }
    'uqbp'      = @{ Path = 'scripts\utils\withdraw-lock-ticket.ps1'; Args = @{ Name = 'Build.Phone' } }
    'uqbw'      = @{ Path = 'scripts\utils\withdraw-lock-ticket.ps1'; Args = @{ Name = 'Build.Wear' } }
    'uqcp'      = @{ Path = 'scripts\utils\withdraw-lock-ticket.ps1'; Args = @{ Name = 'Code.Phone' } }
    'uqcw'      = @{ Path = 'scripts\utils\withdraw-lock-ticket.ps1'; Args = @{ Name = 'Code.Wear' } }
    'uqcs'      = @{ Path = 'scripts\utils\withdraw-lock-ticket.ps1'; Args = @{ Name = 'Code.Scripts' } }
    # Ticket leases are the third thing a killed flow leaves behind, and the one the built-in sweep
    # will not touch for 45 minutes: its window is sized for a working session that writes nothing,
    # not for a dead one. Clean judges on live evidence instead - see the verb's own docs.
    'ul'        = @{ Path = 'scripts\spec_catalog\ticket-lease.ps1'; Args = @{ Verb = 'Clean' } }
    # adb swiss-army (scripts/devtest/adb.ps1). `adb` is the full passthrough - the verb
    # and any options ride in via $Rest, e.g. `.\a.ps1 adb log -Tail 400 -Grep S0035`.
    # The rest are fixed-verb shortcuts; extra options still forward through $Rest.
    'adb'       = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{} }
    'adb-devices' = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'devices' } }
    'adb-shot'  = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'shot' } }
    'adb-log'   = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'log' } }
    'adb-current' = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'current' } }
    'adb-launch' = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'launch' } }
    'adb-logcat-clear' = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'logcat-clear' } }
    # Kept, and kept pointing at the removed verb on purpose: it now refuses and names both replacements,
    # which is the whole remedy. Repointing it at either one silently would rebuild the S1572 trap.
    'adb-clear' = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'clear' } }
}

# Validate command
if (-not $scripts.ContainsKey($Command)) {
    Write-Host "❌ Unknown command: $Command" -ForegroundColor Red
    Write-Host ""
    Write-Host "Available commands:" -ForegroundColor Yellow
    Write-Host "  r    - Build AAB Release" -ForegroundColor Cyan
    Write-Host "  vr   - Build VR Release APK" -ForegroundColor Cyan
    Write-Host "  cc   - Commit without push" -ForegroundColor Cyan
    Write-Host "  id   - Install Standard Debug APK on device (NO launch)" -ForegroundColor Cyan
    Write-Host "  ind  - Install noLegal Debug APK on device (NO launch)" -ForegroundColor Cyan
    Write-Host "  ivn  - Install noLegal Debug APK on device (NO launch)" -ForegroundColor Cyan
    Write-Host "  dc   - Build Debug Clean" -ForegroundColor Cyan
    Write-Host "  d    - Fast reusable debug build" -ForegroundColor Cyan
    Write-Host "  db   - Fast reusable debug build without zip" -ForegroundColor Cyan
    Write-Host "  dav  - Debug build with timestamped app version" -ForegroundColor Cyan
    Write-Host "  bd   - Build Debug without zip (typo-tolerant alias for db)" -ForegroundColor Cyan
    Write-Host "  dq   - Build Debug quiet (no zip, suppresses known-noise lines)" -ForegroundColor Cyan
    Write-Host "  cd   - Clean + Debug + zip" -ForegroundColor Cyan
    Write-Host "  cdb  - Clean + Debug without zip" -ForegroundColor Cyan
    Write-Host "  fk   - Fast Kotlin compile check (standard)" -ForegroundColor Cyan
    Write-Host "  fkn  - Fast Kotlin compile check (noLegal)" -ForegroundColor Cyan
    Write-Host "  fr   - Fast resources/manifest check" -ForegroundColor Cyan
    Write-Host "  fc   - Fast code + resources check" -ForegroundColor Cyan
    Write-Host "         fk/fr/fc take -Flavor Standard|NoLegal|Lite|Photos|Legacy|Vr|Foss," -ForegroundColor DarkCyan
    Write-Host "         e.g. '.\a.ps1 fc -Flavor Lite' - proves any single flavor." -ForegroundColor DarkCyan
    Write-Host "  fu   - Fast full unit-test suite (app_v2)" -ForegroundColor Cyan
    Write-Host "  fa   - Fast instrumented-test COMPILE check (app_v2 androidTest)" -ForegroundColor Cyan
    Write-Host "  fam  - RUN the Room migration tests on a connected device (database-upgrade proof)" -ForegroundColor Cyan
    Write-Host "         fam/fwm take -DeviceId <serial>; several devices attached = refusal, not a fan-out" -ForegroundColor Cyan
    Write-Host "  fw   - Fast Kotlin compile check, wear module (standard flavor)" -ForegroundColor Cyan
    Write-Host "  fwn  - Fast Kotlin compile check, wear module (noLegal flavor)" -ForegroundColor Cyan
    Write-Host "  fwr  - Fast resources/manifest check, wear module" -ForegroundColor Cyan
    Write-Host "  fwu  - Fast unit-test suite, wear module" -ForegroundColor Cyan
    Write-Host "         fk/fkn/fr/fc/fu all check app_v2 - a wear/ change needs fw/fwr/fwu." -ForegroundColor DarkCyan
    Write-Host "         a wear/src/<flavor> change needs fwn too - fw only sees standard (S2486)." -ForegroundColor DarkCyan
    Write-Host "  flr  - Fast lint-rules detector test suite (:lint-rules:test)" -ForegroundColor Cyan
    Write-Host "  fg   - Fast static gates batch (neuroslop+pm+listener+flavor+ticket-log)" -ForegroundColor Cyan
    Write-Host "  fs   - Script regression suites (-ChangedFiles / -ListOnly; bare = full sweep)" -ForegroundColor Cyan
    Write-Host "  mb   - Run standard macrobenchmark suite" -ForegroundColor Cyan
    Write-Host "  gbp  - Generate standard baseline profile" -ForegroundColor Cyan
    Write-Host "  cls  - Clean Gradle caches" -ForegroundColor Cyan
    Write-Host "  c    - Commit & Push" -ForegroundColor Cyan
    Write-Host "  ch   - Check Typo/Lint" -ForegroundColor Cyan
    Write-Host "  s    - Setup Test Media" -ForegroundColor Cyan
    Write-Host "  b    - Build and Push All (same as bp)" -ForegroundColor Cyan
    Write-Host "  bp   - Build and Push All" -ForegroundColor Cyan
    Write-Host "  ss   - Show unresolved specs (alias: sca-specs)" -ForegroundColor Cyan
    Write-Host "  bf   - Show last build failure block" -ForegroundColor Cyan
    Write-Host "  bfd  - Build failure digest (structured JSON + verdict)" -ForegroundColor Cyan
    Write-Host "  nl   - Build noLegal Release" -ForegroundColor Cyan
    Write-Host "  nd   - Build noLegal Debug" -ForegroundColor Cyan
    Write-Host "  wd   - Build Wear OS Debug and distribute APK" -ForegroundColor Cyan
    Write-Host "  iw   - Build + install noLegal Wear OS Debug (-DeviceId <watch> when multiple devices)" -ForegroundColor Cyan
    Write-Host "  r1   - Run the release queue unattended, instance A (fresh process per ticket)" -ForegroundColor Cyan
    Write-Host "  r2   - Same, instance B - the second parallel stream" -ForegroundColor Cyan
    Write-Host "  r3   - Same, instance C - the third parallel stream" -ForegroundColor Cyan
    Write-Host "  rs   - Stop the runners after the ticket each is on (-Kill to terminate now)" -ForegroundColor Cyan
    Write-Host "  rm   - Monitor the runners (-Watch to refresh, -Json for the snapshot)" -ForegroundColor Cyan
    Write-Host "  rmw  - Monitor page: detached writer + browser, temp/monitor/index.html (-Stop, -Status)" -ForegroundColor Cyan
    Write-Host "  ub   - Unlock build: every build domain, stale/dead only (-Force to override)" -ForegroundColor Cyan
    Write-Host "         ubp/ubw - one domain: Build.Phone / Build.Wear" -ForegroundColor Cyan
    Write-Host "  uc   - Unlock code: every code domain, stale/dead only (-Force to override)" -ForegroundColor Cyan
    Write-Host "         ucp/ucw/ucs - one domain: Code.Phone / Code.Wear / Code.Scripts" -ForegroundColor Cyan
    Write-Host "  uqb  - Withdraw this session's place in every BUILD domain queue (locks untouched)" -ForegroundColor Cyan
    Write-Host "         uqbp/uqbw - one domain" -ForegroundColor Cyan
    Write-Host "  uqc  - Withdraw this session's place in every CODE domain queue (locks untouched)" -ForegroundColor Cyan
    Write-Host "         uqcp/uqcw/uqcs - one domain" -ForegroundColor Cyan
    Write-Host "  ul   - Unlock leases: drop ticket leases a killed flow left behind (-Force = all)" -ForegroundColor Cyan
    Write-Host "  adb  - adb swiss-army passthrough, e.g. 'adb log -Tail 400 -Grep S0035'" -ForegroundColor Cyan
    Write-Host "  adb-devices / adb-shot / adb-log / adb-current / adb-launch / adb-logcat-clear" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage: .\a.ps1 <command>" -ForegroundColor Gray
    Write-Host "Example: .\a.ps1 d" -ForegroundColor Gray
    exit 1
}

# Get script path
$scriptEntry = $scripts[$Command]
$scriptPath = Join-Path $ProjectRoot $scriptEntry.Path
$scriptArgs = $scriptEntry.Args

# Verify script exists
if (-not (Test-Path $scriptPath)) {
    Write-Host "❌ Script not found: $scriptPath" -ForegroundColor Red
    exit 1
}

# Release commands are always built from the release worktree (main branch).
# The worktree lives at ../FastMediaSorter_release - created via:
#   git worktree add ../FastMediaSorter_release main
$releaseCommands = @('r', 'nl', 'vr')
if ($releaseCommands -contains $Command) {
    $worktreePath = Get-SiblingPath -Name "FastMediaSorter_release"
    if (Test-Path $worktreePath) {
        Write-Host "Release build - delegating to release worktree [main]" -ForegroundColor Cyan
        Write-Host "  $worktreePath" -ForegroundColor DarkGray

        # Pull latest main into worktree
        Write-Host "Pulling latest main..." -ForegroundColor Yellow
        Push-Location $worktreePath
        git pull --ff-only
        $pullExit = $LASTEXITCODE
        Pop-Location
        if ($pullExit -ne 0) {
            Write-Host "Warning: git pull --ff-only failed (exit $pullExit)." -ForegroundColor Yellow
            Write-Host "Worktree may have uncommitted changes or a diverged history." -ForegroundColor Yellow
            Write-Host "Resolve manually in $worktreePath, then re-run." -ForegroundColor Yellow
            exit 1
        }

        # Sync gitignored-but-required files from dev directory to release worktree.
        # Source of truth is always the dev directory; worktree copies are transient.
        $syncManifest = Join-Path $ProjectRoot "scripts\release-worktree-sync.txt"
        if (Test-Path $syncManifest) {
            Write-Host "Syncing local files to release worktree..." -ForegroundColor Yellow
            $syncLines = Get-Content $syncManifest | Where-Object { $_ -notmatch '^\s*#' -and $_.Trim() -ne '' }
            foreach ($relPath in $syncLines) {
                $relPath = $relPath.Trim()
                $srcFile = Join-Path $ProjectRoot $relPath
                $dstFile = Join-Path $worktreePath $relPath
                if (Test-Path $srcFile) {
                    $dstDir = Split-Path $dstFile -Parent
                    if (-not (Test-Path $dstDir)) {
                        New-Item -ItemType Directory -Path $dstDir -Force | Out-Null
                    }
                    Copy-Item -Path $srcFile -Destination $dstFile -Force
                    Write-Host "  synced: $relPath" -ForegroundColor DarkGray
                }
                else {
                    Write-Host "  skipped (not found): $relPath" -ForegroundColor DarkYellow
                }
            }
        }

        # Run the script from inside the worktree so $PSScriptRoot resolves there
        $worktreeScript = Join-Path $worktreePath $scriptEntry.Path
        if (-not (Test-Path $worktreeScript)) {
            Write-Host "Error: script not found in release worktree: $worktreeScript" -ForegroundColor Red
            exit 1
        }
        $argsDisplay = if ($scriptArgs -is [hashtable]) {
            ($scriptArgs.GetEnumerator() | ForEach-Object {
                if ($_.Value -is [bool]) { "-$($_.Key)" } else { "-$($_.Key) $($_.Value)" }
            }) -join ' '
        } else { $scriptArgs -join ' ' }
        Write-Host "Executing (worktree): $($scriptEntry.Path) $argsDisplay $($Rest -join ' ')" -ForegroundColor Green
        Write-Host ""
        # CRITICAL: change CWD to worktree before invoking the build script.
        # Gradle resolves the project directory from CWD (not from gradlew.bat location),
        # so without this Push-Location Gradle would build the DEV project even though
        # the script writes versionCode/Name into the worktree's build.gradle.kts -
        # producing artifacts with stale versions and silently mirroring dev outputs.
        Push-Location $worktreePath
        try {
            # The working directory is passed explicitly rather than inherited: under S2412's
            # isolation the build runs in a CHILD process, and a child does not inherit Push-Location.
            Invoke-LauncherTarget -Path $worktreeScript -PresetArgs $scriptArgs -ExtraArgs $Rest `
                -WorkingDirectory $worktreePath
            $buildExit = $LASTEXITCODE
        }
        finally {
            Pop-Location
        }

        # Mirror DOWNLOADS from worktree to local dev directory
        if ($buildExit -eq 0) {
            $worktreeDownloads = Join-Path $worktreePath "DOWNLOADS"
            $localDownloads = Join-Path $ProjectRoot  "DOWNLOADS"
            if (Test-Path $worktreeDownloads) {
                if (-not (Test-Path $localDownloads)) {
                    New-Item -ItemType Directory -Path $localDownloads | Out-Null
                }
                # Copy all build artifacts (overwrite) - skip the journal so we append below
                Get-ChildItem -Path $worktreeDownloads -File |
                Where-Object { $_.Name -ne "builds_versions.lst" } |
                ForEach-Object {
                    Copy-Item -Path $_.FullName -Destination (Join-Path $localDownloads $_.Name) -Force
                }
                # Append only the last journal line (most recent build entry) to local journal
                $worktreeJournal = Join-Path $worktreeDownloads "builds_versions.lst"
                $localJournal = Join-Path $localDownloads    "builds_versions.lst"
                if (Test-Path $worktreeJournal) {
                    $lastEntry = Get-Content $worktreeJournal | Select-Object -Last 1
                    if ($lastEntry) {
                        Add-Content -Path $localJournal -Value $lastEntry -Encoding UTF8
                    }
                }
                Write-Host ""
                Write-Host "DOWNLOADS synced to: $localDownloads" -ForegroundColor Cyan
            }
        }

        exit $buildExit
    }
    else {
        Write-Host "Warning: release worktree not found at $worktreePath" -ForegroundColor Yellow
        Write-Host "Set it up once with:" -ForegroundColor Gray
        Write-Host "  git worktree add ../FastMediaSorter_release main" -ForegroundColor Gray
        Write-Host "Falling back to current directory ($(git branch --show-current 2>$null))." -ForegroundColor Yellow
        Write-Host ""
    }
}

# Execute script (non-release commands, or release fallback when no worktree)
$argsDisplay = if ($scriptArgs -is [hashtable]) {
    ($scriptArgs.GetEnumerator() | ForEach-Object {
        if ($_.Value -is [bool]) { "-$($_.Key)" } else { "-$($_.Key) $($_.Value)" }
    }) -join ' '
} else { $scriptArgs -join ' ' }
Write-Host "Executing: $($scriptEntry.Path) $argsDisplay $($Rest -join ' ')" -ForegroundColor Green
Write-Host ""

Invoke-LauncherTarget -Path $scriptPath -PresetArgs $scriptArgs -ExtraArgs $Rest

# Return exit code from executed script
exit $LASTEXITCODE
