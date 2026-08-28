#requires -Version 7.0
<#
.SYNOPSIS
    Parity gate: the wear module's mirrored resource-icon set must match app_v2's source of truth.

.DESCRIPTION
    S2129 ships a resource's own icon to the watch BY REFERENCE (ADR-1): the sync payload carries
    the short `ico-NN-NNN` id and the watch resolves it against its own copy of the vectors. That
    only works while the copy matches, and the copy is generated, not maintained:

      app_v2/src/main/res/drawable/ico_*.xml          (source, hand-authored)
        -> wear/src/main/res/drawable/ico_*.xml        (generated copy)
        -> wear/.../wear/ui/icon/WearResourceIconRegistry.kt  (generated map)

    Drift here fails SILENTLY, which is why it needs a machine rather than a reviewer. A resource
    arrives carrying an id the watch does not hold, `resolveDrawable` answers null, and the watch
    quietly falls back to the type-derived glyph - restoring the exact defect S2129 removes, with
    no crash, no log and no visible symptom to trace back. Strategic §7 rates this the
    highest-probability risk of the phase.

    Thin forwarder: the comparison itself lives once in the generator's -Check mode, so the gate
    and the fix are the same code and cannot disagree about what "current" means.

.NOTES
    Exit codes:
      0  the mirrored set and the generated registry match the source
      1  drift found - regenerate with scripts/wear/generate-wear-resource-icons.ps1
      2  cannot verify - the generator is missing, or the source set is absent or unreadable
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$generator = Join-Path (Split-Path -Parent $PSScriptRoot) 'wear/generate-wear-resource-icons.ps1'
if (-not (Test-Path $generator)) {
    Write-Host "assert-resource-icon-parity: generator not found: $generator"
    exit 2
}

$output = & $generator -Check 2>&1
$generatorExit = $LASTEXITCODE

# -Gate is accepted for batch-runner symmetry only. Parity has no baseline to ratchet against:
# the two copies match or they do not, so the verdict is the same with or without the switch.
if (-not $Quiet -or $generatorExit -ne 0) {
    $output | ForEach-Object { Write-Host $_ }
}

switch ($generatorExit) {
    0 {
        if (-not $Quiet) { Write-Host 'assert-resource-icon-parity: PASS' }
        exit 0
    }
    1 {
        Write-Host 'assert-resource-icon-parity: FAIL - wear icon mirror has drifted from app_v2.'
        exit 1
    }
    default {
        Write-Host "assert-resource-icon-parity: cannot verify (generator exit $generatorExit)."
        exit 2
    }
}
