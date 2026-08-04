# Combined post-change runner.
# Chains the applicable mechanical post-change steps for a given change type.
#
# Usage:
#   pwsh -NoProfile -File scripts/post-change.ps1 `
#       -File "scripts/post-change.ps1" `
#       -Target "post-change.ps1" `
#       -Description "added change-type routing" `
#       -ChangeType Script
#
#   pwsh -NoProfile -File scripts/post-change.ps1 `
#       -File "app_v2/src/main/java/.../Foo.kt" `
#       -Target "FooClass" `
#       -Description "added bar feature" `
#       -ChangeType Kotlin `
#       [-Module app_v2]
#
#   pwsh -NoProfile -File scripts/post-change.ps1 `
#       -File "app_v2/src/main/res/values/strings.xml" `
#       -Target "string/foo_title" `
#       -Description "added foo strings" `
#       -ChangeType Xml `
#       -KeyPrefix "foo_"
#
# Backward compatibility:
#   -SkipScan + -KeyPrefix => Xml
#   -SkipScan              => Doc
#   -KeyPrefix             => Mixed
#   no router flags        => Kotlin
#
# .NOTES
#   Exit codes (S1338, CLAUDE.md Rule 7):
#     0  every gate that ran passed. The verdict line reads either
#        "post-change: PASS" or "post-change: PASS WITH ADVISORIES (n)" -
#        the latter means a gate found something it could not attribute to
#        this change, and the caller is expected to read the listed names.
#     1  a gate failed. Something was inspected and judged defective.
#     2  could not verify. Nothing was inspected, or a gate could not run:
#        an invalid/absent/unexpanded file argument, or missing tooling.
#
#   A caller must distinguish 1 from 2. "Found a defect" and "did not look"
#   are different answers, and treating 2 as success is how a green verdict
#   comes to certify nothing at all.

param(
    [Parameter(Mandatory = $true, ParameterSetName = 'Single')][string]$File,
    # S1338: a closure spans 4.34 files on average and 62% of closures span more
    # than one, so scoping the gates to a single -File certified roughly a
    # quarter of the change. Pass the whole changed set here; -File stays for
    # every existing caller and is folded into the same set.
    [Parameter(Mandatory = $true, ParameterSetName = 'Multi')][string[]]$Files,
    [Parameter(Mandatory = $true)][string]$Target,
    [Parameter(Mandatory = $true)][string]$Description,
    [ValidateSet('Doc', 'Script', 'Config', 'Kotlin', 'Xml', 'Mixed')]
    [string]$ChangeType,
    [string]$Module = "app_v2",
    [string]$KeyPrefix,
    [switch]$SkipScan,
    # S0826: per-change closure on an always-dirty tree. When set, detekt is diff-scoped to
    # -File (fails only on findings in THIS change), and every count-ratchet gate (neuroslop,
    # listener-symmetry, flavor-flag, deprecated-pm; S0848/S0850) judges a real FATAL delta on
    # the changed file (growth vs HEAD) instead of a full-project scan - other tickets' WIP no
    # longer trips them. Only icon-inventory-sync stays advisory (its re-render is repo-wide).
    # Release/CI omit the switch for the strict full project gate.
    [switch]$ScopeToFile,
    # S1338 phase 05: acknowledge the document-registry records this change touches. The
    # mandate was stated in five always-on places and obeyed at 0.6-3% of its own cadence,
    # which teaches that a mandate is optional. It is a trigger now: a closure whose changed
    # set intersects a registered document's `paths` names the matching record ids and stays
    # short of a clean PASS until they are passed back here. Accepts ids or 'all'.
    [string[]]$RegistryAck
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

# S1338: one resolved set drives every downstream consumer, so a gate can never
# silently judge a narrower slice than the verdict claims to cover. $File keeps
# working as the "primary" file for the dev-log line and the single-file gates
# that have no changed-set parameter.
# S1184: `pwsh -File` binds `-Files a.kt,b.kt` as ONE array element, so the set has to be
# comma-split here or the validation below rejects the whole CSV as a single missing path.
$rawFiles = if ($PSCmdlet.ParameterSetName -eq 'Multi') { @($Files) } else { @($File) }
$changedFiles = @(
    $rawFiles |
        ForEach-Object { ([string]$_) -split ',' } |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }
)
if (-not $File) { $File = $changedFiles[0] }

# S1338: an unexpanded shell variable used to reach the gates as a literal path
# and every gate then found nothing to complain about, so the facade printed a
# full green PASS certifying no file at all - 3 confirmed occurrences in 463
# runs. Refuse the run instead, and refuse it as "could not verify" (exit 2)
# rather than "found a defect" (exit 1): nothing was inspected.
$badArgs = @()
foreach ($candidate in $changedFiles) {
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        $badArgs += "<empty>"
        continue
    }
    if ($candidate -match '[$%]') {
        $badArgs += "$candidate (unexpanded shell variable)"
        continue
    }
    $probe = if ([System.IO.Path]::IsPathRooted($candidate)) {
        $candidate
    }
    else {
        Join-Path $root $candidate
    }
    if (-not (Test-Path $probe)) {
        $badArgs += "$candidate (not found)"
    }
}
if ($badArgs.Count -gt 0) {
    Write-Host "post-change: CANNOT VERIFY - invalid file argument(s):" -ForegroundColor Red
    foreach ($bad in $badArgs) { Write-Host "  $bad" -ForegroundColor Red }
    Write-Error "post-change: $($badArgs.Count) invalid file argument(s); nothing was inspected." -ErrorAction Continue
    exit 2
}

