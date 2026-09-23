#requires -Version 7.0
<#
.SYNOPSIS
    S1343: every tactical step carries a non-empty `**Why:**` field (count ratchet).

.DESCRIPTION
    The tactical step form adopted by S1343 (pilot verdict `adopt`, recorded in
    dev/spec-form-pilot.jsonl) puts a mandatory `**Why:**` field between
    `**Prompt for developer:**` and `**Verification:**` in every `### Step` block of
    PLAN/<Sxxxx>_<slug>/PHASE_*.md. The field is at least one sentence of rationale
    sourced from the strategic spec, or the literal `not stated in strategic spec`
    when the strategic spec states no reason. Strategic S1343 §5.3 rules out leaving
    the form to agent memory, which is why this gate exists at all.

    Ratchet, not absolute: hundreds of phase files predate the form, so the gate pins
    the current number of Why-less steps in a checked-in baseline and fails only when
    that number grows. A new plan written from the updated template contributes zero.

    Baseline mechanism, not a HEAD diff (S1343 phase 03 correction). Every sibling
    ratchet gate scopes itself with `git diff HEAD`, which cannot work here: `PLAN/` is
    gitignored (.gitignore line 144), so no phase file has a HEAD blob to compare
    against and `git diff --name-only HEAD` returns none of them. A single-integer
    baseline file is the same contract the em-dash / stub-todo / flavor-flag gates use.

    Exit codes (S1070):
      0 - clean (count <= baseline), or audit mode (no -Gate), or baseline updated.
      1 - substantive failure: more Why-less steps than the baseline allows; or -UpdateBaseline
          would raise the baseline and no -Reason was given.
      2 - the gate itself cannot run (PLAN/ or the baseline file is missing/unreadable).
          Distinct from 1 on purpose.
      4 - Code.Scripts is held by another session, so no baseline was written. The queue place is
          held - wait for the turn in the background and rerun (S2635).

.PARAMETER Gate
    Fail-closed: exit 1 when the count exceeds the baseline.

.PARAMETER UpdateBaseline
    Lower the baseline to the current count. A rise is refused unless -Reason is given
    (S3438, contract CHECK-BASELINE rules 2 and 6): the steps it would accept are printed
    either way, so a re-freeze can never absorb new debt as a side effect of a re-run.

.PARAMETER Reason
    With -UpdateBaseline: why new Why-less steps are being accepted. Required for a rise only.

.PARAMETER Quiet
    Print only the expected/actual summary, not the per-step list.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-tactical-step-form.ps1
    pwsh -NoProfile -File scripts/quality/assert-tactical-step-form.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [string]$Reason,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')

$planRoot = Join-Path $repoRoot 'PLAN'
$baselineFile = Join-Path $PSScriptRoot 'tactical-step-form-baseline.txt'

. (Join-Path $PSScriptRoot 'lib/absent-input.ps1')

if (-not (Test-Path -LiteralPath $planRoot)) {
    # S3075: PLAN/ is gitignored, so its absence is a property of the checkout, not a finding.
    Exit-InputAbsent -Gate 'assert-tactical-step-form' -Path 'PLAN/' `
        -Reason 'gitignored - present only on a workstation checkout'
}

# A step's Why is satisfied by one sentence of prose, or by the literal
# `not stated in strategic spec`. That literal is a valid answer by design: it records
# that the rationale was never written down, which is information - unlike an invented
# reason, the S1225 repeat risk strategic S1343 §7 names as the top hazard of this form.
function Get-OffendingSteps {
    param([string[]]$Lines, [string]$RelPath)

    $offenders = [System.Collections.Generic.List[string]]::new()
    $title = $null
    $why = [System.Collections.Generic.List[string]]::new()
    $inWhy = $false
    $seenWhy = $false

    $flush = {
        if ($null -eq $title) { return }
        $text = ($why -join ' ').Trim()
        $ok = $seenWhy -and $text -and
              ($text -match '[.!?]' -or $text -eq 'not stated in strategic spec')
        if (-not $ok) { $offenders.Add("${RelPath}: $title") }
    }

    foreach ($line in $Lines) {
        if ($line -match '^### Step ') {
            & $flush
            $title = $line.TrimStart('#', ' ')
            $why.Clear(); $inWhy = $false; $seenWhy = $false
            continue
        }
        if ($null -eq $title) { continue }
        if ($line -match '^##+ ') { & $flush; $title = $null; continue }
        if ($line -match '^\*\*Why:\*\*') { $inWhy = $true; $seenWhy = $true; continue }
        if ($inWhy) {
            if ($line -match '^\*\*' -or $line -match '^---\s*$') { $inWhy = $false; continue }
            if ($line.Trim()) { $why.Add($line.Trim()) }
        }
    }
    & $flush
    return , $offenders
}

$phaseFiles = @(Get-ChildItem -LiteralPath $planRoot -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Get-ChildItem -LiteralPath $_.FullName -Filter 'PHASE_*.md' -File -ErrorAction SilentlyContinue })

$offenders = [System.Collections.Generic.List[string]]::new()
foreach ($file in $phaseFiles) {
    $rel = 'PLAN/' + $file.Directory.Name + '/' + $file.Name
    foreach ($o in (Get-OffendingSteps -Lines (Get-Content -LiteralPath $file.FullName) -RelPath $rel)) {
        $offenders.Add($o)
    }
}
$actual = $offenders.Count

if ($UpdateBaseline) {
    $previous = if (Test-Path -LiteralPath $baselineFile) { (Get-Content -LiteralPath $baselineFile -Raw).Trim() } else { 'absent' }
    $previousCount = 0
    if ([int]::TryParse($previous, [ref]$previousCount) -and $actual -gt $previousCount) {
        Write-Host "assert-tactical-step-form: -UpdateBaseline would RAISE the baseline $previousCount -> $actual. Why-less steps now:"
        foreach ($o in $offenders) { Write-Host "  $o" }
        if (-not "$Reason".Trim()) {
            Write-Host 'assert-tactical-step-form: FAIL - refusing to raise without -Reason "<why this debt is accepted>".' -ForegroundColor Red
            exit 1
        }
        Write-Host "assert-tactical-step-form: accepting the rise, reason: $Reason"
    }
    $scope = $null
    try {
        $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-tactical-step-form.ps1 -UpdateBaseline'
        Set-Content -LiteralPath $baselineFile -Value $actual -Encoding utf8NoBOM
    }
    finally { Exit-CodeLockScope -Scope $scope }
    Write-Host "assert-tactical-step-form: baseline $previous -> $actual."
    exit 0
}

if (-not (Test-Path -LiteralPath $baselineFile)) {
    Write-Error "baseline file not found at $baselineFile - run with -UpdateBaseline to seed it" -ErrorAction Continue
    exit 2
}
$raw = (Get-Content -LiteralPath $baselineFile -Raw).Trim()
$baseline = 0
if (-not [int]::TryParse($raw, [ref]$baseline)) {
    Write-Error "baseline file is not a single integer: $baselineFile" -ErrorAction Continue
    exit 2
}

Write-Host "assert-tactical-step-form: scanned $($phaseFiles.Count) phase file(s) under PLAN/."
if (-not $Quiet -and $actual -gt $baseline) {
    foreach ($o in $offenders) { Write-Host "  $o" }
}
Write-Host "expected: <= $baseline Why-less step(s) | actual: $actual"

if ($actual -gt $baseline -and $Gate) {
    Write-Host 'assert-tactical-step-form: FAIL' -ForegroundColor Red
    exit 1
}
Write-Host 'assert-tactical-step-form: PASS' -ForegroundColor Green
exit 0
