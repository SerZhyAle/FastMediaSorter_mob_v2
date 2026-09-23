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

    S2832: discovery is shared with the conformance gate too - both count through
    Get-RoomMigrationSource, so a hop declared inside an aggregate file (AppDatabase.kt declares
    thirty) counts here exactly as it counts there. Pairing widened with the count: a hop is paired
    when a test's NAME carries the hop or the test's TEXT invokes it, with the whole-chain test
    excluded so its constant mentions cannot dissolve frozen baseline debt.

    The gate is a RATCHET, not an absolute rule, because the tree already carries migrations that
    predate the testing habit (31..42 at the time this gate was written). Demanding tests for those
    retroactively would either block every closure or invite a blanket suppression; both are worse
    than freezing the debt and refusing to let it grow. A migration added from now on needs its test.

    Pairing is by number, by name or by text: a hop declared as Migration<N>To<M> requires a test
    whose name contains "Migration<N>To<M>", or whose text mentions "Migration<N>To<M>" or the
    "MIGRATION_N_M" constant (S2832). That a test compiles is the job of
    `.\a.ps1 fa` (phone) or `.\a.ps1 faw` (watch), and that it passes is the job of running it on a
    device with `.\a.ps1 fam` / `.\a.ps1 fwm`.

    A module with an exported schema and ZERO migrations is clean, not unverifiable. That is the
    watch's real state at database version 1, and refusing it would mean this gate could not be
    switched on until somebody else's ticket wrote the first migration - the deferred activation this
    repository has already paid for four times (S2300, S2306, S2307, S2355). Exit 2 is reserved for a
    registry row whose mandatory paths do not exist, and it names the database.

    Baseline file: migration-test-pairing-baseline.txt, one "<database>:NNToMM" token per line. The
    prefix is mandatory (S2355): without it a frozen debt row of one database would suppress a live
    finding carrying the same hop number in another. It names a DATABASE rather than a module since
    S2829, when the watch grew three of them out of one directory. Regenerate with -UpdateBaseline
    only when deliberately accepting a new untested migration, which should be never.

.PARAMETER Gate
    Exit 1 when an unbaselined migration has no test. Without it the script only reports.

.PARAMETER UpdateBaseline
    Rewrite the baseline from the current tree. Dropping tokens needs nothing; adding one needs
    -Reason, and every added or dropped token is printed (S3460).

.PARAMETER Reason
    Why -UpdateBaseline may accept a new untested migration. Recorded as a header line.

.PARAMETER List
    Print every migration and its test status, grouped by database.

.PARAMETER Module
    Optional filter narrowing the run to one module, for hand runs. Absent means every registered
    database - a caller that must remember to ask twice is the wiring mistake the registry removes.

.NOTES
    Exit codes: 0 no unbaselined gap (or reporting only), 1 unbaselined gap under -Gate,
    2 cannot verify (a registry row's migration directory, schema directory or registration file is
      missing - the message names the database; or -UpdateBaseline would add a token without
      -Reason, and nothing is written),
    4 Code.Scripts is held by another session, so no baseline was written. The queue place is held -
      wait for the turn in the background and rerun (S2635).
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$List,
    [string]$Module,
    [string]$Reason
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'lib/room-databases.ps1')
# The code-lock helper is loaded LAZILY, inside the -UpdateBaseline branch, not here. This script
# is exercised by its contract suite from a temp sandbox that copies scripts/quality/ alone, so a
# top-level dot-source of ../utils/ cannot resolve and every case dies before it asserts anything.
# Loading it at the write keeps the sandbox's read-only cases working and still makes the helper
# mandatory on the one path that needs it (S2635).
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

# One record per migration across every database. S2832: discovery goes through the shared
# Get-RoomMigrationSource (S2830) instead of this script's own directory listing, so a hop declared
# inside an aggregate file - AppDatabase.kt declares thirty - counts exactly as the conformance gate
# counts it. Two checkers with private ideas of one subject is the S1621 failure this repository
# already paid for.
$records = [System.Collections.Generic.List[object]]::new()
$emptyDatabases = [System.Collections.Generic.List[string]]::new()

