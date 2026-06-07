# Combined post-change runner.
# Chains the applicable mechanical post-change steps for a given change type.
#
# Usage:
#   pwsh -NoProfile -File scripts/post-change.ps1 `
#       -File "scripts/post-change.ps1" `
#       -Target "post-change.ps1" `
#       -Description "added change-type routing" `
#       -ChangeType Script
#
#   pwsh -NoProfile -File scripts/post-change.ps1 `
#       -File "app_v2/src/main/java/.../Foo.kt" `
#       -Target "FooClass" `
#       -Description "added bar feature" `
#       -ChangeType Kotlin `
#       [-Module app_v2]
#
#   pwsh -NoProfile -File scripts/post-change.ps1 `
#       -File "app_v2/src/main/res/values/strings.xml" `
#       -Target "string/foo_title" `
#       -Description "added foo strings" `
#       -ChangeType Xml `
#       -KeyPrefix "foo_"
#
# Backward compatibility:
#   -SkipScan + -KeyPrefix => Xml
#   -SkipScan              => Doc
#   -KeyPrefix             => Mixed
#   no router flags        => Kotlin

param(
    [Parameter(Mandatory = $true)][string]$File,
    [Parameter(Mandatory = $true)][string]$Target,
    [Parameter(Mandatory = $true)][string]$Description,
    [ValidateSet('Doc', 'Script', 'Config', 'Kotlin', 'Xml', 'Mixed')]
    [string]$ChangeType,
    [string]$Module = "app_v2",
    [string]$KeyPrefix,
    [switch]$SkipScan
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

$pwsh = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    "pwsh"
}

$totalSw = [System.Diagnostics.Stopwatch]::StartNew()

function Write-StepResult(
    [string]$Label,
    [ValidateSet('PASS', 'FAIL', 'SKIP')][string]$Status,
    [int]$ElapsedMs,
    [string]$Details = ''
) {
    $color = switch ($Status) {
        'PASS' { 'Green' }
        'FAIL' { 'Red' }
        default { 'DarkGray' }
    }

    $message = "  [$Label] $Status"
    if ($ElapsedMs -ge 0) {
        $message += " ($ElapsedMs ms)"
    }
    if (-not [string]::IsNullOrWhiteSpace($Details)) {
        $message += " - $Details"
    }

    Write-Host $message -ForegroundColor $color
}

function Invoke-Step([string]$Label, [scriptblock]$Action) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    try {
        $global:LASTEXITCODE = 0
        & $Action
        $exitCode = if ($LASTEXITCODE) { [int]$LASTEXITCODE } else { 0 }
        if ($exitCode -ne 0) {
            throw "exit $exitCode"
        }

        $sw.Stop()
        Write-StepResult -Label $Label -Status PASS -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds)
    }
    catch {
        $sw.Stop()
        $exitCode = if ($LASTEXITCODE -and [int]$LASTEXITCODE -ne 0) { [int]$LASTEXITCODE } else { 1 }
        $reason = $_.Exception.Message
        if ($reason -eq "exit $exitCode") {
            $reason = "child exit code $exitCode"
        }

        Write-StepResult -Label $Label -Status FAIL -ElapsedMs ([int]$sw.Elapsed.TotalMilliseconds) -Details $reason
        exit $exitCode
    }
}

function Skip-Step([string]$Label, [string]$Reason) {
    Write-StepResult -Label $Label -Status SKIP -ElapsedMs 0 -Details $Reason
}

$resolvedChangeType = if ($PSBoundParameters.ContainsKey('ChangeType')) {
    $ChangeType
}
elseif ($SkipScan -and -not [string]::IsNullOrWhiteSpace($KeyPrefix)) {
    'Xml'
}
elseif ($SkipScan) {
    'Doc'
}
elseif (-not [string]::IsNullOrWhiteSpace($KeyPrefix)) {
    'Mixed'
}
else {
    'Kotlin'
}

$runsCatalogSync = $resolvedChangeType -in @('Kotlin', 'Mixed')
$runsStringsAudit = $resolvedChangeType -in @('Xml', 'Mixed')
$runsTicketLogAudit = $resolvedChangeType -in @('Kotlin', 'Mixed')
$runsDocPinsSync = $resolvedChangeType -in @('Config', 'Doc', 'Mixed')
$runsFlavorFlagGate = $resolvedChangeType -in @('Kotlin', 'Mixed')

Write-Host "post-change: $resolvedChangeType | $File -> $Target" -ForegroundColor Yellow

if ($SkipScan) {
    Write-Host "  [compat] -SkipScan is deprecated; resolved ChangeType=$resolvedChangeType" -ForegroundColor DarkGray
}

Invoke-Step "dev-log" {
    & $pwsh -NoProfile -File (Join-Path $root "scripts/add_to_dev_log.ps1") $File $Target $Description
}

Skip-Step "feature-docs" "skill-owned; evaluate only for new public capability"
Skip-Step "functionality-log" "skill-owned; evaluate only for user-visible behaviour change"

if ($runsCatalogSync) {
    Invoke-Step "catalog-sync" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/catalog_sync.ps1") -Module $Module
    }
}
else {
    Skip-Step "catalog-sync" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsStringsAudit) {
    if (-not [string]::IsNullOrWhiteSpace($KeyPrefix)) {
        Invoke-Step "strings-audit" {
            & $pwsh -NoProfile -File (Join-Path $root "scripts/check_strings_localized.ps1") -Module $Module -KeyPrefix $KeyPrefix
        }
    }
    else {
        Skip-Step "strings-audit" "ChangeType $resolvedChangeType requires -KeyPrefix"
    }
}
else {
    Skip-Step "strings-audit" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsTicketLogAudit) {
    Invoke-Step "ticket-log-audit" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-no-ticket-logs.ps1") -Gate -Quiet
    }
}
else {
    Skip-Step "ticket-log-audit" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsDocPinsSync) {
    Invoke-Step "doc-pins-sync" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/generate-toolchain-pins.ps1") -Check
    }
}
else {
    Skip-Step "doc-pins-sync" "not applicable for ChangeType $resolvedChangeType"
}

if ($runsFlavorFlagGate) {
    Invoke-Step "flavor-flag-gate" {
        & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-flavor-flags-not-growing.ps1") -Gate
    }
}
else {
    Skip-Step "flavor-flag-gate" "not applicable for ChangeType $resolvedChangeType"
}

Skip-Step "spec-catalog-sync" "skill-owned; run only on spec status transition"

$totalSw.Stop()
Write-Host "post-change: PASS ($resolvedChangeType, $([int]$totalSw.Elapsed.TotalMilliseconds) ms)" -ForegroundColor Green
exit 0
