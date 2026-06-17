#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Android logcat analysis utility for FastMediaSorter v2.

.DESCRIPTION
    Parses Android logcat files (format: YYYY-MM-DD HH:MM:SS.mmm PID-TID TAG PKG LEVEL MESSAGE)
    and provides filtering, searching, context display, crash detection, flow tracing, and stats.

.PARAMETER LogFile
    Path to the log file. Default: temp\current.log.
    Accepts ALL three log formats - auto-detected at load time:
      FORMAT 1 LOGCAT  - standard adb/AS copy-paste: DATE TIME PID-TID TAG PKG LVL  MSG
      FORMAT 2 JSON    - Android Studio "Export to File" (.logcat JSON)
      FORMAT 3 TIMBER  - app device export: DATE TIME LVL/TAG: MSG
    Use .\scripts\utils\extract-device-logs.ps1 to pull a fresh log from a connected device.
    Use .\scripts\utils\convert-log.ps1 to pre-convert any format to standard logcat text.

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
    .\scripts\utils\search-log.ps1 -Exceptions -Count            # machine: "Match count: N"
    .\scripts\utils\search-log.ps1 -Exceptions -Json             # machine: {"count":N,"blocks":[{block,line,trigger,lines,text}]}

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
    [switch]$Json,                       # Machine-readable JSON for -Exceptions mode (count + per-block line refs)
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

# ─── Format detection ─────────────────────────────────────────────────────────
# Returns: "LOGCAT" | "JSON" | "TIMBER"
function Get-LogFormat([string]$path) {
    $preview = Get-Content -Path $path -TotalCount 10
    $firstNonEmpty = $preview | Where-Object { $_.Trim() -ne "" } | Select-Object -First 1
    if (-not $firstNonEmpty) { return "LOGCAT" }
    # JSON: Android Studio .logcat export
    if ($firstNonEmpty.Trim().StartsWith("{")) { return "JSON" }
    # TIMBER: YYYY-MM-DD HH:MM:SS.mmm LVL/TAG: MSG  (no PID-TID between timestamp and tag)
    if ($firstNonEmpty -match '^\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d+\s+[VDIWEF]/') { return "TIMBER" }
    if ($firstNonEmpty -match '^={3,}\s*Log') { return "TIMBER" }
    # LOGCAT: standard adb / AS copy-paste
    return "LOGCAT"
}

# ─── Level character helper ────────────────────────────────────────────────────
function ConvertTo-LevelChar([string]$level) {
    switch ($level.ToUpper()) {
        "VERBOSE" { return "V" }
        "DEBUG"   { return "D" }
        "INFO"    { return "I" }
        "WARN"    { return "W" }
        "WARNING" { return "W" }
        "ERROR"   { return "E" }
        "FATAL"   { return "E" }
        "ASSERT"  { return "E" }
        default   { return "D" }
    }
}

# ─── Parse line into PSCustomObject ──────────────────────────────────────────
# Format 1 - standard logcat: DATE TIME PID-TID TAG<spaces> PACKAGE<spaces> LEVEL  MESSAGE
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

# Format 3 - Timber device export: DATE TIME LVL/TAG: MSG
$TimberLineRegex = '^(?<ts>\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d+)\s+(?<lvl>[VDIWEF])/(?<tag>[^:]+):\s*(?<msg>.*)$'

function Parse-TimberLine([string]$line) {
    if ($line -match $TimberLineRegex) {
        return [PSCustomObject]@{
            Raw  = $line
            TS   = $Matches['ts']
            Time = $Matches['ts'].Substring(11, 8)
            PID  = "0-0"
            Tag  = $Matches['tag'].Trim()
            Pkg  = "com.sza.fastmediasorter"  # Timber is always app-only - package is implicit
            Lvl  = $Matches['lvl']
            Msg  = $Matches['msg']
        }
    }
    return $null
}

# ─── Format-aware load and parse ─────────────────────────────────────────────
$logFormat = Get-LogFormat $LogFile
$parsedList = [System.Collections.Generic.List[object]]::new()
$rawLines   = [string[]]@()

