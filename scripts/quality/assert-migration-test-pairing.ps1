#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: every Room migration must have an instrumented migration test, in every module
    that owns a Room database, and the set of migrations without one may never grow.

.DESCRIPTION
    S1844. A Room migration is the only thing standing between a user and a destroyed database on
    update, and until now the pairing between MigrationNNToMM.kt and AppDatabaseMigrationNNToMMTest.kt
    was held together by habit alone. Nothing failed when a migration shipped untested, and nothing
    failed when a test was deleted.

    S2355 widened it from one module to every Room database. The gate had the phone's two directories
    written into it as literals, so the watch database - which has existed with its own exported
    schema since S1862 - was never looked at. The database list now lives in
    scripts/quality/lib/room-databases.ps1 and is shared with assert-migration-schema-conformance,
    so the two cannot disagree about which databases exist (S1621); a third database is a row there
    and no edit here.

    The gate is a RATCHET, not an absolute rule, because the tree already carries migrations that
    predate the testing habit (31..42 at the time this gate was written). Demanding tests for those
    retroactively would either block every closure or invite a blanket suppression; both are worse
    than freezing the debt and refusing to let it grow. A migration added from now on needs its test.

    Pairing is by number only: Migration<N>To<M>.kt requires a test file whose name contains
    "Migration<N>To<M>". The test's content is not inspected here - that a test compiles is the job of
    `.\a.ps1 fa` (phone) or `.\a.ps1 faw` (watch), and that it passes is the job of running it on a
    device with `.\a.ps1 fam` / `.\a.ps1 fwm`.

    A module with an exported schema and ZERO migrations is clean, not unverifiable. That is the
    watch's real state at database version 1, and refusing it would mean this gate could not be
    switched on until somebody else's ticket wrote the first migration - the deferred activation this
    repository has already paid for four times (S2300, S2306, S2307, S2355). Exit 2 is reserved for a
    registry row whose mandatory paths do not exist, and it names the module.

    Baseline file: migration-test-pairing-baseline.txt, one "<module>:NNToMM" token per line. The
    module prefix is mandatory (S2355): without it a frozen debt row of one module would suppress a
    live finding carrying the same hop number in another. Regenerate with -UpdateBaseline only when
    deliberately accepting a new untested migration, which should be never.

.PARAMETER Gate
    Exit 1 when an unbaselined migration has no test. Without it the script only reports.

.PARAMETER UpdateBaseline
    Rewrite the baseline from the current tree.

.PARAMETER List
    Print every migration and its test status, grouped by module.

.PARAMETER Module
    Optional filter narrowing the run to one module, for hand runs. Absent means every registered
    database - a caller that must remember to ask twice is the wiring mistake the registry removes.

.NOTES
    Exit codes: 0 no unbaselined gap (or reporting only), 1 unbaselined gap under -Gate,
    2 cannot verify (a registry row's migration directory, schema directory or registration file is
      missing - the message names the module).
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$List,
    [string]$Module
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'lib/room-databases.ps1')
$baselineFile = Join-Path $PSScriptRoot 'migration-test-pairing-baseline.txt'

$registryFindings = @(Test-RoomDatabaseRegistry -RepoRoot $repoRoot)
if ($registryFindings.Count -gt 0) {
    foreach ($finding in $registryFindings) {
        [Console]::Error.WriteLine("assert-migration-test-pairing: cannot verify - $($finding.Message)")
    }
    exit 2
}

$databases = @(if ($Module) { Get-RoomDatabaseRegistry -RepoRoot $repoRoot -Module $Module } else { Get-RoomDatabaseRegistry -RepoRoot $repoRoot })
if ($databases.Count -eq 0) {
    [Console]::Error.WriteLine("assert-migration-test-pairing: cannot verify - no registered Room database matches -Module '$Module'")
    exit 2
}

# One record per migration across every database. "Migration31To32.kt" -> "31To32", anchored so a
# helper like MigrationHelpers.kt is not mistaken for a migration.
$records = [System.Collections.Generic.List[object]]::new()
$emptyModules = [System.Collections.Generic.List[string]]::new()

