#requires -Version 7.0
<#
.SYNOPSIS
    Coordinate device access through the machine-wide SZA device lease tool.

.DESCRIPTION
    Selects the explicit SZA harness first, then the newest installed plugin with the tool.
    Older installations use a warned, repository-local fallback. Physical keys are ADB
    serials. Emulator keys are avd:<name>.

.PARAMETER StaleMinutes
    Compatibility parameter for the local fallback. Shared leases use SZA's Device timing.

.NOTES
    Exit codes: 0 done, 1 error, 3 claim lost, 4 release refused.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Claim', 'Release', 'List', 'Status', 'Sweep')][string]$Verb,
    [string[]]$Id,
    [string]$Reason = 'device-lease',
    [switch]$Json,
    [int]$StaleMinutes = 0,
    [switch]$Any,
    [switch]$All
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
. (Join-Path $PSScriptRoot 'lib/device-lease-client.ps1')

$shared = Find-SharedDeviceLease
if ($shared) {
    $PSBoundParameters.Remove('StaleMinutes') | Out-Null
    $previousRoot = $env:SZA_PROJECT_ROOT
    try {
        $env:SZA_PROJECT_ROOT = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
        & $shared @PSBoundParameters
        $code = $LASTEXITCODE
    }
    finally { $env:SZA_PROJECT_ROOT = $previousRoot }
    exit $code
}

[Console]::Error.WriteLine('WARNING: Shared SZA device lease tool unavailable. Using the repository-local store.')
$local = Join-Path $PSScriptRoot 'lib/device-lease-local.ps1'
$keys = @($Id -split '[,;]' | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Select-Object -Unique)
$common = @{ Reason = $Reason; StaleMinutes = $StaleMinutes }

if ($Verb -eq 'Release' -and $All) {
    $owned = @(& $local -Verb Status -Json -ReadOnly @common | ConvertFrom-Json | Where-Object { $_.mine })
    $keys = @(@($keys) + @($owned.id) | Where-Object { $_ } | Select-Object -Unique)
    if ($keys.Count -eq 0) {
        if ($Json) { '{"outcome":"absent","released":[],"refused":[]}' }
        else { 'device-lease: this session holds no lease.' }
        exit 0
    }
}
if ($keys.Count -le 1 -or $Verb -notin @('Claim', 'Release')) {
    & $local -Verb $Verb -Id ($keys -join '') -Json:$Json @common
    exit $LASTEXITCODE
}

foreach ($key in $keys) {
    if ($key -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._:-]{0,95}$') {
        [Console]::Error.WriteLine("device-lease: invalid target key '$key'.")
        exit 1
    }
}
$changed = [Collections.Generic.List[string]]::new()
$refused = [Collections.Generic.List[string]]::new()
$absent = [Collections.Generic.List[string]]::new()
foreach ($key in $keys) {
    $output = & $local -Verb $Verb -Id $key -Json @common
    $code = $LASTEXITCODE
    if ($code -eq 0) {
        $result = $output | ConvertFrom-Json
        if ($result.outcome -in @('claimed', 'released')) { $changed.Add($key) }
        if ($result.outcome -eq 'absent') { $absent.Add($key) }
        if ($Verb -eq 'Claim' -and $Any) {
            if ($Json) { $output } else { "device-lease: $key ($($result.outcome))." }
            exit 0
        }
        continue
    }
    if ($Verb -eq 'Release' -and $code -eq 4) { $refused.Add($key); continue }
    if ($Verb -eq 'Claim' -and $Any -and $code -eq 3) { $refused.Add($key); continue }
    if ($Verb -eq 'Claim') {
        foreach ($acquired in $changed) { & $local -Verb Release -Id $acquired -Json @common | Out-Null }
    }
    if ($Json) { $output } else { "device-lease: $Verb failed for '$key' (exit $code)." }
    exit $code
}
if ($Verb -eq 'Claim') {
    if ($refused.Count -gt 0) { $outcome = 'claim-lost'; $code = 3 }
    elseif ($changed.Count -eq 0) { $outcome = 'already-mine'; $code = 0 }
    else { $outcome = 'claimed'; $code = 0 }
    $result = @{ outcome = $outcome; id = $keys[0]; ids = $keys }
}
else {
    if ($refused.Count -gt 0) { $outcome = 'release-refused'; $code = 4 }
    elseif ($changed.Count -eq 0) { $outcome = 'absent'; $code = 0 }
    else { $outcome = 'released'; $code = 0 }
    $result = @{ outcome = $outcome; released = @($changed); refused = @($refused); absent = @($absent) }
}
if ($Json) { $result | ConvertTo-Json -Compress }
else { "device-lease: $outcome ($($keys -join ', '))." }
exit $code
