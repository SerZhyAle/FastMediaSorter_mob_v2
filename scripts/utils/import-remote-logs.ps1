#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Imports the newest diagnostic-log archive dropped by an externally-tested device into logs/.

.DESCRIPTION
    The app's built-in "export logs" action (LogExportHelper.kt) zips every on-device
    fastmediasorter_*.log file and shares it out. When the app is tested on a remote/external
    device (not connected via adb to this machine), that share is routed through Google Drive,
    which lands the archive in a locally synced drop folder (default C:\GD\temp).

    The archive's filename is NOT a reliable signal: it comes from the share intent's
    EXTRA_SUBJECT text (export_logs_subject string), which is locale-dependent (EN/RU/UK - the
    device may be running any locale) and Google Drive appends " (1)", " (2)", etc. on repeat
    drops. It may also arrive with no file extension at all. Selection is therefore by
    recency + zip validity + content only - never by name.

    Algorithm: scan SourceDir top-level (never recursive - it is a general-purpose drop folder
    with unrelated personal files, not a logs-only directory), newest-first by LastWriteTime.
    Open each candidate as a zip (central-directory read only - cheap even for a large unrelated
    .zip elsewhere in the folder) and take the first one containing at least one *.log entry.

    Extraction into DestDir is additive and dedup-aware: an entry already present with the same
    byte length is left untouched and reported as "unchanged" (the export re-bundles the
    device's full on-device log history every time, so most entries in any given drop have
    already been imported and analysed in a prior run). Missing or size-different entries are
    (re)written and reported as new/updated - callers should feed only that set to further
    analysis (e.g. /log-reader), not the full archive.

.PARAMETER SourceDir
    Drop folder to scan for the archive. Default: C:\GD\temp

.PARAMETER DestDir
    Destination for extracted log files. Default: logs (repo-relative)

.PARAMETER ArchivePath
    Skip auto-detection and import this exact archive (any name/extension).

.PARAMETER MaxCandidates
    Cap on how many newest files in SourceDir are probed for zip-ness before giving up.

.PARAMETER Json
    Emit a single JSON result object on stdout; all human-readable text is suppressed.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/import-remote-logs.ps1
    Auto-detect the newest diagnostic archive in C:\GD\temp, extract new/updated sessions into logs/.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/import-remote-logs.ps1 -ArchivePath "C:\GD\temp\Log export (3)" -Json
    Import one specific archive, bypassing auto-detection, machine-readable output.

.NOTES
    Exit codes (stable):
      0 - OK (archive found and processed; zero new files is still OK - see data.newFiles)
      1 - SourceDir does not exist
      2 - no zip-with-.log-entries candidate found (or -ArchivePath is not a valid such zip)
      3 - extraction failure (I/O error writing to DestDir)
#>
[CmdletBinding()]
param(
    [string]$SourceDir = 'C:\GD\temp',
    [string]$DestDir = 'logs',
    [string]$ArchivePath,
    [int]$MaxCandidates = 25,
    [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression.FileSystem

$script:result = [ordered]@{
    ok              = $false
    exitCode        = 0
    archive         = $null
    archiveModified = $null
    destDir         = $null
    newFiles        = @()
    updatedFiles    = @()
    unchangedFiles  = @()
    reason          = $null
}

function Write-Line {
    param([string]$Text, [string]$Color = 'White')
    if (-not $Json) { Write-Host $Text -ForegroundColor $Color }
}

function Emit-Ok {
    $script:result.ok = $true
    if ($Json) { $script:result | ConvertTo-Json -Compress -Depth 6 }
    exit 0
}

function Fail {
    param([int]$Code, [string]$Reason)
    $script:result.exitCode = $Code
    $script:result.reason = $Reason
    if ($Json) {
        $script:result | ConvertTo-Json -Compress -Depth 6
    } else {
        Write-Host "FAIL ($Code) - $Reason" -ForegroundColor Red
    }
    exit $Code
}

# Try to open $Path as a zip and confirm it carries at least one *.log entry.
# Returns the opened ZipArchive on success (caller must Dispose), $null on any failure -
# a failure here just means "not our archive", not a hard error.
function Test-LogArchive {
    param([string]$Path)
    try {
        $zipArchive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    } catch {
        return $null
    }
    $hasLog = $zipArchive.Entries | Where-Object { $_.Name -like '*.log' } | Select-Object -First 1
    if (-not $hasLog) {
        $zipArchive.Dispose()
        return $null
    }
    return $zipArchive
}

# ---------- resolve archive ----------

$zip = $null
$archiveFile = $null

if ($ArchivePath) {
    if (-not (Test-Path -LiteralPath $ArchivePath -PathType Leaf)) {
        Fail 2 "ArchivePath not found: $ArchivePath"
    }
    $archiveFile = Get-Item -LiteralPath $ArchivePath
    $zip = Test-LogArchive -Path $archiveFile.FullName
    if (-not $zip) {
        Fail 2 "ArchivePath is not a readable zip with .log entries: $ArchivePath"
    }
} else {
    if (-not (Test-Path -LiteralPath $SourceDir -PathType Container)) {
        Fail 1 "SourceDir not found: $SourceDir"
    }
    $candidates = Get-ChildItem -LiteralPath $SourceDir -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First $MaxCandidates
    foreach ($candidate in $candidates) {
        $zip = Test-LogArchive -Path $candidate.FullName
        if ($zip) { $archiveFile = $candidate; break }
    }
    if (-not $zip) {
        Fail 2 "No zip archive with .log entries found among the $($candidates.Count) newest files in $SourceDir"
    }
}

Write-Line "Archive:  $($archiveFile.FullName)" 'Cyan'
Write-Line "Modified: $($archiveFile.LastWriteTime)" 'Cyan'

$script:result.archive = $archiveFile.FullName
$script:result.archiveModified = $archiveFile.LastWriteTime.ToString('o')

# ---------- extract new/updated entries ----------

if (-not (Test-Path -LiteralPath $DestDir)) {
    New-Item -ItemType Directory -Path $DestDir -Force | Out-Null
}
$destFull = (Get-Item -LiteralPath $DestDir).FullName
$script:result.destDir = $destFull

$newFiles = @()
$updatedFiles = @()
$unchangedFiles = @()

try {
    foreach ($entry in $zip.Entries) {
        if ($entry.Name -notlike '*.log') { continue }
        $destPath = Join-Path $destFull $entry.Name
        $exists = Test-Path -LiteralPath $destPath -PathType Leaf
        if ($exists) {
            $existingLength = (Get-Item -LiteralPath $destPath).Length
            if ($existingLength -eq $entry.Length) {
                $unchangedFiles += $entry.Name
                continue
            }
            $updatedFiles += $entry.Name
        } else {
            $newFiles += $entry.Name
        }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
    }
} catch {
    $zip.Dispose()
    Fail 3 "Extraction failed: $($_.Exception.Message)"
}
$zip.Dispose()

$script:result.newFiles = $newFiles
$script:result.updatedFiles = $updatedFiles
$script:result.unchangedFiles = $unchangedFiles

Write-Line "New:       $($newFiles.Count)" 'Green'
foreach ($f in $newFiles) { Write-Line "  + $f" 'Green' }
Write-Line "Updated:   $($updatedFiles.Count)" 'Yellow'
foreach ($f in $updatedFiles) { Write-Line "  ~ $f" 'Yellow' }
Write-Line "Unchanged: $($unchangedFiles.Count)" 'DarkGray'

Emit-Ok
