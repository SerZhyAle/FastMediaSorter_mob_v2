#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: what a Room migration writes in SQL must match the exported schema Room
    validates the upgraded database against, and every migration must be registered.

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

    Five dimensions, all decidable from text - no device, no gradle, no Room:

      registration   every MigrationNNToMM.kt is registered in DatabaseModule.addMigrations(),
                     every exported schema version below the declared one has an outgoing edge,
                     and the declared version has an exported schema. An unregistered migration
                     is not a no-op: the hop throws, and the recovery path wipes the database.
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
      chain-test     AppDatabaseMigrationChainTest - the one test that walks the oldest exported
                     schema to the current version - must name the DECLARED version and the newest
                     migration. Frozen at an older target it keeps passing while covering one hop
                     less every release, which is the exact shape of evidence this gate refuses.

    RATCHET, like assert-migration-test-pairing. The tree carries migrations older than the
    exported-schema set (31..35 have no schema to compare against and are reported as skipped)
    and any finding on a migration that already shipped is history, not a defect to fix - the
    database on those users' devices is whatever the SQL actually produced. Baseline file:
    migration-schema-conformance-baseline.txt, one finding key per line. A NEW disagreement
    fails; regenerate with -UpdateBaseline only when deliberately accepting one, which for a
    migration that has not shipped should be never.

.PARAMETER Gate
    Exit 1 when an unbaselined disagreement is found. Without it the script only reports.

.PARAMETER UpdateBaseline
    Rewrite the baseline from the current tree.

.PARAMETER List
    Print every migration with its statement count and verdict, including the baselined ones.

.PARAMETER Quiet
    Suppress the per-migration progress lines; the verdict line is always printed.

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
      2  cannot verify - the migration directory, the schema directory, DatabaseModule.kt or
         AppDatabase.kt is missing, or the declared schema version cannot be read.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [switch]$List,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath -Detailed
    exit 0
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$dbDir = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/data/local/db'
$schemaDir = Join-Path $repoRoot 'app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase'
$moduleFile = Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt'
$appDbFile = Join-Path $dbDir 'AppDatabase.kt'
$baselineFile = Join-Path $PSScriptRoot 'migration-schema-conformance-baseline.txt'

function Stop-CannotVerify([string]$message) {
    [Console]::Error.WriteLine("assert-migration-schema-conformance: cannot verify - $message")
    exit 2
}

if (-not (Test-Path $dbDir)) { Stop-CannotVerify "migration directory not found: $dbDir" }
if (-not (Test-Path $schemaDir)) { Stop-CannotVerify "exported schema directory not found: $schemaDir" }
if (-not (Test-Path $moduleFile)) { Stop-CannotVerify "DatabaseModule.kt not found: $moduleFile" }
if (-not (Test-Path $appDbFile)) { Stop-CannotVerify "AppDatabase.kt not found: $appDbFile" }

# The declared schema version. Room validates against schemas/<version>.json, so everything
# below is anchored to this number rather than to the highest file on disk - a stale extra
# JSON must not be able to redefine what the app ships.
$appDbText = Get-Content $appDbFile -Raw
$versionMatch = [regex]::Match($appDbText, '(?m)^\s*version\s*=\s*(\d+)')
if (-not $versionMatch.Success) { Stop-CannotVerify "no 'version = N' found in AppDatabase.kt" }
$declaredVersion = [int]$versionMatch.Groups[1].Value

$findings = [System.Collections.Generic.List[object]]::new()
function Add-Finding([string]$migration, [string]$dimension, [string]$subject, [string]$message) {
    $findings.Add([pscustomobject]@{
            Key       = "$migration|$dimension|$subject"
            Migration = $migration
            Dimension = $dimension
            Subject   = $subject
            Message   = $message
        })
}