$pwsh = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    "pwsh"
}

$totalSw = [System.Diagnostics.Stopwatch]::StartNew()

# S1338: every advisory gate that found something lands here, so the final
# verdict can report what it could not attribute instead of swallowing it.
$script:AdvisoryFindings = @()

function Write-StepResult(
    [string]$Label,
    [ValidateSet('PASS', 'FAIL', 'SKIP')][string]$Status,
    [int]$ElapsedMs,
    [string]$Details = ''
) {
    $color = switch ($Status) {
        'PASS' { 'Green' }
        'FAIL' { 'Red' }
        default { 'DarkGray' }
    }

    $message = "  [$Label] $Status"
    if ($ElapsedMs -ge 0) {
        $message += " ($ElapsedMs ms)"
    }
    if (-not [string]::IsNullOrWhiteSpace($Details)) {
        $message += " - $Details"
    }

    Write-Host $message -ForegroundColor $color
}

function Invoke-Step([string]$Label, [scriptblock]$Action) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    try {
        $global:LASTEXITCODE = 0
        & $Action
        $exitCode = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
        if ($exitCode -ne 0) {
            throw "exit $exitCode"
        }

        $sw.Stop()
        Write-StepResult -Label $Label -Status PASS -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
    }
    catch {
        $sw.Stop()
        $exitCode = if ($LASTEXITCODE -and [int]$LASTEXITCODE -ne 0) { [int]$LASTEXITCODE } else { 1 }
        $reason = $_.Exception.Message
        if ($reason -eq "exit $exitCode") {
            $reason = "child exit code $exitCode"
        }

        Write-StepResult -Label $Label -Status FAIL -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) -Details $reason
        exit $exitCode
    }
}

function Skip-Step([string]$Label, [string]$Reason) {
    Write-StepResult -Label $Label -Status SKIP -ElapsedMs 0 -Details $Reason
}

# S0826: like Invoke-Step but non-fatal. A project-wide gate that cannot attribute its
# failure to THIS change (today only icon-inventory-sync; the count-ratchet gates moved to
# FATAL per-file deltas in S0848/S0850) is reported as a WARN under -ScopeToFile and the
# facade keeps going instead of aborting the close. The operator still sees it.
function Invoke-AdvisoryStep([string]$Label, [scriptblock]$Action, [string]$AdvisoryDetails) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $global:LASTEXITCODE = 0
        & $Action
        $exitCode = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
        $sw.Stop()
        if ($exitCode -ne 0) {
            # Most advisory gates are project-wide ratchets whose finding may belong to another
            # ticket's WIP. A caller that knows better (the preflight judges YOUR files only)
            # passes its own wording, so the verdict line never misdescribes what was found.
            $details = if ($AdvisoryDetails) { $AdvisoryDetails } else { "advisory (project-wide ratchet; not attributed to your change - verify your files manually)" }
            Write-StepResult -Label $Label -Status SKIP -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) -Details $details
            $script:AdvisoryFindings += "$Label (exit $exitCode)"
        }
        else {
            Write-StepResult -Label $Label -Status PASS -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
        }
    }
    catch {
        $sw.Stop()
        Write-StepResult -Label $Label -Status SKIP -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) -Details "advisory (gate error: $($_.Exception.Message))"
        $script:AdvisoryFindings += "$Label (gate error)"
    }
}

$resolvedChangeType = if ($PSBoundParameters.ContainsKey('ChangeType')) {
    $ChangeType
}
elseif ($SkipScan -and -not [string]::IsNullOrWhiteSpace($KeyPrefix)) {
    'Xml'
}
elseif ($SkipScan) {
    'Doc'
}
elseif (-not [string]::IsNullOrWhiteSpace($KeyPrefix)) {
    'Mixed'
}
else {
    'Kotlin'
}

# S1372: every path-keyed gate used to test the FIRST path the caller named, so a
# multi-file close silently skipped any gate whose trigger file was not first, while still printing
# a clean PASS. Two confirmed occurrences: S1363 (script-cheatsheet-sync skipped over a new .ps1)
# and S1370 (all-features skipped over docs/ALL_FEATURES.jsonl, in the same run where
# document-registry DID see it). Applicability is a property of the changed SET, not of one member.
$normChangedFiles = @($changedFiles | ForEach-Object { ($_ -replace '\\', '/') -replace '^\./', '' })
function Test-AnyChangedFile([string]$Pattern) {
    foreach ($candidate in $normChangedFiles) {
        if ($candidate -match $Pattern) { return $true }
    }
    return $false
}
function Get-FirstChangedFileMatch([string]$Pattern) {
    foreach ($candidate in $normChangedFiles) {
        if ($candidate -match $Pattern) { return $candidate }
    }
    return $null
}

$runsCatalogSync = $resolvedChangeType -in @('Kotlin', 'Mixed')
$runsStringsAudit = $resolvedChangeType -in @('Xml', 'Mixed')
$runsStringFormatGate = (($resolvedChangeType -in @('Xml', 'Mixed')) -and
    (Test-AnyChangedFile 'src/[^/]+/res/values[^/]*/strings.*\.xml$'))
