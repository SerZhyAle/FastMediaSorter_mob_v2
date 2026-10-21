#requires -Version 7.0
<#
.SYNOPSIS
    Run here the checks GitHub's CI runs, so a red is found before the push and not after it.

.DESCRIPTION
    Release Step 6b of /skill-release, between the docs commit and the push of the DEBUG branch.
    Every other local check is scoped to a ticket's changed files, and the ratchet gates judge a
    per-file delta; the whole-tree state that CI judges therefore drifts unseen from ticket to
    ticket and surfaces only on GitHub, once per release. Measured 2026-10-21 on the run that
    prompted this script: five independent reds on one push (two whole-tree static gates, the
    Android lint of both modules, four unit tests - three of them stale expectations a local
    full-suite run reports in the first minutes).

    Each step is the repository's own entry point for the work the named CI job does:

      static-gates  assert-fast-gates.ps1, unscoped          Static Gates job
      lint-rules    a.ps1 flr  (:lint-rules:test)            Verify (wear + lint detectors)
      unit-wear     a.ps1 fwu  (wear unit suite)             Verify (wear + lint detectors)
      lint-wear     a.ps1 flw  (wear lintStandardDebug)      Verify (wear + lint detectors)
      unit-phone    a.ps1 fu   (app_v2 unit suite)           Verify (app_v2)
      lint-phone    a.ps1 fl   (app_v2 lintStandardDebug)    Verify (app_v2)

    EVERY step runs even after a red, as CI runs gradle with --continue: one pass names all the
    reds, where stopping at the first would cost one full pass per defect. The summary prints one
    line per step with its measured seconds. A step's full output goes to the run log under
    temp/metrics/ci-parity-runs/, not to the console.

    NOT COVERED, and the summary says so: the manifest-risk sweep over every flavor's merged
    manifest, the lite/photos/legacy assemble, the R8 release build, and anything that only a
    Linux runner on JDK 21 shows (an example is a reflective-access failure inside a TLS test,
    which passes on the Windows workstation). A green here narrows what CI can find; it does not
    replace CI.

    This is a long job (gradle). Run it in the background and read its verdict, or in the
    foreground with the longest tool timeout; never start it and move on without reading the
    exit code.

.PARAMETER Only
    Run only the named steps (comma-separated): static-gates, lint-rules, unit-wear, lint-wear,
    unit-phone, lint-phone. Absent means all six.

.PARAMETER List
    Print the plan (step, entry point, the CI job it mirrors) and exit 0 without running anything.

.PARAMETER PlanPath
    JSON file replacing the built-in plan, an array of {name, ci, args} where args is the argument
    list given to `pwsh -NoProfile`. For the contract suite; not for release use.

.PARAMETER RepoRoot
    Repository root. Defaults to the directory two levels above this script.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/run-ci-parity.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/release/run-ci-parity.ps1 -Only static-gates,lint-rules

.NOTES
    Exit codes:
      0 - every selected step exited 0
      1 - at least one selected step failed (it exited non-zero other than 2)
      2 - could not verify: an unknown step name, an unreadable plan, or a step that itself
          exited 2 (it could not verify) with no step failed
