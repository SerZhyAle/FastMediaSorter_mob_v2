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

param(
    [Parameter(Mandatory = $true)][string]$File,
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
    [switch]$ScopeToFile
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$pwsh = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    "pwsh"
}

$totalSw = [System.Diagnostics.Stopwatch]::StartNew()

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
function Invoke-AdvisoryStep([string]$Label, [scriptblock]$Action) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $global:LASTEXITCODE = 0
        & $Action
        $exitCode = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
        $sw.Stop()
        if ($exitCode -ne 0) {
            Write-StepResult -Label $Label -Status SKIP -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) -Details "advisory (project-wide ratchet; not attributed to your change - verify your files manually)"
        }
        else {
            Write-StepResult -Label $Label -Status PASS -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
        }
    }
    catch {
        $sw.Stop()
        Write-StepResult -Label $Label -Status SKIP -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) -Details "advisory (gate error: $($_.Exception.Message))"
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

$runsCatalogSync = $resolvedChangeType -in @('Kotlin', 'Mixed')
$runsStringsAudit = $resolvedChangeType -in @('Xml', 'Mixed')
$runsTicketLogAudit = $resolvedChangeType -in @('Kotlin', 'Mixed')
$runsDocPinsSync = $resolvedChangeType -in @('Config', 'Doc', 'Mixed')
$runsFlavorFlagGate = $resolvedChangeType -in @('Kotlin', 'Mixed')
# S0383 neuroslop ratchet gate. Covers Kotlin (trivial comments / swallowing catch /
# unsafe Flow collects) and Xml (hardcoded layout colors). Baselines only ratchet DOWN.
$runsNeuroslopGate = $resolvedChangeType -in @('Kotlin', 'Xml', 'Mixed')
# S0720 detekt + ktlint static-analysis gate. Runs :app_v2:detekt :wear:detekt over a
# committed per-module baseline (only NEW findings fail). Kotlin/Mixed only - it invokes
# gradle, so it is scoped to changes that actually touch .kt to keep other paths fast.
$runsDetektGate = $resolvedChangeType -in @('Kotlin', 'Mixed')
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
$runsAllFeaturesGate = (($File -replace '\\', '/') -match 'docs/ALL_FEATURES.*\.(jsonl|json)$')
# S0440 settings-doc drift gate. Fires only when the touched file is a settings
# surface (settings fragment layout, the settings-search pipeline, a per-flavor
# availability module) or a settings doc artifact (manifest / annotations /
# reference). Re-runs the composite gate so a settings change that skipped
# regenerating the manifest, annotations, or reference is blocked. Narrow trigger.
$normFile = ($File -replace '\\', '/')
$runsSettingsDocGate = (
    $normFile -match 'app_v2/src/main/res/layout/fragment_settings_.*\.xml$' -or
    $normFile -match 'app_v2/.*/ui/settings/search/' -or
    $normFile -match 'SettingsSearchAvailabilityModule\.kt$' -or
    $normFile -match 'docs/settings/' -or
    $normFile -match 'docs/SETTINGS_REFERENCE'
)
# S0558 HOW_TO settings-path drift gate. Fires when a HOW_TO guide is edited -
# validates the embedded "Settings -> .." recipes against the manifest. Standalone
# (pure text, no gradle) so a doc edit stays fast; also runs as stage 5 of the
# settings-doc composite so a manifest/vocab change re-checks the guides.
$runsHowToPathGate = ($normFile -match 'docs/HOW_TO.*\.md$')
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
        $normFile -match 'docs/icons/' -or
        $normFile -match 'docs/ICON_LEGEND' -or
        $normFile -match 'app_v2/src/main/res/layout/fragment_settings_.*\.xml$' -or
        $normFile -match 'app_v2/src/main/res/values[^/]*/strings.*\.xml$'
    )
)
# S0684 dialog-cancel-style gate. Fires only when a dialog / bottom-sheet layout is touched -
# a cancel/negative action button in such a pair must use Widget.FastMediaSorter.Button.DialogCancel,
# never a one-off cancel style. Baseline ratchets DOWN. Narrow trigger keeps it cheap.
$runsDialogCancelGate = (($resolvedChangeType -in @('Xml', 'Mixed')) -and
    ($normFile -match 'res/layout.*/(dialog_|bottom_sheet_).*\.xml$'))
