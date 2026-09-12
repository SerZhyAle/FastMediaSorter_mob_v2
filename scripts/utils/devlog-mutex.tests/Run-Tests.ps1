#requires -Version 7.0
<#
.SYNOPSIS
    S3022 - contract suite for scripts/utils/devlog-mutex.ps1.

.DESCRIPTION
    The helper exists to take the SAME mutex the canon dev-log writer takes, so a repo-side
    whole-file rewrite of dev/CHANGELOG.md is serialised against every session's appends. That
    name is the entire contract, and it is duplicated across a boundary this repository may not
    edit (CLAUDE.md Rule 8): the writer keeps it in a private function of a script that appends a
    row the moment it is dot-sourced, so the formula cannot be imported and had to be restated.

    A canon-side rename would therefore leave two different mutexes, both working, serialising
    nothing against each other - silent by construction, and its only symptom is the flake S3022
    was opened for, which cost a 983-second release-scope run to notice. So case A derives the
    writer's name from the INSTALLED writer's own source text rather than restating the formula a
    second time, and fails when the two disagree.

    Nothing here touches dev/CHANGELOG.md or the repository's real mutex: every case uses a sample
    root, so the blocking case cannot stall a sibling session's closure.

.NOTES
    Exit codes:
    0   all cases pass.
    1   at least one case failed.
    2   could not verify - the canon harness is not installed, so case A has nothing to compare
        against. An absent harness is an incomplete environment, not a broken helper.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$helper = Join-Path $repoRoot 'scripts/utils/devlog-mutex.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

function Resolve-CanonDevLogWriter {
    # The forwarders' own candidate walk, in their order: an explicit harness root, then the
    # newest plugin version in the cache, then a canon checkout. Versions sort as versions, not
    # as strings - a string sort puts 2026.1001.1 below 2026.903.1 (see any forwarder's header).
    $candidates = @()
    if ($env:SZA_HARNESS_ROOT) { $candidates += $env:SZA_HARNESS_ROOT }
    $cache = Join-Path $env:USERPROFILE '.claude\plugins\cache\sza-unified-rules\sza'
    if (Test-Path -LiteralPath $cache) {
        $candidates += @(Get-ChildItem -LiteralPath $cache -Directory -ErrorAction SilentlyContinue |
            ForEach-Object {
                $parsed = $null
                [void][version]::TryParse($_.Name, [ref]$parsed)
                [pscustomobject]@{ Path = $_.FullName; Version = $parsed }
            } | Sort-Object @{ Expression = { $null -ne $_.Version }; Descending = $true },
                            @{ Expression = { $_.Version }; Descending = $true },
                            @{ Expression = { $_.Path }; Descending = $true } |
            ForEach-Object { Join-Path $_.Path 'tools\harness' })
    }
    if ($env:SZA_CANON_ROOT) { $candidates += (Join-Path $env:SZA_CANON_ROOT 'tools\harness') }
    foreach ($dir in $candidates) {
        $probe = Join-Path $dir 'devlog\add_to_dev_log.ps1'
        if (Test-Path -LiteralPath $probe) { return $probe }
    }
    return $null
}

function Get-FunctionText([string]$Path, [string]$Name) {
    # Harness functions are declared at column 0 and closed by a '}' at column 0, so the block is
    # read by that shape rather than by parsing - the point is to run the WRITER's own lines, not
    # to restate them here, which is the whole reason this case exists.
    $lines = [System.IO.File]::ReadAllLines($Path)
    $start = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^function\s+$([regex]::Escape($Name))\b") { $start = $i; break }
    }
    if ($start -lt 0) { return $null }
    for ($j = $start + 1; $j -lt $lines.Count; $j++) {
        if ($lines[$j] -eq '}') { return ($lines[$start..$j] -join "`n") }
    }
    return $null
}

. $helper

Write-Host 'devlog-mutex contract suite' -ForegroundColor Cyan

# --- A: the helper's name is the canon writer's name, derived from the writer's own source. ---
Write-Host 'A: the mutex name matches the installed canon writer' -ForegroundColor Yellow
$writer = Resolve-CanonDevLogWriter
if (-not $writer) {
    Write-Host '  devlog-mutex tests: COULD NOT VERIFY - the SZA harness is not installed' -ForegroundColor Yellow
    Write-Host '  Install or update it:  claude plugin update sza@sza-unified-rules' -ForegroundColor Yellow
    exit 2
}
Write-Host "  writer: $writer" -ForegroundColor DarkGray
$canonText = Get-FunctionText -Path $writer -Name 'Get-DevLogMutexName'
if (-not $canonText) {
    Write-Host "  devlog-mutex tests: COULD NOT VERIFY - Get-DevLogMutexName is not declared in $writer" -ForegroundColor Yellow
    exit 2
}