$runsTicketLogAudit = $resolvedChangeType -in @('Kotlin', 'Mixed')
$runsDocPinsSync = $resolvedChangeType -in @('Config', 'Doc', 'Mixed')
# S1075: same trigger as doc-pins-sync - drift enters via a Gradle bump (Config) or a
# hand edit to dev/TECH_REQUIREMENTS.md (Doc). Checks the doc pins the generator does not own.
$runsDocPinDrift = $resolvedChangeType -in @('Config', 'Doc', 'Mixed')
$runsFlavorFlagGate = $resolvedChangeType -in @('Kotlin', 'Mixed')
# S0383 neuroslop ratchet gate. Covers Kotlin (trivial comments / swallowing catch /
# unsafe Flow collects) and Xml (hardcoded layout colors). Baselines only ratchet DOWN.
$runsNeuroslopGate = $resolvedChangeType -in @('Kotlin', 'Xml', 'Mixed')
# S1031 public-mutable-reactive-state ratchet gate. Bans a public (non-private) val/var of
# Mutable(StateFlow|LiveData|SharedFlow). Kotlin/Mixed only. Baseline ratchets DOWN.
$runsPublicMutableFlowGate = $resolvedChangeType -in @('Kotlin', 'Mixed')
# S0720 detekt + ktlint static-analysis gate. Runs :app_v2:detekt :wear:detekt over a
# committed per-module baseline (only NEW findings fail). Kotlin/Mixed only - it invokes
# gradle, so it is scoped to changes that actually touch .kt to keep other paths fast.
$runsDetektGate = $resolvedChangeType -in @('Kotlin', 'Mixed')
# S1356 detekt-baseline absorption gate. Fires when a committed detekt baseline (or its ID snapshot)
# is among the changed files. A whole-module `detektBaseline` re-freeze accepts every live finding in
# that module at once - on 2026-08-02 one absorbed the debt S1198 and S1328 were written about, and
# left no journal row, so it was found only by accident. Keyed on the WHOLE changed set rather than
# $File: a baseline is almost never the first path a caller names, and the single-file applicability
# bug is its own ticket (S1372) - do not copy that shape here.
$runsBaselineAbsorptionGate = @($changedFiles | Where-Object {
        ($_ -replace '\\', '/') -match 'config/detekt/baseline-[A-Za-z0-9_]+\.(xml|ids)$'
    }).Count -gt 0
# S0416 FGS-notification gate. Blocks the Android 16 "Bad notification for startForeground"
# crash class: ?attr-tinted notification small icons (A) and foreground-service paths that
# build a notification without ensuring their channel (B). Covers Kotlin + Xml (drawables).
$runsFgsGate = $resolvedChangeType -in @('Kotlin', 'Xml', 'Mixed')
# S0467 deprecated-PackageManager-flags gate. Keeps src/main at zero raw-int getPackageInfo /
# getApplicationInfo / queryIntentActivities / resolveActivity overloads (deprecated since API 33).
$runsPmFlagsGate = $resolvedChangeType -in @('Kotlin', 'Mixed')
# S0507 focus-highlight ratchet gate. Layout-only concern: interactive views without a visible
# focus indication (Rule 16) must never grow. Covers Xml + Mixed (layout edits). Baseline ratchets DOWN.
$runsFocusHighlightGate = $resolvedChangeType -in @('Xml', 'Mixed')
# S0489 ALL_FEATURES inventory drift gate. Fires only when the touched file is the
# inventory data, its schema, or the noLegal variant - validates the JSONL and blocks
# a silent record-count drop below the committed baseline. Narrow trigger by path.
$runsAllFeaturesGate = Test-AnyChangedFile 'docs/ALL_FEATURES.*\.(jsonl|json)$'
# S0440 settings-doc drift gate. Fires only when the touched file is a settings
# surface (settings fragment layout, the settings-search pipeline, a per-flavor
# availability module) or a settings doc artifact (manifest / annotations /
# reference). Re-runs the composite gate so a settings change that skipped
# regenerating the manifest, annotations, or reference is blocked. Narrow trigger.
$runsSettingsDocGate = (
    (Test-AnyChangedFile 'app_v2/src/main/res/layout/fragment_settings_.*\.xml$') -or
    (Test-AnyChangedFile 'app_v2/.*/ui/settings/search/') -or
    (Test-AnyChangedFile 'SettingsSearchAvailabilityModule\.kt$') -or
    (Test-AnyChangedFile 'docs/settings/') -or
    (Test-AnyChangedFile 'docs/SETTINGS_REFERENCE')
)
# S0558/S0945 settings-path drift gate. Fires when a HOW_TO or narrative guide
# (README/QUICK_START/FAQ/TROUBLESHOOTING, all locales) is edited - validates the
# embedded "Settings -> .." recipes against the manifest. Standalone (pure text, no
# gradle) so a doc edit stays fast; also runs as stage 5 of the settings-doc
# composite so a manifest/vocab change re-checks every guide.
$runsHowToPathGate = Test-AnyChangedFile 'docs/(HOW_TO|README|QUICK_START|FAQ|TROUBLESHOOTING)[A-Z_]*\.md$'
# S0815/S0939 icon-inventory drift gate. Fires for generated icon docs AND for
# settings-source files that can stale the committed inventory/legend:
# - docs/icons/**, docs/ICON_LEGEND*
# - app_v2/src/main/res/layout/fragment_settings_*.xml
# - app_v2/src/main/res/values*/strings*.xml
# Re-checks the cheap settings-source freshness scan, asset coverage, orphans,
# legend freshness, and cross-locale parity (pure text/file, no gradle). The heavy
# inventory-vs-source export test stays opt-in / CI-only, so it is NOT run here.
$runsIconInventoryGate = (
    ($resolvedChangeType -in @('Doc', 'Xml', 'Mixed')) -and
    (
        (Test-AnyChangedFile 'docs/icons/') -or
        (Test-AnyChangedFile 'docs/ICON_LEGEND') -or
        (Test-AnyChangedFile 'app_v2/src/main/res/layout/fragment_settings_.*\.xml$') -or
        (Test-AnyChangedFile 'app_v2/src/main/res/values[^/]*/strings.*\.xml$')
    )
)
# S0684 dialog-cancel-style gate. Fires only when a dialog / bottom-sheet layout is touched -
# a cancel/negative action button in such a pair must use Widget.FastMediaSorter.Button.DialogCancel,
# never a one-off cancel style. Baseline ratchets DOWN. Narrow trigger keeps it cheap.
$runsDialogCancelGate = (($resolvedChangeType -in @('Xml', 'Mixed')) -and
    (Test-AnyChangedFile 'res/layout.*/(dialog_|bottom_sheet_).*\.xml$'))
