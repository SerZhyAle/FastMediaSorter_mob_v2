#requires -Version 7.0
<#
.SYNOPSIS
    S1216: gate wrapper for the device-profile preset matrix coverage check.

.DESCRIPTION
    Thin adapter over `scripts/check_device_profile_presets.ps1` so the check joins the gate
    convention of this folder: same `-Gate` / `-Quiet` shape, discoverable by
    `assert-fast-gates.ps1`, invoked from `scripts/post-change.ps1`.

    The check itself lives outside `scripts/quality/` because it also carries an authoring mode
    (`-AddMissing`, which rewrites the matrix). A gate must never write, so the wrapper exposes
    only the read-only path.

    What it catches: a new AppSettings field that is neither given a row in
    `app_v2/src/main/assets/device_profile_presets.csv` nor declared in
    `docs/settings/device-profile-nonpresettable.json`; a matrix row whose value the applier would
    silently drop; a cell outside the range its Settings screen accepts. That drift is exactly how
    the matrix reached 40 uncovered fields while a correct checker sat in the repo, unwired.

.PARAMETER Gate
    Gate framing: exit 1 on any violation. The underlying check already exits 1, so this only
    switches the reporting to a one-line verdict.

.PARAMETER Quiet
    Suppress the informational counters. Violations are always printed.

.NOTES
    Exit codes (CLAUDE.md Rule 7 / S1070 contract):
      0  PASS - matrix, registry and applier agree.
      1  FAIL - at least one coverage rule is violated, or the underlying check is missing.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-device-profile-matrix.ps1 -Gate
.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-device-profile-matrix.ps1 -Gate -Quiet
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$check = Join-Path $repoRoot 'scripts/check_device_profile_presets.ps1'

if (-not (Test-Path $check)) {
    Write-Error "assert-device-profile-matrix: check script not found: $check" -ErrorAction Continue
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    'pwsh'
}

$childArgs = @('-NoProfile', '-File', $check)
if ($Gate) { $childArgs += '-Gate' }
if ($Quiet) { $childArgs += '-Quiet' }

& $pwshExe @childArgs
$code = $LASTEXITCODE

if ($code -ne 0 -and $Gate) {
    Write-Error 'assert-device-profile-matrix: FAIL - see the violations above.' -ErrorAction Continue
}
exit $code
