#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: every Room migration must have an instrumented migration test, and the set of
    migrations without one may never grow.

.DESCRIPTION
    S1844. A Room migration is the only thing standing between a user and a destroyed database on
    update, and until now the pairing between MigrationNNToMM.kt and AppDatabaseMigrationNNToMMTest.kt
    was held together by habit alone. Nothing failed when a migration shipped untested, and nothing
    failed when a test was deleted.

    The gate is a RATCHET, not an absolute rule, because the tree already carries migrations that
    predate the testing habit (31..42 at the time this gate was written). Demanding tests for those
    retroactively would either block every closure or invite a blanket suppression; both are worse
    than freezing the debt and refusing to let it grow. A migration added from now on needs its test.

    Pairing is by number only: Migration<N>To<M>.kt requires a test file whose name contains
    "Migration<N>To<M>". The test's content is not inspected here - that a test compiles is the job of
    `.\a.ps1 fa`, and that it passes is the job of running it on a device.

    Baseline file: migration-test-pairing-baseline.txt, one "NNToMM" token per line. Regenerate with
    -UpdateBaseline only when deliberately accepting a new untested migration, which should be never.

.PARAMETER Gate
    Exit 1 when an unbaselined migration has no test. Without it the script only reports.

.PARAMETER UpdateBaseline
    Rewrite the baseline from the current tree.

.PARAMETER List
    Print every migration and its test status.

.NOTES
    Exit codes: 0 no unbaselined gap (or reporting only), 1 unbaselined gap under -Gate,
    2 cannot verify (migration or test directory missing).
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$List
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$dbDir = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/data/local/db'
$testDir = Join-Path $repoRoot 'app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db'
$baselineFile = Join-Path $PSScriptRoot 'migration-test-pairing-baseline.txt'

if (-not (Test-Path $dbDir)) {
    [Console]::Error.WriteLine("assert-migration-test-pairing: cannot verify - migration directory not found: $dbDir")
    exit 2
}
if (-not (Test-Path $testDir)) {
    [Console]::Error.WriteLine("assert-migration-test-pairing: cannot verify - androidTest directory not found: $testDir")
    exit 2
}

# "Migration31To32.kt" -> "31To32". Anchored so a helper like MigrationHelpers.kt is not mistaken
# for a migration.
$migrations = @(
    Get-ChildItem -Path $dbDir -Filter 'Migration*.kt' -File |
        ForEach-Object { if ($_.BaseName -match '^Migration(\d+To\d+)$') { $Matches[1] } } |
        Sort-Object { [int](($_ -split 'To')[0]) }
)
if ($migrations.Count -eq 0) {
    [Console]::Error.WriteLine('assert-migration-test-pairing: cannot verify - no MigrationNNToMM.kt found')
    exit 2
}

$testNames = @(Get-ChildItem -Path $testDir -Filter '*.kt' -File | ForEach-Object { $_.BaseName })
$untested = @($migrations | Where-Object { $token = $_; -not ($testNames | Where-Object { $_ -like "*Migration$token*" }) })

if ($UpdateBaseline) {
    $untested | Set-Content -Path $baselineFile -Encoding utf8NoBOM
    Write-Host ("assert-migration-test-pairing: baseline rewritten - {0} untested migration(s)." -f $untested.Count)
    exit 0
}

$baseline = if (Test-Path $baselineFile) {
    @(Get-Content $baselineFile | ForEach-Object { $_.Trim() } | Where-Object { $_ })
} else { @() }

if ($List) {
    foreach ($m in $migrations) {
        $state = if ($untested -contains $m) { if ($baseline -contains $m) { 'untested (baselined)' } else { 'UNTESTED' } } else { 'tested' }
        Write-Host ("  Migration{0,-8} {1}" -f $m, $state)
    }
}

$new = @($untested | Where-Object { $baseline -notcontains $_ })

if ($new.Count -gt 0) {
    Write-Host ("assert-migration-test-pairing: FAIL - {0} migration(s) have no instrumented test and are not baselined:" -f $new.Count) -ForegroundColor Red
    foreach ($m in $new) {
        Write-Host ("  Migration{0}.kt has no AppDatabaseMigration{0}Test.kt" -f $m) -ForegroundColor Red
    }
    Write-Host "  A migration is the only thing between a user and a destroyed database on update." -ForegroundColor Red
    Write-Host "  Add the test next to its siblings, then verify it compiles with: .\a.ps1 fa" -ForegroundColor Red
    if ($Gate) { exit 1 }
    exit 0
}

Write-Host ("assert-migration-test-pairing: PASS - {0} migration(s), {1} tested, {2} baselined as untested debt." -f `
        $migrations.Count, ($migrations.Count - $untested.Count), $untested.Count) -ForegroundColor Green
exit 0