if ($logFormat -eq "JSON") {
    # ── Format 2: Android Studio .logcat JSON export ─────────────────────────
    if (-not $Json) { Write-Host ("Loading JSON .logcat: {0}" -f (Split-Path $LogFile -Leaf)) -ForegroundColor DarkGray }
    $json = Get-Content -Path $LogFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $syntheticLines = [System.Collections.Generic.List[string]]::new()

    foreach ($entry in $json.logcatMessages) {
        $h   = $entry.header
        $lvl = ConvertTo-LevelChar $h.logLevel
        $pidNum = $h.pid
        $tidNum = $h.tid
        $tag = $h.tag
        $pkg = if ($h.applicationId) { $h.applicationId } else { $h.processName }

        # Convert Unix timestamp {seconds, nanos} → local datetime
        $epochSec = [long]$h.timestamp.seconds
        $nanos    = [long]$h.timestamp.nanos
        $dt = [DateTimeOffset]::FromUnixTimeSeconds($epochSec).ToLocalTime()
        $ms = [int]($nanos / 1000000)
        $ts = $dt.ToString("yyyy-MM-dd HH:mm:ss") + ".$($ms.ToString('D3'))"
        $time = $ts.Substring(11, 8)

        $msg = $entry.message
        # Synthetic standard-format raw line (used by context mode)
        $raw = "$ts $pidNum-$tidNum $tag $pkg $lvl  $msg"
        $syntheticLines.Add($raw)

        $p = [PSCustomObject]@{
            Raw  = $raw
            TS   = $ts
            Time = $time
            PID  = "$pidNum-$tidNum"
            Tag  = $tag
            Pkg  = $pkg
            Lvl  = $lvl
            Msg  = $msg
            LineIdx      = $syntheticLines.Count - 1
            Continuation = [System.Collections.Generic.List[string]]::new()
        }
        $parsedList.Add($p)
    }

    $rawLines = $syntheticLines.ToArray()

} elseif ($logFormat -eq "TIMBER") {
    # ── Format 3: Timber / app device export ────────────────────────────────
    if (-not $Json) { Write-Host ("Loading Timber log: {0}" -f (Split-Path $LogFile -Leaf)) -ForegroundColor DarkGray }
    $rawLines = @(Get-Content -Path $LogFile -Encoding UTF8)   # @() guards single-line/empty logs (Get-Content returns a scalar otherwise)

    for ($idx = 0; $idx -lt $rawLines.Count; $idx++) {
        $p = Parse-TimberLine $rawLines[$idx]
        if ($null -ne $p) {
            Add-Member -InputObject $p -NotePropertyName LineIdx      -NotePropertyValue $idx
            Add-Member -InputObject $p -NotePropertyName Continuation -NotePropertyValue ([System.Collections.Generic.List[string]]::new())
            $parsedList.Add($p)
        } else {
            # Header lines (=== Log started ===, banner text, stack traces)
            if ($parsedList.Count -gt 0) {
                $parsedList[$parsedList.Count - 1].Continuation.Add($rawLines[$idx])
            }
        }
    }

} else {
    # ── Format 1: Standard logcat (adb / AS copy-paste) ─────────────────────
    if (-not $Json) { Write-Host ("Loading logcat: {0}" -f (Split-Path $LogFile -Leaf)) -ForegroundColor DarkGray }
    $rawLines = @(Get-Content -Path $LogFile -Encoding UTF8)   # @() guards single-line/empty logs (Get-Content returns a scalar otherwise)

    for ($idx = 0; $idx -lt $rawLines.Count; $idx++) {
        $p = Parse-Line $rawLines[$idx]
        if ($null -ne $p) {
            Add-Member -InputObject $p -NotePropertyName LineIdx      -NotePropertyValue $idx
            Add-Member -InputObject $p -NotePropertyName Continuation -NotePropertyValue ([System.Collections.Generic.List[string]]::new())
            $parsedList.Add($p)
        } else {
            # Attach continuation lines (stack traces, banner, separators) to the previous entry
            if ($parsedList.Count -gt 0) {
                $parsedList[$parsedList.Count - 1].Continuation.Add($rawLines[$idx])
            }
        }
    }
}