# S0721 listener symmetry gate. Runs on Kotlin or Mixed change types.
$runsListenerSymmetryGate = $resolvedChangeType -in @('Kotlin', 'Mixed')
# S0918 orientation-implied-feature gate. Fires only when a manifest is touched - an
# activity that pins screenOrientation implies a required screen.* hardware feature,
# which shrinks Google Play device reach unless src/main declares it not-required.
$runsOrientationFeatureGate = Test-AnyChangedFile 'AndroidManifest\.xml$'
# Script-cheatsheet drift gate. Fires when a repo PowerShell script (under the
# cheatsheet's discovery roots scripts/ and dev/CATALOG/scripts/) or the generated
# doc itself is touched - a changed param() block staleens docs/SCRIPT_CHEATSHEET.md
# unless regenerated. Pure AST parse + byte-diff (no gradle), so it stays cheap.
$runsScriptCheatsheetGate = (
    (Test-AnyChangedFile '(^|/)scripts/.*\.ps1$') -or
    (Test-AnyChangedFile 'docs/SCRIPT_CHEATSHEET\.md$')
)
# S1392 flavor-matrix doc-conformance gate. Fires when the flavor grid itself moves
# (app_v2/build.gradle.kts), when the generated snapshot / rendered table is touched, or when one
# of the documents carrying a checked glyph table is edited. Compares cell VALUES against
# docs/flavors/flavor-matrix.json, so a marker inverted against the gates cannot land silently -
# the failure mode that left docs/HOW_TO.md stating the opposite of the lite flags on two rows.
# Pure text plus one JSON, no gradle daemon.
$runsFlavorMatrixDocGate = (
    (Test-AnyChangedFile 'app_v2/build\.gradle\.kts$') -or
    (Test-AnyChangedFile 'docs/FLAVOR_MATRIX\.md$') -or
    (Test-AnyChangedFile 'docs/flavors/flavor-matrix\.json$') -or
    (Test-AnyChangedFile 'docs/DEV_OPS\.md$') -or
    (Test-AnyChangedFile 'docs/HOW_TO[A-Z_]*\.md$') -or
    (Test-AnyChangedFile 'scripts/(quality/flavor-matrix-docs\.psd1|quality/assert-flavor-matrix-docs\.ps1|docs/generate-flavor-matrix\.ps1)$')
)

Write-Host "post-change: $resolvedChangeType | $File -> $Target" -ForegroundColor Yellow

# S1372: keyed on the whole set - a resource file is rarely the first path a caller names, and
# resolving the source set from $File alone judges the wrong flavor in a mixed close.
$resourceSourceSet = $null
$resourceSourceSetFile = Get-FirstChangedFileMatch '^[^/]+/src/([^/]+)/res/'
if ($resourceSourceSetFile -and $resourceSourceSetFile -match '^[^/]+/src/([^/]+)/res/') {
    $resourceSourceSet = $Matches[1]
}

if ($SkipScan) {
    Write-Host "  [compat] -SkipScan is deprecated; resolved ChangeType=$resolvedChangeType" -ForegroundColor DarkGray
}

