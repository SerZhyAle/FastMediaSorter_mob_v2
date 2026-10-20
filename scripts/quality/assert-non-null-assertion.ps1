#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: `!!` non-null-assertion operators in src/main *.kt must never grow.

.DESCRIPTION
    S1338: thin wrapper. The rule itself - what counts as a violation, which files it reads,
    which baseline it ratchets against - lives once in scripts/quality/lib/source-matchers.ps1
    and is executed by assert-source-gates.ps1, which applies every lexical rule over a SINGLE
    walk of the tree instead of one walk per gate. This file stays on disk so every existing
    caller keeps working unchanged.

    Behaviour, baseline file and exit codes are identical to the standalone version.

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

$forward = @{ Only = 'non-null-assertion' }
if ($Gate) { $forward.Gate = $true }
if ($UpdateBaseline) { $forward.UpdateBaseline = $true }
if ($List) { $forward.List = $true }
if ($ChangedFiles) { $forward.ChangedFiles = $ChangedFiles }

. (Join-Path $PSScriptRoot 'lib/changed-files.ps1')
$engine = Invoke-SourceGatesEngine -Runner (Join-Path $PSScriptRoot 'assert-source-gates.ps1') -Forward $forward
$code = $engine.Code
# CHECK-VERDICT rule 5: the verdict line names this wrapper, not the aggregator it forwards to.
switch ($code) {
    0 { if ($engine.EmptySet) { Write-EmptySetPass -GateName 'assert-non-null-assertion' } else { Write-Host 'assert-non-null-assertion: PASS' } }
    1 { Write-Host 'assert-non-null-assertion: FAIL' }
    default { Write-Host "assert-non-null-assertion: COULD NOT VERIFY (assert-source-gates exited $code)" }
}
exit $code
