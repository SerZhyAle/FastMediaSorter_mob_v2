#requires -Version 7.0
<#
.SYNOPSIS
    Rejects raw Wear navigation route and argument literals outside route registries.
#>
[CmdletBinding()]
param([switch]$Gate)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$wearRoot = Join-Path $root 'wear/src'
$registryFiles = @('WearRoutes.kt', 'SettingsRoutes.kt')
$patterns = @(
    '(?:composable|navigate|popUpTo|navArgument)\s*\(\s*"',
    'arguments\??\.(?:getString|getInt|getLong|getBoolean)\s*\(\s*"',
    'startDestination\s*=\s*"'
)
$violations = @()

Get-ChildItem -LiteralPath $wearRoot -Recurse -Filter '*.kt' | Where-Object {
    $_.Name -notin $registryFiles
} | ForEach-Object {
    $file = $_
    $lineNumber = 0
    Get-Content -LiteralPath $file.FullName | ForEach-Object {
        $lineNumber++
        foreach ($pattern in $patterns) {
            if ($_ -match $pattern) {
                $relativePath = $file.FullName.Substring($root.Length + 1)
                $violations += "${relativePath}:$lineNumber"
                break
            }
        }
    }
}

if ($violations.Count -eq 0) {
    Write-Host 'assert-wear-route-literals: PASS - no raw navigation route or argument literals.'
    exit 0
}

Write-Error ("assert-wear-route-literals: FAIL - raw literals found:`n" + ($violations -join "`n")) -ErrorAction Continue
if ($Gate) { exit 1 }
exit 0