# Two roots: one arbitrary, one this checkout. The arbitrary one proves the formula rather than a
# coincidence of the real path, and a space plus mixed case exercise the lowercasing.
foreach ($sample in @('C:\Sample Repo\Mixed Case', $repoRoot)) {
    $probe = [scriptblock]::Create($canonText + "`nGet-DevLogMutexName -RepoRoot " + "'" + $sample.Replace("'", "''") + "'")
    $canonName = & $probe   # child scope: the writer's function does not leak into this suite
    $ourName = Get-DevLogMutexName -RepoRoot $sample
    Assert-That "A1 '$sample'" ($canonName -eq $ourName) "canon='$canonName' ours='$ourName'"
}

# --- B: the shape the helper promises - a Global mutex name with a 32-hex checkout hash. ---
Write-Host 'B: the name has the shape a system mutex accepts' -ForegroundColor Yellow
$name = Get-DevLogMutexName -RepoRoot $repoRoot
Assert-That 'B1 Global prefix and 32 hex digits' ($name -match '^Global\\FMS-DevLog-[0-9A-F]{32}$') "name=$name"
Assert-That 'B2 case does not change the name' ((Get-DevLogMutexName -RepoRoot $repoRoot.ToUpperInvariant()) -eq $name) 'upper-cased root produced a different name'

# --- C: a second process actually waits. A name that matches but does not serialise is the same
# lost update with more ceremony, so the mutex is exercised rather than inspected. ---
Write-Host 'C: a second process blocks until the holder releases' -ForegroundColor Yellow
$sampleRoot = 'C:\sample\devlog-mutex-tests'   # never the repository's own mutex
$holderBody = @"
. '$helper'
Enter-DevLogWriteLock -RepoRoot '$sampleRoot'
[System.IO.File]::WriteAllText('{MARKER}', 'held')
Start-Sleep -Seconds 3
Exit-DevLogWriteLock
"@
$sandbox = Join-Path ([System.IO.Path]::GetTempPath()) ("devlog-mutex-" + [Guid]::NewGuid().ToString().Substring(0, 8))
New-Item -ItemType Directory -Path $sandbox -Force | Out-Null
try {
    $marker = Join-Path $sandbox 'held.txt'
    $holderPath = Join-Path $sandbox 'holder.ps1'
    Set-Content -LiteralPath $holderPath -Value $holderBody.Replace('{MARKER}', $marker.Replace('\', '\\')) -Encoding utf8NoBOM
    $holder = Start-Process -FilePath $pwshExe -PassThru -WindowStyle Hidden `
        -ArgumentList @('-NoProfile', '-File', $holderPath)
    try {
        $deadline = (Get-Date).AddSeconds(20)
        while (-not (Test-Path -LiteralPath $marker) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 50 }
        if (-not (Test-Path -LiteralPath $marker)) {
            Assert-That 'C1 the holder took the lock' $false 'the holder never reported holding it'
        } else {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            Enter-DevLogWriteLock -RepoRoot $sampleRoot
            $sw.Stop()
            Exit-DevLogWriteLock
            # The holder sleeps 3 s from the marker. A waiter that returns in well under a second
            # took a different mutex; one that waits at all took the same one.
            Assert-That 'C1 the waiter blocked until the holder released' ($sw.Elapsed.TotalSeconds -gt 1.5) "waited $([math]::Round($sw.Elapsed.TotalSeconds, 2))s"
        }
    }
    finally {
        if (-not $holder.HasExited) { $holder.Kill() }
    }

    # --- D: releasing what was never taken is the shape a finally block needs. ---
    Write-Host 'D: Exit-DevLogWriteLock is safe when the lock was never taken' -ForegroundColor Yellow
    $threw = $false
    try { Exit-DevLogWriteLock; Exit-DevLogWriteLock } catch { $threw = $true }
    Assert-That 'D1 no throw' (-not $threw) 'Exit-DevLogWriteLock threw on an unheld lock'
}
finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
if ($script:fail -eq 0) {
    Write-Host "devlog-mutex tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "devlog-mutex tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