# ---- schema cache -----------------------------------------------------------------------
$schemaCache = @{}
function Get-Schema([int]$version) {
    if ($schemaCache.ContainsKey($version)) { return $schemaCache[$version] }
    $path = Join-Path $schemaDir "$version.json"
    $value = if (Test-Path $path) { (Get-Content $path -Raw | ConvertFrom-Json).database } else { $null }
    $schemaCache[$version] = $value
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

# ---- registration -----------------------------------------------------------------------
$moduleText = Get-Content $moduleFile -Raw
$addBlock = [regex]::Match($moduleText, '\.addMigrations\s*\((?<body>[\s\S]*?)\)\s*$', 'Multiline')
$registeredBody = if ($addBlock.Success) { $addBlock.Groups['body'].Value } else { $moduleText }
$registeredEdges = @{}
foreach ($m in [regex]::Matches($registeredBody, 'MIGRATION_(\d+)_(\d+)')) {
    $from = [int]$m.Groups[1].Value
    $to = [int]$m.Groups[2].Value
    $registeredEdges["$($from)To$($to)"] = $true
}
if ($registeredEdges.Count -eq 0) { Stop-CannotVerify 'no MIGRATION_N_M reference found in DatabaseModule.kt' }

if (-not (Test-Path (Join-Path $schemaDir "$declaredVersion.json"))) {
    Add-Finding "v$declaredVersion" 'registration' "schemas/$declaredVersion.json" `
        "AppDatabase declares version $declaredVersion but no exported schema exists for it - Room has nothing to validate an upgraded database against"
}

$exportedVersions = @(
    Get-ChildItem -Path $schemaDir -Filter '*.json' -File |
        ForEach-Object { $n = 0; if ([int]::TryParse($_.BaseName, [ref]$n)) { $n } } |
        Sort-Object
)
foreach ($v in $exportedVersions) {
    if ($v -ge $declaredVersion) { continue }
    $hasEdge = $registeredEdges.Keys | Where-Object { $_ -match "^$v" + 'To\d+$' }
    if (-not $hasEdge) {
        Add-Finding "v$v" 'registration' "schemas/$v.json" `
            "schema version $v shipped but no registered migration leaves it - a device on $v is wiped by the recovery path on update"
    }
}

