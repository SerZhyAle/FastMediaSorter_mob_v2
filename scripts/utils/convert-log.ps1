#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Converts Android log files of any supported format to unified standard logcat text.

.DESCRIPTION
    Supports three input formats produced by Android Studio and device tools:

    FORMAT 1 — LOGCAT (standard)
      Produced by: Android Studio Logcat copy-paste or adb logcat
      Pattern: YYYY-MM-DD HH:MM:SS.mmm PID-TID TAG PKG LVL  MESSAGE
      Example: 2026-04-20 00:52:13.639 25733-25733 ApplicationLoaders com.example D  text

    FORMAT 2 — JSON (.logcat)
      Produced by: Android Studio "Export to File" → saves as .logcat (JSON)
      Structure: { "metadata": {...}, "logcatMessages": [ { "header": {...}, "message": "..." } ] }
      header fields: logLevel (DEBUG/INFO/WARN/ERROR/VERBOSE/FATAL), pid, tid,
                     applicationId, processName, tag, timestamp { seconds, nanos }

    FORMAT 3 — TIMBER (app device export)
      Produced by: Timber FileTree or app's own log export to /sdcard/
      Pattern: YYYY-MM-DD HH:MM:SS.mmm LVL/TAG: MESSAGE
      Example: 2026-04-19 21:17:55.057 I/App: Starting network monitor

    Output is always FORMAT 1 (standard logcat text), compatible with search-log.ps1.

.PARAMETER InputFile
    Path to source log file (any format). Auto-detected.

.PARAMETER OutputFile
    Path to write normalized logcat text. Defaults to temp/<name>.normalized.log

.PARAMETER AppId
    Package name to inject as PKG for Timber logs (default: com.sza.fastmediasorter).

.EXAMPLES
    # Convert .logcat JSON → standard text
    .\scripts\utils\convert-log.ps1 -InputFile "logs/export.logcat"

    # Convert Timber log → standard text
    .\scripts\utils\convert-log.ps1 -InputFile "logs/fastmediasorter_20260419.log"

    # Specify output path
    .\scripts\utils\convert-log.ps1 -InputFile "logs/export.logcat" -OutputFile "temp/converted.log"

    # Then analyse with search-log.ps1
    .\scripts\utils\search-log.ps1 -LogFile "temp/converted.log" -Errors
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$InputFile,

    [string]$OutputFile = "",
    [string]$AppId = "com.sza.fastmediasorter"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ─── Resolve input file ──────────────────────────────────────────────────────
if (-not [System.IO.Path]::IsPathRooted($InputFile)) {
    $InputFile = Join-Path (Get-Location) $InputFile
}
if (-not (Test-Path $InputFile)) {
    Write-Error "Input file not found: $InputFile"
    exit 1
}

# ─── Resolve output file ─────────────────────────────────────────────────────
if ($OutputFile -eq "") {
    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($InputFile)
    $tempDir = Join-Path (Get-Location) "temp"
    if (-not (Test-Path $tempDir)) { New-Item -ItemType Directory -Path $tempDir -Force | Out-Null }
    $OutputFile = Join-Path $tempDir "$baseName.normalized.log"
}

# ─── Format detection ─────────────────────────────────────────────────────────
# Reads first few lines to identify the format.
function Get-LogFormat([string]$path) {
    $preview = Get-Content -Path $path -TotalCount 10
    $firstNonEmpty = $preview | Where-Object { $_.Trim() -ne "" } | Select-Object -First 1
    if (-not $firstNonEmpty) { return "UNKNOWN" }

    # JSON: file starts with { (Android Studio .logcat export)
    if ($firstNonEmpty.Trim().StartsWith("{")) { return "JSON" }

    # TIMBER: YYYY-MM-DD HH:MM:SS.mmm LVL/TAG: MSG  (no PID-TID, no package between tag and level)
    if ($firstNonEmpty -match '^\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d+\s+[VDIWEF]/') { return "TIMBER" }
    # TIMBER header variant: === Log started ===
    if ($firstNonEmpty -match '^={3,}\s*Log') { return "TIMBER" }

    # LOGCAT: YYYY-MM-DD HH:MM:SS.mmm PID-TID TAG PKG LVL  MSG
    if ($firstNonEmpty -match '^\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d+\s+\d+-\d+') { return "LOGCAT" }

    return "UNKNOWN"
}

# ─── Level mapping ────────────────────────────────────────────────────────────
function ConvertTo-LevelChar([string]$level) {
    switch ($level.ToUpper()) {
        "VERBOSE" { return "V" }
        "DEBUG" { return "D" }
        "INFO" { return "I" }
        "WARN" { return "W" }
        "WARNING" { return "W" }
        "ERROR" { return "E" }
        "FATAL" { return "E" }
        "ASSERT" { return "E" }
        default { return "D" }
    }
}

