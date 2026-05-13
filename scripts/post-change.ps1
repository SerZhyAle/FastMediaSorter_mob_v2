# Combined post-change runner.
# Chains: dev-log → catalog scan → catalog render → (optional) strings check.
# Use -SkipScan for doc/resource-only changes that do not touch .kt files.
#
# Usage:
#   pwsh -File scripts/post-change.ps1 `
#       -File "app_v2/src/main/java/.../Foo.kt" `
#       -Target "FooClass" `
#       -Description "added bar feature" `
#       [-Module app_v2] [-KeyPrefix "foo_"] [-SkipScan]

param(
    [Parameter(Mandatory=$true)][string]$File,
    [Parameter(Mandatory=$true)][string]$Target,
    [Parameter(Mandatory=$true)][string]$Description,
    [string]$Module = "app_v2",
    [string]$KeyPrefix,
    [switch]$SkipScan
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$pwsh = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else {
    "pwsh"
}

$totalSw = [System.Diagnostics.Stopwatch]::StartNew()

function Step([string]$label, [scriptblock]$action) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    Write-Host "  [$label] " -ForegroundColor Cyan -NoNewline
    & $action
    $sw.Stop()
    Write-Host "$([int]$sw.Elapsed.TotalMilliseconds) ms" -ForegroundColor DarkGray
}

Write-Host "post-change: $File → $Target" -ForegroundColor Yellow

Step "dev-log" {
    & $pwsh -File (Join-Path $root "scripts/add_to_dev_log.ps1") $File $Target $Description
}

if (-not $SkipScan) {
    Step "scan" {
        & $pwsh -File (Join-Path $root "dev/CATALOG/scripts/scan.ps1") -Module $Module
    }
    Step "render" {
        & $pwsh -File (Join-Path $root "dev/CATALOG/scripts/render.ps1") -Module $Module
    }
} else {
    Write-Host "  [scan/render] skipped (-SkipScan)" -ForegroundColor DarkGray
}

if ($KeyPrefix) {
    Step "strings" {
        & $pwsh -File (Join-Path $root "scripts/check_strings_localized.ps1") -KeyPrefix $KeyPrefix
    }
}

$totalSw.Stop()
Write-Host "Done in $([math]::Round($totalSw.Elapsed.TotalSeconds, 1)) s" -ForegroundColor Green
