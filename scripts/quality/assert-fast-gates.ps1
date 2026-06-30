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
      - assert-detekt                (only with -IncludeDetekt; honours -ChangedFiles)

    Each child runs as its own process so a child `exit` cannot kill this aggregator.

    Modes:
      (default)      Run each gate in -Gate mode; print per-gate PASS/FAIL; exit 1 if any failed.
      -IncludeDetekt Also run the (slow) gradle detekt gate.
      -ChangedFiles  Passed through to detekt for diff-scoped judgement (see assert-detekt).

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
    'assert-no-ticket-logs.ps1'           = @('-Quiet')
    'assert-flavor-flags-not-growing.ps1' = @()
    'assert-neuroslop.ps1'                = @()
    'assert-deprecated-pm-flags.ps1'      = @()
    'assert-listener-symmetry.ps1'        = @()
}

$results = [System.Collections.Generic.List[object]]::new()
foreach ($entry in $gates.GetEnumerator()) {
    $path = Join-Path $PSScriptRoot $entry.Key
    if (-not (Test-Path $path)) {
        $results.Add([pscustomobject]@{ Gate = $entry.Key; Status = 'MISSING'; Ms = 0 })
        continue
    }
    $extraArgs = @($entry.Value)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    & $pwshExe -NoProfile -File $path -Gate @extraArgs | Write-Host
    $sw.Stop()
    $status = ($LASTEXITCODE -eq 0) ? 'PASS' : 'FAIL'
    $results.Add([pscustomobject]@{ Gate = $entry.Key; Status = $status; Ms = [int]$sw.Elapsed.TotalMilliseconds })
}

if ($IncludeDetekt) {
    $detektArgs = @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'assert-detekt.ps1'), '-Gate')
    if ($PSBoundParameters.ContainsKey('Module')) { $detektArgs += @('-Module', $Module) }
    if ($ChangedFiles) { $detektArgs += '-ChangedFiles'; $detektArgs += $ChangedFiles }
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
