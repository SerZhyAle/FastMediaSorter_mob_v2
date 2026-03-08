#!/usr/bin/env pwsh
# Log analysis utility for FastMediaSorter Android logcat files
# Format: YYYY-MM-DD HH:MM:SS.mmm PID-TID TAG PACKAGE LEVEL MESSAGE
#
# Usage examples:
#   .\scripts\utils\search-log.ps1 -Errors
#   .\scripts\utils\search-log.ps1 -Pattern "BrowseViewModel" -Context 3
#   .\scripts\utils\search-log.ps1 -Tag "ImageLoading" -Level D
#   .\scripts\utils\search-log.ps1 -Errors -From "01:20:00" -To "01:25:00"
#   .\scripts\utils\search-log.ps1 -Summary
#   .\scripts\utils\search-log.ps1 -Pattern "SORT_DEBUG" -Last 30
#   .\scripts\utils\search-log.ps1 -Spam -Top 15
#   .\scripts\utils\search-log.ps1 -Flow "BrowseViewModel","GoogleDrive","MediaFileAdapter"

param(
    [string]$LogFile     = "temp\current.log",

    # --- Filter params ---
    [string]$Pattern     = "",               # Regex pattern in message or tag
    [string]$Tag         = "",               # Exact or partial tag match
    [ValidateSet("V","D","I","W","E","")]
    [string]$Level       = "",               # Log level: V D I W E

    # --- Shortcuts ---
    [switch]$Errors,                         # Only E level
    [switch]$Warnings,                       # W + E levels
    [switch]$Summary,                        # Stats overview
    [switch]$Spam,                           # Top repeated tags (noise detection)

    # --- Flow tracing ---
    [string[]]$Flow      = @(),              # Comma-separated tags to trace in sequence

    # --- Context & limits ---
    [int]$Context        = 0,               # Lines before/after match
    [int]$Top            = 0,               # First N results
    [int]$Last           = 0,               # Last N results
    [switch]$Count,                         # Just print match count

    # --- Time range ---
    [string]$From        = "",              # HH:MM:SS start time
    [string]$To          = "",              # HH:MM:SS end time

    # --- Filtering ---
    [switch]$AppOnly,                       # Only com.sza.fastmediasorter lines
    [string]$Exclude     = "",             # Regex to exclude from results

    # --- Output ---
    [switch]$NoColor,
    [string]$OutFile     = ""              # Save results to file
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ─── Resolve log file ────────────────────────────────────────────────────────
if (-not [System.IO.Path]::IsPathRooted($LogFile)) {
    $LogFile = Join-Path (Get-Location) $LogFile
}
if (-not (Test-Path $LogFile)) {
    Write-Error "Log file not found: $LogFile"
    exit 1
}

# ─── Color helpers ────────────────────────────────────────────────────────────
function Get-LevelColor([string]$lvl) {
    if ($NoColor) { return "White" }
    switch ($lvl) {
        "E" { return "Red" }
        "W" { return "Yellow" }
        "I" { return "Cyan" }
        "D" { return "Gray" }
        "V" { return "DarkGray" }
        default { return "White" }
    }
}

# ─── Parse line into PSCustomObject ──────────────────────────────────────────
# Format: DATE TIME PID-TID TAG<spaces> PACKAGE<spaces> LEVEL  MESSAGE
$LineRegex = '^(?<ts>\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d+)\s+(?<pid>\d+-\d+)\s+(?<tag>\S+)\s+(?<pkg>\S+)\s+(?<lvl>[VDIWEF])\s+(?<msg>.*)$'

function Parse-Line([string]$line) {
    if ($line -match $LineRegex) {
        return [PSCustomObject]@{
            Raw  = $line
            TS   = $Matches['ts']
            Time = $Matches['ts'].Substring(11, 8)   # HH:MM:SS
            PID  = $Matches['pid']
            Tag  = $Matches['tag']
            Pkg  = $Matches['pkg']
            Lvl  = $Matches['lvl']
            Msg  = $Matches['msg']
        }
    }
    return $null
}

# ─── Load and parse ───────────────────────────────────────────────────────────
$rawLines = Get-Content -Path $LogFile -Encoding UTF8
$parsed   = $rawLines | ForEach-Object { Parse-Line $_ } | Where-Object { $null -ne $_ }

Write-Host "Loaded $($parsed.Count) parsed lines from $LogFile" -ForegroundColor DarkGray

# ─── SUMMARY mode ─────────────────────────────────────────────────────────────
if ($Summary) {
    Write-Host "`n=== LOG SUMMARY ===" -ForegroundColor Cyan

    $total = $parsed.Count
    $byLevel = $parsed | Group-Object Lvl | Sort-Object Name
    Write-Host "`n[Level distribution]" -ForegroundColor White
    foreach ($g in $byLevel) {
        $pct = [math]::Round($g.Count * 100 / $total, 1)
        $color = Get-LevelColor $g.Name
        Write-Host ("  {0}  {1,6} ({2,5}%)" -f $g.Name, $g.Count, $pct) -ForegroundColor $color
    }

    $errLines  = $parsed | Where-Object { $_.Lvl -eq "E" }
    $warnLines = $parsed | Where-Object { $_.Lvl -eq "W" }

    if ($errLines) {
        Write-Host "`n[Errors ($($errLines.Count))]" -ForegroundColor Red
        $errLines | Select-Object -First 20 | ForEach-Object {
            Write-Host ("  {0}  [{1}]  {2}" -f $_.Time, $_.Tag, $_.Msg) -ForegroundColor Red
        }
        if ($errLines.Count -gt 20) { Write-Host "  ... and $($errLines.Count - 20) more" -ForegroundColor DarkRed }
    }

    if ($warnLines) {
        Write-Host "`n[Warnings (first 10)]" -ForegroundColor Yellow
        $warnLines | Select-Object -First 10 | ForEach-Object {
            Write-Host ("  {0}  [{1}]  {2}" -f $_.Time, $_.Tag, $_.Msg) -ForegroundColor Yellow
        }
    }

    $timeSpan = ""
    if ($parsed.Count -gt 0) {
        $tsFirst = $parsed[0].Time
        $tsLast  = $parsed[-1].Time
        $timeSpan = "$tsFirst → $tsLast"
    }
    Write-Host "`n[Time range]  $timeSpan" -ForegroundColor White

    $topTags = $parsed | Group-Object Tag | Sort-Object Count -Descending | Select-Object -First 10
    Write-Host "`n[Top 10 tags by volume]" -ForegroundColor White
    $topTags | ForEach-Object { Write-Host ("  {0,6}  {1}" -f $_.Count, $_.Name) -ForegroundColor Gray }

    return
}

# ─── SPAM mode ────────────────────────────────────────────────────────────────
if ($Spam) {
    $limit = if ($Top -gt 0) { $Top } else { 20 }
    Write-Host "`n=== TOP $limit SPAM TAGS ===" -ForegroundColor Yellow
    $parsed | Group-Object Tag | Sort-Object Count -Descending | Select-Object -First $limit |
        ForEach-Object { Write-Host ("{0,6}  {1}" -f $_.Count, $_.Name) -ForegroundColor Gray }
    return
}

# ─── FLOW mode ────────────────────────────────────────────────────────────────
if ($Flow.Count -gt 0) {
    Write-Host "`n=== FLOW TRACE: $($Flow -join ' -> ') ===" -ForegroundColor Cyan
    $tagPattern = ($Flow | ForEach-Object { [regex]::Escape($_) }) -join "|"
    $filtered = $parsed | Where-Object { $_.Tag -match $tagPattern }
    $filtered | ForEach-Object {
        $color = Get-LevelColor $_.Lvl
        Write-Host ("{0}  {1,-30}  {2}  {3}" -f $_.Time, $_.Tag, $_.Lvl, $_.Msg) -ForegroundColor $color
    }
    Write-Host "`nMatched $($filtered.Count) lines across $($Flow.Count) tags." -ForegroundColor DarkGray
    return
}

# ─── FILTER pipeline ─────────────────────────────────────────────────────────
$results = $parsed

# Time range
if ($From -ne "") {
    $results = $results | Where-Object { $_.Time -ge $From }
}
if ($To -ne "") {
    $results = $results | Where-Object { $_.Time -le $To }
}

# App-only filter
if ($AppOnly) {
    $results = $results | Where-Object { $_.Pkg -match "fastmediasorter" }
}

# Level filters
if ($Errors) {
    $results = $results | Where-Object { $_.Lvl -eq "E" }
} elseif ($Warnings) {
    $results = $results | Where-Object { $_.Lvl -in @("W","E") }
} elseif ($Level -ne "") {
    $results = $results | Where-Object { $_.Lvl -eq $Level }
}

# Tag filter
if ($Tag -ne "") {
    $results = $results | Where-Object { $_.Tag -match $Tag }
}

# Pattern filter (message + tag)
if ($Pattern -ne "") {
    $results = $results | Where-Object { $_.Msg -match $Pattern -or $_.Tag -match $Pattern }
}

# Exclude filter
if ($Exclude -ne "") {
    $results = $results | Where-Object { $_.Raw -notmatch $Exclude }
}

# Count only
if ($Count) {
    Write-Host "Match count: $($results.Count)" -ForegroundColor Cyan
    return
}

# Top / Last limiting
if ($Top -gt 0) {
    $results = $results | Select-Object -First $Top
} elseif ($Last -gt 0) {
    $results = $results | Select-Object -Last $Last
}

# ─── Context mode (reindex raw lines) ────────────────────────────────────────
if ($Context -gt 0 -and ($Pattern -ne "" -or $Tag -ne "")) {
    Write-Host "`n=== RESULTS WITH CONTEXT ($Context lines) ===" -ForegroundColor Cyan
    $matchedRaw = $results | ForEach-Object { $_.Raw }
    $matchSet   = [System.Collections.Generic.HashSet[string]]::new()
    $matchedRaw | ForEach-Object { $null = $matchSet.Add($_) }

    $totalLines = $rawLines.Count
    $printedIdx = [System.Collections.Generic.HashSet[int]]::new()

    for ($i = 0; $i -lt $totalLines; $i++) {
        if ($matchSet.Contains($rawLines[$i])) {
            $start = [Math]::Max(0, $i - $Context)
            $end   = [Math]::Min($totalLines - 1, $i + $Context)
            if ($start -gt 0 -and -not $printedIdx.Contains($start - 1)) {
                Write-Host "---" -ForegroundColor DarkGray
            }
            for ($j = $start; $j -le $end; $j++) {
                if (-not $printedIdx.Contains($j)) {
                    $null = $printedIdx.Add($j)
                    $p = Parse-Line $rawLines[$j]
                    if ($j -eq $i) {
                        $col = if ($NoColor) { "White" } else { "Green" }
                        Write-Host $rawLines[$j] -ForegroundColor $col
                    } else {
                        $col = if ($p) { Get-LevelColor $p.Lvl } else { "DarkGray" }
                        Write-Host $rawLines[$j] -ForegroundColor $col
                    }
                }
            }
        }
    }
    Write-Host "`nMatched $($results.Count) lines." -ForegroundColor DarkGray
    return
}

# ─── Normal output ────────────────────────────────────────────────────────────
$output = [System.Collections.Generic.List[string]]::new()

if ($results.Count -eq 0) {
    Write-Host "No matches found." -ForegroundColor Yellow
    return
}

Write-Host "`n=== $($results.Count) MATCHES ===" -ForegroundColor Cyan

foreach ($r in $results) {
    $line = "{0}  {1,-28}  {2}  {3}" -f $r.Time, $r.Tag, $r.Lvl, $r.Msg
    $col  = Get-LevelColor $r.Lvl
    Write-Host $line -ForegroundColor $col
    $output.Add($line)
}

if ($OutFile -ne "") {
    $output | Out-File -FilePath $OutFile -Encoding UTF8
    Write-Host "`nSaved to: $OutFile" -ForegroundColor Green
}