foreach ($db in $databases) {
    $migrations = @(
        Get-ChildItem -Path $db.MigrationDir -Filter 'Migration*.kt' -File |
            ForEach-Object { if ($_.BaseName -match '^Migration(\d+To\d+)$') { $Matches[1] } } |
            Sort-Object { [int](($_ -split 'To')[0]) }
    )
    if ($migrations.Count -eq 0) {
        $emptyModules.Add($db.Module)
        continue
    }

    # An absent androidTest directory is a finding against a module that HAS migrations - every one
    # of them is untested - rather than an inability to verify.
    $testNames = @()
    if (Test-Path $db.AndroidTestDir) {
        $testNames = @(Get-ChildItem -Path $db.AndroidTestDir -Filter '*.kt' -File | ForEach-Object { $_.BaseName })
    }

    foreach ($token in $migrations) {
        $tested = [bool](@($testNames | Where-Object { $_ -like "*Migration$token*" }).Count)
        $records.Add([pscustomobject]@{
                Module = $db.Module
                Key    = "$($db.Key):$token"
                Token  = $token
                Tested = $tested
            })
    }
}

if ($records.Count -eq 0) {
    Write-Host ("assert-migration-test-pairing: PASS - no migration exists in any registered database ({0}); nothing to pair." -f ($emptyModules -join ', ')) -ForegroundColor Green
    exit 0
}

$untested = @($records | Where-Object { -not $_.Tested })

if ($UpdateBaseline) {
    ($untested | ForEach-Object { $_.Key }) | Set-Content -Path $baselineFile -Encoding utf8NoBOM
    Write-Host ("assert-migration-test-pairing: baseline rewritten - {0} untested migration(s)." -f $untested.Count)
    exit 0
}

# Assigned in two statements, not as an if-expression: an empty array returned from an `if`
# collapses to $null, and $null.Count is a terminating error under StrictMode.
$baseline = @()
if (Test-Path $baselineFile) {
    $baseline = @(Get-Content $baselineFile | ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith('#') })
}

if ($List) {
    foreach ($db in $databases) {
        $moduleRecords = @($records | Where-Object { $_.Module -eq $db.Module })
        if ($moduleRecords.Count -eq 0) {
            Write-Host ("  [{0}] no migration yet - database carries an exported schema only" -f $db.Module)
            continue
        }
        foreach ($record in $moduleRecords) {
            $state = if ($record.Tested) { 'tested' } elseif ($baseline -contains $record.Key) { 'untested (baselined)' } else { 'UNTESTED' }
            Write-Host ("  [{0}] Migration{1,-8} {2}" -f $record.Module, $record.Token, $state)
        }
    }
}

$new = @($untested | Where-Object { $baseline -notcontains $_.Key })

if ($new.Count -gt 0) {
    Write-Host ("assert-migration-test-pairing: FAIL - {0} migration(s) have no instrumented test and are not baselined:" -f $new.Count) -ForegroundColor Red
    foreach ($record in $new) {
        $db = $databases | Where-Object { $_.Module -eq $record.Module } | Select-Object -First 1
        Write-Host ("  [{0}] Migration{1}.kt has no matching Migration{1} test in {2}" -f `
                $record.Module, $record.Token, $db.RelativePaths.AndroidTestDir) -ForegroundColor Red
    }
    Write-Host "  A migration is the only thing between a user and a destroyed database on update." -ForegroundColor Red
    Write-Host "  Add the test next to its siblings, then verify it compiles: .\a.ps1 fa (app_v2), .\a.ps1 faw (wear)." -ForegroundColor Red
    if ($Gate) { exit 1 }
    exit 0
}

$moduleSummary = @(
    foreach ($db in $databases) {
        $moduleRecords = @($records | Where-Object { $_.Module -eq $db.Module })
        if ($moduleRecords.Count -eq 0) { "$($db.Module) 0 (schema only)" }
        else { "$($db.Module) $($moduleRecords.Count)" }
    }
) -join ', '

Write-Host ("assert-migration-test-pairing: PASS - {0} migration(s) across {1} database(s) [{2}], {3} tested, {4} baselined as untested debt." -f `
        $records.Count, $databases.Count, $moduleSummary, ($records.Count - $untested.Count), $untested.Count) -ForegroundColor Green
exit 0
