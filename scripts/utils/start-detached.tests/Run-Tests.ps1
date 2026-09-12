<#
.SYNOPSIS
    Regression suite for scripts/utils/start-detached.ps1 (S2400).
.DESCRIPTION
    Pins the launcher's contract:
      A. a detached `pwsh -Command 'Start-Sleep 2; exit 7'` returns at once with a log path, -Status
         reads `running` before the marker exists and `exit 7` after, and the child's stdout is in
         the log.
      B. a missing command exits 2; a missing .ps1 exits 2.
      C. the log lands under temp/scratch/ with no ticket and under temp/Sxxxx/ with one.
      D. -Status on a log that does not exist exits 1.
      E. -OutDir (S2406) puts the log and the marker under that directory instead of the bucket.
    Artifacts are written under temp/S2400/ and temp/scratch/ and removed at the end.

    Exit codes:
      0  all cases pass.
      1  at least one case failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }
$launcher = Join-Path $repoRoot 'scripts/utils/start-detached.ps1'

$script:pass = 0
$script:fail = 0
function Assert-That([string]$name, [bool]$condition, [string]$detail = '') {
    if ($condition) { $script:pass++; Write-Host "  PASS  $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FAIL  $name`n        $detail" -ForegroundColor Red }
}
function Invoke-Launcher([string[]]$argv) {
    $out = & $pwshExe -NoProfile -File $launcher @argv 2>&1 | Out-String
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = $out }
}
function Get-LogPath([string]$text) {
    if ($text -match 'log=(\S+)') { return (Join-Path $repoRoot $Matches[1]) }
    return $null
}
function Wait-Marker([string]$logPath, [int]$seconds) {
    $done = [System.IO.Path]::ChangeExtension($logPath, '.done')
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $done) { return $true }
        Start-Sleep -Milliseconds 250
    }
    return $false
}

$created = [System.Collections.Generic.List[string]]::new()
try {
    Write-Host "start-detached.ps1 regression suite" -ForegroundColor Cyan

    # A. a real detached child with a known exit code and a known stdout line.
    $a = Invoke-Launcher @('-Command', 'pwsh', '-Arguments', '-NoProfile -Command "Write-Output s2400-marker; Start-Sleep -Seconds 2; exit 7"', '-Ticket', 'S2400', '-Label', 'suite-a')
    Assert-That "A1. launcher returns 0 at once" ($a.Code -eq 0) "exit $($a.Code): $($a.Text)"
    $logA = Get-LogPath $a.Text
    Assert-That "A2. a log path is printed" ($null -ne $logA) $a.Text
    if ($logA) {
        $created.Add($logA)
        $relA = $logA.Substring($repoRoot.Length).TrimStart('\', '/')
        $s1 = Invoke-Launcher @('-Status', $relA)
        Assert-That "A3. -Status reads running before the marker" ($s1.Code -eq 0 -and $s1.Text.Trim() -eq 'running') "exit $($s1.Code): $($s1.Text)"
        $arrived = Wait-Marker $logA 20
        Assert-That "A4. the marker arrives after the child exits" $arrived "no .done within 20 s"
        $s2 = Invoke-Launcher @('-Status', $relA)
        Assert-That "A5. -Status reads exit 7 from the marker" ($s2.Code -eq 0 -and $s2.Text.Trim() -eq 'exit 7') "exit $($s2.Code): $($s2.Text)"
        $logText = Get-Content -LiteralPath $logA -Raw
        Assert-That "A6. the child's stdout is in the log" ($logText -match 's2400-marker') $logText
        Assert-That "A7. no .err sidecar is left behind" (-not (Test-Path ([System.IO.Path]::ChangeExtension($logA, '.err'))))
        Assert-That "C2. ticket log lands under temp/S2400/" ($relA -replace '\\', '/' -like 'temp/S2400/detached-suite-a-*.log') $relA
    }

    # B. refusals happen in the caller, where they can be seen.
    $b1 = Invoke-Launcher @('-Command', 'no-such-command-s2400')
    Assert-That "B1. unknown command exits 2" ($b1.Code -eq 2 -and $b1.Text -match 'not found') "exit $($b1.Code): $($b1.Text)"
    $b2 = Invoke-Launcher @('-Command', 'scripts/utils/no-such-script-s2400.ps1')
    Assert-That "B2. missing .ps1 exits 2" ($b2.Code -eq 2 -and $b2.Text -match 'not found') "exit $($b2.Code): $($b2.Text)"

    # C. no ticket -> scratch; also exercises the .ps1 route.
    $c = Invoke-Launcher @('-Command', 'scripts/utils/start-detached.ps1', '-Arguments', '-Status nope', '-Label', 'suite-c')
    Assert-That "C1a. .ps1 route starts" ($c.Code -eq 0) "exit $($c.Code): $($c.Text)"
    $logC = Get-LogPath $c.Text
    if ($logC) {
        $created.Add($logC)
        $relC = $logC.Substring($repoRoot.Length).TrimStart('\', '/')
        Assert-That "C1b. scratch log lands under temp/scratch/" ($relC -replace '\\', '/' -like 'temp/scratch/detached-suite-c-*.log') $relC
        $arrivedC = Wait-Marker $logC 20
        $sC = Invoke-Launcher @('-Status', $relC)
        Assert-That "C1c. the inner script's exit 1 reaches the marker" ($arrivedC -and $sC.Text.Trim() -eq 'exit 1') "arrived=$arrivedC status=$($sC.Text)"
    }

    # D. status on nothing.
    $d = Invoke-Launcher @('-Status', 'temp/scratch/detached-does-not-exist.log')
    Assert-That "D1. -Status on a missing log exits 1" ($d.Code -eq 1) "exit $($d.Code): $($d.Text)"

    # E. -OutDir wins over the ticket bucket (S2406: the monitor writer keeps everything in one directory).
    $e = Invoke-Launcher @('-Command', 'pwsh', '-Arguments', '-NoProfile -Command "exit 0"', '-Ticket', 'S2400', '-OutDir', 'temp/S2400/outdir-case', '-Label', 'suite-e')
    Assert-That "E1. launcher with -OutDir returns 0" ($e.Code -eq 0) "exit $($e.Code): $($e.Text)"
    $logE = Get-LogPath $e.Text
    if ($logE) {
        $created.Add($logE)
        $relE = ($logE.Substring($repoRoot.Length).TrimStart('\', '/')) -replace '\\', '/'
        Assert-That "E2. the log lands under -OutDir, not under the ticket bucket" ($relE -like 'temp/S2400/outdir-case/detached-suite-e-*.log') $relE
        $arrivedE = Wait-Marker $logE 20
        Assert-That "E3. the marker lands beside it" ($arrivedE -and (Test-Path ([System.IO.Path]::ChangeExtension($logE, '.done')))) "arrived=$arrivedE"
    }
}
finally {
    foreach ($log in $created) {
        foreach ($ext in '.log', '.done', '.err') {
            $p = [System.IO.Path]::ChangeExtension($log, $ext)
            if (Test-Path -LiteralPath $p) { Remove-Item -LiteralPath $p -Force -ErrorAction SilentlyContinue }
        }
    }
    Remove-Item -LiteralPath (Join-Path $repoRoot 'temp/S2400/outdir-case') -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("start-detached.tests: {0} passed, {1} failed" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail) { 'Red' } else { 'Green' })
if ($script:fail -gt 0) { exit 1 }
exit 0
