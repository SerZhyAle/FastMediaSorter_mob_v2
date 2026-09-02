#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: what a Room migration writes in SQL must match the exported schema Room
    validates the upgraded database against, and every migration must be registered - in every
    module that owns a Room database.

.DESCRIPTION
    S2306. A Room migration is compared against the exported schema at runtime, on the user's
    device, during the first launch after an update - and nowhere else. Nothing in this
    repository compared the two: `fk` compiles Kotlin without reading a schema JSON, the
    androidTest compile gate proves a test file parses without running it, and
    assert-migration-test-pairing proves a test EXISTS without executing its assertions. So a
    migration whose SQL disagrees with the schema by one character closed green through every
    gate and detonated on the first phone that upgraded.

    That is not hypothetical. MIGRATION_53_54 (S2251) added `screen_index` while
    LauncherCellEntity declares `screenIndex`, and the entity declared no default while the SQL
    wrote `DEFAULT 0`. Both halves are visible in the tree, statically, from the migration file
    and 54.json alone. On 2026-09-01 the owner's phone instead reported "Migration didn't
    properly handle: launcher_cells", the recovery path in DatabaseModule backed the database up
    and deleted it, and 20 resources, 26 network credentials, 7 favourites and a 139-cell
    desktop went with it. A user without that backup loses them permanently.

    S2355 widened it from one module to every Room database. This gate carried the phone's
    migration directory, schema directory and DatabaseModule.kt as literal paths, so the watch
    database - version 1 with its own exported schema since S1862 - was outside all of it. Worse
    than a gap: post-change.ps1 fired this gate on any changed `schemas/*.json`, which matches
    wear/schemas/**, so editing the watch schema ran a gate that read app_v2 and printed PASS.
    The database list now lives in scripts/quality/lib/room-databases.ps1, shared with
    assert-migration-test-pairing so the two cannot disagree about which databases exist (S1621).

    A module with an exported schema and ZERO migrations is clean, not unverifiable - the watch's
    real state at version 1, and a Room builder with no .addMigrations(..) at all is correct there.
    Refusing it would mean the gate could not be switched on until somebody else's ticket wrote the
    first migration, which is the deferred activation this repository has paid for four times.

    Six dimensions, all decidable from text - no device, no gradle, no Room:

      registration   every MigrationNNToMM.kt is registered in the module's Room builder, every
                     exported schema version below the declared one has an outgoing edge, and the
                     declared version has an exported schema. An unregistered migration is not a
                     no-op: the hop throws, and the recovery path wipes the database.
      column-name    a column added by ALTER TABLE .. ADD COLUMN must exist under that exact
                     name in the TARGET version's schema. This is the S2251 defect.
      not-null       NOT NULL in the SQL must match notNull in the schema, and a NOT NULL added
                     column must carry a DEFAULT - SQLite refuses the statement without one.
      column-default when the schema declares a defaultValue for the added column, the SQL must
                     write an equal one. Deliberately one-directional: Room refuses an
                     entity-declared default the table does not have, and tolerates the reverse,
                     so flagging the reverse would invent failures the runtime does not have.
      table-name     a table named by ALTER TABLE or CREATE TABLE must exist in the target
                     schema, unless the same migration renames or drops it (the
                     create-copy/rename-over idiom of MIGRATION_47_48).
      chain-test     the module's whole-chain test - the one test that walks the oldest exported
                     schema to the current version - must name the DECLARED version and the newest
                     migration. Frozen at an older target it keeps passing while covering one hop
                     less every release, which is the exact shape of evidence this gate refuses.
                     Skipped for a module with no migration yet; required from its first one on.

    RATCHET, like assert-migration-test-pairing. The tree carries migrations older than the
    exported-schema set (31..35 have no schema to compare against and are reported as skipped)
    and any finding on a migration that already shipped is history, not a defect to fix - the
    database on those users' devices is whatever the SQL actually produced. Baseline file:
    migration-schema-conformance-baseline.txt, one finding key per line, module-prefixed since
    S2355. A NEW disagreement fails; regenerate with -UpdateBaseline only when deliberately
    accepting one, which for a migration that has not shipped should be never.

.PARAMETER Gate
    Exit 1 when an unbaselined disagreement is found. Without it the script only reports.

.PARAMETER UpdateBaseline
    Rewrite the baseline from the current tree.

.PARAMETER List
    Print every migration with its statement count and verdict, including the baselined ones.

.PARAMETER Quiet
    Suppress the per-migration progress lines; the verdict line is always printed.

.PARAMETER Module
    Optional filter narrowing the run to one module, for hand runs. Absent means every registered
    database - a caller that must remember to ask twice is the wiring mistake the registry removes.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-migration-schema-conformance.ps1 -Gate

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-migration-schema-conformance.ps1 -List

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  no unbaselined disagreement (or reporting only, without -Gate).
      1  an unbaselined disagreement was found, under -Gate.
      2  cannot verify - a registry row's migration directory, schema directory or registration
         file is missing, no @Database class was found for a row, or its declared version cannot
         be read. Every such message names the module.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$List,
    [switch]$Quiet,
    [string]$Module,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath -Detailed
    exit 0
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'lib/room-databases.ps1')
$baselineFile = Join-Path $PSScriptRoot 'migration-schema-conformance-baseline.txt'

function Stop-CannotVerify([string]$message) {
    [Console]::Error.WriteLine("assert-migration-schema-conformance: cannot verify - $message")
    exit 2
}

$registryFindings = @(Test-RoomDatabaseRegistry -RepoRoot $repoRoot)
if ($registryFindings.Count -gt 0) {
    foreach ($finding in $registryFindings) {
        [Console]::Error.WriteLine("assert-migration-schema-conformance: cannot verify - $($finding.Message)")
    }
    exit 2
}

$databases = @(if ($Module) { Get-RoomDatabaseRegistry -RepoRoot $repoRoot -Module $Module } else { Get-RoomDatabaseRegistry -RepoRoot $repoRoot })
if ($databases.Count -eq 0) { Stop-CannotVerify "no registered Room database matches -Module '$Module'" }

$findings = [System.Collections.Generic.List[object]]::new()
function Add-Finding([string]$module, [string]$migration, [string]$dimension, [string]$subject, [string]$message) {
    $findings.Add([pscustomobject]@{
            Key       = "$module|$migration|$dimension|$subject"
            Module    = $module
            Migration = $migration
            Dimension = $dimension
            Subject   = $subject
            Message   = $message
        })
}

# ---- schema cache -----------------------------------------------------------------------
# Keyed by directory as well as version: two databases both have a 1.json and they are not the
# same file.
$schemaCache = @{}
function Get-Schema([string]$schemaDir, [int]$version) {
    $cacheKey = "$schemaDir|$version"
    if ($schemaCache.ContainsKey($cacheKey)) { return $schemaCache[$cacheKey] }
    $path = Join-Path $schemaDir "$version.json"
    $value = if (Test-Path $path) { (Get-Content $path -Raw | ConvertFrom-Json).database } else { $null }
    $schemaCache[$cacheKey] = $value
    return $value
}

function Get-SchemaTable($schema, [string]$tableName) {
    if ($null -eq $schema) { return $null }
    return $schema.entities | Where-Object { $_.tableName -eq $tableName } | Select-Object -First 1
}

function Get-SchemaField($table, [string]$columnName) {
    if ($null -eq $table) { return $null }
    if ($table.PSObject.Properties.Name -notcontains 'fields') { return $null }
    return $table.fields | Where-Object { $_.columnName -eq $columnName } | Select-Object -First 1
}

# An FTS entity omits keys an ordinary one carries, and StrictMode turns a missing key into a
# terminating error rather than a null - so every schema read goes through here.
function Get-SchemaProperty($node, [string]$name) {
    if ($null -eq $node) { return $null }
    if ($node.PSObject.Properties.Name -notcontains $name) { return $null }
    return $node.$name
}

# Room compares defaults after stripping wrapping parentheses, and treats NULL case-insensitively.
# Mirror exactly that much and no more - a normaliser cleverer than the runtime's would hide a
# difference the device still refuses.
function ConvertTo-ComparableDefault([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return '' }
    $v = $value.Trim()
    while ($v.Length -gt 1 -and $v.StartsWith('(') -and $v.EndsWith(')')) { $v = $v.Substring(1, $v.Length - 2).Trim() }
    if ($v -ieq 'NULL') { return 'NULL' }
    return $v
}

$reports = [System.Collections.Generic.List[object]]::new()
$moduleSummaries = [System.Collections.Generic.List[object]]::new()

foreach ($db in $databases) {
    # The declared schema version, read from whichever class in the module's database directory
    # carries @Database. Named by annotation rather than by file name: the phone's is AppDatabase.kt
    # and the watch's is WearVoiceNoteDatabase.kt.
    $dbClassFile = @(
        Get-ChildItem -Path $db.MigrationDir -Filter '*.kt' -File |
            Where-Object { (Get-Content $_.FullName -Raw) -match '(?m)^\s*@Database\s*\(' }
    )
    if ($dbClassFile.Count -eq 0) { Stop-CannotVerify "$($db.Module): no @Database class found in $($db.RelativePaths.MigrationDir)" }
    if ($dbClassFile.Count -gt 1) { Stop-CannotVerify "$($db.Module): $($dbClassFile.Count) @Database classes in $($db.RelativePaths.MigrationDir) - the registry names one database per row" }

    $dbClassText = Get-Content $dbClassFile[0].FullName -Raw
    $versionMatch = [regex]::Match($dbClassText, 'version\s*=\s*(\d+)')
    if (-not $versionMatch.Success) { Stop-CannotVerify "$($db.Module): no 'version = N' found in $($dbClassFile[0].Name)" }
    $declaredVersion = [int]$versionMatch.Groups[1].Value

    # "Migration31To32.kt" -> "31To32". Anchored so a helper like MigrationHelpers.kt is not taken
    # for a migration.
    $migrationFiles = @(
        Get-ChildItem -Path $db.MigrationDir -Filter 'Migration*.kt' -File |
            Where-Object { $_.BaseName -match '^Migration(\d+)To(\d+)$' } |
            Sort-Object { [int]([regex]::Match($_.BaseName, '^Migration(\d+)To').Groups[1].Value) }
    )

    # ---- registration ---------------------------------------------------------------------
    $moduleText = Get-Content $db.RegistrationFile -Raw
    $addBlock = [regex]::Match($moduleText, '\.addMigrations\s*\((?<body>[\s\S]*?)\)\s*$', 'Multiline')
    $registeredBody = if ($addBlock.Success) { $addBlock.Groups['body'].Value } else { $moduleText }
    $registeredEdges = @{}
    foreach ($m in [regex]::Matches($registeredBody, 'MIGRATION_(\d+)_(\d+)')) {
        $from = [int]$m.Groups[1].Value
        $to = [int]$m.Groups[2].Value
        $registeredEdges["$($from)To$($to)"] = $true
    }
    # A builder with no .addMigrations(..) at all is correct for a database that has no migration
    # yet. It is only a failure to verify when migration files exist and none of them is registered.
    if ($registeredEdges.Count -eq 0 -and $migrationFiles.Count -gt 0) {
        Stop-CannotVerify "$($db.Module): $($migrationFiles.Count) migration file(s) exist but no MIGRATION_N_M reference is in $($db.RelativePaths.RegistrationFile)"
    }

    if (-not (Test-Path (Join-Path $db.SchemaDir "$declaredVersion.json"))) {
        Add-Finding $db.Key "v$declaredVersion" 'registration' "schemas/$declaredVersion.json" `
            "$($db.Module): the database declares version $declaredVersion but no exported schema exists for it - Room has nothing to validate an upgraded database against"
    }

    $exportedVersions = @(
        Get-ChildItem -Path $db.SchemaDir -Filter '*.json' -File |
            ForEach-Object { $n = 0; if ([int]::TryParse($_.BaseName, [ref]$n)) { $n } } |
            Sort-Object
    )
    foreach ($v in $exportedVersions) {
        if ($v -ge $declaredVersion) { continue }
        $hasEdge = $registeredEdges.Keys | Where-Object { $_ -match "^$v" + 'To\d+$' }
        if (-not $hasEdge) {
            Add-Finding $db.Key "v$v" 'registration' "schemas/$v.json" `
                "$($db.Module): schema version $v shipped but no registered migration leaves it - a device on $v is wiped by the recovery path on update"
        }
    }

    # ---- chain-test -----------------------------------------------------------------------
    # The whole-chain test is the only evidence that the SEQUENCE runs, and it names its target
    # version as a literal. Absent that check it keeps passing while the version moves past it -
    # green, and one hop shorter every release. Skipped while the module has no migration: there is
    # no chain to walk, and demanding the file would block the gate's own activation.
    if ($migrationFiles.Count -gt 0) {
        $chainTestFile = Join-Path $db.AndroidTestDir $db.ChainTestFile
        if (-not (Test-Path $chainTestFile)) {
            Add-Finding $db.Key 'chain' 'chain-test' $db.ChainTestFile `
                "$($db.Module): $($db.ChainTestFile) does not exist in $($db.RelativePaths.AndroidTestDir), so nothing walks the whole migration chain"
        }
        else {
            $chainText = Get-Content $chainTestFile -Raw
            $chainVersionMatch = [regex]::Match($chainText, "$($db.ChainTestConstant)\s*=\s*(\d+)")
            if (-not $chainVersionMatch.Success) {
                Add-Finding $db.Key 'chain' 'chain-test' $db.ChainTestConstant `
                    "$($db.Module): $($db.ChainTestFile) declares no $($db.ChainTestConstant) constant, so nothing can tell which version it walks to"
            }
            elseif ([int]$chainVersionMatch.Groups[1].Value -ne $declaredVersion) {
                Add-Finding $db.Key 'chain' 'chain-test' $db.ChainTestConstant `
                    "$($db.Module): $($db.ChainTestFile) walks to $($chainVersionMatch.Groups[1].Value) while the database declares $declaredVersion - the newest hop is covered by nothing"
            }
            $newestEdge = "MIGRATION_$($declaredVersion - 1)_$declaredVersion"
            if ($chainText -notmatch [regex]::Escape($newestEdge)) {
                Add-Finding $db.Key 'chain' 'chain-test' $newestEdge `
                    "$($db.Module): $($db.ChainTestFile) does not pass $newestEdge, so the chain stops short of the version the app ships"
            }
        }
    }

    # ---- per-migration SQL dimensions ------------------------------------------------------
    foreach ($file in $migrationFiles) {
        $null = $file.BaseName -match '^Migration(\d+)To(\d+)$'
        $from = [int]$Matches[1]
        $to = [int]$Matches[2]
        $token = "$($from)To$($to)"

        if (-not $registeredEdges.ContainsKey($token)) {
            Add-Finding $db.Key $token 'registration' "MIGRATION_$($from)_$($to)" `
                "$($db.Module): Migration$token.kt exists but is not registered in $($db.RelativePaths.RegistrationFile) - the hop throws and the recovery path deletes the database"
        }

        # Join Kotlin string concatenation ("CREATE TABLE .." + "..") so a statement split across
        # lines is matched as the single SQL statement it becomes at runtime.
        $text = (Get-Content $file.FullName -Raw) -replace '"\s*\+\s*\r?\n?\s*"', ''

        $schema = Get-Schema $db.SchemaDir $to
        $statements = 0
        $skipped = $null -eq $schema

        # Tables this migration renames away or drops are absent from the target schema by design.
        $transientTables = @{}
        foreach ($m in [regex]::Matches($text, '(?i)ALTER\s+TABLE\s+`?(?<t>[A-Za-z0-9_]+)`?\s+RENAME\s+TO')) {
            $transientTables[$m.Groups['t'].Value] = $true
        }
        foreach ($m in [regex]::Matches($text, '(?i)DROP\s+TABLE(?:\s+IF\s+EXISTS)?\s+`?(?<t>[A-Za-z0-9_]+)`?')) {
            $transientTables[$m.Groups['t'].Value] = $true
        }

        foreach ($m in [regex]::Matches($text, '(?i)CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+`?(?<t>[A-Za-z0-9_]+)`?')) {
            $statements++
            $table = $m.Groups['t'].Value
            if ($skipped -or $transientTables.ContainsKey($table)) { continue }
            if ($null -eq (Get-SchemaTable $schema $table)) {
                Add-Finding $db.Key $token 'table-name' $table `
                    "$($db.Module): CREATE TABLE `"$table`" but schema $to has no such table - Room validates a table this migration never created under that name"
            }
        }

        foreach ($m in [regex]::Matches($text, '(?i)ALTER\s+TABLE\s+`?(?<t>[A-Za-z0-9_]+)`?\s+ADD\s+COLUMN\s+`?(?<c>[A-Za-z0-9_]+)`?(?<rest>[^"]*)')) {
            $statements++
            if ($skipped) { continue }
            $table = $m.Groups['t'].Value
            $column = $m.Groups['c'].Value
            $rest = $m.Groups['rest'].Value
            $subject = "$table.$column"

            $sqlNotNull = [regex]::IsMatch($rest, '(?i)\bNOT\s+NULL\b')
            $defaultMatch = [regex]::Match($rest, "(?i)\bDEFAULT\s+(?<d>'(?:[^']|'')*'|\(?[^\s`")]+\)?)")
            $sqlDefault = if ($defaultMatch.Success) { ConvertTo-ComparableDefault $defaultMatch.Groups['d'].Value } else { $null }

            if ($sqlNotNull -and $null -eq $sqlDefault) {
                Add-Finding $db.Key $token 'not-null' $subject `
                    "$($db.Module): ADD COLUMN `"$column`" is NOT NULL with no DEFAULT - SQLite refuses the statement on any table that has rows"
            }

            $schemaTable = Get-SchemaTable $schema $table
            if ($null -eq $schemaTable) {
                if (-not $transientTables.ContainsKey($table)) {
                    Add-Finding $db.Key $token 'table-name' $table `
                        "$($db.Module): ALTER TABLE `"$table`" but schema $to has no such table"
                }
                continue
            }

            $field = Get-SchemaField $schemaTable $column
            if ($null -eq $field) {
                $near = (@(Get-SchemaProperty $schemaTable 'fields') | ForEach-Object { $_.columnName }) -join ', '
                Add-Finding $db.Key $token 'column-name' $subject `
                    "$($db.Module): ADD COLUMN `"$column`" but schema $to declares no such column on `"$table`" - Room's validation fails on every upgrading device and the database is reset. Schema has: $near"
                continue
            }

            $schemaNotNull = [bool](Get-SchemaProperty $field 'notNull')
            if ($schemaNotNull -ne $sqlNotNull) {
                $sqlWord = if ($sqlNotNull) { 'NOT NULL' } else { 'nullable' }
                $schemaWord = if ($schemaNotNull) { 'NOT NULL' } else { 'nullable' }
                Add-Finding $db.Key $token 'not-null' $subject `
                    "$($db.Module): SQL adds `"$column`" as $sqlWord, schema $to declares it $schemaWord"
            }

            $rawDefault = Get-SchemaProperty $field 'defaultValue'
            $schemaDefault = if ($null -ne $rawDefault) { ConvertTo-ComparableDefault ([string]$rawDefault) } else { $null }
            if ($null -ne $schemaDefault -and $schemaDefault -ne $sqlDefault) {
                $shown = if ($null -eq $sqlDefault) { '<none>' } else { $sqlDefault }
                Add-Finding $db.Key $token 'column-default' $subject `
                    "$($db.Module): schema $to declares DEFAULT $schemaDefault for `"$column`", the migration writes $shown - Room refuses a default the table does not carry"
            }
        }

        $reports.Add([pscustomobject]@{
                Module     = $db.Module
                Key        = $db.Key
                Token      = $token
                Statements = $statements
                Skipped    = $skipped
                Findings   = @($findings | Where-Object { $_.Module -eq $db.Key -and $_.Migration -eq $token }).Count
            })
    }

    $moduleSummaries.Add([pscustomobject]@{
            Module          = $db.Module
            Key             = $db.Key
            DeclaredVersion = $declaredVersion
            MigrationCount  = $migrationFiles.Count
        })
}

# ---- baseline ---------------------------------------------------------------------------
if ($UpdateBaseline) {
    ($findings | ForEach-Object { $_.Key }) | Set-Content -Path $baselineFile -Encoding utf8NoBOM
    Write-Host ("assert-migration-schema-conformance: baseline rewritten - {0} accepted disagreement(s)." -f $findings.Count)
    exit 0
}

# Assigned in two statements, not as an if-expression: an empty array returned from an `if`
# collapses to $null, and $null.Count is a terminating error under StrictMode.
$baseline = @()
if (Test-Path $baselineFile) {
    $baseline = @(Get-Content $baselineFile | ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith('#') })
}

if ($List) {
    foreach ($summary in $moduleSummaries) {
        if ($summary.MigrationCount -eq 0) {
            Write-Host ("  [{0}] version {1}, no migration yet - exported schema only" -f $summary.Module, $summary.DeclaredVersion)
            continue
        }
        Write-Host ("  [{0}] version {1}" -f $summary.Module, $summary.DeclaredVersion)
        foreach ($r in @($reports | Where-Object { $_.Key -eq $summary.Key })) {
            $state = if ($r.Skipped) { 'no exported schema - skipped' } elseif ($r.Findings -gt 0) { "$($r.Findings) finding(s)" } else { 'clean' }
            Write-Host ("    Migration{0,-8} {1,3} statement(s)  {2}" -f $r.Token, $r.Statements, $state)
        }
    }
}

$new = @($findings | Where-Object { $baseline -notcontains $_.Key })

if ($new.Count -gt 0) {
    Write-Host ("assert-migration-schema-conformance: FAIL - {0} migration/schema disagreement(s):" -f $new.Count) -ForegroundColor Red
    foreach ($f in $new) {
        Write-Host ("  [{0}] Migration{1}: {2}" -f $f.Dimension, $f.Migration, $f.Message) -ForegroundColor Red
    }
    Write-Host "  Room compares these on the user's device during the first launch after an update, and" -ForegroundColor Red
    Write-Host "  a mismatch there deletes the database. Fix the SQL or the entity, regenerate the schema" -ForegroundColor Red
    Write-Host "  with .\a.ps1 fk (app_v2) or .\a.ps1 fw (wear), then re-run. Never baseline a migration" -ForegroundColor Red
    Write-Host "  that has not shipped." -ForegroundColor Red
    if ($Gate) { exit 1 }
    exit 0
}

if (-not $Quiet -or $List) {
    $perModule = @(
        foreach ($summary in $moduleSummaries) {
            # A module with no migration reads "0 compared, 0 skipped" otherwise, which sounds like
            # something was declined rather than like the database being at its first version.
            if ($summary.MigrationCount -eq 0) {
                "{0} version {1}: no migration yet, exported schema only" -f $summary.Module, $summary.DeclaredVersion
                continue
            }
            $moduleReports = @($reports | Where-Object { $_.Key -eq $summary.Key })
            $compared = @($moduleReports | Where-Object { -not $_.Skipped }).Count
            $skippedCount = @($moduleReports | Where-Object { $_.Skipped }).Count
            "{0} version {1}: {2} compared, {3} skipped (no schema exported)" -f $summary.Module, $summary.DeclaredVersion, $compared, $skippedCount
        }
    ) -join '; '
    Write-Host ("assert-migration-schema-conformance: PASS - {0} database(s) [{1}], {2} baselined." -f `
            $moduleSummaries.Count, $perModule, $baseline.Count) -ForegroundColor Green
}
else {
    Write-Host 'assert-migration-schema-conformance: PASS' -ForegroundColor Green
}
exit 0
