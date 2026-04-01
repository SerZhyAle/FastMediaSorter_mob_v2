#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Android logcat analysis utility for FastMediaSorter v2.

.DESCRIPTION
    Parses Android logcat files (format: YYYY-MM-DD HH:MM:SS.mmm PID-TID TAG PKG LEVEL MESSAGE)
    and provides filtering, searching, context display, crash detection, flow tracing, and stats.

.PARAMETER LogFile
    Path to the logcat file. Default: temp\current.log.
    Use .\scripts\utils\extract-device-logs.ps1 to pull a fresh log from a connected device.

.EXAMPLES
    # ── Basic filters ──────────────────────────────────────────────
    .\scripts\utils\search-log.ps1 -Errors
    .\scripts\utils\search-log.ps1 -Warnings
    .\scripts\utils\search-log.ps1 -Errors -From "01:20:00" -To "01:25:00"
    .\scripts\utils\search-log.ps1 -Level I -AppOnly

    # ── Pattern search ─────────────────────────────────────────────
    .\scripts\utils\search-log.ps1 -Pattern "SMB video metadata"
    .\scripts\utils\search-log.ps1 -Pattern "SMB video metadata" -Context 5 -Top 30
    .\scripts\utils\search-log.ps1 -Pattern "timeout|refused|failed" -Context 3
    .\scripts\utils\search-log.ps1 -Pattern "SORT_DEBUG" -Last 30
    .\scripts\utils\search-log.ps1 -Pattern "thumbnail" -Top 30 -CaseSensitive
    .\scripts\utils\search-log.ps1 -Pattern "Exception|crash" -Context 5 -OutFile "temp/errors.txt"

    # ── Tag search ─────────────────────────────────────────────────
    .\scripts\utils\search-log.ps1 -Tag "BrowseViewModel"
    .\scripts\utils\search-log.ps1 -Tag "ImageLoad" -Level E
    .\scripts\utils\search-log.ps1 -Tag "BrowseViewModel" -Exclude "updateLayout|scrollTo"

    # ── Thread/PID filter ──────────────────────────────────────────
    .\scripts\utils\search-log.ps1 -Thread "4823" -Errors
    .\scripts\utils\search-log.ps1 -ProcessFilter "1234-5678"

    # ── Crash / exception detection ────────────────────────────────
    .\scripts\utils\search-log.ps1 -Exceptions
    .\scripts\utils\search-log.ps1 -Exceptions -OutFile "temp/crashes.txt"

    # ── Deduplication ──────────────────────────────────────────────
    .\scripts\utils\search-log.ps1 -Pattern "failed" -Unique
    .\scripts\utils\search-log.ps1 -Errors -Unique -Stats

    # ── Overview & stats ───────────────────────────────────────────
    .\scripts\utils\search-log.ps1 -Summary
    .\scripts\utils\search-log.ps1 -Spam -Top 20
    .\scripts\utils\search-log.ps1 -Pattern "SMB" -Stats
    .\scripts\utils\search-log.ps1 -Errors -Count

    # ── Flow trace (multi-tag correlation) ─────────────────────────
    .\scripts\utils\search-log.ps1 -Flow "BrowseViewModel","GoogleDrive","MediaFileAdapter"

    # ── Save output ────────────────────────────────────────────────
    .\scripts\utils\search-log.ps1 -Errors -OutFile "temp/errors.txt"
    .\scripts\utils\search-log.ps1 -Pattern "SMB" -Context 3 -OutFile "temp/smb_debug.txt"

    # ── Custom log file ────────────────────────────────────────────
    .\scripts\utils\search-log.ps1 -LogFile "temp/build_err7.txt" -Errors
#>