foreach ($db in $databases) {
    $hops = @(Get-RoomMigrationSource -Database $db)
    if ($hops.Count -eq 0) {
        $emptyDatabases.Add($db.Key)
        continue
    }

    # An absent androidTest directory is a finding against a module that HAS migrations - every one
    # of them is untested - rather than an inability to verify.
    #
    # S2832: a hop is paired by the test's NAME or by its TEXT. WearVoiceNoteMigrationTest proves its
    # hop by invoking MIGRATION_1_2 inside runMigrationsAndValidate - a stronger proof than a file
    # name - so a rule that reads only names would demand a rename of a correct test. Each file is
    # read once per database, not once per hop.
    #
    # The whole-chain test is excluded from this pairing on purpose: AppDatabaseMigrationChainTest.kt
    # walks every hop by constant name (MIGRATION_36_37 .. MIGRATION_57_58 - seven of them frozen in
    # the baseline), so counting its mentions would dissolve that frozen debt in silence.
    $testBodies = @()
    if (Test-Path $db.AndroidTestDir) {
        $chainBase = if ($db.ChainTestFile) { [System.IO.Path]::GetFileNameWithoutExtension($db.ChainTestFile) } else { $null }
        $testBodies = @(
            Get-ChildItem -Path $db.AndroidTestDir -Filter '*.kt' -File |
                Where-Object { -not $chainBase -or $_.BaseName -ne $chainBase } |
                ForEach-Object { [pscustomobject]@{ Name = $_.BaseName; Text = (Get-Content -LiteralPath $_.FullName -Raw) } }
        )
    }

    foreach ($hop in $hops) {
        $nameToken = "Migration$($hop.Token)"
        $constantToken = "MIGRATION_$($hop.From)_$($hop.To)"
        $tested = [bool](@($testBodies | Where-Object {
                    ($_.Name -like "*$nameToken*") -or
                    ($_.Text -match [regex]::Escape($nameToken)) -or
                    ($_.Text -match [regex]::Escape($constantToken))
                }).Count)
        $records.Add([pscustomobject]@{
                Module   = $db.Module
                DbKey    = $db.Key
                FileName = $hop.SourceFile
                Key      = "$($db.Key):$($hop.Token)"
                Token    = $hop.Token
                Tested   = $tested
            })
    }
}

if ($records.Count -eq 0) {
    Write-Host ("assert-migration-test-pairing: PASS - no migration exists in any registered database ({0}); nothing to pair." -f ($emptyDatabases -join ', ')) -ForegroundColor Green
    exit 0
}

$untested = @($records | Where-Object { -not $_.Tested })

if ($UpdateBaseline) {
    . (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')
    . (Join-Path $PSScriptRoot 'lib/baseline-set-writer.ps1')
    $previous = @()
    if (Test-Path $baselineFile) {
        $previous = @(Get-Content $baselineFile | ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith('#') })
    }
    $currentKeys = @($untested | ForEach-Object { $_.Key })
    $writeAllowed = Test-BaselineWrite -Gate 'assert-migration-test-pairing' `
        -Previous $previous -Current $currentKeys -Reason $Reason
    if (-not $writeAllowed) { exit 2 }
    $scope = $null
    try {
        $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-migration-test-pairing.ps1 -UpdateBaseline'
        (@(Get-BaselineReasonLine -Reason $Reason) + $currentKeys) | Set-Content -Path $baselineFile -Encoding utf8NoBOM
    }
    finally { Exit-CodeLockScope -Scope $scope }
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
        $dbRecords = @($records | Where-Object { $_.DbKey -eq $db.Key })
        if ($dbRecords.Count -eq 0) {
            Write-Host ("  [{0}] no migration yet - database carries an exported schema only" -f $db.Key)
            continue
        }
        foreach ($record in $dbRecords) {
            $state = if ($record.Tested) { 'tested' } elseif ($baseline -contains $record.Key) { 'untested (baselined)' } else { 'UNTESTED' }
            Write-Host ("  [{0}] {1,-20} {2}" -f $record.DbKey, $record.FileName, $state)
        }
    }
}

$new = @($untested | Where-Object { $baseline -notcontains $_.Key })

if ($new.Count -gt 0) {
    Write-Host ("assert-migration-test-pairing: FAIL - {0} migration(s) have no instrumented test and are not baselined:" -f $new.Count) -ForegroundColor Red
    foreach ($record in $new) {
        $db = $databases | Where-Object { $_.Key -eq $record.DbKey } | Select-Object -First 1
        Write-Host ("  [{0}] {1} has no matching Migration{2} test in {3}" -f `
                $record.DbKey, $record.FileName, $record.Token, $db.RelativePaths.AndroidTestDir) -ForegroundColor Red
    }
    Write-Host "  A migration is the only thing between a user and a destroyed database on update." -ForegroundColor Red
    Write-Host "  Add the test next to its siblings, then verify it compiles: .\a.ps1 fa (app_v2), .\a.ps1 faw (wear)." -ForegroundColor Red
    if ($Gate) { exit 1 }
    exit 0
}

$databaseSummary = @(
    foreach ($db in $databases) {
        $dbRecords = @($records | Where-Object { $_.DbKey -eq $db.Key })
        if ($dbRecords.Count -eq 0) { "$($db.Key) 0 (schema only)" }
        else { "$($db.Key) $($dbRecords.Count)" }
    }
) -join ', '

Write-Host ("assert-migration-test-pairing: PASS - {0} migration(s) across {1} database(s) [{2}], {3} tested, {4} baselined as untested debt." -f `
        $records.Count, $databases.Count, $databaseSummary, ($records.Count - $untested.Count), $untested.Count) -ForegroundColor Green
exit 0
