#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for scripts/utils/reap-abandoned-script-processes.ps1 (S2610).

.DESCRIPTION
    The reaper's whole risk is the false positive: it kills process trees, and the live processes it
    must never touch look YOUNGER and CHEAPER than the abandoned ones. Measured on this workstation
    2026-09-05 - three live queue-runner windows at 150 minutes and 2.6-3.2 s of CPU, three
    abandoned ones at 475 minutes and 5.4-5.8 s - so a threshold on age or CPU selects the wrong
    population, and only the structural exclusions separate them. Each is asserted here against a
    process this suite starts itself, never against whatever happens to be running.

    Every case runs the reaper with -OlderThanMinutes 0 so the verdict is about the exclusions and
    not about waiting an hour, and reads the -Json payload rather than the printed table.

Exit codes: 0 = every case passed; 1 = a case failed; 2 = the reaper could not be run, so nothing
            was verified.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$reaper = Join-Path $repoRoot 'scripts\utils\reap-abandoned-script-processes.ps1'
if (-not (Test-Path -LiteralPath $reaper)) {
    Write-Host "reap tests: reaper not found at $reaper" -ForegroundColor Red
    exit 2
}
$probe = Join-Path $PSScriptRoot 'fixtures\probe.ps1'
$supervise = Join-Path $PSScriptRoot 'fixtures\supervise.ps1'
$pwshPath = (Get-Command pwsh).Source

$spawned = [System.Collections.Generic.List[object]]::new()

function Start-Fixture {
    param([string]$Arguments, [bool]$HoldStdin)
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $pwshPath
    $psi.Arguments = $Arguments
    $psi.WorkingDirectory = $repoRoot
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    if ($HoldStdin) {
        # The runtime's shape: a stdin pipe opened and never closed, which is what turns the
        # mandatory-parameter prompt from a question into a permanent block.
        $psi.RedirectStandardInput = $true
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
    }
    $p = [System.Diagnostics.Process]::Start($psi)
    $script:spawned.Add($p)
    return $p
}

$failures = @()
function Assert-Verdict {
    param([string]$Case, [int]$ProcessId, [string]$Expected, $Payload)
    $isCandidate = @($Payload.candidates | Where-Object { $_.pid -eq $ProcessId }).Count -gt 0
    $isProtected = @($Payload.protected | Where-Object { $_.pid -eq $ProcessId }).Count -gt 0
    $actual = if ($isCandidate) { 'candidate' } elseif ($isProtected) { 'protected' } else { 'absent' }
    if ($actual -eq $Expected) {
        Write-Host "  PASS  $Case - $actual" -ForegroundColor Green
    }
    else {
        Write-Host "  FAIL  $Case - expected $Expected, got $actual (pid $ProcessId)" -ForegroundColor Red
        $script:failures += $Case
    }
}

try {
    # Case 1 is the defect itself: a repository script blocked on a mandatory-parameter prompt.
    $hung = Start-Fixture -Arguments "-NoProfile -File `"$probe`"" -HoldStdin $true
    # Cases 2 and 3 are long-lived by design and must survive the reaper.
    $noExit = Start-Fixture -Arguments "-NoProfile -NoExit -File `"$probe`" -Required x" -HoldStdin $false
    $loop = Start-Fixture -Arguments "-NoProfile -File `"$probe`" -Required x -Loop" -HoldStdin $false
    # Case 4 is idle itself but supervising live work.
    $parent = Start-Fixture -Arguments "-NoProfile -File `"$supervise`"" -HoldStdin $false

    # Let the fixtures reach the state under test - the hang must be blocked at binding, and the
    # supervisor must have started its child - before anything is classified.
    Start-Sleep -Seconds 6

    $json = & $pwshPath -NoProfile -File $reaper -OlderThanMinutes 0 -Json
    if ($LASTEXITCODE -ne 0) {
        Write-Host "reap tests: reaper exited $LASTEXITCODE" -ForegroundColor Red
        exit 2
    }
    $payload = $json | ConvertFrom-Json

    Assert-Verdict -Case 'hung on a mandatory parameter is reaped' -ProcessId $hung.Id -Expected 'candidate' -Payload $payload
    Assert-Verdict -Case '-NoExit console window is spared' -ProcessId $noExit.Id -Expected 'protected' -Payload $payload
    Assert-Verdict -Case '-Loop long-running script is spared' -ProcessId $loop.Id -Expected 'protected' -Payload $payload
    Assert-Verdict -Case 'supervisor of a live child is spared' -ProcessId $parent.Id -Expected 'protected' -Payload $payload
}
finally {
    foreach ($p in $spawned) {
        try { if (-not $p.HasExited) { $p.Kill($true) } } catch { }
    }
}

if ($failures.Count -gt 0) {
    Write-Host "reap tests: $($failures.Count) case(s) failed." -ForegroundColor Red
    exit 1
}
Write-Host "reap tests: all cases passed." -ForegroundColor Green
exit 0