#>
[CmdletBinding()]
param(
    [string[]] $Only,
    [switch] $List,
    [string] $PlanPath,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

$plan = @(
    @{ name = 'static-gates'; ci = 'Static Gates';                                args = @('-File', 'scripts/quality/assert-fast-gates.ps1') }
    @{ name = 'lint-rules';   ci = 'Verify (wear + lint detectors)';              args = @('-File', 'a.ps1', 'flr') }
    @{ name = 'unit-wear';    ci = 'Verify (wear + lint detectors)';              args = @('-File', 'a.ps1', 'fwu') }
    @{ name = 'lint-wear';    ci = 'Verify (wear + lint detectors)';              args = @('-File', 'a.ps1', 'flw') }
    @{ name = 'unit-phone';   ci = 'Verify (app_v2 - lint + unit tests + build)'; args = @('-File', 'a.ps1', 'fu') }
    @{ name = 'lint-phone';   ci = 'Verify (app_v2 - lint + unit tests + build)'; args = @('-File', 'a.ps1', 'fl') }
)

if ($PlanPath) {
    try {
        $loaded = @(Get-Content -LiteralPath $PlanPath -Raw -Encoding utf8 | ConvertFrom-Json)
        $plan = @($loaded | ForEach-Object { @{ name = [string]$_.name; ci = [string]$_.ci; args = @($_.args | ForEach-Object { [string]$_ }) } })
    } catch {
        Write-Host "run-ci-parity: COULD NOT VERIFY - plan unreadable: $PlanPath ($($_.Exception.Message))" -ForegroundColor Yellow
        exit 2
    }
}

$selected = $plan
if ($Only) {
    $wanted = @($Only | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $known = @($plan | ForEach-Object { $_.name })
    $unknown = @($wanted | Where-Object { $known -notcontains $_ })
    if ($unknown.Count -gt 0) {
        Write-Host "run-ci-parity: COULD NOT VERIFY - unknown step(s): $($unknown -join ', '). Known: $($known -join ', ')." -ForegroundColor Yellow
        exit 2
    }
    $selected = @($plan | Where-Object { $wanted -contains $_.name })
}

if ($List) {
    foreach ($s in $selected) { Write-Host ('  {0,-13} pwsh -NoProfile {1}   <- {2}' -f $s.name, ($s.args -join ' '), $s.ci) }
    exit 0
}

$runDir = Join-Path $RepoRoot 'temp/metrics/ci-parity-runs'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$logPath = Join-Path $runDir ('{0}-{1}.log' -f (Get-Date -Format 'yyyyMMdd-HHmmss'), $PID)

Push-Location $RepoRoot
try {
    $results = foreach ($step in $selected) {
        Write-Host ("run-ci-parity: {0} .." -f $step.name)
        Add-Content -LiteralPath $logPath -Value ("===== {0}  (pwsh -NoProfile {1})" -f $step.name, ($step.args -join ' '))
        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        & pwsh -NoProfile @($step.args) *>> $logPath
        $code = $LASTEXITCODE
        $watch.Stop()
        $verdict = switch ($code) { 0 { 'PASS' } 2 { 'COULD-NOT-VERIFY' } default { 'FAIL' } }
        Add-Content -LiteralPath $logPath -Value ("===== {0} exit {1} ({2:n0} s)" -f $step.name, $code, $watch.Elapsed.TotalSeconds)
        [pscustomobject]@{ Name = $step.name; Ci = $step.ci; Code = $code; Verdict = $verdict; Seconds = [int]$watch.Elapsed.TotalSeconds }
    }
} finally {
    Pop-Location
}

Write-Host ''
Write-Host 'run-ci-parity summary:'
foreach ($r in $results) {
    $color = switch ($r.Verdict) { 'PASS' { 'Green' } 'FAIL' { 'Red' } default { 'Yellow' } }
    Write-Host ('  {0,-17} {1,-13} exit {2,-3} {3,5} s   <- {4}' -f $r.Verdict, $r.Name, $r.Code, $r.Seconds, $r.Ci) -ForegroundColor $color
}
Write-Host "  log: $logPath"
Write-Host '  not covered here: manifest-risk sweep (every flavor), lite/photos/legacy assemble, R8 release build, Linux/JDK 21-only behaviour.'

$failed = @($results | Where-Object { $_.Verdict -eq 'FAIL' })
$unverified = @($results | Where-Object { $_.Verdict -eq 'COULD-NOT-VERIFY' })
if ($failed.Count -gt 0) {
    Write-Host ("run-ci-parity: FAIL - {0} of {1} step(s) red: {2}. Fix them before the push; the log names each failure." -f $failed.Count, @($results).Count, (($failed | ForEach-Object Name) -join ', ')) -ForegroundColor Red
    exit 1
}
if ($unverified.Count -gt 0) {
    Write-Host ("run-ci-parity: COULD NOT VERIFY - {0} step(s) exited 2: {1}." -f $unverified.Count, (($unverified | ForEach-Object Name) -join ', ')) -ForegroundColor Yellow
    exit 2
}
Write-Host ("run-ci-parity: PASS ({0} of {0} steps)." -f @($results).Count) -ForegroundColor Green
exit 0