# ─── JSON converter ───────────────────────────────────────────────────────────
function Convert-JsonLogcat([string]$path) {
    Write-Host "  Parsing JSON..." -ForegroundColor DarkGray
    $json = Get-Content -Path $path -Raw -Encoding UTF8 | ConvertFrom-Json

    $lines = [System.Collections.Generic.List[string]]::new()

    # Metadata header
    $meta = $json.metadata
    if ($meta.device.physicalDevice) {
        $dev = $meta.device.physicalDevice
        $lines.Add("# Device: $($dev.manufacturer) $($dev.model) Android $($dev.release) (API $($dev.featureLevel))")
    }
    if ($meta.filter) {
        $lines.Add("# Filter: $($meta.filter.Trim())")
    }
    $lines.Add("# Converted from JSON .logcat by convert-log.ps1")
    $lines.Add("")

    foreach ($entry in $json.logcatMessages) {
        $h = $entry.header
        $lvl = ConvertTo-LevelChar $h.logLevel
        $pidNum = $h.pid
        $tidNum = $h.tid
        $tag = $h.tag
        $pkg = if ($h.applicationId) { $h.applicationId } else { $h.processName }

        # Convert Unix timestamp (seconds + nanos) to local datetime string
        $epochSec = [long]$h.timestamp.seconds
        $nanos = [long]$h.timestamp.nanos
        $dt = [DateTimeOffset]::FromUnixTimeSeconds($epochSec).ToLocalTime()
        $ms = [int]($nanos / 1000000)
        $ts = $dt.ToString("yyyy-MM-dd HH:mm:ss") + ".$($ms.ToString('D3'))"

        # Standard logcat format: DATE TIME PID-TID TAG PKG LVL  MSG
        $line = "$ts $pidNum-$tidNum $tag $pkg $lvl  $($entry.message)"
        $lines.Add($line)
    }

    return $lines
}

# ─── TIMBER converter ─────────────────────────────────────────────────────────
# Timber format: YYYY-MM-DD HH:MM:SS.mmm LVL/TAG: MESSAGE
# or header lines: === Log started: ... ===  /  === App version: ... ===
$TimberRegex = '^(?<ts>\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d+)\s+(?<lvl>[VDIWEF])/(?<tag>[^:]+):\s*(?<msg>.*)$'

function Convert-TimberLog([string]$path, [string]$appPkg) {
    Write-Host "  Parsing Timber format..." -ForegroundColor DarkGray
    $raw = Get-Content -Path $path -Encoding UTF8
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("# Converted from Timber device export by convert-log.ps1")
    $lines.Add("# App package: $appPkg")
    $lines.Add("")

    foreach ($line in $raw) {
        if ($line -match $TimberRegex) {
            $ts = $Matches['ts']
            $lvl = $Matches['lvl']
            $tag = $Matches['tag'].Trim()
            $msg = $Matches['msg']
            # Standard logcat: use 0-0 for PID-TID (Timber doesn't expose them)
            # package is always the app (Timber is app-internal)
            $lines.Add("$ts 0-0 $tag $appPkg $lvl  $msg")
        }
        else {
            # Pass through non-structured lines (=== banners, stack traces, etc.)
            # Prefix with # so search-log.ps1 treats them as continuation
            if ($line.Trim() -ne "") {
                $lines.Add("# $line")
            }
        }
    }

    return $lines
}

# ─── LOGCAT pass-through ─────────────────────────────────────────────────────
function Convert-Logcat([string]$path) {
    Write-Host "  Format is already standard logcat — copying as-is." -ForegroundColor DarkGray
    return [string[]](Get-Content -Path $path -Encoding UTF8)
}

# ─── Main ─────────────────────────────────────────────────────────────────────
$format = Get-LogFormat $InputFile
Write-Host "Input : $InputFile" -ForegroundColor Cyan
Write-Host "Format: $format" -ForegroundColor Cyan
Write-Host "Output: $OutputFile" -ForegroundColor Cyan

$outputLines = switch ($format) {
    "JSON" { Convert-JsonLogcat  $InputFile }
    "TIMBER" { Convert-TimberLog   $InputFile $AppId }
    "LOGCAT" { Convert-Logcat      $InputFile }
    default {
        Write-Warning "Unknown log format — passing through without conversion."
        Convert-Logcat $InputFile
    }
}

$outDir = Split-Path $OutputFile -Parent
if ($outDir -and -not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}
$outputLines | Out-File -FilePath $OutputFile -Encoding UTF8

$lineCount = @($outputLines).Count
Write-Host "Done: $lineCount lines written to $OutputFile" -ForegroundColor Green
Write-Host ""
Write-Host "Now you can analyse with:" -ForegroundColor DarkGray
Write-Host "  .\scripts\utils\search-log.ps1 -LogFile `"$OutputFile`" -Summary" -ForegroundColor Gray
Write-Host "  .\scripts\utils\search-log.ps1 -LogFile `"$OutputFile`" -Errors" -ForegroundColor Gray
