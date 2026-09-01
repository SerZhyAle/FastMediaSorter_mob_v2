<#
.SYNOPSIS
    Post-release housekeeping facade - prunes, archives and compresses every temp surface at once.

.DESCRIPTION
    S2327: three scripts already existed for three different piles of scratch and NONE of them was
    called by any pipeline - each had to be remembered by hand, so none of them ran. A release is the
    natural moment to run all three: the version is out, its scratch is finished with, and no ticket
    of that release is still reading its own artifacts.

    The stages, in the order a release wants them:
      1. gradle-tmp   - build-JVM leftovers under temp/gradle-tmp (prune-gradle-tmp.ps1).
      2. repo temp/   - stale ticket scratch and loose artifacts moved to temp/archive/<stamp>/
                        (archive-temp.ps1). Live tickets keep their artifacts; nothing is deleted.
      3. archive zip  - an archive stamp older than -CompressArchivesOlderThanDays becomes one .zip
                        and its directory goes. This is the only stage that compresses: the moved
                        artifacts are kept for a reader, and a reader who needs a two-month-old
                        logcat can open a zip.
      4. %TEMP%       - the user profile's own residue, by age (clean-user-temp.ps1). Outside the
                        repository, so it is the one stage that DELETES rather than archives.

    A stage that cannot run does not stop the ones after it: they are independent piles, and a
    missing gradle-tmp says nothing about whether %TEMP% needs sweeping. Every stage's own verdict is
    printed, and the exit code reports whether any of them failed.

.PARAMETER OlderThanDays
    Age floor handed to archive-temp.ps1 and clean-user-temp.ps1. Default 7.

.PARAMETER CompressArchivesOlderThanDays
    Age floor for zipping a temp/archive/<stamp> directory. Default 30. Zero disables the stage.

.PARAMETER SkipUserTemp
    Leave %TEMP% alone. The one stage that deletes outside the repository, so it has its own off
    switch for a machine where something else owns that directory.

.PARAMETER DryRun
    Report what every stage would do and change nothing.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/post-release-cleanup.ps1 -DryRun

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - every stage that ran completed.
      1 - at least one stage failed; the failing stage is named.
      2 - could not run: the repository root does not carry the stage scripts.
