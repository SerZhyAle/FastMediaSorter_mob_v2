<#
.SYNOPSIS
    Appends a structured entry to the development changelog (dev/CHANGELOG.md).

.DESCRIPTION
    Every code modification by an AI agent or developer must be logged.
    This script appends a timestamped line to dev/CHANGELOG.md in a
    machine-readable, grep-friendly format.

.PARAMETER FilePath
    Relative path to the modified file (e.g. app_v2/src/.../MyClass.kt).

.PARAMETER Target
    Class name, function name, or module affected (e.g. "GoogleDriveRestClient" or "shouldRefreshToken").

.PARAMETER Description
    Short English description of the change and its purpose.

.EXAMPLE
    .\scripts\add_to_dev_log.ps1 "app_v2/src/.../GlideAppModule.kt" "GlideAppModule" "Fixed memory cache formula: heap*10% cap 64MB instead of availMem*40%"

.EXAMPLE
    .\scripts\add_to_dev_log.ps1 "AGENTS.md" "AGENTS.md" "Added mandatory dev changelog logging rule"
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$FilePath,

    [Parameter(Mandatory = $true, Position = 1)]
    [string]$Target,

    [Parameter(Mandatory = $true, Position = 2)]
    [string]$Description,

    [Parameter(Mandatory = $false)]
    [string]$Branch = "",

    # Bypass the recent-duplicate guard (e.g. a genuine second identical-desc change).
    [Parameter(Mandatory = $false)]
    [switch]$AllowDuplicate
)

$ErrorActionPreference = "Stop"

# Detect current git branch for changelog context
if ([string]::IsNullOrEmpty($Branch)) {
    $detectedBranch = (git branch --show-current 2>$null).Trim()
    if ([string]::IsNullOrEmpty($detectedBranch)) {
        # Detached HEAD - use short SHA
        $shortSha = (git rev-parse --short HEAD 2>$null).Trim()
        $detectedBranch = if ($shortSha) { "detached/$shortSha" } else { "unknown" }
    }
    $Branch = $detectedBranch
}

# Resolve paths - script is at <repo>/scripts/add_to_dev_log.ps1
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
$repoRoot = Split-Path -Parent $scriptDir
if (-not $repoRoot -or -not (Test-Path (Join-Path $repoRoot "settings.gradle.kts"))) {
    $repoRoot = (Get-Location).Path
}
$logFile = Join-Path (Join-Path $repoRoot "dev") "CHANGELOG.md"

# Create log file with header if it doesn't exist
if (-not (Test-Path $logFile)) {
    $header = @"
# Development Changelog

Auto-generated log of all code modifications.
Format: `| datetime | file | target | description |`

---

| DateTime | File | Target | Description |
|----------|------|--------|-------------|
"@
    New-Item -ItemType File -Path $logFile -Force | Out-Null
    Set-Content -Path $logFile -Value $header -Encoding UTF8
}

# Generate timestamp
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

# Sanitize inputs (escape pipe characters for Markdown table)
$safeFile = $FilePath -replace '\|', '\|'
$safeTarget = $Target -replace '\|', '\|'
$safeDesc = $Description -replace '\|', '\|'

# Dedup guard: a retried post-change / facade re-run must not append an identical
# entry. Compare the semantic signature (file | target | desc), ignoring the
# timestamp and branch tag, against the most recent entries. Observed failure:
# three identical S1181 rows within 6 min from repeated post-change runs.
#
# S1622: the `[set of N: ..]` tail is written by post-change.ps1, not by the caller, and
# it is the one part of the description that CHANGES between two runs of the same closure -
# fixing what an advisory named usually regenerates a file, which then joins the set. So it
# is normalised out of both sides: it may not identify the change. Everything the caller
# actually wrote still does. Measured 2026-08-13: since the tail appeared on 2026-08-08 the
# guard caught every exact repeat, and the only two rows that escaped it were of this shape.
#
# The signature closes on the `[branch:` marker that follows the description in every row,
# so it matches a whole description and not a prefix of one - before this, a row described
# as "Fix A" counted as a duplicate of a row described as "Fix A and B".
#
# S2072: the anchor file is NOT part of the key either. post-change.ps1 writes the first element
# of the caller's -Files set into the File column, so the anchor encodes file ORDER, not the
# identity of the change - and the closure rule asks the caller to name the whole set, which an
# agent re-running a closure after fixing a gate naturally rebuilds shorter or in another order.
# That moved the anchor, the signature missed, and a second permanent row landed for one logical
# change (observed on S2000: same target, same description, 37-file set then 16-file set). What
# identifies a change is what the caller asserted about it - target plus description - so those
# are the whole key. Two genuinely distinct changes sharing both, inside the 8-row window, are
# what -AllowDuplicate is for; collapsing them is also what Rule 12 journalling granularity asks.
$setSuffixInDesc = '\s*\[set of \d+:[^\]]*\]\s*$'
# In a written row the tail sits immediately before the branch tag; anchoring there keeps a
# `[set of ..]` a caller typed inside its own prose out of it.
$setSuffixInRow  = '\s*\[set of \d+:[^\]]*\](?=\s*\[branch:)'
$coreDesc = $safeDesc -replace $setSuffixInDesc, ''
$signature = "``$safeTarget`` | $coreDesc [branch:"
if (-not $AllowDuplicate) {
    $dataRows = @(Get-Content -Path $logFile -Encoding UTF8 | Where-Object { $_ -match '^\|\s\d{4}-\d{2}-\d{2}' })
    if ($dataRows.Count -gt 0) {
        $windowStart = [Math]::Max(0, $dataRows.Count - 8)
        $recent = $dataRows[$windowStart..($dataRows.Count - 1)]
        foreach ($row in $recent) {
            if (($row -replace $setSuffixInRow, '').Contains($signature)) {
                Write-Host "[DEV_LOG] SKIP duplicate (identical to a recent entry): $safeFile | $safeTarget | $safeDesc" -ForegroundColor Yellow
                exit 0
            }
        }
    }
}

# Append entry
$branchTag = "[branch: $Branch]"
$entry = "| $timestamp | ``$safeFile`` | ``$safeTarget`` | $safeDesc $branchTag |"
Add-Content -Path $logFile -Value $entry -Encoding UTF8

Write-Host "[DEV_LOG] $timestamp | $safeFile | $safeTarget | $safeDesc $branchTag" -ForegroundColor Green
