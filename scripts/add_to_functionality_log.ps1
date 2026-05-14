<#
.SYNOPSIS
    Appends a structured entry to the functionality log (dev/FUNCTIONALITY.log).

.DESCRIPTION
    Developer-facing history of user-visible functionality lifecycle
    (created, changed, deleted, fixed). Distinct from dev/CHANGELOG.md
    (low-level code-change journal) and docs/FEATURES.md (end-user catalogue).

    One line per record:
        [YYYY-MM-DD HH:MM] [Sxxxx|------] [OP    ] <english description>

    Trigger: a completed user-visible behaviour change. Skip refactors,
    internal optimisations, or anything a user cannot perceive.

.PARAMETER Id
    Spec ticket id in the form Sxxxx (4 digits). Empty/omitted -> the slot
    is rendered as [------], used for infrastructural records without a spec.

.PARAMETER Op
    Operation code. One of: ADD, CHANGE, DELETE, FIX (case-insensitive input,
    UPPER in the log, padded to 6 chars).

.PARAMETER Description
    Short English description of the change. Newlines are collapsed to spaces;
    surrounding whitespace is trimmed.

.EXAMPLE
    .\scripts\add_to_functionality_log.ps1 -Id S0123 -Op ADD -Description "Per-folder thumbnail cache size override"

.EXAMPLE
    .\scripts\add_to_functionality_log.ps1 -Op CHANGE -Description "Player overflow menu reordered for one-hand reach"

.EXAMPLE
    .\scripts\add_to_functionality_log.ps1 -Id S0099 -Op FIX -Description "StandalonePlayer delete IAE on API 35"
#>
param(
    [Parameter(Mandatory = $false)]
    [string]$Id = "",

    [Parameter(Mandatory = $true)]
    [string]$Op,

    [Parameter(Mandatory = $true)]
    [string]$Description
)

$ErrorActionPreference = "Stop"

# Validate Id (empty allowed; if present must be Sxxxx)
$idSlot = "------"
if (-not [string]::IsNullOrWhiteSpace($Id)) {
    $trimmedId = $Id.Trim()
    if ($trimmedId -notmatch '^S\d{4}$') {
        Write-Error "Invalid -Id '$trimmedId'. Expected format: Sxxxx (e.g. S0123) or empty."
        exit 1
    }
    $idSlot = $trimmedId
}

# Validate Op
$opUpper = $Op.Trim().ToUpperInvariant()
$validOps = @("ADD", "CHANGE", "DELETE", "FIX")
if ($validOps -notcontains $opUpper) {
    Write-Error "Invalid -Op '$Op'. Expected one of: ADD, CHANGE, DELETE, FIX."
    exit 1
}
$opSlot = $opUpper.PadRight(6, ' ')

# Sanitize description: collapse newlines, trim, squeeze internal whitespace
$desc = $Description -replace "`r`n", " " -replace "`n", " " -replace "`r", " "
$desc = ($desc -replace '\s+', ' ').Trim()
if ([string]::IsNullOrWhiteSpace($desc)) {
    Write-Error "Description is empty after sanitisation."
    exit 1
}

# Resolve paths -- script lives at <repo>/scripts/add_to_functionality_log.ps1
$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }
$repoRoot = Split-Path -Parent $scriptDir
if (-not $repoRoot -or -not (Test-Path (Join-Path $repoRoot "settings.gradle.kts"))) {
    $repoRoot = (Get-Location).Path
}
$logFile = Join-Path (Join-Path $repoRoot "dev") "FUNCTIONALITY.log"

# UTF-8 (no BOM) encoding object for all writes
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

# Create log file with header if it doesn't exist
if (-not (Test-Path $logFile)) {
    $headerLines = @(
        "# Functionality Log",
        "# Developer-facing history of user-visible functionality (created/changed/deleted/fixed).",
        "# Format: [YYYY-MM-DD HH:MM] [Sxxxx|------] [OP    ] <english description>",
        "# User-facing feature catalogue: docs/FEATURES.md.",
        "# Code-change journal: dev/CHANGELOG.md.",
        ""
    )
    $headerText = ($headerLines -join "`n") + "`n"
    [System.IO.File]::WriteAllText($logFile, $headerText, $utf8NoBom)
}

# Compose and append entry (always ends with newline)
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm"
$entry = "[$timestamp] [$idSlot] [$opSlot] $desc"
[System.IO.File]::AppendAllText($logFile, $entry + "`n", $utf8NoBom)

Write-Host "[FUNC_LOG] [$idSlot] [$($opUpper)] $desc" -ForegroundColor Green
