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
      - assert-source-gates          (S1338: every lexical ratchet rule over ONE walk of the
                                      tree - the nine neuroslop rules plus flavor-flags,
                                      deprecated-pm-flags, public-mutable-flow, window-insets,
                                      swallowed-cancellation, activity-logic (S1329),
                                      untracked-dialog and the two string-resource rules)
      - assert-listener-symmetry
      - assert-qualifier-shadowing   (values-land key a smallestWidth bucket always outranks)
      - assert-tactical-step-form    (S1343 Why-field ratchet over PLAN/*/PHASE_*.md)
      - assert-flavor-matrix-docs    (S1392 doc flavor tables vs the generated capability snapshot)
- assert-sdk-pin-claims        (S1438 SDK pins stated in prose vs the build files)
      - assert-ctor-arg-slots        (S1470 primary constructors near the 255 argument-slot ceiling)
      - assert-packaging-excludes-parity (S1679 shared-library payload stripped in one module only)
      - assert-module-version-parity  (app_v2 / wear version fields under one applicationId)
      - assert-splash-brand-sync     (S1706 generated splash drawables vs their strings and template)
      - assert-retired-dependency-names (S1489 prose naming a dependency the project replaced)
      - assert-notification-small-icon (S1399 a small-icon setter handed a drawable literal)
      - assert-backup-rules-consistent (S1552 API 31+ extraction rules vs the pre-31 backup rules)
      - assert-launcher-reset-coverage (S1540 launcher settings vs the launcher reset's field list)
      - assert-unreferenced-strings   (S1568 string keys nothing under <module>/src references)
      - assert-maestro-oracle        (S1612 Maestro flows that are green without proving anything)
      - assert-hook-inventory        (S1604 registered Claude Code hooks vs docs/AGENT_HOOKS.md)
      - assert-rule-digest-sync      (S1548 CLAUDE.md numbered rules vs the two full digests)
      - assert-gson-persistence-contract (S1639 a durable Gson model whose wire names nothing pins)
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
. (Join-Path $PSScriptRoot 'lib/gate-telemetry.ps1')

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    'pwsh'
}

# name -> extra args (beyond -Gate). Order matters: cheapest/most-deterministic first.
$gates = [ordered]@{
    'assert-no-ticket-logs.ps1'                 = @('-Quiet')
    'assert-ticket-acceptance-probes.ps1'       = @('-Quiet')
    # S1338: one entry, twelve lexical rules, ONE walk of the tree. It replaces the five
    # separate entries that each spawned a pwsh process and each re-walked app_v2/src -
    # neuroslop (nine rules), flavor-flags, public-mutable-flow and deprecated-pm-flags.
    # The individual scripts still exist as wrappers for any direct caller.
    'assert-source-gates.ps1'                   = @()
    'assert-wear-route-literals.ps1'            = @()
    'assert-listener-symmetry.ps1'              = @()
    'assert-orientation-implied-feature.ps1'    = @()
    # S1549: an activity that absorbs 'orientation' in configChanges never re-inflates on
    # rotation, so its res/layout-land variant applies only on a landscape cold start. The
    # same defect was fixed pointwise twice before anyone inventoried the project.
    'assert-orientation-layout-pairing.ps1'     = @()
    # S1568: a string resource nothing references. 397 had accumulated in values/strings.xml, each
    # one paid for again by every locale tranche. Baseline is an allowlist of NAMES, not a count, so
    # a new dead key cannot hide behind a deleted one.
    'assert-unreferenced-strings.ps1'           = @('-Quiet')
    # S1612: Maestro flow YAML vs the oracle convention. A flow that carries optional: true on its
    # proof assertion, a regex selector Maestro never matches, or a coordinate tap standing in for
    # an assertion is GREEN while proving nothing - that is how the previous generation of flows
    # became fictitious. Static YAML scan of two directories, no device and no gradle daemon.
    'assert-maestro-oracle.ps1'                 = @('-Quiet')
    # S1070: guards the tooling itself rather than app sources - a bare Write-Error under
    # EAP=Stop makes the following `exit N` unreachable, so a script's documented code
    # collapses to 1. Cheap (scans scripts/*.ps1 only) and the class has regrown 3 times.
    'assert-exit-contract.ps1'                  = @('-Quiet')
    # S1075: dev/TECH_REQUIREMENTS.md pins vs Gradle truth. Static parse of build files
    # + one doc; no gradle daemon. Catches a dependency bump that forgot the doc.
    'assert-doc-pin-drift.ps1'                  = @('-Quiet')
    # S1679: a packaging exclusion for a SHARED transitive library present in app_v2 but not in wear.
    # The pin gate above compares versions against docs and cannot express "both modules must strip
    # the same dead payload". S0385 excluded BouncyCastle's post-quantum tables in app_v2 only, and
    # wear shipped them for months - 1.2 MB, 10 % of its release APK, on a watch. Parses the two
    # build files by brace balance, no gradle daemon.
    'assert-packaging-excludes-parity.ps1'      = @('-Quiet')
    # The version fields of app_v2 and wear are one contract under one applicationId: the same
    # versionName, and versionCodes that must NOT collide - Play refuses the repeat at submission
    # time, long after the build passed. The tree carried both modules on 260815161. Reads the two
    # checked-in constants; no gradle daemon.
    'assert-module-version-parity.ps1'          = @('-Quiet')
    # S1706: ic_splash_app_brand.xml is generated per locale from the strings and one template, so a
    # hand edit to one variant compiles and renders while silently diverging from the other twelve.
    # Runs the generator in -Check mode for both modules; no gradle daemon.
    'assert-splash-brand-sync.ps1'              = @('-Quiet')
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
    # S1825: detect orphaned intermediate .flat compiled resources in merged_res/
    'assert-orphaned-merged-resources.ps1'       = @('-Quiet')
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
    # S1598: the closure facade prints a recovery hint under every failed gate, keyed by the
    # gate label. A label the hint registry never heard of fails mute, and the gap is invisible
    # until that gate next fails - which is precisely the moment the hint was needed. Reads two
    # files as text, no gradle daemon.
    'assert-gate-hints-sync.ps1'                = @()
    # S1604: five of the eleven registered hooks were documented nowhere an agent reads, and two
    # of those alter the tool call itself - one refuses a Grep/Glob, one rewrites Read input. An
    # agent refused by an undocumented guard cannot find out what refused it. Compares the two
    # settings files against docs/AGENT_HOOKS.md as text, no gradle daemon.
    'assert-hook-inventory.ps1'                 = @()
    # S1548: the two full digests of CLAUDE.md's numbered rules are written by hand and nothing
    # compared them, so copilot-instructions.md silently lacked five rules - including four whose
    # violation a hook refuses outright. Reads four markdown files as text, no gradle daemon.
    'assert-rule-digest-sync.ps1'               = @()
    # S1639: a model whose Gson JSON outlives the process, with neither @SerializedName on its fields
    # nor a keep rule holding their names. R8 renames them per build, so the writer and the reader are
    # two different mappings and the record read after an update is wrong or half-null. The class
    # reached users six times (S0719, S0737, S1630, S1631, S1632, S1638) and was fixed one model at a
    # time because nothing tied "this goes to storage" to "its names are pinned" - the two facts live
    # in different files and usually different modules. Parses both source trees and the two
    # proguard-rules.pro files, no gradle daemon.
    'assert-gson-persistence-contract.ps1'      = @('-Quiet')
    # S1674: Room, DataStore and SharedPreferences can persist enum `name` values across an
    # update. R8 must keep the matching enum fields stable or a later version cannot `valueOf`
    # the earlier value. The gate inventories those boundaries against base release rules.
    'assert-enum-persistence-contract.ps1'      = @('-Quiet')
}

# S1338: these five accept -ChangedFiles and used to be invoked with no arguments at
# all, so every one of them scanned project-wide and went red on another ticket's
# in-flight drift - a 42% FAIL rate that trained the operator to read gate output as
# noise. Forwarded only when the caller actually supplied the parameter, so a release
# or CI run keeps the strict project-wide judgement.
$changedFilesAware = @(
    'assert-source-gates.ps1',
    'assert-listener-symmetry.ps1',
    'assert-gson-persistence-contract.ps1'
)

$results = [System.Collections.Generic.List[object]]::new()
foreach ($entry in $gates.GetEnumerator()) {
    $path = Join-Path $PSScriptRoot $entry.Key
    if (-not (Test-Path $path)) {
        $results.Add([pscustomobject]@{ Gate = $entry.Key; Status = 'MISSING'; Ms = 0 })
        Write-GateTelemetryRecord -Runner 'assert-fast-gates' -Gate $entry.Key -Status 'MISSING' -ExitCode 2 -ElapsedMs 0
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
    Write-GateTelemetryRecord -Runner 'assert-fast-gates' -Gate $entry.Key -Status $status -ExitCode ([int]$LASTEXITCODE) -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
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
    Write-GateTelemetryRecord -Runner 'assert-fast-gates' -Gate 'assert-detekt.ps1' -Status $status -ExitCode ([int]$LASTEXITCODE) -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
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