param(
    [string]$LogFile = "temp\current.log",

    # --- Filter params ---
    [string]$Pattern = "",               # Regex pattern in message or tag
    [string]$Tag = "",               # Exact or partial tag match
    [ValidateSet("V", "D", "I", "W", "E", "")]
    [string]$Level = "",               # Log level: V D I W E
    [string]$ProcessFilter = "",               # Filter by PID-TID (e.g. "1234-5678" or "1234")
    [string]$Thread = "",               # Filter by thread ID (TID part of PID-TID)

    # --- Shortcuts ---
    [switch]$Errors,                     # Only E level
    [switch]$Warnings,                   # W + E levels
    [switch]$Exceptions,                 # Find FATAL / Exception blocks with stack traces
    [switch]$Summary,                    # Stats overview
    [switch]$Spam,                       # Top repeated tags (noise detection)
    [switch]$Stats,                      # Show level/tag breakdown of matched results

    # --- Flow tracing ---
    [string[]]$Flow = @(),               # Tags to trace in sequence (multi-tag correlation)

    # --- Context & limits ---
    [int]$Context = 0,                   # Lines before/after each match
    [int]$Top = 0,                   # First N results
    [int]$Last = 0,                   # Last N results
    [switch]$Count,                      # Just print match count
    [switch]$Unique,                     # Deduplicate identical Tag+Msg combinations

    # --- Time range ---
    [string]$From = "",                  # HH:MM:SS start time
    [string]$To = "",                  # HH:MM:SS end time

    # --- Matching ---
    [switch]$CaseSensitive,              # Case-sensitive pattern/tag matching (default: insensitive)

    # --- Filtering ---
    [switch]$AppOnly,                    # Only com.sza.fastmediasorter lines
    [string]$Exclude = "",               # Regex to exclude from results

    # --- Output ---
    [switch]$NoColor,
    [string]$OutFile = ""                # Save results to file
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

# ─── Regex matching helper (respects -CaseSensitive) ───────────────────────
function Test-Match([string]$text, [string]$pattern) {
    if ($CaseSensitive) { return [regex]::IsMatch($text, $pattern) }
    return [regex]::IsMatch($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
}

# ─── Output buffer (for -OutFile capture across all modes) ──────────────────
$outBuffer = [System.Collections.Generic.List[string]]::new()

function Write-Out {
    param([string]$text, [string]$color = "White")
    if ($OutFile -ne "") { $outBuffer.Add($text) }
    if ($NoColor) { Write-Host $text }
    else { Write-Host $text -ForegroundColor $color }
}

function Save-OutFile {
    if ($OutFile -ne "") {
        $dir = Split-Path $OutFile -Parent
        if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        $outBuffer | Out-File -FilePath $OutFile -Encoding UTF8
        Write-Host "`nSaved to: $OutFile" -ForegroundColor Green
    }
}

# ─── Stats helper ─────────────────────────────────────────────────────────────
function Show-Stats([object[]]$items, [string]$label) {
    Write-Out "`n[Stats for $label]" "White"
    $byLvl = $items | Group-Object Lvl | Sort-Object Name
    foreach ($g in $byLvl) {
        $col = Get-LevelColor $g.Name
        Write-Out ("  Level {0}: {1,5} lines" -f $g.Name, $g.Count) $col
    }
    $byTag = $items | Group-Object Tag | Sort-Object Count -Descending | Select-Object -First 10
    Write-Out "  Top tags:" "White"
    $byTag | ForEach-Object { Write-Out ("    {0,5}  {1}" -f $_.Count, $_.Name) "Gray" }
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

# ─── Load and parse (index-aware) ────────────────────────────────────────────
$rawLines = Get-Content -Path $LogFile -Encoding UTF8
$parsedList = [System.Collections.Generic.List[object]]::new()
for ($idx = 0; $idx -lt $rawLines.Count; $idx++) {
    $p = Parse-Line $rawLines[$idx]
    if ($null -ne $p) {
        Add-Member -InputObject $p -NotePropertyName LineIdx -NotePropertyValue $idx
        $parsedList.Add($p)
    }
}
$parsed = $parsedList.ToArray()

Write-Host "Loaded $($parsed.Count) parsed lines from $LogFile" -ForegroundColor DarkGray

# ─── SUMMARY mode ─────────────────────────────────────────────────────────────
if ($Summary) {
    Write-Out "`n=== LOG SUMMARY ===" "Cyan"

    $total = $parsed.Count
    $byLevel = $parsed | Group-Object Lvl | Sort-Object Name
    Write-Out "`n[Level distribution]" "White"
    foreach ($g in $byLevel) {
        $pct = [math]::Round($g.Count * 100 / $total, 1)
        $color = Get-LevelColor $g.Name
        Write-Out ("  {0}  {1,6} ({2,5}%)" -f $g.Name, $g.Count, $pct) $color
    }

    $errLines = @($parsed | Where-Object { $_.Lvl -eq "E" })
    $warnLines = @($parsed | Where-Object { $_.Lvl -eq "W" })

    if ($errLines.Count -gt 0) {
        Write-Out "`n[Errors ($($errLines.Count))]" "Red"
        $errLines | Select-Object -First 20 | ForEach-Object {
            Write-Out ("  {0}  [{1}]  {2}" -f $_.Time, $_.Tag, $_.Msg) "Red"
        }
        if ($errLines.Count -gt 20) { Write-Out "  ... and $($errLines.Count - 20) more" "DarkRed" }
    }

    if ($warnLines.Count -gt 0) {
        Write-Out "`n[Warnings (first 10)]" "Yellow"
        $warnLines | Select-Object -First 10 | ForEach-Object {
            Write-Out ("  {0}  [{1}]  {2}" -f $_.Time, $_.Tag, $_.Msg) "Yellow"
        }
    }

    $timeSpan = ""
    if ($parsed.Count -gt 0) {
        $tsFirst = $parsed[0].Time
        $tsLast = $parsed[-1].Time
        $timeSpan = "$tsFirst → $tsLast"
    }
    Write-Out "`n[Time range]  $timeSpan" "White"

    $topTags = $parsed | Group-Object Tag | Sort-Object Count -Descending | Select-Object -First 10
    Write-Out "`n[Top 10 tags by volume]" "White"
    $topTags | ForEach-Object { Write-Out ("  {0,6}  {1}" -f $_.Count, $_.Name) "Gray" }

    Save-OutFile
    return
}

# ─── SPAM mode ────────────────────────────────────────────────────────────────
if ($Spam) {
    $limit = if ($Top -gt 0) { $Top } else { 20 }
    Write-Out "`n=== TOP $limit SPAM TAGS ===" "Yellow"
    $parsed | Group-Object Tag | Sort-Object Count -Descending | Select-Object -First $limit |
    ForEach-Object { Write-Out ("{0,6}  {1}" -f $_.Count, $_.Name) "Gray" }
    Save-OutFile
    return
}

# ─── EXCEPTIONS mode ──────────────────────────────────────────────────────────
# Finds FATAL EXCEPTION blocks, Java stack traces, and ANR triggers
if ($Exceptions) {
    Write-Out "`n=== EXCEPTION / CRASH BLOCKS ===" "Red"
    $crashPatterns = 'FATAL EXCEPTION|AndroidRuntime|Exception:|Caused by:|Process:.*PID:|ANR in|begin of crash dump'
    $crashIndices = [System.Collections.Generic.HashSet[int]]::new()
    $blocks = 0

    for ($i = 0; $i -lt $rawLines.Count; $i++) {
        if ($rawLines[$i] -match $crashPatterns) {
            $null = $crashIndices.Add($i)
        }
    }

    # Expand each hit: capture the surrounding block (up to 60 lines forward for stack trace)
    $printedIdx = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($ci in ($crashIndices | Sort-Object)) {
        if ($printedIdx.Contains($ci)) { continue }
        $blocks++
        Write-Out "" "DarkGray"
        Write-Out ("══ BLOCK #{0} (line {1}) ══" -f $blocks, ($ci + 1)) "Magenta"
        $end = [Math]::Min($rawLines.Count - 1, $ci + 80)
        for ($j = $ci; $j -le $end; $j++) {
            if ($printedIdx.Contains($j)) { break }
            $null = $printedIdx.Add($j)
            $p = Parse-Line $rawLines[$j]
            $col = if ($p) { Get-LevelColor $p.Lvl } else { "DarkGray" }
            # Stop block at next non-crash/stack-trace parsed line that is not E/W
            if ($j -gt $ci -and $null -ne $p -and $p.Lvl -notin @("E", "W") -and
                $rawLines[$j] -notmatch '^\s+at |Caused by:|Exception:') { break }
            Write-Out ("[{0,5}] {1}" -f ($j + 1), $rawLines[$j]) $col
        }
    }

    if ($blocks -eq 0) {
        Write-Out "No exception/crash blocks found." "Green"
    }
    else {
        Write-Out "`n$blocks exception block(s) found." "Red"
    }
    Save-OutFile
    return
}

# ─── FLOW mode ────────────────────────────────────────────────────────────────
if ($Flow.Count -gt 0) {
    Write-Out "`n=== FLOW TRACE: $($Flow -join ' -> ') ===" "Cyan"
    $tagPattern = ($Flow | ForEach-Object { [regex]::Escape($_) }) -join "|"
    $filtered = @($parsed | Where-Object { Test-Match $_.Tag $tagPattern })
    $filtered | ForEach-Object {
        $color = Get-LevelColor $_.Lvl
        Write-Out ("[{0,5}] {1}  {2,-30}  {3}  {4}" -f ($_.LineIdx + 1), $_.Time, $_.Tag, $_.Lvl, $_.Msg) $color
    }
    Write-Out "`nMatched $($filtered.Count) lines across $($Flow.Count) tags." "DarkGray"
    Save-OutFile
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

# PID / Thread filter
if ($ProcessFilter -ne "") {
    $results = $results | Where-Object { $_.PID -match ([regex]::Escape($ProcessFilter)) }
}
if ($Thread -ne "") {
    # PID field is "pid-tid"; match the tid part after the dash
    $results = $results | Where-Object { $_.PID -match "-$([regex]::Escape($Thread))$" }
}

# Level filters
if ($Errors) {
    $results = $results | Where-Object { $_.Lvl -eq "E" }
}
elseif ($Warnings) {
    $results = $results | Where-Object { $_.Lvl -in @("W", "E") }
}
elseif ($Level -ne "") {
    $results = $results | Where-Object { $_.Lvl -eq $Level }
}

# Tag filter
if ($Tag -ne "") {
    $results = $results | Where-Object { Test-Match $_.Tag $Tag }
}

# Pattern filter (message + tag)
if ($Pattern -ne "") {
    $results = $results | Where-Object { (Test-Match $_.Msg $Pattern) -or (Test-Match $_.Tag $Pattern) }
}

# Exclude filter
if ($Exclude -ne "") {
    $results = $results | Where-Object { -not (Test-Match $_.Raw $Exclude) }
}

# Deduplicate by Tag+Msg
if ($Unique) {
    $seen = [System.Collections.Generic.HashSet[string]]::new()
    $results = $results | Where-Object { $seen.Add("$($_.Tag)|$($_.Msg)") }
}

# Count only
if ($Count) {
    Write-Host "Match count: $(@($results).Count)" -ForegroundColor Cyan
    return
}

# Top / Last limiting
if ($Top -gt 0) {
    $results = @($results | Select-Object -First $Top)
}
elseif ($Last -gt 0) {
    $results = @($results | Select-Object -Last $Last)
}
else {
    $results = @($results)
}

# ─── Context mode (index-based — handles duplicate log lines correctly) ───────
if ($Context -gt 0 -and ($Pattern -ne "" -or $Tag -ne "")) {
    Write-Out "`n=== RESULTS WITH CONTEXT ($Context lines) ===" "Cyan"

    # Collect the original raw-file indices of matched results
    $matchIdxSet = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($r in $results) { $null = $matchIdxSet.Add($r.LineIdx) }

    $totalLines = $rawLines.Count
    $printedIdx = [System.Collections.Generic.HashSet[int]]::new()

    foreach ($matchIdx in ($matchIdxSet | Sort-Object)) {
        $start = [Math]::Max(0, $matchIdx - $Context)
        $end = [Math]::Min($totalLines - 1, $matchIdx + $Context)

        if ($start -gt 0 -and -not $printedIdx.Contains($start - 1)) {
            Write-Out "---" "DarkGray"
        }

        for ($j = $start; $j -le $end; $j++) {
            if ($printedIdx.Contains($j)) { continue }
            $null = $printedIdx.Add($j)
            $p = Parse-Line $rawLines[$j]
            $lineNum = "[{0,5}]" -f ($j + 1)

            if ($j -eq $matchIdx) {
                $col = if ($NoColor) { "White" } else { "Green" }
                Write-Out "$lineNum $($rawLines[$j])" $col
            }
            else {
                $col = if ($p) { Get-LevelColor $p.Lvl } else { "DarkGray" }
                Write-Out "$lineNum $($rawLines[$j])" $col
            }
        }
    }

    Write-Out "`nMatched $($results.Count) lines." "DarkGray"
    if ($Stats) { Show-Stats $results "matched lines" }
    Save-OutFile
    return
}

# ─── Normal output ────────────────────────────────────────────────────────────
if ($results.Count -eq 0) {
    Write-Host "No matches found." -ForegroundColor Yellow
    return
}

Write-Out "`n=== $($results.Count) MATCHES ===" "Cyan"

foreach ($r in $results) {
    $line = "[{0,5}] {1}  {2,-28}  {3}  {4}" -f ($r.LineIdx + 1), $r.Time, $r.Tag, $r.Lvl, $r.Msg
    Write-Out $line (Get-LevelColor $r.Lvl)
}

if ($Stats) { Show-Stats $results "matched lines" }
Save-OutFile
