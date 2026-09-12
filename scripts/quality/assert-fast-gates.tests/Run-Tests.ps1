#requires -Version 7.0
<#
.SYNOPSIS
    S2693: contract suite for the two-verdict form of assert-fast-gates.ps1.

.DESCRIPTION
    The battery splits its summary when, and only when, it is given a changed set: gates that
    received the set are judged as YOUR SET and decide the exit code; every other gate judges the
    whole tree and is advisory. The invariant is stated as a relation between the printed blocks
    and the exit code, never as an expected colour, because the tree this runs on carries other
    sessions' unfinished work and any absolute expectation would be flaky by construction.

    One real battery run is spent, not two: the no-set form is asserted from the script's own text
    and its exit-code contract, which is what assert-exit-contract.ps1 also reads.

.PARAMETER Help
    Show help documentation and usage.

    Exit codes:
      0  every case passed.
      1  at least one case failed.
      2  cannot verify - the subject script is missing.
#>
[CmdletBinding()]
param([switch]$Help)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) { Get-Help -Full $PSCommandPath; exit 0 }

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$subject = Join-Path $repoRoot 'scripts/quality/assert-fast-gates.ps1'
if (-not (Test-Path -LiteralPath $subject)) {
    Write-Host "cannot verify: $subject is missing."
    exit 2
}

$failures = 0
function Assert-Case {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if ($Condition) {
        Write-Host "  PASS  $Name"
    }
    else {
        Write-Host "  FAIL  $Name$(if ($Detail) { " - $Detail" })"
        $script:failures++
    }
}

Write-Host 'assert-fast-gates.tests'

$text = Get-Content -LiteralPath $subject -Raw

Assert-Case 'the split is conditioned on a changed set being supplied' `
    ($text -match '\$split\s*=\s*\[bool\]\$ChangedFiles')

Assert-Case 'both summary blocks exist' `
    (($text -match 'YOUR SET') -and ($text -match 'THE TREE'))

Assert-Case 'the exit code is taken from the set block, not from every gate' `
    ($text -match '\$failed\s*=\s*\$setFailures\.Count')

Assert-Case 'the exit-code contract documents the changed-set form' `
    ($text -match '(?s)Exit codes:.*-ChangedFiles')

# S2693 re-audit: a tree gate that takes no -ChangedFiles still judges the caller's own file, and
# its red must not hide as advisory. The promotion is asserted from the text on both sides of it -
# the classification and the block selection - so removing either half fails here.
Assert-Case 'a red tree gate naming a changed file is promoted into the set block' `
    (($text -match "\`$rowScope\s*=\s*'set-named'") -and ($text -match "Scope -in @\('set', 'set-named'\)"))

Assert-Case 'promotion searches the repo-relative path, never the bare file name' `
    ($text -match '\$changedNeedles \+= @\(\$rel, \$rel\.Replace')

Assert-Case 'detekt telemetry records the explicit set or tree scope' `
    ($text -match '\$detektScope\s*=\s*if \(\$split\) \{ ''set'' \} else \{ ''tree'' \}')

Assert-Case 'detekt telemetry records paths and a count only after its report is readable' `
    (($text -match 'FindingDetailsAvailable') -and ($text -match 'FindingCount') -and
        ($text -match 'FindingPaths') -and ($text -match 'if \(\$detektFindings\.Ok\)'))

# The one real run. A file that exists and is not Kotlin keeps the set small; the assertion is the
# relation between the blocks and the code, so the tree's actual state cannot make it flaky.
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }
$probeFile = 'scripts/quality/assert-fast-gates.ps1'
$output = & $pwshExe -NoProfile -File $subject -ChangedFiles $probeFile 2>&1 | Out-String
$code = [int]$LASTEXITCODE

Assert-Case 'a run with a changed set prints the set block' ($output -match 'YOUR SET')
Assert-Case 'a run with a changed set prints the tree block' ($output -match 'THE TREE')

$setBlock = ''
if ($output -match '(?s)YOUR SET:(.*?)assert-fast-gates summary - THE TREE') { $setBlock = $Matches[1] }
$setHasFailure = ($setBlock -match '\bFAIL\b') -or ($setBlock -match '\bMISSING\b')

Assert-Case 'the exit code is 1 exactly when the set block carries a red gate' `
    ((($code -eq 1) -and $setHasFailure) -or (($code -eq 0) -and -not $setHasFailure)) `
    "exit $code, set block red = $setHasFailure"

Write-Host ''
if ($failures -gt 0) {
    Write-Host "assert-fast-gates.tests: FAIL ($failures case(s))."
    exit 1
}
Write-Host 'assert-fast-gates.tests: PASS.'
exit 0
