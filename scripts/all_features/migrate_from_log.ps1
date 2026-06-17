<#
.SYNOPSIS
    One-shot migration: convert dev/FUNCTIONALITY.log into ALL_FEATURES records.

.DESCRIPTION
    Reads the chronological functionality log, keeps ADD/CHANGE entries (the ones
    describing a user-visible capability), folds repeated lines for the same
    capability into one current-state record, and drops entries whose capability
    was later DELETEd. Each surviving capability is written via add.ps1 (upsert),
    so re-running is idempotent: same derivation -> same id -> upsert, no dupes.

    id/area/name are derived heuristically from the log text; Phase 03's central
    sweep normalizes them afterward. spec provenance is carried from the [Sxxxx]
    slot. Non-ASCII punctuation is folded to ASCII (log is meant to be EN).

.EXAMPLE
    .\scripts\all_features\migrate_from_log.ps1
#>
param(
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"
trap { Write-Error $_; exit 1 }

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
if (-not $repoRoot -or -not (Test-Path (Join-Path $repoRoot "settings.gradle.kts"))) {
    $repoRoot = (Get-Location).Path
}
$logFile = Join-Path (Join-Path $repoRoot "dev") "FUNCTIONALITY.log"
$addScript = Join-Path $scriptDir "add.ps1"
if (-not (Test-Path $logFile)) { Write-Error "Log not found: $logFile"; exit 1 }
if (-not (Test-Path $addScript)) { Write-Error "add.ps1 not found"; exit 1 }

# Fold common Unicode punctuation to ASCII, drop the rest. Uses code points only
# (no Unicode literals in source) to stay byte-clean across editors.
$dashes = @(0x2013, 0x2014, 0x2212)
$squotes = @(0x2018, 0x2019, 0x201A, 0x2032)
$dquotes = @(0x201C, 0x201D, 0x201E, 0x2033)
function ConvertTo-Ascii([string]$s) {
    $sb = New-Object System.Text.StringBuilder
    foreach ($ch in $s.ToCharArray()) {
        $code = [int][char]$ch
        if ($dashes -contains $code) { [void]$sb.Append('-') }
        elseif ($squotes -contains $code) { [void]$sb.Append("'") }
        elseif ($dquotes -contains $code) { [void]$sb.Append('"') }
        elseif ($code -eq 0x2026) { [void]$sb.Append('..') }
        elseif ($code -le 127) { [void]$sb.Append($ch) }
    }
    return ($sb.ToString() -replace '\s+', ' ').Trim()
}

# Ordered area keyword map (first match wins)
$areaMap = @(
    @{ Area = "Network and Cloud"; Words = @("sftp", "ftp", "smb", "nas", "cloud", "onedrive", "dropbox", "drive", "cast", "chromecast", "network") }
    @{ Area = "Audio Player"; Words = @("audio", "midi", "cover-art", "cover art", "lyrics", "car-audio", "now playing", "track") }
    @{ Area = "Video Player"; Words = @("video", "exoplayer", "m2ts", "picture-in-picture", " pip ", "decoder", "playback", "media3") }
    @{ Area = "OCR and Translation"; Words = @("ocr", "translat", "tesseract", "ml kit") }
    @{ Area = "Slideshow"; Words = @("slideshow") }
    @{ Area = "Sharing"; Words = @("share", "instagram", "telegram", "google keep", " keep", "send to", "social") }
    @{ Area = "Documents"; Words = @("epub", "pdf", "reader", "tts", "read aloud", "e-book", "ebook") }
    @{ Area = "Text Editor"; Words = @("markdown", "calculator", "text editor", ".txt", ".md") }
    @{ Area = "Image Viewer"; Words = @("image", "gif", "crop", "draw", "annotat", "photo") }
    @{ Area = "VR"; Words = @("vr ", "passthrough", "openxr", "headset", "quest", "stereoscopic") }
    @{ Area = "Widgets"; Words = @("widget") }
    @{ Area = "File Operations"; Words = @("trash", "duplicate", "copy", "move", "delete", "file operation") }
    @{ Area = "Settings"; Words = @("settings", "toggle", "permission", "backup", "restore") }
)
$flavorWords = @{ vr = @("vr ", "passthrough", "openxr", "quest", "headset"); noLegal = @("nolegal", "no-legal", "yt-dlp", "newpipe"); lite = @("lite flavor"); photos = @("photos flavor"); legacy = @("legacy flavor", "api 23", "android 6") }

function Get-Area([string]$text) {
    $t = " " + $text.ToLowerInvariant() + " "
    foreach ($m in $areaMap) { foreach ($w in $m.Words) { if ($t.Contains($w)) { return $m.Area } } }
    return "General"
}
function Get-Flavors([string]$text) {
    $t = " " + $text.ToLowerInvariant() + " "
    $list = New-Object System.Collections.Generic.List[string]
    foreach ($k in $flavorWords.Keys) { foreach ($w in $flavorWords[$k]) { if ($t.Contains($w)) { if (-not $list.Contains($k)) { $list.Add($k) }; break } } }
    if ($list.Count -eq 0) { $list.Add("standard") }
    return ($list -join ",")
}
function Get-Slug([string]$text, [int]$maxWords) {
    $s = ($text.ToLowerInvariant() -replace '[^a-z0-9]+', '-').Trim('-')
    $parts = @($s -split '-' | Where-Object { $_ })
    if ($parts.Count -gt $maxWords) { $parts = $parts[0..($maxWords - 1)] }
    $r = ($parts -join '-')
    if (-not $r) { $r = "item" }
    return $r
}

$lineRe = '^\[(?<ts>[^\]]+)\]\s*\[(?<id>[^\]]+)\]\s*\[(?<op>[^\]]+)\]\s*(?<desc>.+)$'
$deleted = @{}
$records = [ordered]@{}

foreach ($raw in (Get-Content -LiteralPath $logFile -Encoding UTF8)) {
    if ($raw.TrimStart().StartsWith("#")) { continue }
    if ([string]::IsNullOrWhiteSpace($raw)) { continue }
    $m = [regex]::Match($raw, $lineRe)
    if (-not $m.Success) { continue }
    $idSlot = $m.Groups['id'].Value.Trim()
    $op = $m.Groups['op'].Value.Trim().ToUpperInvariant()
    $desc = ConvertTo-Ascii $m.Groups['desc'].Value
    if ([string]::IsNullOrWhiteSpace($desc)) { continue }

    $area = Get-Area $desc
    $nameRaw = ($desc -split ':')[0]
    $name = ($nameRaw.Substring(0, [Math]::Min(80, $nameRaw.Length))).Trim()
    if ($name.Length -lt 3) { $name = $desc.Substring(0, [Math]::Min(80, $desc.Length)).Trim() }
    $areaSlug = Get-Slug $area 3
    $featSlug = Get-Slug $name 6
    $baseId = "$areaSlug.$featSlug"

    if ($op -eq "DELETE") { $deleted[$baseId] = $true; continue }
    if ($op -ne "ADD" -and $op -ne "CHANGE") { continue }

    $spec = if ($idSlot -match '^S\d{4}$') { $idSlot } else { "" }
    $flavors = Get-Flavors $desc
    $records[$baseId] = [ordered]@{ id = $baseId; area = $area; name = $name; desc = $desc; flavors = $flavors; spec = $spec }
}

$written = 0
foreach ($key in $records.Keys) {
    if ($deleted.ContainsKey($key)) { continue }
    $r = $records[$key]
    $addArgs = @{ Id = $r.id; Area = $r.area; Name = $r.name; Description = $r.desc; Flavors = $r.flavors; Quiet = $true }
    if ($r.spec) { $addArgs.Spec = $r.spec }
    & $addScript @addArgs
    if ($LASTEXITCODE -ne 0) { Write-Error "add.ps1 failed for $($r.id)"; exit 1 }
    $written++
}

if (-not $Quiet) { Write-Host "[MIGRATE] wrote $written record(s) from FUNCTIONALITY.log" -ForegroundColor Green }
exit 0
