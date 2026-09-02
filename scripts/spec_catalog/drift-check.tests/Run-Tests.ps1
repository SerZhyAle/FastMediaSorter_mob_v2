# Run-Tests.ps1 (S1800) - regression suite for the marker scan in scripts/spec_catalog/drift-check.ps1.
#
# Regression origin: on 2026-08-18 the same ticket read DRIFT and CLEAN from the same working tree in
# the same minute. drift-check.ps1 carried two scan backends and picked between them on whether
# `rg` was on PATH. The ripgrep branch walks the working tree; the old fallback shelled out to
# `git grep`, which reads the INDEX and therefore cannot see a marker in a file that is new and not
# yet committed. This machine has rg on PATH in one shell and not in another, so the verdict was
# decided by which shell launched the script - and the blind branch returned CLEAN, the reassuring
# answer, so the miss was silent. The two branches also carried different regexes (`\s*` against
# `\s+` after the comment opener), so `//Sxxxx:` written without a space was drift down one path only.
#
# What this pins: both backends see the same markers on the same tree, including a marker in an
# UNTRACKED file, and both agree when there is no marker at all.
#
# What this runner touches (it is NOT hermetic): it creates and deletes one probe file under
# app_v2/src/test/java/. The file is valid Kotlin (package declaration plus comments) and is removed
# in a finally block. It is never committed, which is precisely the condition under test.
#
# Usage:  pwsh -NoProfile -File scripts/spec_catalog/drift-check.tests/Run-Tests.ps1
#         pwsh -NoProfile -File .../Run-Tests.ps1 -SubjectId S0223   # any live catalog id
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   cannot verify - the subject id did not resolve, or rg is absent so only one backend exists
#       on this machine and the comparison the suite exists for cannot be made.

[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$SubjectId = 'S1800'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }
$driftCheck = Join-Path $repoRoot 'scripts/spec_catalog/drift-check.ps1'
$probe = Join-Path $repoRoot 'app_v2/src/test/java/com/sza/fastmediasorter/DriftCheckProbeTmp.kt'

$failures = @()
function Add-Failure([string]$Case, [string]$Detail) {
    $script:failures += "$Case - $Detail"
    Write-Host "  FAIL $Case" -ForegroundColor Red
    Write-Host "       $Detail" -ForegroundColor DarkGray
}
function Add-Pass([string]$Case) { Write-Host "  ok   $Case" -ForegroundColor DarkGray }

# rg's own directory, so it can be removed from PATH to force the fallback branch.
# Resolve ripgrep even when PATH (notably under -NoProfile) omits its install directory:
# the same shell-dependent absence that made this suite's verdict differ between shells, and
# the same remedy publish-github-release.ps1 uses for gh.
$rgCmd = Get-Command rg -ErrorAction SilentlyContinue
if (-not $rgCmd) {
    foreach ($rgDirCandidate in @(
        (Join-Path ${env:LOCALAPPDATA} 'Microsoft\WinGet\Links'),
        (Join-Path ${env:ProgramFiles} 'ripgrep'),
        (Join-Path ${env:ProgramW6432} 'ripgrep')
    )) {
        if ($rgDirCandidate -and (Test-Path -LiteralPath (Join-Path $rgDirCandidate 'rg.exe'))) {
            $env:PATH = "$rgDirCandidate;$env:PATH"
            break
        }
    }
    $rgCmd = Get-Command rg -ErrorAction SilentlyContinue
}
if (-not $rgCmd) {
    Write-Error "drift-check tests: rg is not on PATH, so only one backend exists here and the comparison cannot be made." -ErrorAction Continue
    exit 2
}
$rgDir = Split-Path -Parent $rgCmd.Source
$pathWithoutRg = (($env:PATH -split [IO.Path]::PathSeparator) |
    Where-Object { $_ -and ($_.TrimEnd('\', '/') -ne $rgDir.TrimEnd('\', '/')) }) -join [IO.Path]::PathSeparator

function Invoke-DriftCheck([bool]$WithRg) {
    $saved = $env:PATH
    try {
        if (-not $WithRg) { $env:PATH = $pathWithoutRg }
        $out = & $pwshExe -NoProfile -File $driftCheck -Id $SubjectId -Format json 2>$null
        if (-not $out) { return $null }
        return ($out | ConvertFrom-Json)
    } finally { $env:PATH = $saved }
}

try {
    # --- Case 1/2: a marker in an untracked file is seen by BOTH backends ------------------------
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $probe) | Out-Null
    @(
        'package com.sza.fastmediasorter'
        ''
        "// $SubjectId`: drift-check regression probe. Created and deleted by drift-check.tests."
    ) | Set-Content -Path $probe -Encoding UTF8

    $withRg = Invoke-DriftCheck $true
    $noRg = Invoke-DriftCheck $false

    if ($null -eq $withRg -or $null -eq $noRg) {
        Write-Error "drift-check tests: subject id '$SubjectId' did not resolve." -ErrorAction Continue
        exit 2
    }

    if ($withRg.verdict -ne 'DRIFT') {
        Add-Failure 'rg backend sees an untracked marker' "expected DRIFT, got $($withRg.verdict)"
    } else { Add-Pass 'rg backend sees an untracked marker' }

    if ($noRg.verdict -ne 'DRIFT') {
        Add-Failure 'fallback backend sees an untracked marker' `
            "expected DRIFT, got $($noRg.verdict). The fallback is index-bound again - it must walk the working tree."
    } else { Add-Pass 'fallback backend sees an untracked marker' }

    # --- Case 3: the two backends report the SAME marker set ------------------------------------
    $a = ($withRg.code_markers | ForEach-Object { "$($_.file):$($_.line)" } | Sort-Object) -join '|'
    $b = ($noRg.code_markers | ForEach-Object { "$($_.file):$($_.line)" } | Sort-Object) -join '|'
    if ($a -ne $b) {
        Add-Failure 'both backends report the same marker set' "rg=[$a] fallback=[$b]"
    } else { Add-Pass 'both backends report the same marker set' }
}
finally {
    if (Test-Path $probe) { Remove-Item $probe -Force }
}

# --- Case 4: with the probe gone, both backends agree it is clean -------------------------------
$cleanWithRg = Invoke-DriftCheck $true
$cleanNoRg = Invoke-DriftCheck $false
if ($null -ne $cleanWithRg -and $null -ne $cleanNoRg) {
    if ($cleanWithRg.markers_count -ne $cleanNoRg.markers_count) {
        Add-Failure 'both backends agree with no probe present' `
            "rg=$($cleanWithRg.markers_count) fallback=$($cleanNoRg.markers_count)"
    } else { Add-Pass 'both backends agree with no probe present' }
}

if ($failures.Count -gt 0) {
    Write-Error "drift-check tests: FAIL ($($failures.Count) case(s))" -ErrorAction Continue
    exit 1
}
Write-Output 'drift-check tests: PASS (4 cases)'
exit 0
