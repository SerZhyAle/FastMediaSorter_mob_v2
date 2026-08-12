#requires -Version 7.0
<#
.SYNOPSIS
    Keep the closure facade's gate labels and the recovery-hint registry in step.

.DESCRIPTION
    scripts/post-change.ps1 prints a recovery hint under every failed gate, read
    from scripts/quality/gate-recovery-hints.psd1 and keyed by the gate label.
    A label with no entry prints without a hint, which is harmless at the moment
    it happens and invisible until then - the gap surfaces only when that gate
    next fails, which is exactly when the hint was needed (S1598).

    This gate reports both directions of drift:
      - a facade label with no registry entry (a gate that will fail mute);
      - a registry key naming no facade label (a hint for a gate that is gone).

    Exit codes (S1070):
      0 - registry and facade agree (or audit mode).
      1 - substantive failure: at least one label or key is unpaired (-Gate only).
      2 - the gate itself cannot run: the facade or the registry is missing or
          unreadable, so nothing could be compared.

.PARAMETER Gate
    Fail-closed: exit 1 when any label or key is unpaired.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-gate-hints-sync.ps1
    pwsh -NoProfile -File scripts/quality/assert-gate-hints-sync.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$facade = Join-Path $repoRoot 'scripts/post-change.ps1'
$registry = Join-Path $repoRoot 'scripts/quality/gate-recovery-hints.psd1'

foreach ($required in @($facade, $registry)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Error "assert-gate-hints-sync: not found: $required" -ErrorAction Continue
        exit 2
    }
}

try {
    $hints = Import-PowerShellDataFile -LiteralPath $registry
}
catch {
    Write-Error "assert-gate-hints-sync: unreadable registry: $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

# A step is "a label followed by its script block". The dispatch head varies: the
# ratchet gates are called through a variable holding either the fatal or the
# advisory wrapper, so matching the wrapper names alone would miss them.
$stepRx = [regex]'(?:Invoke-Gate|Invoke-Step|Invoke-AdvisoryStep|&\s+\$\w+)\s+"([^"]+)"\s*\{'
$facadeText = Get-Content -LiteralPath $facade -Raw
$labels = @($stepRx.Matches($facadeText) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)

if ($labels.Count -eq 0) {
    Write-Error "assert-gate-hints-sync: no gate labels found in $facade - the extraction pattern no longer matches the facade." -ErrorAction Continue
    exit 2
}

$keys = @($hints.Keys | Sort-Object)
$missingHint = @($labels | Where-Object { $_ -notin $keys })
$orphanKey = @($keys | Where-Object { $_ -notin $labels })

if ($missingHint.Count -gt 0) {
    Write-Host "Gate labels with no recovery hint:`n"
    foreach ($label in $missingHint) { Write-Host "  $label" }
    Write-Host ''
}

if ($orphanKey.Count -gt 0) {
    Write-Host "Recovery hints naming no gate label:`n"
    foreach ($key in $orphanKey) { Write-Host "  $key" }
    Write-Host ''
}

$actual = $missingHint.Count + $orphanKey.Count
Write-Host ("assert-gate-hints-sync: expected: 0 | actual: {0}  (labels: {1}, hints: {2})" -f $actual, $labels.Count, $keys.Count)

if ($Gate -and $actual -gt 0) { exit 1 }
exit 0