if ($runsStringsAudit) {
    # S1193: an absent -KeyPrefix used to SKIP this step, so the parity gate only ran when a caller
    # happened to pass a prefix. Nobody ever swept the whole catalog, and 21 keys reached the shipping
    # app untranslated. No prefix now means audit everything, not audit nothing.
    $auditPrefix = if ([string]::IsNullOrWhiteSpace($KeyPrefix)) { '*' } else { $KeyPrefix }
    # A strings edit under src/<flavor>/res must be audited against that flavor's locale dirs. Auditing
    # main instead would report a clean pass over files the change never touched.
    $auditSourceSet = if ($resourceSourceSet) { $resourceSourceSet } else { 'main' }
    Invoke-Step "strings-audit" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/check_strings_localized.ps1") `
            -Module $Module -SourceSet $auditSourceSet -KeyPrefix $auditPrefix
    }
}
else {
    Skip-Step "strings-audit" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsStringFormatGate) {
    Invoke-Step "string-format-gate" {
        $a = @(
            '-NoProfile',
            '-File',
            (Join-Path $root "scripts/quality/assert-string-format.ps1"),
            '-Gate',
            '-Module',
            $Module
        )
        if (-not [string]::IsNullOrWhiteSpace($resourceSourceSet)) {
            $a += @('-SourceSet', $resourceSourceSet)
        }
        & $pwsh @a
    }
}
else {
    Skip-Step "string-format-gate" "not applicable - no changed file is a strings resource file"
}

if ($runsTicketLogAudit) {
    Invoke-Step "ticket-log-audit" {
        # S1338: -Quiet suppressed exactly the File:Line list needed to fix a violation,
        # so a FAIL here reported that something was wrong and nothing about where.
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-no-ticket-logs.ps1") -Gate
    }
}
else {
    Skip-Step "ticket-log-audit" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsDocPinsSync) {
    Invoke-Step "doc-pins-sync" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/generate-toolchain-pins.ps1") -Check
    }
}
else {
    Skip-Step "doc-pins-sync" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsDocPinDrift) {
    Invoke-Step "doc-pin-drift" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-doc-pin-drift.ps1") -Gate -Quiet
    }
}
else {
    Skip-Step "doc-pin-drift" "not applicable for ChangeType $resolvedChangeType"
}

# S1356: fatal, never advisory. The whole defect was that absorbing another ticket's debt produced
# no signal at all - a warning here would reproduce it politely. Pure text, no gradle.
if ($runsBaselineAbsorptionGate) {
    Invoke-Step "detekt-baseline-absorption" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-detekt-baseline-absorption.ps1") -Gate
    }
}
else {
    Skip-Step "detekt-baseline-absorption" "not applicable - no detekt baseline among the changed files"
}

# S0826: a project-wide gate without per-file delta support runs advisory (warn, non-fatal)
# under -ScopeToFile; fatal otherwise. Since S0850 only icon-inventory-sync still uses this -
# the count-ratchet gates all judge FATAL per-file deltas.
$ratchetRunner = if ($ScopeToFile) { 'Invoke-AdvisoryStep' } else { 'Invoke-Step' }

