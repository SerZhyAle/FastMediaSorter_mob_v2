<#
.SYNOPSIS
    S1984 - every device-independent pre-release gate, for every module, from one place.

.DESCRIPTION
    The pre-release sweep splits in two: gates that judge the tree, and steps that drive a device.
    The first half is identical for the phone and for the watch, and until this script existed it
    was written out per module inside the phone command - so the watch was covered only where a
    gate had gained a `-Module` parameter for an unrelated reason. Coverage by coincidence is what
    strategic S1984 section 4 names as the defect: a gate added later reaches the phone and stops.

    The module list lives here, in this script's own default. A caller names a module only to
    narrow a run deliberately; adding a module to the release contract is an edit to this file and
    to nothing else.

    Every gate runs even after one fails, because a sweep that stops at the first finding hides how
    much work the operator actually has.

.PARAMETER Modules
    Modules to judge with the per-module gates. Default: both shipped modules.

.PARAMETER Json
    Emit one JSON object with the per-gate results instead of the human lines.

.PARAMETER Quiet
    Suppress the per-gate pass lines. Findings and the final verdict are still printed.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-prerelease-content-gates.ps1
    pwsh -NoProfile -File scripts/quality/assert-prerelease-content-gates.ps1 -Modules wear

.NOTES
    Exit codes:
      0  every gating gate passed for every requested module; an advisory finding still exits 0
      1  at least one gating gate found a defect, and no gate was unable to verify
      2  at least one gate could not verify - its own exit 2, or the gate script is missing

    Code 2 outranks code 1 on purpose. "Did not look" and "found a defect" are different answers,
    and a release sweep that reads an unverifiable gate as a mere failure would let the operator
    fix what was reported and ship what was never judged.

    One gate is advisory rather than gating: guide coverage reports a capability no guide mentions,
    which the release campaign writes up later and which has never blocked a release. Its finding is
    printed and returned in -Json, and it does not move the verdict - collapsing it into a blocker
    here would change what the phone sweep proves, which strategic S1984 section 2 forbids.
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string[]]$Modules = @('app_v2', 'wear'),

    [switch]$Json,

    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gateDir = Join-Path $repoRoot 'scripts/quality'

$results = @()

function Invoke-Gate {
    param(
        [Parameter(Mandatory)][string]$Name,
        [string]$Module,
        [string[]]$ExtraArgs = @(),
        [switch]$Advisory
    )

    $path = Join-Path $gateDir $Name
    $label = if ($Module) { "$Name $Module" } else { $Name }

    if (-not (Test-Path -LiteralPath $path)) {
        Write-Host "content-gates: $label -> missing gate script" -ForegroundColor Yellow
        return [pscustomobject]@{ Gate = $Name; Module = $Module; Exit = 2; Advisory = [bool]$Advisory; Detail = "gate script not found: $path" }
    }

    $callArgs = @('-NoProfile', '-File', $path)
    if ($Module) { $callArgs += @('-Module', $Module) }
    $callArgs += $ExtraArgs

    $output = & pwsh @callArgs 2>&1
    $code = $LASTEXITCODE

    if ($code -ne 0 -or -not $Quiet) {
        $colour = if ($code -eq 0) { 'Green' } elseif ($code -eq 1 -and $Advisory) { 'Yellow' } elseif ($code -eq 1) { 'Red' } else { 'Yellow' }
        $suffix = if ($Advisory -and $code -eq 1) { ' (advisory)' } else { '' }
        Write-Host "content-gates: $label -> $code$suffix" -ForegroundColor $colour
    }
    if ($code -ne 0) { Write-Host ($output -join "`n") }

    return [pscustomobject]@{ Gate = $Name; Module = $Module; Exit = $code; Advisory = [bool]$Advisory; Detail = ($output -join "`n") }
}

foreach ($m in $Modules) {
    $results += Invoke-Gate -Name 'assert-new-lexemes-translated.ps1' -Module $m
}
foreach ($m in $Modules) {
    $results += Invoke-Gate -Name 'assert-no-orphan-merged-resources.ps1' -Module $m
}

$results += Invoke-Gate -Name 'assert-guide-coverage.ps1' -ExtraArgs @('-Gate') -Advisory

# Deliberately no -Module: this gate defaults to every module it supports, so narrowing it here
# would silently drop a module the caller did not ask to drop.
$results += Invoke-Gate -Name 'assert-splash-brand-sync.ps1'

$unverifiable = @($results | Where-Object { $_.Exit -ne 0 -and $_.Exit -ne 1 })
$failed = @($results | Where-Object { $_.Exit -eq 1 -and -not $_.Advisory })
$advisories = @($results | Where-Object { $_.Exit -eq 1 -and $_.Advisory })

$verdict = if ($unverifiable.Count -gt 0) { 2 } elseif ($failed.Count -gt 0) { 1 } else { 0 }

if ($Json) {
    [pscustomobject]@{
        ok         = ($verdict -eq 0)
        exitCode   = $verdict
        modules    = $Modules
        advisories = $advisories.Count
        gates      = $results | ForEach-Object {
            [pscustomobject]@{ gate = $_.Gate; module = $_.Module; exit = $_.Exit; advisory = $_.Advisory }
        }
    } | ConvertTo-Json -Depth 4 -Compress
}

function Format-GateNames {
    param([object[]]$Rows)
    return (($Rows | ForEach-Object { if ($_.Module) { "$($_.Gate) ($($_.Module))" } else { $_.Gate } }) -join ', ')
}

if ($verdict -eq 2) {
    if (-not $Json) {
        Write-Error "assert-prerelease-content-gates: could not verify - $(Format-GateNames $unverifiable)" -ErrorAction Continue
    }
    exit 2
}
if ($verdict -eq 1) {
    if (-not $Json) {
        Write-Error "assert-prerelease-content-gates: failing gates - $(Format-GateNames $failed)" -ErrorAction Continue
    }
    exit 1
}

if (-not $Json) {
    if ($advisories.Count -gt 0) {
        Write-Host "assert-prerelease-content-gates: PASS WITH ADVISORIES ($($advisories.Count)) - $(Format-GateNames $advisories)" -ForegroundColor Yellow
    } elseif (-not $Quiet) {
        Write-Host "assert-prerelease-content-gates: PASS (modules: $($Modules -join ', '))." -ForegroundColor Green
    }
}
exit 0