# S0721 listener symmetry gate. Runs on Kotlin or Mixed change types.
$runsListenerSymmetryGate = $resolvedChangeType -in @('Kotlin', 'Mixed')
# S0918 orientation-implied-feature gate. Fires only when a manifest is touched - an
# activity that pins screenOrientation implies a required screen.* hardware feature,
# which shrinks Google Play device reach unless src/main declares it not-required.
$runsOrientationFeatureGate = ($normFile -match 'AndroidManifest\.xml$')

Write-Host "post-change: $resolvedChangeType | $File -> $Target" -ForegroundColor Yellow

if ($SkipScan) {
    Write-Host "  [compat] -SkipScan is deprecated; resolved ChangeType=$resolvedChangeType" -ForegroundColor DarkGray
}

Invoke-Step "dev-log" {
    & $pwsh -NoProfile -File (Join-Path $root "scripts/add_to_dev_log.ps1") $File $Target $Description
}

Skip-Step "feature-docs" "skill-owned; evaluate only for new public capability"
Skip-Step "functionality-log" "skill-owned; evaluate only for user-visible behaviour change"

if ($runsCatalogSync) {
    Invoke-Step "catalog-sync" {
        # S0848: incremental scan - only the changed file gets a fresh git last-touched;
        # the rest reuse their prior JSONL date, avoiding a per-file `git log` storm.
        & $pwsh -NoProfile -File (Join-Path $root "scripts/catalog_sync.ps1") -Module $Module -ChangedFiles $File
    }
}
else {
    Skip-Step "catalog-sync" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsStringsAudit) {
    if (-not [string]::IsNullOrWhiteSpace($KeyPrefix)) {
        Invoke-Step "strings-audit" {
            & $pwsh -NoProfile -File (Join-Path $root "scripts/check_strings_localized.ps1") -Module $Module -KeyPrefix $KeyPrefix
        }
    }
    else {
        Skip-Step "strings-audit" "ChangeType $resolvedChangeType requires -KeyPrefix"
    }
}
else {
    Skip-Step "strings-audit" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsTicketLogAudit) {
    Invoke-Step "ticket-log-audit" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-no-ticket-logs.ps1") -Gate -Quiet
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
        $detektArgs += @('-ChangedFiles', $File)
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
        if ($ScopeToFile) { $a += @('-ChangedFiles', $File) }
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
        if ($ScopeToFile) { $a += @('-ChangedFiles', $File) }
        & $pwsh @a
    }
}
else {
    Skip-Step "neuroslop-gate" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsOrientationFeatureGate) {
    Invoke-Step "orientation-implied-feature-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-orientation-implied-feature.ps1") -Gate
    }
}
else {
    Skip-Step "orientation-implied-feature-gate" "not applicable - touched file is not an AndroidManifest.xml"
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
        if ($ScopeToFile) { $a += @('-ChangedFiles', $File) }
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
    Skip-Step "dialog-cancel-style-gate" "not applicable - touched file is not a dialog/bottom-sheet layout"
}

if ($runsListenerSymmetryGate) {
    # S0850: under -ScopeToFile the gate judges per-file imbalance growth vs HEAD and stays
    # FATAL - an edit that degrades symmetry in this change fails, unrelated pre-existing
    # imbalance elsewhere does not.
    Invoke-Step "listener-symmetry-gate" {
        $a = @('-NoProfile', '-File', (Join-Path $root "scripts/quality/assert-listener-symmetry.ps1"), '-Gate')
        if ($ScopeToFile) { $a += @('-ChangedFiles', $File) }
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
    Skip-Step "all-features-gate" "not applicable - touched file is not an ALL_FEATURES artifact"
}

if ($runsSettingsDocGate) {
    Invoke-Step "settings-doc-sync-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-settings-doc-sync.ps1") -Gate
    }
}
else {
    Skip-Step "settings-doc-sync-gate" "not applicable - touched file is not a settings surface or settings doc"
}

if ($runsHowToPathGate) {
    Invoke-Step "howto-settings-paths-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-howto-settings-paths.ps1") -Gate
    }
}
else {
    Skip-Step "howto-settings-paths-gate" "not applicable - touched file is not a HOW_TO guide"
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
    Skip-Step "icon-inventory-sync-gate" "not applicable - touched file is not icon docs or a settings icon/title source"
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

Skip-Step "spec-catalog-sync" "skill-owned; run only on spec status transition"

$totalSw.Stop()
Write-Host "post-change: PASS ($resolvedChangeType, $([int]$totalSw.Elapsed.TotalMilliseconds) ms)" -ForegroundColor Green
exit 0
