#requires -Version 7.0
# S4488: the tier of a check - Fast (5 s or less) or Release - and the question every runner asks before it
# starts one: "does this check run in the tier I was asked for?".
#
# The class of each check is data, not code: scripts/quality/gate-tiers.json, generated from the gate
# telemetry by generate-gate-tiers.ps1. This library only reads it. Dot-sourced by post-change.ps1,
# assert-fast-gates.ps1 and a.ps1, so it must stay free of state those scripts own.
#
# Which tier a run is in, most specific first:
#   1. the caller's explicit -Tier Fast|Release;
#   2. $env:FMS_TIER = Fast|Release - how a release flow run by an agent says so from the command line;
#   3. an agent shell ($env:CLAUDECODE = 1) is Fast: between releases an agent runs only checks of 5 s or less;
#   4. anything else - the owner's own shell, GitHub CI - is Release, exactly as every runner behaved before.
# A release script exports FMS_TIER=Release itself so an agent shell can never run it at the fast tier.
#
# Failing safe is the contract: an unreadable registry, an unknown tier word or a check with no row all
# resolve to "run it", never to "skip it", so this library can slow a run down but never weaken a release.

function Resolve-ActiveTier {
    param([string]$Requested)
    foreach ($candidate in @($Requested, $env:FMS_TIER)) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        if ($candidate -ieq 'Fast') { return 'Fast' }
        if ($candidate -ieq 'Release') { return 'Release' }
    }
    if ($env:CLAUDECODE -eq '1') { return 'Fast' }
    return 'Release'
}

$script:GateTierRegistry = $null
$script:GateTierRegistryLoaded = $false

function Get-GateTierRegistry {
    if ($script:GateTierRegistryLoaded) { return $script:GateTierRegistry }
    $script:GateTierRegistryLoaded = $true
    $path = Join-Path $PSScriptRoot '..\gate-tiers.json'
    try {
        if (Test-Path -LiteralPath $path) {
            $script:GateTierRegistry = Get-Content -LiteralPath $path -Raw -Encoding utf8 | ConvertFrom-Json
        }
    }
    catch {
        Write-Host "  [gate-tier] WARN - registry unreadable, every check runs: $($_.Exception.Message)" -ForegroundColor Yellow
        $script:GateTierRegistry = $null
    }
    return $script:GateTierRegistry
}

# Kind: gates (post-change step label), scripts (a gate script's file name), targets (an a.ps1 target).
# Returns the registry row or $null when the check has no row.
function Get-GateTierRow {
    param([ValidateSet('gates', 'scripts', 'targets')][string]$Kind, [string]$Name)
    $registry = Get-GateTierRegistry
    if ($null -eq $registry -or [string]::IsNullOrWhiteSpace($Name)) { return $null }
    $section = $registry.PSObject.Properties[$Kind]
    if ($null -eq $section -or $null -eq $section.Value) { return $null }
    $row = $section.Value.PSObject.Properties[$Name]
    if ($null -eq $row) { return $null }
    return $row.Value
}

# True when the check runs in $Tier. Release runs everything. Fast runs a check only when the registry
# says 'fast'; a check with no row is release until a release run has measured it.
# With no registry at all the answer is true (see the header: fail safe).
function Test-TierRuns {
    param([ValidateSet('gates', 'scripts', 'targets')][string]$Kind, [string]$Name, [string]$Tier)
    if ($Tier -ne 'Fast') { return $true }
    if ($null -eq (Get-GateTierRegistry)) { return $true }
    $row = Get-GateTierRow -Kind $Kind -Name $Name
    return ($null -ne $row -and [string]$row.tier -eq 'fast')
}

# The pooled-gate start sites know a script, not a label.
function Get-ArgvScriptName {
    param([string[]]$Argv)
    for ($i = 0; $i -lt $Argv.Count - 1; $i++) {
        if ($Argv[$i] -eq '-File') { return [System.IO.Path]::GetFileName([string]$Argv[$i + 1]) }
    }
    return $null
}

function Get-TierSkipNote {
    param([ValidateSet('gates', 'scripts', 'targets')][string]$Kind, [string]$Name)
    $row = Get-GateTierRow -Kind $Kind -Name $Name
    if ($null -eq $row) { return 'release tier (no recorded time) - runs at the release' }
    return "release tier (median $($row.medianSec) s over the 5 s line) - runs at the release"
}