# "Migration31To32.kt" -> "31To32". Anchored so a helper like MigrationHelpers.kt is not taken
# for a migration.
# chain-test: the whole-chain test is the only evidence that the SEQUENCE runs, and it names its target
# version as a literal. Absent that check it keeps passing while the version moves past it - green, and
# one hop shorter every release.
$chainTestFile = Join-Path $repoRoot 'app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db/AppDatabaseMigrationChainTest.kt'
if (Test-Path $chainTestFile) {
    $chainText = Get-Content $chainTestFile -Raw
    $chainVersionMatch = [regex]::Match($chainText, 'CURRENT_SCHEMA\s*=\s*(\d+)')
    if (-not $chainVersionMatch.Success) {
        Add-Finding 'chain' 'chain-test' 'CURRENT_SCHEMA' `
            'AppDatabaseMigrationChainTest declares no CURRENT_SCHEMA constant, so nothing can tell which version it walks to'
    }
    elseif ([int]$chainVersionMatch.Groups[1].Value -ne $declaredVersion) {
        Add-Finding 'chain' 'chain-test' 'CURRENT_SCHEMA' `
            "AppDatabaseMigrationChainTest walks to $($chainVersionMatch.Groups[1].Value) while AppDatabase declares $declaredVersion - the newest hop is covered by nothing"
    }
    $newestEdge = "MIGRATION_$($declaredVersion - 1)_$declaredVersion"
    if ($chainText -notmatch [regex]::Escape($newestEdge)) {
        Add-Finding 'chain' 'chain-test' $newestEdge `
            "AppDatabaseMigrationChainTest does not pass $newestEdge, so the chain stops short of the version the app ships"
    }
}

$migrationFiles = @(
    Get-ChildItem -Path $dbDir -Filter 'Migration*.kt' -File |
        Where-Object { $_.BaseName -match '^Migration(\d+)To(\d+)$' } |
        Sort-Object { [int]([regex]::Match($_.BaseName, '^Migration(\d+)To').Groups[1].Value) }
)
if ($migrationFiles.Count -eq 0) { Stop-CannotVerify 'no MigrationNNToMM.kt found' }

$reports = [System.Collections.Generic.List[object]]::new()

foreach ($file in $migrationFiles) {
    $null = $file.BaseName -match '^Migration(\d+)To(\d+)$'
    $from = [int]$Matches[1]
    $to = [int]$Matches[2]
    $token = "$($from)To$($to)"

    if (-not $registeredEdges.ContainsKey($token)) {
        Add-Finding $token 'registration' "MIGRATION_$($from)_$($to)" `
            "Migration$token.kt exists but is not in DatabaseModule.addMigrations() - the hop throws and the recovery path deletes the database"
    }

    # Join Kotlin string concatenation ("CREATE TABLE .." + "..") so a statement split across
    # lines is matched as the single SQL statement it becomes at runtime.
    $text = (Get-Content $file.FullName -Raw) -replace '"\s*\+\s*\r?\n?\s*"', ''

    $schema = Get-Schema $to
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
            Add-Finding $token 'table-name' $table `
                "CREATE TABLE `"$table`" but schema $to has no such table - Room validates a table this migration never created under that name"
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
            Add-Finding $token 'not-null' $subject `
                "ADD COLUMN `"$column`" is NOT NULL with no DEFAULT - SQLite refuses the statement on any table that has rows"
        }

        $schemaTable = Get-SchemaTable $schema $table
        if ($null -eq $schemaTable) {
            if (-not $transientTables.ContainsKey($table)) {
                Add-Finding $token 'table-name' $table `
                    "ALTER TABLE `"$table`" but schema $to has no such table"
            }
            continue
        }

        $field = Get-SchemaField $schemaTable $column
        if ($null -eq $field) {
            $near = (@(Get-SchemaProperty $schemaTable 'fields') | ForEach-Object { $_.columnName }) -join ', '
            Add-Finding $token 'column-name' $subject `
                "ADD COLUMN `"$column`" but schema $to declares no such column on `"$table`" - Room's validation fails on every upgrading device and the database is reset. Schema has: $near"
            continue
        }

        $schemaNotNull = [bool](Get-SchemaProperty $field 'notNull')
        if ($schemaNotNull -ne $sqlNotNull) {
            $sqlWord = if ($sqlNotNull) { 'NOT NULL' } else { 'nullable' }
            $schemaWord = if ($schemaNotNull) { 'NOT NULL' } else { 'nullable' }
            Add-Finding $token 'not-null' $subject `
                "SQL adds `"$column`" as $sqlWord, schema $to declares it $schemaWord"
        }

        $rawDefault = Get-SchemaProperty $field 'defaultValue'
        $schemaDefault = if ($null -ne $rawDefault) { ConvertTo-ComparableDefault ([string]$rawDefault) } else { $null }
        if ($null -ne $schemaDefault -and $schemaDefault -ne $sqlDefault) {
            $shown = if ($null -eq $sqlDefault) { '<none>' } else { $sqlDefault }
            Add-Finding $token 'column-default' $subject `
                "schema $to declares DEFAULT $schemaDefault for `"$column`", the migration writes $shown - Room refuses a default the table does not carry"
        }
    }

    $reports.Add([pscustomobject]@{
            Token      = $token
            Statements = $statements
            Skipped    = $skipped
            Findings   = @($findings | Where-Object { $_.Migration -eq $token }).Count
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
    foreach ($r in $reports) {
        $state = if ($r.Skipped) { 'no exported schema - skipped' } elseif ($r.Findings -gt 0) { "$($r.Findings) finding(s)" } else { 'clean' }
        Write-Host ("  Migration{0,-8} {1,3} statement(s)  {2}" -f $r.Token, $r.Statements, $state)
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
    Write-Host "  with .\a.ps1 fk, then re-run. Never baseline a migration that has not shipped." -ForegroundColor Red
    if ($Gate) { exit 1 }
    exit 0
}

if (-not $Quiet -or $List) {
    $compared = @($reports | Where-Object { -not $_.Skipped }).Count
    $skippedCount = @($reports | Where-Object { $_.Skipped }).Count
    Write-Host ("assert-migration-schema-conformance: PASS - version {0}, {1} migration(s) compared against their exported schema, {2} skipped (no schema exported), {3} baselined." -f `
            $declaredVersion, $compared, $skippedCount, $baseline.Count) -ForegroundColor Green
}
else {
    Write-Host 'assert-migration-schema-conformance: PASS' -ForegroundColor Green
}
exit 0
