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
    [string]$Branch = ""
)

$ErrorActionPreference = "Stop"

# Detect current git branch for changelog context
if ([string]::IsNullOrEmpty($Branch)) {
    $detectedBranch = (git branch --show-current 2>$null).Trim()
    if ([string]::IsNullOrEmpty($detectedBranch)) {
        # Detached HEAD — use short SHA
        $shortSha = (git rev-parse --short HEAD 2>$null).Trim()
        $detectedBranch = if ($shortSha) { "detached/$shortSha" } else { "unknown" }
    }
    $Branch = $detectedBranch
}

# Resolve paths — script is at <repo>/scripts/add_to_dev_log.ps1
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

# Append entry
$branchTag = "[branch: $Branch]"
$entry = "| $timestamp | ``$safeFile`` | ``$safeTarget`` | $safeDesc $branchTag |"
Add-Content -Path $logFile -Value $entry -Encoding UTF8

Write-Host "[DEV_LOG] $timestamp | $safeFile | $safeTarget | $safeDesc $branchTag" -ForegroundColor Green
