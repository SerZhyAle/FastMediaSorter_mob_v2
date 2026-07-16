# Run-Tests.ps1 (S1077) - regression suite for Get-DetektFindingFiles, the report reader behind
# scripts/quality/assert-detekt.ps1's diff-scoped verdict.
#
# What broke: the reader swallowed "no report" and "unparseable report" and returned an empty finding
# list. The caller reads an empty list as "no findings in my files" and prints
#   assert-detekt: PASS [scoped] - 0 file(s) with new findings project-wide
# ..on top of a detekt run that had just FAILED (the diff-scoped branch is only reachable when gradle
# exited non-zero). So the gate answered "clean" precisely when it could not look.
#
# Both ends are asserted, because a check that only ever goes green proves nothing:
#   * missing / malformed report  -> Ok=$false  (the defect),
#   * valid report                -> Ok=$true and the finding files are read exactly (no over-blocking).
#
# Hermetic: every case builds its own <module>/build/reports/detekt/ tree under $env:TEMP. Nothing in
# the repo is read or written, so this never depends on whether detekt has been run.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-detekt.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/quality/lib/detekt-report.ps1')

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

# Builds a throwaway repo root holding <module>/build/reports/detekt/detekt.xml with $Content.
# $Content = $null means "write no file at all" (the missing-report case).
function New-FakeRepo([string]$module, $content) {
    $root = Join-Path $env:TEMP ("detekt-tests-" + [guid]::NewGuid().ToString('N'))
    $dir = Join-Path $root "$module/build/reports/detekt"
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    if ($null -ne $content) {
        [System.IO.File]::WriteAllText((Join-Path $dir 'detekt.xml'), $content)
    }
    return $root
}

$VALID_WITH_FINDING = @'
<?xml version="1.0" encoding="UTF-8"?>
<checkstyle version="4.3">
<file name="P:\ANDROID\FastMediaSorter_mob_v2\app_v2\src\main\java\com\sza\Alpha.kt">
	<error line="60" column="7" severity="warning" message="Class 'Alpha' with '47' functions detected." source="detekt.TooManyFunctions" />
</file>
<file name="P:\ANDROID\FastMediaSorter_mob_v2\app_v2\src\main\java\com\sza\Beta.kt">
	<error line="12" column="5" severity="warning" message="too complex" source="detekt.CyclomaticComplexMethod" />
</file>
</checkstyle>
'@

# detekt emits this when a module has no findings above baseline - a real "clean", not a problem.
$VALID_EMPTY = @'
<?xml version="1.0" encoding="UTF-8"?>
<checkstyle version="4.3">
</checkstyle>
'@

$roots = [System.Collections.Generic.List[string]]::new()

try {
    # --- A: the defect. No report at all must NOT read as "no findings". ---
    Write-Host 'A: missing report fails closed' -ForegroundColor Yellow
    $rootA = New-FakeRepo 'app_v2' $null; $roots.Add($rootA)
    $a = Get-DetektFindingFiles -RepoRoot $rootA -Modules @('app_v2')
    Assert-That 'A1 Ok is false when the report is absent' (-not $a.Ok) "Ok=$($a.Ok) - this is the fail-open bug"
    Assert-That 'A2 Reason names the missing report' ($a.Reason -like '*no report*') "Reason='$($a.Reason)'"
    Assert-That 'A3 Files is empty (must not be mistaken for a verdict)' (@($a.Files).Count -eq 0) "Files=$($a.Files -join ',')"

    # --- B: a truncated / malformed report is also "could not look", not "clean". ---
    Write-Host 'B: unparseable report fails closed' -ForegroundColor Yellow
    $rootB = New-FakeRepo 'app_v2' '<checkstyle><file name="x.kt"><error '; $roots.Add($rootB)
    $b = Get-DetektFindingFiles -RepoRoot $rootB -Modules @('app_v2')
    Assert-That 'B1 Ok is false on malformed XML' (-not $b.Ok) "Ok=$($b.Ok)"
    Assert-That 'B2 Reason says unparseable' ($b.Reason -like '*unparseable*') "Reason='$($b.Reason)'"

    # --- C: no over-blocking. A valid report is read, and its files come back exactly. ---
    Write-Host 'C: valid report is read, not refused' -ForegroundColor Yellow
    $rootC = New-FakeRepo 'app_v2' $VALID_WITH_FINDING; $roots.Add($rootC)
    $c = Get-DetektFindingFiles -RepoRoot $rootC -Modules @('app_v2')
    Assert-That 'C1 Ok is true' ($c.Ok) "Reason='$($c.Reason)'"
    Assert-That 'C2 both finding files returned' (@($c.Files).Count -eq 2) "Files=$($c.Files -join ',')"
    Assert-That 'C3 paths normalised to forward slashes' (@($c.Files | Where-Object { $_ -like '*\*' }).Count -eq 0) "Files=$($c.Files -join ',')"
    Assert-That 'C4 paths lowercased for the caller''s -like match' ($c.Files -contains ($c.Files[0].ToLower())) "Files=$($c.Files -join ',')"
    Assert-That 'C5 Alpha.kt present' (@($c.Files | Where-Object { $_ -like '*alpha.kt' }).Count -eq 1) "Files=$($c.Files -join ',')"

    # --- D: an empty <checkstyle/> is a genuine clean module, and must NOT trip the fail-closed net. ---
    Write-Host 'D: empty report is clean, not a refusal' -ForegroundColor Yellow
    $rootD = New-FakeRepo 'app_v2' $VALID_EMPTY; $roots.Add($rootD)
    $d = Get-DetektFindingFiles -RepoRoot $rootD -Modules @('app_v2')
    Assert-That 'D1 Ok is true for an empty report' ($d.Ok) "Reason='$($d.Reason)' - over-blocking: clean module refused"
    Assert-That 'D2 no files' (@($d.Files).Count -eq 0) "Files=$($d.Files -join ',')"

    # --- E: multi-module. One good report does not excuse a missing sibling - the caller asked about
    # both, so it can only narrow if it can read both. ---
    Write-Host 'E: a missing sibling module still fails closed' -ForegroundColor Yellow
    $rootE = New-FakeRepo 'app_v2' $VALID_WITH_FINDING; $roots.Add($rootE)
    New-Item -ItemType Directory -Path (Join-Path $rootE 'wear/build/reports/detekt') -Force | Out-Null
    $e = Get-DetektFindingFiles -RepoRoot $rootE -Modules @('app_v2', 'wear')
    Assert-That 'E1 Ok is false when only wear''s report is missing' (-not $e.Ok) "Ok=$($e.Ok) - app_v2 being readable must not excuse wear"
    Assert-That 'E2 Reason names wear, not app_v2' ($e.Reason -like '*wear*' -and $e.Reason -notlike '*app_v2*') "Reason='$($e.Reason)'"
}
finally {
    foreach ($r in $roots) { Remove-Item -LiteralPath $r -Recurse -Force -ErrorAction SilentlyContinue }
    Write-Host 'fake repos removed' -ForegroundColor DarkGray
}

Write-Host ''
if ($script:fail -eq 0) {
    Write-Host "assert-detekt tests: $script:pass passed" -ForegroundColor Green
    exit 0
}
Write-Host "assert-detekt tests: $script:pass passed, $script:fail FAILED" -ForegroundColor Red
exit 1
