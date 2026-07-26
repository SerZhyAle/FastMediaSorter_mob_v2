# Run-Tests.ps1 (S1184) - regression tests for the shared -ChangedFiles CSV normalizer.
#
# Proves the multi-file verdict equals the union of the single-file verdicts across the three
# code paths that a comma-joined -ChangedFiles used to break:
#   - Expand-ChangedFiles          (scripts/quality/lib/changed-files.ps1)
#   - Select-ChangedFileFindings   (assert-detekt's scoped match)
#   - Measure-ChangedFileGrowth    (count-ratchet gates via changed-files-delta.ps1)
#
# Hermetic / dirty-tree-safe: pure in-process function calls plus scratch files under temp/ only.
# Touches nothing tracked. Exit 0 = all cases pass, 1 = at least one failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$testsRoot = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $testsRoot '..' '..')).Path
. (Join-Path $repoRoot 'scripts/quality/lib/changed-files.ps1')
. (Join-Path $repoRoot 'scripts/quality/lib/changed-files-delta.ps1')

$script:pass = 0
$script:fail = 0

function Assert-Equal {
    param([Parameter(Mandatory)][string]$Name, $Expected, $Actual)
    if ($Expected -eq $Actual) {
        Write-Host ("PASS | {0} | {1}" -f $Name, $Actual)
        $script:pass++
    }
    else {
        Write-Host ("FAIL | {0} | expected: {1} | actual: {2}" -f $Name, $Expected, $Actual)
        $script:fail++
    }
}

# --- Expand-ChangedFiles: CSV single element splits; array and CSV agree -----------------------
Assert-Equal 'Expand CSV two files -> 2'      2 (@(Expand-ChangedFiles -ChangedFiles @('a.kt,b.kt')).Count)
Assert-Equal 'Expand array two files -> 2'    2 (@(Expand-ChangedFiles -ChangedFiles @('a.kt', 'b.kt')).Count)
Assert-Equal 'Expand single file -> 1'        1 (@(Expand-ChangedFiles -ChangedFiles @('a.kt')).Count)
Assert-Equal 'Expand mixed CSV+array -> 3'    3 (@(Expand-ChangedFiles -ChangedFiles @('a.kt,b.kt', 'c.kt')).Count)
Assert-Equal 'Expand trims whitespace -> 2'   2 (@(Expand-ChangedFiles -ChangedFiles @('a.kt , b.kt')).Count)

# --- Select-ChangedFileFindings: a finding in one of several changed files is still matched -----
$findings = @('p:/repo/app_v2/src/foo.kt', 'p:/repo/app_v2/src/other.kt')
$csvMatch = @(Select-ChangedFileFindings -FindingFiles $findings -ChangedFiles @('app_v2/src/foo.kt,app_v2/src/bar.kt'))
$arrMatch = @(Select-ChangedFileFindings -FindingFiles $findings -ChangedFiles @('app_v2/src/foo.kt', 'app_v2/src/bar.kt'))
Assert-Equal 'Select CSV matches foo -> 1'    1 $csvMatch.Count
Assert-Equal 'Select array matches foo -> 1'  1 $arrMatch.Count
Assert-Equal 'Select CSV == array match'      $arrMatch.Count $csvMatch.Count
# A clean changed-file set (no finding) matches nothing - so a single clean file is a real PASS.
$noMatch = @(Select-ChangedFileFindings -FindingFiles $findings -ChangedFiles @('app_v2/src/bar.kt'))
Assert-Equal 'Select clean-only matches -> 0'  0 $noMatch.Count

# --- Measure-ChangedFileGrowth: CSV total equals array total (new-file counts all occurrences) --
$scratch = Join-Path $repoRoot 'temp/S1184/qtest'
if (Test-Path $scratch) { Remove-Item -Recurse -Force $scratch }
New-Item -ItemType Directory -Force $scratch | Out-Null
Set-Content -LiteralPath (Join-Path $scratch 'a.kt') -Value "GlobalScope`nGlobalScope" -NoNewline
Set-Content -LiteralPath (Join-Path $scratch 'b.kt') -Value "GlobalScope" -NoNewline
$countFn = { param($t) ([regex]::Matches($t, 'GlobalScope')).Count }
$csvFiles = @("$scratch/a.kt,$scratch/b.kt")
$arrFiles = @("$scratch/a.kt", "$scratch/b.kt")
$gCsv = (Measure-ChangedFileGrowth -ChangedFiles $csvFiles -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countFn).Growth
$gArr = (Measure-ChangedFileGrowth -ChangedFiles $arrFiles -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countFn).Growth
Assert-Equal 'Measure array growth -> 3'      3 $gArr
Assert-Equal 'Measure CSV growth -> 3'        3 $gCsv
Assert-Equal 'Measure CSV == array growth'    $gArr $gCsv
Remove-Item -Recurse -Force $scratch

Write-Host ("RESULT | pass: {0} | fail: {1}" -f $script:pass, $script:fail)
if ($script:fail -eq 0) { exit 0 } else { exit 1 }