#>
[CmdletBinding()]
param(
    [int]$OlderThanDays = 7,
    [int]$CompressArchivesOlderThanDays = 30,
    [switch]$SkipUserTemp,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pwshExe = (Get-Process -Id $PID).Path
$failures = [System.Collections.Generic.List[string]]::new()

$pruneCli = Join-Path $PSScriptRoot 'prune-gradle-tmp.ps1'
$archiveCli = Join-Path $PSScriptRoot 'archive-temp.ps1'
$userTempCli = Join-Path $PSScriptRoot 'clean-user-temp.ps1'
foreach ($cli in @($pruneCli, $archiveCli, $userTempCli)) {
    if (-not (Test-Path -LiteralPath $cli)) {
        Write-Error "post-release-cleanup: stage script not found - $cli" -ErrorAction Continue
        exit 2
    }
}

function Invoke-Stage {
    param([string]$Name, [string[]]$Argv, [switch]$SuppressWhatIf)

    Write-Host ''
    Write-Host "== $Name ==" -ForegroundColor Cyan
    if ($SuppressWhatIf) {
        # -WhatIf prints one line per candidate, and gradle-tmp holds thousands. The stage's own
        # closing summary already says how many entries matched, so a release step reads it instead
        # of scrolling past 3000 lines nobody checks one by one.
        $lines = @(& $pwshExe @Argv 2>&1 | Where-Object { "$_" -notmatch '^What if:' })
        $lines | ForEach-Object { Write-Host $_ }
    }
    else {
        & $pwshExe @Argv
    }
    $code = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
    # Exit 2 is "could not look", not "found a problem" - a machine without temp/gradle-tmp has
    # nothing to prune, and reporting that as a failed release step teaches nobody anything.
    if ($code -eq 2) {
        Write-Host "  $Name - nothing to do (stage reported it could not run)." -ForegroundColor DarkGray
        return
    }
    if ($code -ne 0) {
        Write-Host "  $Name - FAILED (exit $code)" -ForegroundColor Red
        $failures.Add("$Name (exit $code)")
    }
}

if ($DryRun) {
    Invoke-Stage 'gradle-tmp' @('-NoProfile', '-File', $pruneCli, '-WhatIf') -SuppressWhatIf
}
else {
    Invoke-Stage 'gradle-tmp' @('-NoProfile', '-File', $pruneCli)
}

$archiveArgs = @('-NoProfile', '-File', $archiveCli, '-OlderThanDays', "$OlderThanDays")
if ($DryRun) { $archiveArgs += '-DryRun' }
Invoke-Stage 'repo temp/' $archiveArgs

# ---- archive compression -----------------------------------------------------------------
if ($CompressArchivesOlderThanDays -gt 0) {
    Write-Host ''
    Write-Host '== archive zip ==' -ForegroundColor Cyan
    $archiveRoot = Join-Path $repoRoot 'temp/archive'
    if (-not (Test-Path -LiteralPath $archiveRoot)) {
        Write-Host '  no temp/archive yet - nothing to compress.' -ForegroundColor DarkGray
    }
    else {
        $cutoff = (Get-Date).AddDays(-$CompressArchivesOlderThanDays)
        $stamps = @(Get-ChildItem -LiteralPath $archiveRoot -Directory -Force -ErrorAction SilentlyContinue |
                Where-Object { $_.LastWriteTime -lt $cutoff })
        if ($stamps.Count -eq 0) {
            Write-Host ("  no archive stamp older than {0:yyyy-MM-dd}." -f $cutoff) -ForegroundColor DarkGray
        }
        foreach ($stamp in $stamps) {
            $zipPath = "$($stamp.FullName).zip"
            if (Test-Path -LiteralPath $zipPath) {
                Write-Host "  $($stamp.Name) - a zip of that name already exists; left alone." -ForegroundColor Yellow
                continue
            }
            $before = (Get-ChildItem -LiteralPath $stamp.FullName -Recurse -File -Force -ErrorAction SilentlyContinue |
                    Measure-Object Length -Sum).Sum
            if ($DryRun) {
                Write-Host ("  would compress {0} ({1:N1} MB)" -f $stamp.Name, ($before / 1MB)) -ForegroundColor Yellow
                continue
            }
            try {
                Compress-Archive -Path (Join-Path $stamp.FullName '*') -DestinationPath $zipPath -CompressionLevel Optimal -ErrorAction Stop
                # The directory only goes once the zip exists and is readable. A compression that
                # half-succeeded must never be the reason an artifact is gone.
                $null = [System.IO.Compression.ZipFile]::OpenRead($zipPath).Dispose()
                [System.IO.Directory]::Delete($stamp.FullName, $true)
                $after = (Get-Item -LiteralPath $zipPath).Length
                Write-Host ("  {0}: {1:N1} MB -> {2:N1} MB" -f $stamp.Name, ($before / 1MB), ($after / 1MB)) -ForegroundColor Green
            }
            catch {
                Write-Host "  $($stamp.Name) - FAILED: $($_.Exception.Message)" -ForegroundColor Red
                $failures.Add("archive zip/$($stamp.Name)")
            }
        }
    }
}

if ($SkipUserTemp) {
    Write-Host ''
    Write-Host '== user %TEMP% == skipped (-SkipUserTemp)' -ForegroundColor DarkGray
}
else {
    $userArgs = @('-NoProfile', '-File', $userTempCli, '-OlderThanDays', "$OlderThanDays")
    if ($DryRun) { $userArgs += '-DryRun' }
    Invoke-Stage 'user %TEMP%' $userArgs
}

Write-Host ''
if ($failures.Count -gt 0) {
    Write-Error ("post-release-cleanup: FAIL - {0}" -f ($failures -join ', ')) -ErrorAction Continue
    exit 1
}
Write-Host ("post-release-cleanup: PASS{0}" -f $(if ($DryRun) { ' (dry run - nothing was changed)' } else { '' })) -ForegroundColor Green
exit 0