$parsed = $parsedList.ToArray()
$continuationCount = $rawLines.Count - $parsed.Count

if (-not $Json) {
    Write-Host ("Loaded {0} raw lines → {1} structured + {2} continuation/separator  [{3}] [FORMAT: {4}]" -f `
        $rawLines.Count, $parsed.Count, $continuationCount, (Split-Path $LogFile -Leaf), $logFormat) -ForegroundColor DarkGray
}

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

    # For JSON format - show device metadata from the .logcat header
    if ($logFormat -eq "JSON") {
        $jsonMeta = (Get-Content -Path $LogFile -Raw -Encoding UTF8 | ConvertFrom-Json).metadata
        if ($jsonMeta.device -and ($jsonMeta.device.PSObject.Properties.Name -contains 'physicalDevice')) {
            $dev = $jsonMeta.device.physicalDevice
            Write-Out "`n[Device (JSON metadata)]" "White"
            Write-Out ("  $($dev.manufacturer) $($dev.model)  Android $($dev.release)  API $($dev.featureLevel)") "Gray"
        } elseif ($jsonMeta.device -and ($jsonMeta.device.PSObject.Properties.Name -contains 'emulatorDevice')) {
            $dev = $jsonMeta.device.emulatorDevice
            Write-Out "`n[Device (JSON metadata)]" "White"
            Write-Out ("  Emulator: $($dev.avdName)  Android $($dev.release)  API $($dev.featureLevel)  serial=$($dev.serialNumber)") "Gray"
        }
        if ($jsonMeta.filter) {
            Write-Out ("  Filter: $($jsonMeta.filter.Trim())") "Gray"
        }
    }

    # Startup banner - check continuation lines (LOGCAT/TIMBER) and Timber header lines
    $bannerEntry = $parsed | Where-Object {
        $_.Continuation.Count -gt 5 -and
        ($_.Continuation | Where-Object { $_ -match 'FAST MEDIA SORTER' })
    } | Select-Object -First 1

    # For Timber format - also look for the === App version === header line in raw lines
    if (-not $bannerEntry -and $logFormat -eq "TIMBER") {
        $verLine = $rawLines | Where-Object { $_ -match '^={3,}\s*App version:' } | Select-Object -First 1
        if ($verLine -and $verLine -match 'App version:\s*(.+?)\s*={0,}$') {
            Write-Out "`n[App Version (Timber header)]" "White"
            Write-Out ("  $($Matches[1])") "Gray"
        }
    }
    if ($bannerEntry) {
        Write-Out "`n[Startup Banner]" "White"
        $keyFields = @(
            'Version Name', 'Version Code', 'App ID', 'Build Type', 'Flavor',
            'Android Version', 'SDK / API Level', 'Model',
            'Total RAM', 'Heap Max'
        )
        foreach ($field in $keyFields) {
            $bline = $bannerEntry.Continuation | Where-Object { $_ -match "\b$([regex]::Escape($field))\b" } | Select-Object -First 1
            if ($bline -and $bline -match ':\s*(.+)$') {
                Write-Out ("  {0,-22}: {1}" -f $field, $Matches[1].Trim()) "Gray"
            }
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
# Finds FATAL EXCEPTION blocks, Java stack traces, and ANR triggers.
# Output modes: -Json (machine), -Count ("Match count: N"), else human-readable.
if ($Exceptions) {
    $crashPatterns = 'FATAL EXCEPTION|AndroidRuntime|Exception:|Caused by:|Process:.*PID:|ANR in|begin of crash dump|beginning of crash'
    $crashIndices = [System.Collections.Generic.HashSet[int]]::new()

    for ($i = 0; $i -lt $rawLines.Count; $i++) {
        if ($rawLines[$i] -match $crashPatterns) {
            $null = $crashIndices.Add($i)
        }
    }

    # Expand each hit into a de-duplicated block (up to 80 lines forward for the stack trace),
    # collecting structured refs once so every output mode reports the same blocks/count.
    $printedIdx = [System.Collections.Generic.HashSet[int]]::new()
    $crashBlocks = [System.Collections.Generic.List[object]]::new()
    foreach ($ci in ($crashIndices | Sort-Object)) {
        if ($printedIdx.Contains($ci)) { continue }
        $null = ($rawLines[$ci] -match $crashPatterns)   # re-match to expose the triggering substring
        $trigger = $Matches[0]
        $blockIdx = [System.Collections.Generic.List[int]]::new()
        $end = [Math]::Min($rawLines.Count - 1, $ci + 80)
        for ($j = $ci; $j -le $end; $j++) {
            if ($printedIdx.Contains($j)) { break }
            $null = $printedIdx.Add($j)
            $p = Parse-Line $rawLines[$j]
            # Stop block at next non-crash/stack-trace parsed line that is not E/W (line is still consumed)
            if ($j -gt $ci -and $null -ne $p -and $p.Lvl -notin @("E", "W") -and
                $rawLines[$j] -notmatch '^\s+at |Caused by:|Exception:') { break }
            $null = $blockIdx.Add($j)
        }
        $crashBlocks.Add([PSCustomObject]@{
            Block   = $crashBlocks.Count + 1
            Line    = $ci + 1
            Trigger = $trigger
            Text    = $rawLines[$ci].Trim()
            Lines   = $blockIdx.Count
            Indices = $blockIdx
        })
    }

    # ── JSON output ── single compact object on stdout (banners suppressed); always emits a `blocks` array.
    if ($Json) {
        $blocksOut = $crashBlocks | ForEach-Object {
            [ordered]@{ block = $_.Block; line = $_.Line; trigger = $_.Trigger; lines = $_.Lines; text = $_.Text }
        }
        # Outer object hand-assembled so a single block still serializes as an array, not an object.
        $blocksJson = if ($crashBlocks.Count -gt 0) { @($blocksOut) | ConvertTo-Json -Depth 4 -AsArray -Compress } else { '[]' }
        $payload = '{"count":' + $crashBlocks.Count + ',"blocks":' + $blocksJson + '}'
        if ($OutFile -ne "") {
            $dir = Split-Path $OutFile -Parent
            if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
            $payload | Out-File -FilePath $OutFile -Encoding UTF8
        }
        Write-Output $payload
        return
    }

    # ── Count-only output ── reuses the "Match count: N" contract shared with the filter pipeline.
    if ($Count) {
        Write-Host ("Match count: {0}" -f $crashBlocks.Count) -ForegroundColor Cyan
        return
    }

    # ── Human-readable output ──
    Write-Out "`n=== EXCEPTION / CRASH BLOCKS ===" "Red"
    foreach ($b in $crashBlocks) {
        Write-Out "" "DarkGray"
        Write-Out ("══ BLOCK #{0} (line {1}) ══" -f $b.Block, $b.Line) "Magenta"
        foreach ($j in $b.Indices) {
            $p = Parse-Line $rawLines[$j]
            $col = if ($p) { Get-LevelColor $p.Lvl } else { "DarkGray" }
            Write-Out ("[{0,5}] {1}" -f ($j + 1), $rawLines[$j]) $col
        }
    }

    if ($crashBlocks.Count -eq 0) {
        Write-Out "No exception/crash blocks found." "Green"
    }
    else {
        Write-Out "`n$($crashBlocks.Count) exception block(s) found." "Red"
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

# Pattern filter (message + tag + continuation lines)
if ($Pattern -ne "") {
    $results = $results | Where-Object {
        (Test-Match $_.Msg $Pattern) -or
        (Test-Match $_.Tag $Pattern) -or
        ($_.Continuation.Count -gt 0 -and ($_.Continuation | Where-Object { Test-Match $_ $Pattern }))
    }
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

# ─── Context mode (index-based - handles duplicate log lines correctly) ───────
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
