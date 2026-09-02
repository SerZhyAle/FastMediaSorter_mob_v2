<#
.SYNOPSIS
    Deletes stale entries from the user-profile temp directory (%TEMP%).

.DESCRIPTION
    S2327: the repository's own scratch is handled by archive-temp.ps1 and prune-gradle-tmp.ps1.
    Neither touches %TEMP%, where the toolchain leaves its own residue: unpacked installer payloads,
    extracted native libraries, RDP trace files, downloaded package archives. Measured 2026-09-02 on
    this machine: 7.7 GB in 5191 files, of which 449 MB in 2063 files had not been written for over a
    week, some of it dating back to 2024 - and the directory grows about 3 GB a day.

    This is deliberately NOT framed as a disk-space fix. C: has 2 TB free; the reason to run it is
    that dead extractions accumulate without bound and every tool that enumerates %TEMP% pays for
    them. It is also NOT a cache optimisation: Windows already keeps this content in its standby
    file cache (measured 18.2 GB standby, warm reads at 2346 MB/s against 287 MB/s on first touch),
    so nothing here makes a build faster.

    Safety is the age floor and the path check, in that order:
      - Nothing younger than -OlderThanDays is considered, and the parameter itself refuses a value
        below 2 - a live build writes into %TEMP% continuously, and deleting a running run's scratch
        would turn housekeeping into the failure it exists to avoid.
      - The target must resolve inside the user profile. Pointed anywhere else - C:\Windows\Temp,
        a repository path - it refuses rather than deleting, because a mistyped -Path is the one
        input that turns this script into a disaster.
      - A file another process holds open is skipped and counted, never forced.

.PARAMETER OlderThanDays
    Age floor, by last write time. Default 7. Values below 2 are refused.

.PARAMETER Path
    Directory to clean. Defaults to $env:TEMP. Must resolve inside the user profile.

.PARAMETER DryRun
    Report what would be deleted and exit without touching the filesystem.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/clean-user-temp.ps1 -DryRun

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/clean-user-temp.ps1 -OlderThanDays 14

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - completed, or the dry run completed.
      2 - could not run: the age floor was violated, or the path is missing or outside the profile.
#>
[CmdletBinding()]
param(
    [int]$OlderThanDays = 7,
    [string]$Path,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

if ($OlderThanDays -lt 2) {
    Write-Error "clean-user-temp: refusing -OlderThanDays $OlderThanDays - a live build writes into %TEMP% continuously, so the floor is 2 days." -ErrorAction Continue
    exit 2
}

if (-not $Path) { $Path = $env:TEMP }
if (-not $Path) {
    Write-Error 'clean-user-temp: no path given and %TEMP% is not set.' -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath $Path)) {
    Write-Error "clean-user-temp: path not found - $Path" -ErrorAction Continue
    exit 2
}

$resolved = (Resolve-Path -LiteralPath $Path).Path
$profileRoot = $env:USERPROFILE
if (-not $profileRoot -or -not $resolved.StartsWith($profileRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    Write-Error "clean-user-temp: refusing to clean '$resolved' - it is outside the user profile ($profileRoot)." -ErrorAction Continue
    exit 2
}

$cutoff = (Get-Date).AddDays(-$OlderThanDays)
Write-Host ("clean-user-temp: {0}" -f $resolved)
Write-Host ("  deleting entries last written before {0:yyyy-MM-dd HH:mm}{1}" -f $cutoff, $(if ($DryRun) { ' (dry run)' } else { '' }))

$stale = @(Get-ChildItem -LiteralPath $resolved -Recurse -File -Force -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -lt $cutoff })

if ($stale.Count -eq 0) {
    Write-Host '  nothing stale - done.' -ForegroundColor Green
    exit 0
}

$staleBytes = ($stale | Measure-Object Length -Sum).Sum

if ($DryRun) {
    Write-Host ("  would delete {0:N0} file(s), {1:N0} MB" -f $stale.Count, ($staleBytes / 1MB)) -ForegroundColor Yellow
    foreach ($big in ($stale | Sort-Object Length -Descending | Select-Object -First 10)) {
        Write-Host ("    {0,8:N1} MB  {1:yyyy-MM-dd}  {2}" -f ($big.Length / 1MB), $big.LastWriteTime,
            $big.FullName.Substring($resolved.Length + 1))
    }
    exit 0
}

$freed = 0L
$deleted = 0
$held = 0
$heldNames = [System.Collections.Generic.List[string]]::new()
foreach ($file in $stale) {
    $size = $file.Length
    try {
        # A read-only attribute on an abandoned extraction is not a decision anybody made; clearing
        # it is what makes the delete succeed. A file another process HOLDS still throws, and that
        # one is left alone.
        [System.IO.File]::SetAttributes($file.FullName, [System.IO.FileAttributes]::Normal)
        [System.IO.File]::Delete($file.FullName)
        $freed += $size
        $deleted++
    }
    catch {
        $held++
        if ($heldNames.Count -lt 5) { $heldNames.Add($file.FullName.Substring($resolved.Length + 1)) }
    }
}

# Deepest first, so a directory tree that is now entirely empty collapses in one pass.
$removedDirs = 0
foreach ($dir in @(Get-ChildItem -LiteralPath $resolved -Recurse -Directory -Force -ErrorAction SilentlyContinue |
            Sort-Object { $_.FullName.Length } -Descending)) {
    try {
        if ([System.IO.Directory]::GetFileSystemEntries($dir.FullName).Count -eq 0) {
            [System.IO.Directory]::Delete($dir.FullName)
            $removedDirs++
        }
    }
    catch {
        # A directory that will not go is one something still holds. It costs nothing to leave.
    }
}

Write-Host ("  deleted {0:N0} file(s), freed {1:N0} MB, removed {2:N0} empty director(ies)." -f `
        $deleted, ($freed / 1MB), $removedDirs) -ForegroundColor Green
if ($held -gt 0) {
    Write-Host ("  skipped {0:N0} file(s) held by another process:" -f $held) -ForegroundColor Yellow
    foreach ($name in $heldNames) { Write-Host "    $name" -ForegroundColor Yellow }
}
exit 0
