#requires -Version 7.0
<#
.SYNOPSIS
    S0826: run the fast static Kotlin quality gates in ONE process and aggregate.

.DESCRIPTION
    A convenience batch over the cheap (non-gradle) gates so a dev iterating on a Kotlin
    change runs them with a single command and a single exit code, instead of N separate
    pwsh launches. detekt is gradle-backed and slow, so it is opt-in via -IncludeDetekt.

    Gates (in order):
      - assert-no-ticket-logs        (Sxxxx probe / permanent-log invariant)
      - assert-flavor-flags-not-growing
      - assert-neuroslop             (umbrella over the ratchet detectors)
      - assert-deprecated-pm-flags
      - assert-listener-symmetry
      - assert-qualifier-shadowing   (values-land key a smallestWidth bucket always outranks)
      - assert-tactical-step-form    (S1343 Why-field ratchet over PLAN/*/PHASE_*.md)
      - assert-flavor-matrix-docs    (S1392 doc flavor tables vs the generated capability snapshot)
- assert-sdk-pin-claims        (S1438 SDK pins stated in prose vs the build files)
      - assert-ctor-arg-slots        (S1470 primary constructors near the 255 argument-slot ceiling)
      - assert-retired-dependency-names (S1489 prose naming a dependency the project replaced)
      - assert-notification-small-icon (S1399 a small-icon setter handed a drawable literal)
      - assert-backup-rules-consistent (S1552 API 31+ extraction rules vs the pre-31 backup rules)
      - assert-launcher-reset-coverage (S1540 launcher settings vs the launcher reset's field list)
      - assert-detekt                (only with -IncludeDetekt; honours -ChangedFiles)

    Each child runs as its own process so a child `exit` cannot kill this aggregator.

    Modes:
      (default)      Run each gate in -Gate mode; print per-gate PASS/FAIL; exit 1 if any failed.
      -IncludeDetekt Also run the (slow) gradle detekt gate.
      -ChangedFiles  Diff-scoped judgement. Forwarded to detekt AND to every gate in the
                     table that accepts the parameter (see $changedFilesAware below).
                     Omit it - as a release or CI run does - and every gate keeps its
                     strict project-wide judgement.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1
    pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1 -IncludeDetekt -Module app_v2 -ChangedFiles app_v2/src/main/.../Foo.kt
#>
[CmdletBinding()]
param(
    [switch]$IncludeDetekt,
    [ValidateSet('app_v2', 'wear')]
    [string]$Module,
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    'pwsh'
}

# name -> extra args (beyond -Gate). Order matters: cheapest/most-deterministic first.
$gates = [ordered]@{
    'assert-no-ticket-logs.ps1'                 = @('-Quiet')
    # S1338: one entry, twelve lexical rules, ONE walk of the tree. It replaces the five
    # separate entries that each spawned a pwsh process and each re-walked app_v2/src -
    # neuroslop (nine rules), flavor-flags, public-mutable-flow and deprecated-pm-flags.
    # The individual scripts still exist as wrappers for any direct caller.
    'assert-source-gates.ps1'                   = @()
    'assert-listener-symmetry.ps1'              = @()
    'assert-orientation-implied-feature.ps1'    = @()
    # S1070: guards the tooling itself rather than app sources - a bare Write-Error under
    # EAP=Stop makes the following `exit N` unreachable, so a script's documented code
    # collapses to 1. Cheap (scans scripts/*.ps1 only) and the class has regrown 3 times.
    'assert-exit-contract.ps1'                  = @('-Quiet')
    # S1075: dev/TECH_REQUIREMENTS.md pins vs Gradle truth. Static parse of build files
    # + one doc; no gradle daemon. Catches a dependency bump that forgot the doc.
    'assert-doc-pin-drift.ps1'                  = @('-Quiet')
    # S1438: SDK pins stated in ORDINARY PROSE, which the managed-block gate above cannot see -
    # an index line, an architecture bullet, an agent definition, agent memory. Eight copies said
    # compileSdk 35 while Gradle compiled against 36, and an agent reading one concludes an API is
    # unavailable. Reads the value from the build file every run, so it can never itself go stale.
    'assert-sdk-pin-claims.ps1'                 = @('-Quiet')
    # S1216: device-profile preset matrix vs AppSettings, the non-presettable registry and the
    # applier branches. Data-file parse like the gate above, no gradle daemon. Catches a new
    # setting that never reached the matrix - the drift that left 40 fields uncovered.
    'assert-device-profile-matrix.ps1'          = @('-Quiet')
    # S1259: android:id parity between layout-land and layout-w600dp siblings. w600dp beats
    # -land on wide landscape devices, so an id missing on one side is a latent findViewById
    # null (the recording-indicator include NPE). Static regex over 4 shared files, ~ms.
    'assert-layout-variant-id-parity.ps1'       = @('-Quiet')
    # S1282: a values-land / values-w600dp key that values-sw320dp already declares. smallestWidth
    # outranks orientation and sw320dp matches every device, so the declaration never applies -
    # invisible to the build and to lint, and it survived a year in dimens.xml. Parses a handful of
    # small values-*.xml files, no gradle daemon.
    'assert-qualifier-shadowing.ps1'            = @('-Quiet')
    # S1470: primary constructors approaching the 255 argument-slot ceiling. AppSettings crossed it
    # at one field per ticket; kotlinc and D8 both accepted the class and only the runtime verifier
    # refused it, so the build stayed green while the app could not start at all. Source parse of
    # two directories, no gradle daemon.
    'assert-ctor-arg-slots.ps1'                 = @('-Quiet')
    # S1254: settings-dump secret masking layers - @field:SensitiveSetting on hint-matching
    # AppSettings fields, the dump's annotation check, and the S1187 keep rules. The keep rule
    # vanished once and the leak is only visible in logs exported from a stranger's device.
    'assert-sensitive-settings-annotated.ps1'   = @('-Quiet')
    # S1338: the agent-memory index is injected into EVERY turn, so its size is billed against
    # the whole corpus. It was manually compacted twice and both compactions were undone within
    # a week at ~1.1 KB/day of regrowth - a budget that is not mechanical is not a budget. Ratchet
    # on one file's length plus two advisory scans over ~220 small .md files, no gradle daemon.
    'assert-memory-budget.ps1'                  = @()
    # S1343: the mandatory `**Why:**` field on every tactical step, adopted on the pilot
    # verdict in dev/spec-form-pilot.jsonl. Count ratchet against a checked-in baseline
    # rather than a HEAD diff - PLAN/ is gitignored, so no phase file has a HEAD blob and
    # the diff-scoping every sibling gate uses returns nothing here. Parses ~300 small .md
    # files, no gradle daemon.
    'assert-tactical-step-form.ps1'             = @('-Quiet')
    # S1392: documentation flavor tables vs the generated capability snapshot. Nothing compared a
    # markdown matrix to build.gradle.kts before - the pin checker covers pins, the release
    # snapshot covers `standard` only - so docs/HOW_TO.md sat inverted against the lite gates on
    # two rows until a sibling ticket happened to derive wording from the gates instead. Parses one
    # JSON plus four small docs, no gradle daemon.
    'assert-flavor-matrix-docs.ps1'             = @('-Quiet')
    # S1489: a dependency the project retired, still named in prose. The pin gate above watches jsch
    # but compares the VERSION in one gated row, which was correct the whole time - a version
    # comparator cannot express "this name must not appear at all". So SSHJ survived the S0207/S0046
    # migration in nine documents including the published privacy policy in three locales, plus two
    # Kotlin comments. Regex over docs, dev, store_assets and the two source trees, no gradle daemon.
    'assert-retired-dependency-names.ps1'       = @('-Quiet')
    # S1399: a notification small-icon setter handed an `R.drawable.` literal instead of the one
    # owner. The defect survived thirteen call sites in eleven classes purely by never being checked -
    # with no default to reach for, three background workers settled on the audio glyph and the owner
    # watched a music note while the app moved files on a schedule. Regex over the two source trees,
    # no gradle daemon.
    'assert-notification-small-icon.ps1'        = @('-Quiet')
    # S1552: the API 31+ data-extraction rules against the pre-31 backup rules. Two files express the
    # same intent for different platform versions with nothing tying them together, and the newer one
    # carried the generic value-resource root for its whole life - a valid resource file the build
    # packages and lint accepts, declaring no rule at all. So the settings DataStore and the
    # Keystore-encrypted credentials database were backed up and restored on every Android 12+ device
    # against the exclusions written down next door, which the owner saw as settings rolling back
    # after each reinstall. Parses two small XML files, no gradle daemon.
    'assert-backup-rules-consistent.ps1'        = @('-Quiet')
    # S1453: a test in the shared src/test set for a type that lives only in a flavor-scoped source
    # set. src/test compiles for EVERY flavor, so one misplaced test breaks unit-test COMPILATION on
    # every flavor mounting the disabled counterpart - the release-blocking permission-parity test
    # could not run at all on lite while that was true. Compiling lite's unit tests instead was
    # rejected: lite is the only flavor mounting src/cloudDisabled, so it sees half the defect class.
    # Parses one build file and two source trees, no gradle daemon.
    'assert-shared-test-flavor-scope.ps1'       = @('-Quiet')
    # S1540: the launcher reset lists the settings it restores by name, and nothing held that list.
    # A forgotten line compiles and passes every other gate, then reaches the user as a reset that
    # returns half the launcher to defaults and leaves the rest as it was - an inconsistent store, not
    # a cosmetic miss. Compares two source files as text, no gradle daemon.
    'assert-launcher-reset-coverage.ps1'        = @('-Quiet')
}

# S1338: these five accept -ChangedFiles and used to be invoked with no arguments at
# all, so every one of them scanned project-wide and went red on another ticket's
# in-flight drift - a 42% FAIL rate that trained the operator to read gate output as
# noise. Forwarded only when the caller actually supplied the parameter, so a release
# or CI run keeps the strict project-wide judgement.
$changedFilesAware = @(
    'assert-source-gates.ps1',
    'assert-listener-symmetry.ps1'
)

$results = [System.Collections.Generic.List[object]]::new()
foreach ($entry in $gates.GetEnumerator()) {
    $path = Join-Path $PSScriptRoot $entry.Key
    if (-not (Test-Path $path)) {
        $results.Add([pscustomobject]@{ Gate = $entry.Key; Status = 'MISSING'; Ms = 0 })
        continue
    }
    $extraArgs = @($entry.Value)
    if ($ChangedFiles -and ($entry.Key -in $changedFilesAware)) {
        # S1184: comma-joined, never one element per file - `pwsh -File` binds only the
        # first element to [string[]] and rejects the rest as positional arguments.
        $extraArgs += @('-ChangedFiles', ($ChangedFiles -join ','))
    }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & $pwshExe -NoProfile -File $path -Gate @extraArgs | Write-Host
    $sw.Stop()
    $status = ($LASTEXITCODE -eq 0) ? 'PASS' : 'FAIL'
    $results.Add([pscustomobject]@{ Gate = $entry.Key; Status = $status; Ms = [int]$sw.Elapsed.TotalMilliseconds })
}

if ($IncludeDetekt) {
    $detektArgs = @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'assert-detekt.ps1'), '-Gate')
    if ($PSBoundParameters.ContainsKey('Module')) { $detektArgs += @('-Module', $Module) }
    if ($ChangedFiles) { $detektArgs += @('-ChangedFiles', ($ChangedFiles -join ',')) }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & $pwshExe @detektArgs | Write-Host
    $sw.Stop()
    $status = ($LASTEXITCODE -eq 0) ? 'PASS' : 'FAIL'
    $results.Add([pscustomobject]@{ Gate = 'assert-detekt.ps1'; Status = $status; Ms = [int]$sw.Elapsed.TotalMilliseconds })
}

Write-Host ''
Write-Host 'assert-fast-gates summary:' -ForegroundColor Cyan
$failed = 0
foreach ($r in $results) {
    $color = switch ($r.Status) { 'PASS' { 'Green' } 'FAIL' { 'Red' } default { 'Yellow' } }
    Write-Host ("  {0,-40} {1} ({2} ms)" -f $r.Gate, $r.Status, $r.Ms) -ForegroundColor $color
    if ($r.Status -ne 'PASS') { $failed++ }
}

if ($failed -gt 0) {
    Write-Host "assert-fast-gates: FAIL ($failed gate(s))." -ForegroundColor Red
    exit 1
}
Write-Host 'assert-fast-gates: PASS (all fast gates green).' -ForegroundColor Green
exit 0
