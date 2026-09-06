#requires -Version 7.0
<#
.SYNOPSIS
    Which device module a ticket's on-device test belongs to: app_v2 (phone) or wear (watch).

.DESCRIPTION
    S2611. `scripts/devtest/device-ready.ps1 -Module` selects an attached device by form factor, and
    every caller has to name the module. Eight callers name a constant one, because what they run is
    fixed. Four do not: `/spec-sweep` and `/spec-test-device` enter from a ticket that is already
    parked, `/spec-dev` and `/spec-all` from a run that resolved a module of its own. This script
    answers for the first pair.

    The answer is READ, never guessed: it is the module holding the ticket's debug probes
    (`Timber.d("Sxxxx: ..")`). Those are what the pending device test exists to observe, so the
    device that can produce them is by definition the right one. Both commands operate on a
    `BlockNeedUserTest` ticket, and `check-probe-present.ps1` already refuses that status without a
    probe, so for the callers that need this the answer is present by construction.

    "Carries a probe" is decided by scripts/quality/lib/blockneedusertest-probes.ps1, the same
    helper the tree gate and the closing gate share (S1621) - a third opinion here would let this
    script send a run to a device the gates disagree about. The module behind a path comes from
    scripts/utils/gradle-modules.ps1, which S2121 made the single home of what is true about a
    module, and the phone/watch spelling from its `DeviceModule` field.

    Guessing the form factor instead of reading it is the S2600 incident: a watch-sourced verdict
    was written into a phone ticket's spec. So the two uncertain cases answer differently. No probe
    anywhere -> `app_v2` with the reason stated, because a ticket excused from probes
    (blockneedusertest-probe-baseline.txt) changed no executable path and the phone is the fleet
    default (CLAUDE.md Rule 35). Probes in BOTH modules -> refusal, because one device run cannot
    cover a phone and a watch, and answering with either one would hide half the ticket.

.PARAMETER Id
    The ticket, `Sxxxx`.

.PARAMETER Json
    Emit a single JSON object instead of human-readable lines.

.PARAMETER RepoRoot
    Which tree to scan for probes. Defaults to this repository. Exists so the contract suite can
    point at a throwaway tree instead of asserting against whatever tickets happen to be parked
    today - a suite that reads live state passes vacuously the week nothing is parked.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/resolve-ticket-module.ps1 -Id S2600
    Prints `module: app_v2` plus the probe file that decided it.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/resolve-ticket-module.ps1 -Id S2600 -Json
    Machine-readable: { id, module, reason, probeModules, probeFiles }.

.NOTES
    Exit codes:
      0  resolved - `module` is app_v2 or wear, and `reason` says how it was decided.
      2  cannot verify - the probe helper, the module table or every source root is missing.
      3  the ticket carries probes in more than one module, so no single device run covers it.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^S\d{4}$')]
    [string]$Id,
    [switch]$Json,
    [string]$RepoRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

# The two libraries always come from THIS checkout; only the tree being scanned is overridable.
$scriptRepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$repoRoot = if ($RepoRoot) { (Resolve-Path -LiteralPath $RepoRoot).Path } else { $scriptRepoRoot }

$probeLib = Join-Path $scriptRepoRoot 'scripts/quality/lib/blockneedusertest-probes.ps1'
$moduleLib = Join-Path $scriptRepoRoot 'scripts/utils/gradle-modules.ps1'
foreach ($lib in @($probeLib, $moduleLib)) {
    if (-not (Test-Path -LiteralPath $lib)) {
        Write-Error "resolve-ticket-module: cannot run - $lib is missing." -ErrorAction Continue
        exit 2
    }
}
. $probeLib
. $moduleLib

$sourceRoots = @(Get-ProbeSourceRoot -RepoRoot $repoRoot)
if ($sourceRoots.Count -eq 0) {
    Write-Error "resolve-ticket-module: cannot run - no probe source root exists under $repoRoot." -ErrorAction Continue
    exit 2
}

# One root at a time, not all of them together: Test-TicketProbeInSource stops at its first hit,
# which is the right answer for "does a probe exist" and the wrong one here - a ticket with probes
# on both sides would report whichever root sorted first and look unambiguous.
$hits = @()
foreach ($root in $sourceRoots) {
    $found = Test-TicketProbeInSource -Id $Id -SourceRoots @($root)
    if (-not $found.Found) { continue }
    $relRoot = ($root.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/')
    $resolved = Resolve-GradleModulesForPaths -Path "$relRoot/"
    if ($resolved.Modules.Count -ne 1) {
        Write-Error "resolve-ticket-module: cannot run - probe source root '$relRoot' resolves to $($resolved.Modules.Count) Gradle modules; scripts/utils/gradle-modules.ps1 needs a row that covers it." -ErrorAction Continue
        exit 2
    }
    $hits += [pscustomobject]@{
        DeviceModule = (Get-GradleModuleDeviceModule -Name $resolved.Modules[0])
        File         = (($found.File -replace '\\', '/') -replace [regex]::Escape((($repoRoot -replace '\\', '/') + '/')), '')
    }
}

$deviceModules = @($hits | ForEach-Object { $_.DeviceModule } | Sort-Object -Unique)

if ($deviceModules.Count -gt 1) {
    Write-Error "resolve-ticket-module: $Id carries probes in more than one module ($($deviceModules -join ', ')). One device run covers one form factor, so pick the module deliberately and run the other separately - answering with either alone would leave half the ticket untested." -ErrorAction Continue
    exit 3
}

if ($deviceModules.Count -eq 1) {
    $module = $deviceModules[0]
    $reason = "probe found in $($hits[0].File)"
}
else {
    $module = 'app_v2'
    $reason = "no probe for $Id under $((Get-ProbeSourceRoot -RepoRoot $repoRoot | ForEach-Object { Split-Path -Leaf $_ }) -join ' or ') - defaulted to the phone, which is the fleet default"
}

if ($Json) {
    [pscustomobject]@{
        id           = $Id
        module       = $module
        reason       = $reason
        probeModules = $deviceModules
        probeFiles   = @($hits | ForEach-Object { $_.File })
    } | ConvertTo-Json -Compress
}
else {
    Write-Output "module: $module"
    Write-Output "reason: $reason"
}
exit 0
