#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Imports the newest diagnostic-log archive dropped by an externally-tested device into logs/.

.DESCRIPTION
    The app's built-in "export logs" action (LogExportHelper.kt) zips every on-device
    fastmediasorter_*.log file and shares it out. When the app is tested on a remote/external
    device (not connected via adb to this machine), that share is routed through Google Drive,
    which lands the archive in a locally synced drop folder (the RemoteLogs artifact sink).

    The archive's filename is NOT a reliable signal: it comes from the share intent's
    EXTRA_SUBJECT text (export_logs_subject string), which is locale-dependent (EN/RU/UK - the
    device may be running any locale) and Google Drive appends " (1)", " (2)", etc. on repeat
    drops. It may also arrive with no file extension at all. Selection is therefore by
    recency + zip validity + content only - never by name.

    Algorithm: scan SourceDir top-level (never recursive - it is a general-purpose drop folder
    with unrelated personal files, not a logs-only directory), newest-first by LastWriteTime.
    Open each candidate as a zip (central-directory read only - cheap even for a large unrelated
    .zip elsewhere in the folder) and take the first one containing at least one *.log entry.

    Extraction into DestDir is additive and content-deduped: an entry whose SHA-256 is already
    present is left untouched and reported as "unchanged". A same-name entry with different
    content is retained under a SHA-256 suffix instead of overwriting the earlier session.
    Callers should feed only newly extracted paths to further analysis (e.g. /log-reader), not
    the full archive.

.PARAMETER SourceDir
    Drop folder to scan for the archive. Defaults to the resolved RemoteLogs artifact sink
    (override with FMS_SINK_REMOTE_LOGS).

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
    Auto-detect the newest diagnostic archive in the drop folder, extract new/updated
    sessions into logs/.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/import-remote-logs.ps1 -ArchivePath "$env:FMS_SINK_REMOTE_LOGS\Log export (3)" -Json
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
    [string]$SourceDir,
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

function Get-Sha256FromStream {
    param([System.IO.Stream]$Stream)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([Convert]::ToHexString($sha256.ComputeHash($Stream))).ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

function Get-ZipEntrySha256 {
    param([System.IO.Compression.ZipArchiveEntry]$Entry)
    $stream = $Entry.Open()
    try {
        return Get-Sha256FromStream -Stream $stream
    } finally {
        $stream.Dispose()
    }
}

function Get-CollisionFileName {
    param([string]$EntryName, [string]$Sha256)
    $stem = [System.IO.Path]::GetFileNameWithoutExtension($EntryName)
    $extension = [System.IO.Path]::GetExtension($EntryName)
    return "$stem--$Sha256$extension"
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
    if (-not $SourceDir) {
        . "$PSScriptRoot\project-paths.ps1"
        $SourceDir = Get-ArtifactSink -Kind RemoteLogs -Quiet
    }
    if (-not $SourceDir -or -not (Test-Path -LiteralPath $SourceDir -PathType Container)) {
        Fail 1 ("SourceDir not found: " +
            $(if ($SourceDir) { $SourceDir } else { 'the RemoteLogs sink (set FMS_SINK_REMOTE_LOGS)' }))
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

$knownContent = @{}
Get-ChildItem -LiteralPath $destFull -Filter '*.log' -File | ForEach-Object {
    $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not $knownContent.ContainsKey($hash)) {
        $knownContent[$hash] = $_.Name
    }
}

$newFiles = @()
$updatedFiles = @()
$unchangedFiles = @()

try {
    foreach ($entry in $zip.Entries) {
        if ($entry.Name -notlike '*.log') { continue }
        $entryHash = Get-ZipEntrySha256 -Entry $entry
        if ($knownContent.ContainsKey($entryHash)) {
            $unchangedFiles += $knownContent[$entryHash]
            continue
        }

        $destName = $entry.Name
        $destPath = Join-Path $destFull $destName
        if (Test-Path -LiteralPath $destPath -PathType Leaf) {
            $destName = Get-CollisionFileName -EntryName $entry.Name -Sha256 $entryHash
            $destPath = Join-Path $destFull $destName
        }

        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
        $knownContent[$entryHash] = $destName
        $newFiles += $destName
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
