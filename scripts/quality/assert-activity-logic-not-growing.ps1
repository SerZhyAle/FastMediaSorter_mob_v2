#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: the number of domain-layer field injections in Activities must never grow.

.DESCRIPTION
    S1329: thin wrapper. The rule itself - an `@Inject` field in a `*Activity` class whose declared
    type contains Repository, UseCase, DataSource, Dao or Database, case-sensitively - lives once in
    scripts/quality/lib/source-matchers.ps1 under the rule name `activity-logic`, and is executed by
    assert-source-gates.ps1 over a SINGLE walk of the tree instead of one walk per gate. This file
    exists so the gate has the name every other ratchet gate in this directory has.

    The count comes from source, not from app_v2/lint-baseline.xml, so the gate stays honest when the
    XML baseline is stale. It walks every shipped source set rather than src/main alone, because
    ScreenCaptureConsentActivity lives in app_v2/src/screenCapture/.

    Behaviour, baseline file (activity-logic-baseline.txt) and exit codes are those of the runner.

.NOTES
    Exit codes: 0 at or below baseline, 1 above baseline under -Gate, 2 cannot verify.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$List,
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$forward = @{ Only = 'activity-logic' }
if ($Gate) { $forward.Gate = $true }
if ($UpdateBaseline) { $forward.UpdateBaseline = $true }
if ($List) { $forward.List = $true }
if ($ChangedFiles) { $forward.ChangedFiles = $ChangedFiles }

. (Join-Path $PSScriptRoot 'lib/changed-files.ps1')
$engine = Invoke-SourceGatesEngine -Runner (Join-Path $PSScriptRoot 'assert-source-gates.ps1') -Forward $forward
$code = $engine.Code
# CHECK-VERDICT rule 5: the verdict line names this wrapper, not the aggregator it forwards to.
switch ($code) {
    0 { if ($engine.EmptySet) { Write-EmptySetPass -GateName 'assert-activity-logic-not-growing' } else { Write-Host 'assert-activity-logic-not-growing: PASS' } }
    1 { Write-Host 'assert-activity-logic-not-growing: FAIL' }
    default { Write-Host "assert-activity-logic-not-growing: COULD NOT VERIFY (assert-source-gates exited $code)" }
}
exit $code