# S0848 Phase 02: start the gradle-backed detekt gate as a background thread job BEFORE the
# fast lexical/ratchet gates, then join it after them. detekt does not depend on the lexical
# gates, so overlapping turns wall-clock from (lexical + detekt) into ~max(lexical, detekt).
# Verdict and exit code stay identical to the serial run. The try/finally below guarantees the
# job is stopped even when a lexical gate fails and Invoke-Step calls exit (verified: a finally
# runs before exit propagates), so no orphan detekt/gradle launcher survives a fail-fast close.
# S1338 phase 04 step 04.7: the lexical preflight runs BEFORE the gradle detekt gate is even
# started, so the three rules that make up most of this repo's detekt findings are reported in
# well under a second instead of after a ~23 s round-trip. Advisory by construction: it is
# lexical, it cannot see types, and assert-detekt below remains the verdict.
if ($runsDetektGate -and $changedFiles.Count -gt 0) {
    Invoke-AdvisoryStep "detekt-preflight" {
        & $pwsh '-NoProfile' '-File' (Join-Path $root "scripts/quality/detekt-preflight.ps1") `
            '-ChangedFiles' ($changedFiles -join ',') '-Gate'
    } -AdvisoryDetails 'advisory (lexical, judged on YOUR changed files - fix the lines above; the detekt gate is the verdict)'
}

$detektJob = $null
if ($runsDetektGate) {
    $detektArgs = @(
        '-NoProfile'
        '-File'
        (Join-Path $root "scripts/quality/assert-detekt.ps1")
        '-Gate'
    )
    if ($PSBoundParameters.ContainsKey('Module')) {
        $detektArgs += @('-Module', $Module)
    }
    if ($ScopeToFile) {
        # diff-scope to this change: detekt fails only on findings in -File, not on
        # other tickets' WIP that also sits above baseline on the dirty tree.
        # S1184: one comma-joined argument, never one array element per file. `pwsh -File`
        # binds only the first element to [string[]] and rejects the rest as positional,
        # which is why a multi-file closure died at the first scoped gate. Every consumer
        # comma-splits (Expand-ChangedFiles / Measure-ChangedFileGrowth).
        $detektArgs += @('-ChangedFiles', ($changedFiles -join ','))
    }
    $detektJob = Start-ThreadJob -Name 'detekt-gate' -ScriptBlock {
        param($PwshExe, $Argv)
        $out = & $PwshExe @Argv 2>&1 | Out-String
        [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $out }
    } -ArgumentList $pwsh, $detektArgs
}

try {

if ($runsFlavorFlagGate) {
    # S0848 Phase 04: under -ScopeToFile this gate now judges a real delta on the changed file
    # (growth vs HEAD) rather than an advisory full scan, so it stays FATAL - a NEW flavor flag in
    # this change fails, while other tickets' pre-existing reads no longer trip it.
    Invoke-Step "flavor-flag-gate" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-flavor-flags-not-growing.ps1"), '-Gate')
        if ($ScopeToFile) { $a += @('-ChangedFiles', ($changedFiles -join ',')) }
        & $pwsh @a
    }
}
else {
    Skip-Step "flavor-flag-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsNeuroslopGate) {
    # S0850: under -ScopeToFile every child judges a real delta on the changed file (growth vs
    # HEAD) and the gate stays FATAL - a NEW violation in this change fails, while other
    # tickets' pre-existing findings no longer trip it (mirrors flavor-flags/deprecated-pm).
    Invoke-Step "neuroslop-gate" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-neuroslop.ps1"), '-Gate')
        if ($ScopeToFile) { $a += @('-ChangedFiles', ($changedFiles -join ',')) }
        & $pwsh @a
    }
}
else {
    Skip-Step "neuroslop-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsPublicMutableFlowGate) {
    # S1031: under -ScopeToFile the gate judges a real delta on the changed file (growth vs HEAD)
    # and stays FATAL - a NEW public mutable reactive-state declaration in this change fails,
    # while other tickets' pre-existing findings no longer trip it (mirrors neuroslop).
    Invoke-Step "public-mutable-flow-gate" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-public-mutable-flow.ps1"), '-Gate')
        if ($ScopeToFile) { $a += @('-ChangedFiles', ($changedFiles -join ',')) }
        & $pwsh @a
    }
}
else {
    Skip-Step "public-mutable-flow-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsOrientationFeatureGate) {
    Invoke-Step "orientation-implied-feature-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-orientation-implied-feature.ps1") -Gate
    }
}
else {
    Skip-Step "orientation-implied-feature-gate" "not applicable - no changed file is an AndroidManifest.xml"
}

if ($runsFgsGate) {
    Invoke-Step "fgs-notification-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-fgs-notifications.ps1") -Gate
    }
}
else {
    Skip-Step "fgs-notification-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsPmFlagsGate) {
    # S0848 Phase 04: real delta on the changed file under -ScopeToFile (growth vs HEAD), so this
    # gate stays FATAL - a NEW raw-int PackageManager overload in this change fails, unrelated
    # pre-existing ones in other files do not.
    Invoke-Step "deprecated-pm-flags-gate" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-deprecated-pm-flags.ps1"), '-Gate')
        if ($ScopeToFile) { $a += @('-ChangedFiles', ($changedFiles -join ',')) }
        & $pwsh @a
    }
}
else {
    Skip-Step "deprecated-pm-flags-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsFocusHighlightGate) {
    Invoke-Step "focus-highlight-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-focus-highlight.ps1") -Gate
    }
}
else {
    Skip-Step "focus-highlight-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsDialogCancelGate) {
    Invoke-Step "dialog-cancel-style-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-dialog-cancel-style.ps1") -Gate
    }
}
else {
    Skip-Step "dialog-cancel-style-gate" "not applicable - no changed file is a dialog/bottom-sheet layout"
}

if ($runsListenerSymmetryGate) {
    # S0850: under -ScopeToFile the gate judges per-file imbalance growth vs HEAD and stays
    # FATAL - an edit that degrades symmetry in this change fails, unrelated pre-existing
    # imbalance elsewhere does not.
    Invoke-Step "listener-symmetry-gate" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-listener-symmetry.ps1"), '-Gate')
        if ($ScopeToFile) { $a += @('-ChangedFiles', ($changedFiles -join ',')) }
        & $pwsh @a
    }
}
else {
    Skip-Step "listener-symmetry-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsAllFeaturesGate) {
    Invoke-Step "all-features-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-allfeatures-sync.ps1") -Gate -Quiet
    }
}
else {
    Skip-Step "all-features-gate" "not applicable - no changed file is an ALL_FEATURES artifact"
}

if ($runsSettingsDocGate) {
    Invoke-Step "settings-doc-sync-gate" {
        # S1338 step 04.7: under -ScopeToFile hand it the changed set so its ~28 s gradle stage
        # runs only when a manifest input actually moved. Same rule as the detekt branch above -
        # an unscoped run (release, CI) keeps the strict project-wide judgement.
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-settings-doc-sync.ps1"), '-Gate')
        if ($ScopeToFile -and $changedFiles.Count -gt 0) { $a += @('-ChangedFiles', ($changedFiles -join ',')) }
        & $pwsh @a
    }
}
else {
    Skip-Step "settings-doc-sync-gate" "not applicable - no changed file is a settings surface or settings doc"
}

if ($runsHowToPathGate) {
    Invoke-Step "howto-settings-paths-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-howto-settings-paths.ps1") -Gate
    }
}
else {
    Skip-Step "howto-settings-paths-gate" "not applicable - no changed file is a HOW_TO or narrative settings-path guide"
}

if ($runsIconInventoryGate) {
    # Strict on a full run; advisory under -ScopeToFile because the legend re-render
    # reads live app strings, so unrelated string WIP on the dirty tree could show as
    # legend drift that is not attributable to this change.
    & $ratchetRunner "icon-inventory-sync-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-icon-inventory-sync.ps1") -Gate
    }
}
else {
    Skip-Step "icon-inventory-sync-gate" "not applicable - no changed file is icon docs or a settings icon/title source"
}

if ($runsScriptCheatsheetGate) {
    # Advisory under -ScopeToFile: the check regenerates from every script, so
    # unrelated script-param WIP on a dirty tree could read as cheatsheet drift
    # not attributable to this change. Strict on a full run.
    & $ratchetRunner "script-cheatsheet-sync-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-script-cheatsheet-sync.ps1") -Gate -Quiet
    }
}
else {
    Skip-Step "script-cheatsheet-sync-gate" "not applicable - no changed file is a repo script or the script cheatsheet"
}

if ($runsFlavorMatrixDocGate) {
    # Strict even under -ScopeToFile: the gate judges each declared table against the generated
    # snapshot, so its verdict is attributable to the tables named in this change and never to
    # another ticket's in-flight drift. Nothing about it is a project-wide count ratchet.
    Invoke-Step "flavor-matrix-doc-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-flavor-matrix-docs.ps1") -Gate -Quiet
    }
}
else {
    Skip-Step "flavor-matrix-doc-gate" "not applicable - no changed file is the flavor grid, the generated matrix, or a doc carrying a checked flavor table"
}

# S1338 phase 05: the document-registry trigger. Reads docs/DOCUMENT_REGISTRY.jsonl and reports
# every record whose `paths` cover a file in this change - a registered document moved, so its
# siblings (other locales, the site export, the mirrored page) may now disagree with it. Fires on
# registered paths only, never on every closure, so it stays real where it fires.
$registryPath = Join-Path $root 'docs/DOCUMENT_REGISTRY.jsonl'
if (Test-Path -LiteralPath $registryPath) {
    # `-replace '^\./'`, never `TrimStart('./')`: TrimStart takes a CHAR SET, so it ate the
    # leading dot of `.claude/commands/*.md` and every command-file edit missed its record.
    $normalizedChanged = @($changedFiles | ForEach-Object { ($_ -replace '\\', '/') -replace '^\./', '' })
    $matchedRecords = @()
    foreach ($line in (Get-Content -LiteralPath $registryPath -Encoding UTF8)) {
        $trimmed = "$line".Trim()
        if (-not $trimmed) { continue }
        try { $record = $trimmed | ConvertFrom-Json } catch { continue }
        if (-not ($record.PSObject.Properties.Name -contains 'paths')) { continue }
        # A generated document is owned by its generator and its own sync gate; asking the
        # operator to acknowledge it teaches nothing. Skipping it is why the cheatsheet does
        # not raise an advisory on every closure that changes a param block.
        if (($record.PSObject.Properties.Name -contains 'generated') -and $record.generated) { continue }
        $hitPaths = @()
        $hitPatterns = @()
        foreach ($registered in @($record.paths)) {
            $reg = ($registered -replace '\\', '/')
            foreach ($changed in $normalizedChanged) {
                if ($changed -ieq $reg -or $changed -ilike "$reg/*" -or $changed -ilike $reg) { $hitPatterns += $reg }
                # Exact path, a directory prefix, or a glob - `repository-rules` registers
                # `.claude/commands/*.md`, and a literal-only match silently missed every
                # command-file edit, which is the largest registered surface in the repo.
                if ($changed -ieq $reg -or $changed -ilike "$reg/*" -or $changed -ilike $reg) { $hitPaths += $changed }
            }
        }
        if ($hitPaths.Count -gt 0) {
            $matchedRecords += [pscustomobject]@{
                Id     = [string]$record.id
                Title  = [string]$record.title
                Files  = @($hitPaths | Select-Object -Unique)
                # Siblings are the registered entries this change did NOT touch - listing the
                # pattern that just matched as something to go and update is noise.
                Others = @(@($record.paths) | Where-Object { ($_ -replace '\\', '/') -notin $hitPatterns })
            }
        }
    }

    if ($matchedRecords.Count -eq 0) {
        Skip-Step "document-registry" "not applicable - no changed file is a registered document"
    }
    else {
        # S1340: pwsh -File does not re-split a quoted CSV into array elements (feedback_string_array_param_csv_via_file.md) -
        # split each bound element on comma too, so `-RegistryAck "a,b"` and `-RegistryAck a,b` both work from the Bash tool.
        $ackSet = @($RegistryAck | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        $ackAll = ($ackSet -contains 'all')
        $unacked = @($matchedRecords | Where-Object { -not $ackAll -and $_.Id -notin $ackSet })
        foreach ($rec in $matchedRecords) {
            Write-Host ("  registry: {0} ({1}) <- {2}" -f $rec.Id, $rec.Title, ($rec.Files -join ', '))
            if ($rec.Others.Count -gt 0) {
                Write-Host ("    siblings that may need the same edit: {0}" -f ($rec.Others -join ', '))
            }
        }
        if ($unacked.Count -eq 0) {
            Invoke-Step "document-registry" {
                Write-Host ("  acknowledged: {0}" -f (($matchedRecords | ForEach-Object { $_.Id }) -join ', '))
                $global:LASTEXITCODE = 0
            }
        }
        else {
            Invoke-AdvisoryStep "document-registry" {
                $global:LASTEXITCODE = 1
            } -AdvisoryDetails ("registered document(s) changed and not acknowledged: " +
                (($unacked | ForEach-Object { $_.Id }) -join ', ') +
                ". Read them, update the siblings listed above, then re-run with -RegistryAck '" +
                (($unacked | ForEach-Object { $_.Id }) -join ',') + "'.")
        }
    }
}
else {
    Skip-Step "document-registry" "cannot verify - docs/DOCUMENT_REGISTRY.jsonl not found"
}

# S1216: device-profile preset matrix vs AppSettings, the non-presettable registry and the applier
# branches. Runs on every change because the drift it catches is introduced by adding a settings
# field, which can land under several ChangeTypes. Advisory under -ScopeToFile like the other
# project-wide gates: it reads the whole matrix, so another ticket's settings WIP on a dirty tree
# would otherwise block this ticket's closure. Strict on a full run.
& $ratchetRunner "device-profile-matrix-gate" {
    & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-device-profile-matrix.ps1") -Gate -Quiet
}

# S0848 Phase 02: join the detekt job started before the lexical gates. Preserves the old
# inline verdict surface (failing rule lines printed, fatal on FAIL). Nulling $detektJob right
# after the drain keeps the finally cleanup a no-op once the job has already been received.
if ($runsDetektGate) {
    Invoke-Step "detekt-gate" {
        $r = Receive-Job -Job $detektJob -Wait -AutoRemoveJob
        $script:detektJob = $null
        if ($r -and -not [string]::IsNullOrWhiteSpace($r.Output)) {
            Write-Host ($r.Output.TrimEnd())
        }
        $global:LASTEXITCODE = if ($r) { [int]$r.ExitCode } else { 1 }
    }
}
else {
    Skip-Step "detekt-gate" "not applicable for ChangeType $resolvedChangeType"
}

}
finally {
    # Guarantee no orphan detekt/gradle launcher survives a fail-fast exit from a lexical gate.
    if ($detektJob) {
        try { Stop-Job -Job $detektJob -ErrorAction SilentlyContinue } catch { }
        try { Remove-Job -Job $detektJob -Force -ErrorAction SilentlyContinue } catch { }
    }
    # Tier-2 coordination lock (CLAUDE.md Rule 23): post-change.ps1 is the "logical change is
    # done" checkpoint every code-editing skill already calls, so releasing CODE.LOCK here makes
    # release automatic - skills only need to acquire it (scripts/utils/enter-code-lock.ps1)
    # before their first source edit. Safe no-op if nothing was ever acquired in this run.
    try {
        . (Join-Path $root "scripts/utils/agent-lock.ps1")
        Exit-AgentLock -Name Code
    }
    catch {
        Write-Host "  [code-lock-release] WARN - could not release CODE.LOCK: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# S1338: the two mutating steps run only after every gate has passed. Before this
# they ran first, so 430 failed closures still paid for catalog-sync and still
# wrote a changelog row - 183 duplicate rows for changes that never closed. Running
# them last also makes the facade idempotent: re-running after fixing a gate
# failure produces exactly one row, not two.
if ($runsCatalogSync) {
    Invoke-Step "catalog-sync" {
        # S0848: incremental scan - only the changed file gets a fresh git last-touched;
        # the rest reuse their prior JSONL date, avoiding a per-file `git log` storm.
        & $pwsh -NoProfile -File (Join-Path $root "scripts/catalog_sync.ps1") -Module $Module -ChangedFiles ($changedFiles -join ',')
    }
}
else {
    Skip-Step "catalog-sync" "not applicable for ChangeType $resolvedChangeType"
}

# S1338: the changelog row is written LAST, after every gate and after the only other
# mutating step. Writing it first, or even before catalog-sync, meant a run that failed
# afterwards still left a row claiming the change had closed - and a re-run then added a
# second. Last position makes "there is a row" equivalent to "the closure passed", and the
# catalog index it follows is a gitignored artifact that is safe to rebuild on a re-run.
Invoke-Step "dev-log" {
    & $pwsh -NoProfile -File (Join-Path $root "scripts/add_to_dev_log.ps1") $File $Target $Description
}

Skip-Step "feature-docs" "skill-owned; evaluate only for new public capability"
Skip-Step "functionality-log" "skill-owned; evaluate only for user-visible behaviour change"

Skip-Step "spec-catalog-sync" "skill-owned; run only on spec status transition"

$totalSw.Stop()
# S1338: the bare word PASS is reserved for a run where nothing failed and
# nothing was downgraded. An advisory gate that found something used to be
# invisible in the verdict, so the facade printed PASS on 19% of runs that
# contained a gate failure - and 66% of callers read only the tail.
$elapsedMs = [int]$totalSw.Elapsed.TotalMilliseconds
if ($script:AdvisoryFindings.Count -gt 0) {
    Write-Host ("post-change: PASS WITH ADVISORIES ($($script:AdvisoryFindings.Count)) " +
        "($resolvedChangeType, $elapsedMs ms)") -ForegroundColor Yellow
    foreach ($advisory in $script:AdvisoryFindings) {
        Write-Host "  advisory: $advisory" -ForegroundColor Yellow
    }
    Write-Host "  These gates found something but could not attribute it to this change. Verify your files." -ForegroundColor Yellow
}
else {
    Write-Host "post-change: PASS ($resolvedChangeType, $elapsedMs ms)" -ForegroundColor Green
}
exit 0
