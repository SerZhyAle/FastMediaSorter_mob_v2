# Run-Tests.ps1 (S2306) - regression suite for the migration/schema conformance gate.
#
# The gate exists because of one incident, so the suite's first duty is to replay it: the S2251
# migration added `screen_index` while the entity declared `screenIndex`, and every check in the
# repository stayed green while the owner's phone reset its database. Case 2 is that exact pair,
# and a gate that passes it is worthless no matter how green the live tree looks.
#
# Every case runs against a synthetic repository under a temp dir, removed in a finally block.
# The gate resolves its repo root from its own $PSScriptRoot, so each sandbox holds a copy of
# the gate in 'scripts/quality/' and a minimal app_v2 tree. Nothing here writes into app_v2.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-migration-schema-conformance.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$gateSource = Join-Path $repoRoot 'scripts/quality/assert-migration-schema-conformance.ps1'
$registrySource = Join-Path $repoRoot 'scripts/quality/lib/room-databases.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

# S2355: the second registered database. By default it reproduces the watch as the tree really
# carries it - version 1, no migration, a Room builder with no .addMigrations(..) - which is the
# state the gate must call clean rather than unverifiable. Passing -MigrationSql grows it to version
# 2 with one hop, which is how the wear-specific cases plant their defect.
function New-WearHalf {
    param(
        [string]$Sandbox,
        [string]$MigrationSql,
        [string]$SchemaColumnName,
        [bool]$Register,
        [bool]$OmitSchemaDir,
        # S2355 step 04.0: plant a `const val version = 9` ABOVE the annotation. The gate must still
        # read the annotated version - reading the decoy would compare every migration in the module
        # against the wrong exported schema while printing PASS.
        [bool]$DecoyVersionConstant,
        # S2830: see New-Sandbox for why these two shapes have to exist in the sandbox at all.
        [bool]$AggregateMigration,
        [string]$UnclaimedMigrationFile
    )
    $wearDbDir = Join-Path $Sandbox 'wear/src/main/java/com/sza/fastmediasorter/wear/data/db'
    $wearDiDir = Join-Path $Sandbox 'wear/src/main/java/com/sza/fastmediasorter/wear/di'
    $wearSchemaDir = Join-Path $Sandbox 'wear/schemas/com.sza.fastmediasorter.wear.data.db.WearVoiceNoteDatabase'
    New-Item -ItemType Directory -Force -Path $wearDbDir, $wearDiDir | Out-Null
    if (-not $OmitSchemaDir) { New-Item -ItemType Directory -Force -Path $wearSchemaDir | Out-Null }

    # S2829: the watch's other two databases sit in the same directory, at version 1 with an exported
    # schema and no migration. They are written into every sandbox rather than only into the cases
    # that name them, because the gate reads the real registry - a sandbox missing them would exit 2
    # on every case and the suite would stop testing what each case was written for. Their presence is
    # also the property under test: the third and fourth database must not disturb the first two.
    foreach ($sibling in @('WearHeartRateDatabase', 'WearBloodPressureDatabase')) {
        Set-Content -Path (Join-Path $wearDbDir "$sibling.kt") -Encoding utf8NoBOM -Value @"
package com.sza.fastmediasorter.wear.data.db

@Database(entities = [${sibling}Entity::class], version = 1, exportSchema = true)
abstract class $sibling
"@
        $siblingSchemaDir = Join-Path $Sandbox "wear/schemas/com.sza.fastmediasorter.wear.data.db.$sibling"
        New-Item -ItemType Directory -Force -Path $siblingSchemaDir | Out-Null
        Set-Content -Path (Join-Path $siblingSchemaDir '1.json') -Encoding utf8NoBOM -Value (
            [pscustomobject]@{ formatVersion = 1; database = [pscustomobject]@{
                    version = 1; identityHash = 's1'
                    entities = @([pscustomobject]@{ tableName = 'history'; fields = @(
                                [pscustomobject]@{ fieldPath = 'id'; columnName = 'id'; affinity = 'INTEGER'; notNull = $true }) })
                }
            } | ConvertTo-Json -Depth 12)
    }

    $hasMigration = -not [string]::IsNullOrWhiteSpace($MigrationSql)
    $wearVersion = if ($hasMigration) { 2 } else { 1 }

    # S2830: a hop nobody claims. Written whatever the migration state is, because a directory with
    # no registered migration at all is exactly where the old gate was most confidently wrong.
    if (-not [string]::IsNullOrWhiteSpace($UnclaimedMigrationFile)) {
        Set-Content -Path (Join-Path $wearDbDir $UnclaimedMigrationFile) -Encoding utf8NoBOM -Value @'
package com.sza.fastmediasorter.wear.data.db

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `voice_notes` ADD COLUMN `nothing_reads_me` INTEGER")
    }
}
'@
    }

    $decoy = if ($DecoyVersionConstant) { "const val version = 9$([Environment]::NewLine)" } else { '' }
    Set-Content -Path (Join-Path $wearDbDir 'WearVoiceNoteDatabase.kt') -Encoding utf8NoBOM -Value @"
package com.sza.fastmediasorter.wear.data.db

$decoy@Database(entities = [VoiceNoteEntity::class], version = $wearVersion, exportSchema = true)
abstract class WearVoiceNoteDatabase
"@

    $wearRegistration = ''
    if ($hasMigration) {
        # The aggregate form is the registry's WearVoiceNoteMigrations.kt as the real tree carries it:
        # a hop with no address in its file name, wrapped in an object so its body is one brace level
        # deeper than the per-hop form. Both forms must reach the same verdict (S2830).
        if ($AggregateMigration) {
            Set-Content -Path (Join-Path $wearDbDir 'WearVoiceNoteMigrations.kt') -Encoding utf8NoBOM -Value @"
package com.sza.fastmediasorter.wear.data.db

object WearVoiceNoteMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("$MigrationSql")
        }
    }
}
"@
        }
        else {
            Set-Content -Path (Join-Path $wearDbDir 'Migration1To2.kt') -Encoding utf8NoBOM -Value @"
package com.sza.fastmediasorter.wear.data.db

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("$MigrationSql")
    }
}
"@
        }
        if ($Register) { $wearRegistration = ".addMigrations(`n                MIGRATION_1_2,`n            )" }

        $chainDir = Join-Path $Sandbox 'wear/src/androidTest/java/com/sza/fastmediasorter/wear/data/db'
        New-Item -ItemType Directory -Force -Path $chainDir | Out-Null
        Set-Content -Path (Join-Path $chainDir 'WearVoiceNoteDatabaseMigrationChainTest.kt') -Encoding utf8NoBOM -Value @'
package com.sza.fastmediasorter.wear.data.db

class WearVoiceNoteDatabaseMigrationChainTest {
    fun run() {
        helper.runMigrationsAndValidate(TEST_DB, CURRENT_SCHEMA, true, MIGRATION_1_2)
    }

    private companion object {
        const val CURRENT_SCHEMA = 2
    }
}
'@
    }

    Set-Content -Path (Join-Path $wearDiDir 'WearAppModule.kt') -Encoding utf8NoBOM -Value @"
package com.sza.fastmediasorter.wear.di

object WearAppModule {
    fun provideWearVoiceNoteDatabase(context: Context): WearVoiceNoteDatabase =
        Room.databaseBuilder(context, WearVoiceNoteDatabase::class.java, DB_NAME)
            $wearRegistration
            .build()
}
"@

    if ($OmitSchemaDir) { return }

    $wearFields = @([pscustomobject]@{ fieldPath = 'id'; columnName = 'id'; affinity = 'INTEGER'; notNull = $true })
    Set-Content -Path (Join-Path $wearSchemaDir '1.json') -Encoding utf8NoBOM -Value (
        [pscustomobject]@{ formatVersion = 1; database = [pscustomobject]@{
                version = 1; identityHash = 'w1'
                entities = @([pscustomobject]@{ tableName = 'voice_notes'; fields = $wearFields })
            }
        } | ConvertTo-Json -Depth 12)

    if (-not $hasMigration) { return }

    $wearAdded = [pscustomobject]@{ fieldPath = $SchemaColumnName; columnName = $SchemaColumnName; affinity = 'INTEGER'; notNull = $false }
    Set-Content -Path (Join-Path $wearSchemaDir '2.json') -Encoding utf8NoBOM -Value (
        [pscustomobject]@{ formatVersion = 1; database = [pscustomobject]@{
                version = 2; identityHash = 'w2'
                entities = @([pscustomobject]@{ tableName = 'voice_notes'; fields = @($wearFields + $wearAdded) })
            }
        } | ConvertTo-Json -Depth 12)
}

# Builds a sandbox holding one migration, one exported schema and a DatabaseModule that either
# registers the migration or does not.
function New-Sandbox {
    param(
        [string]$MigrationSql,
        [string]$SchemaColumnName,
        [bool]$SchemaNotNull,
        [string]$SchemaDefault,
        [bool]$Register = $true,
        [bool]$WriteTargetSchema = $true,
        [bool]$IncludeUnexportedOlderMigration = $false,
        # S2355 made a missing chain test a finding for any database that HAS a migration, so the
        # default sandbox writes a correct one - otherwise every case testing an SQL dimension also
        # trips chain-test and stops testing what it was written for. 0 omits it deliberately.
        [int]$ChainTestVersion = 54,
        [string]$BaselineText,
        # S2355: the wear half of the sandbox. Defaults reproduce the real tree - database version 1,
        # no migration, a Room builder with no .addMigrations(..) at all - so every pre-existing case
        # keeps testing exactly what it tested before, with a second database merely present.
        [string]$WearMigrationSql,
        [string]$WearSchemaColumnName = 'transferState',
        [bool]$WearRegister = $true,
        [bool]$OmitWearSchemaDir = $false,
        [bool]$WearDecoyVersionConstant = $false,
        # S2830: write the wear migration as an aggregate object file rather than as Migration1To2.kt.
        # A case that only ever plants its defect in a per-hop file proves nothing about the shape
        # that was invisible - the gate printed "no migration yet" for it and every case stayed green.
        [bool]$WearAggregateMigration = $false,
        # S2830: an extra .kt in the wear migration directory declaring a hop under a name matching no
        # row's prefix and listed in no row's aggregate files. It stands for the file nobody has
        # written yet, which is the only thing goal 2 is a claim about.
        [string]$WearUnclaimedMigrationFile
    )
    $sandbox = Join-Path ([System.IO.Path]::GetTempPath()) ('s2306-' + [System.IO.Path]::GetRandomFileName())
    $qualityDir = Join-Path $sandbox 'scripts/quality'
    $libDir = Join-Path $qualityDir 'lib'
    $dbDir = Join-Path $sandbox 'app_v2/src/main/java/com/sza/fastmediasorter/data/local/db'
    $diDir = Join-Path $sandbox 'app_v2/src/main/java/com/sza/fastmediasorter/core/di'
    $schemaDir = Join-Path $sandbox 'app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase'
    New-Item -ItemType Directory -Force -Path $qualityDir, $libDir, $dbDir, $diDir, $schemaDir | Out-Null

    Copy-Item $gateSource (Join-Path $qualityDir 'assert-migration-schema-conformance.ps1')
    # The gate reads the database list from this library (S2355), so the sandbox is not a working
    # copy of the gate without it.
    Copy-Item $registrySource (Join-Path $libDir 'room-databases.ps1')
    if ($PSBoundParameters.ContainsKey('BaselineText')) {
        Set-Content -Path (Join-Path $qualityDir 'migration-schema-conformance-baseline.txt') -Value $BaselineText -Encoding utf8NoBOM
    }

    New-WearHalf -Sandbox $sandbox -MigrationSql $WearMigrationSql -SchemaColumnName $WearSchemaColumnName `
        -Register $WearRegister -OmitSchemaDir $OmitWearSchemaDir -DecoyVersionConstant $WearDecoyVersionConstant `
        -AggregateMigration $WearAggregateMigration -UnclaimedMigrationFile $WearUnclaimedMigrationFile

    Set-Content -Path (Join-Path $dbDir 'AppDatabase.kt') -Encoding utf8NoBOM -Value @'
package com.sza.fastmediasorter.data.local.db

@Database(
    entities = [LauncherCellEntity::class],
    version = 54,
    exportSchema = true
)
abstract class AppDatabase
'@

    Set-Content -Path (Join-Path $dbDir 'Migration53To54.kt') -Encoding utf8NoBOM -Value @"
package com.sza.fastmediasorter.data.local.db

val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("$MigrationSql")
    }
}
"@

    if ($IncludeUnexportedOlderMigration) {
        Set-Content -Path (Join-Path $dbDir 'Migration51To52.kt') -Encoding utf8NoBOM -Value @'
package com.sza.fastmediasorter.data.local.db

val MIGRATION_51_52 = object : Migration(51, 52) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `launcher_cells` ADD COLUMN `nothing_validates_me` INTEGER")
    }
}
'@
    }

    $registration = if ($Register) { "                MIGRATION_53_54`n" } else { '' }
    if ($IncludeUnexportedOlderMigration) { $registration = "                MIGRATION_51_52,`n" + $registration }
    Set-Content -Path (Join-Path $diDir 'DatabaseModule.kt') -Encoding utf8NoBOM -Value @"
package com.sza.fastmediasorter.core.di

object DatabaseModule {
    private fun buildDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(
                MIGRATION_52_53,
$registration            )
            .build()
    }
}
"@

    if ($ChainTestVersion -gt 0) {
        $chainDir = Join-Path $sandbox 'app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db'
        New-Item -ItemType Directory -Force -Path $chainDir | Out-Null
        Set-Content -Path (Join-Path $chainDir 'AppDatabaseMigrationChainTest.kt') -Encoding utf8NoBOM -Value @"
package com.sza.fastmediasorter.data.local.db

class AppDatabaseMigrationChainTest {
    fun run() {
        helper.runMigrationsAndValidate(TEST_DB, CURRENT_SCHEMA, true, MIGRATION_$($ChainTestVersion - 1)_$ChainTestVersion)
    }

    private companion object {
        const val CURRENT_SCHEMA = $ChainTestVersion
    }
}
"@
    }

    # Schema 53 must exist and have an outgoing edge, or the registration dimension fires on it
    # and every case turns red for a reason it is not testing.
    $fields = @(
        [pscustomobject]@{ fieldPath = 'id'; columnName = 'id'; affinity = 'INTEGER'; notNull = $true }
    )
    Set-Content -Path (Join-Path $schemaDir '53.json') -Encoding utf8NoBOM -Value (
        [pscustomobject]@{ formatVersion = 1; database = [pscustomobject]@{
                version = 53; identityHash = 'aaa'
                entities = @([pscustomobject]@{ tableName = 'launcher_cells'; fields = $fields })
            }
        } | ConvertTo-Json -Depth 12)

    if ($WriteTargetSchema) {
        $added = [ordered]@{ fieldPath = $SchemaColumnName; columnName = $SchemaColumnName; affinity = 'INTEGER'; notNull = $SchemaNotNull }
        if ($PSBoundParameters.ContainsKey('SchemaDefault')) { $added['defaultValue'] = $SchemaDefault }
        Set-Content -Path (Join-Path $schemaDir '54.json') -Encoding utf8NoBOM -Value (
            [pscustomobject]@{ formatVersion = 1; database = [pscustomobject]@{
                    version = 54; identityHash = 'bbb'
                    entities = @([pscustomobject]@{ tableName = 'launcher_cells'; fields = @($fields + [pscustomobject]$added) })
                }
            } | ConvertTo-Json -Depth 12)
    }

    return $sandbox
}

function Invoke-Gate([string]$Sandbox) {
    $out = & $pwshExe -NoProfile -File (Join-Path $Sandbox 'scripts/quality/assert-migration-schema-conformance.ps1') -Gate 2>&1
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Text = ($out | Out-String) }
}

$cases = @(
    @{
        Name    = 'a migration whose column matches the schema passes'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
        }
        Expect  = 0
        Contain = 'PASS'
    },
    @{
        Name    = 'S2251 replay: SQL adds screen_index while the schema declares screenIndex'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screen_index` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
        }
        Expect  = 1
        Contain = 'column-name'
    },
    @{
        Name    = 'S2251 second half: the schema declares a default the migration does not write'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $false; SchemaDefault = '0'
        }
        Expect  = 1
        Contain = 'column-default'
    },
    @{
        Name    = 'NOT NULL added without a DEFAULT is refused by SQLite and by the gate'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true
        }
        Expect  = 1
        Contain = 'no DEFAULT'
    },
    @{
        Name    = 'nullability drift between SQL and schema is a finding'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true
        }
        Expect  = 1
        Contain = 'not-null'
    },
    @{
        Name    = 'a migration file that DatabaseModule never registers is a finding'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'; Register = $false
        }
        Expect  = 1
        Contain = 'is not registered in'
    },
    @{
        Name    = 'the declared version shipping without an exported schema is itself a finding'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'; WriteTargetSchema = $false
        }
        Expect  = 1
        Contain = 'no exported schema exists for it'
    },
    @{
        Name    = 'an older hop whose target schema was never exported is skipped, not failed'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
            IncludeUnexportedOlderMigration = $true
        }
        Expect  = 0
        Contain = '1 skipped'
    },
    @{
        Name    = 'a chain test frozen at the previous version is a finding'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'; ChainTestVersion = 53
        }
        Expect  = 1
        Contain = 'walks to 53'
    },
    @{
        Name    = 'a chain test that names the declared version and the newest hop passes'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'; ChainTestVersion = 54
        }
        Expect  = 0
        Contain = 'PASS'
    },
    @{
        Name    = 'a baselined disagreement does not fail the gate'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screen_index` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
            BaselineText = 'app_v2|53To54|column-name|launcher_cells.screen_index'
        }
        Expect  = 0
        Contain = 'PASS'
    },
    @{
        Name    = 'a database with migrations and no chain test at all is a finding'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'; ChainTestVersion = 0
        }
        Expect  = 1
        Contain = 'nothing walks the whole migration chain'
    },
    # ---- S2355: the second database ---------------------------------------------------------
    @{
        Name    = 'wear at version 1 with no migration and no addMigrations is clean'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
        }
        Expect  = 0
        Contain = 'wear-voice-note version 1: no migration yet'
    },
    # ---- S2829: three databases out of one directory ----------------------------------------
    @{
        # The voice-note database owns Migration1To2.kt and the other two share its directory. A
        # neighbour that claimed that file would be reported at version 1 with a migration whose
        # target schema it never exported - a finding invented out of somebody else's hop.
        Name    = "a sibling database does not claim the voice-note database's migration file"
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
            WearMigrationSql = 'ALTER TABLE `voice_notes` ADD COLUMN `transferState` INTEGER'
            WearSchemaColumnName = 'transferState'
        }
        Expect  = 0
        Contain = 'wear-heart-rate version 1: no migration yet'
    },
    @{
        Name    = 'a column-name disagreement in the wear database is found and names wear'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
            WearMigrationSql = 'ALTER TABLE `voice_notes` ADD COLUMN `transfer_state` INTEGER'
            WearSchemaColumnName = 'transferState'
        }
        Expect  = 1
        Contain = 'wear-voice-note: ADD COLUMN "transfer_state"'
    },
    @{
        Name    = 'an app_v2 baseline key does not suppress the same finding in wear'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
            WearMigrationSql = 'ALTER TABLE `voice_notes` ADD COLUMN `transfer_state` INTEGER'
            WearSchemaColumnName = 'transferState'
            BaselineText = 'app_v2|1To2|column-name|voice_notes.transfer_state'
        }
        Expect  = 1
        Contain = 'wear-voice-note: ADD COLUMN "transfer_state"'
    },
    @{
        Name    = 'a wear migration file the wear builder never registers is found'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
            WearMigrationSql = 'ALTER TABLE `voice_notes` ADD COLUMN `transferState` INTEGER'
            WearRegister = $false
        }
        Expect  = 2
        Contain = 'wear-voice-note: 1 migration(s) are declared but no MIGRATION_N_M reference'
    },
    @{
        # S2830 replay. Before this ticket the gate reported this sandbox as "no migration yet,
        # exported schema only" and exited 0 - the S2251 defect sitting in plain text, unread,
        # because the hop was declared inside an object instead of inside a file named after it.
        Name    = 'S2830 replay: a column-name defect inside an AGGREGATE migration file is found'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
            WearMigrationSql = 'ALTER TABLE `voice_notes` ADD COLUMN `transfer_state` INTEGER'
            WearSchemaColumnName = 'transferState'
            WearAggregateMigration = $true
        }
        Expect  = 1
        Contain = 'wear-voice-note: ADD COLUMN "transfer_state" but schema 2 declares no such column'
    },
    @{
        Name    = 'an aggregate migration that agrees with its schema passes'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
            WearMigrationSql = 'ALTER TABLE `voice_notes` ADD COLUMN `transferState` INTEGER'
            WearAggregateMigration = $true
        }
        Expect  = 0
        Contain = 'PASS'
    },
    @{
        # Goal 2 is a claim about a file nobody has written yet, so the only way to hold it is to
        # write one: a hop under a name no prefix matches, listed in no row's aggregate files.
        Name    = 'a migration declaration no registry row claims is reported'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
            WearUnclaimedMigrationFile = 'ExtraVoiceNoteMigrations.kt'
        }
        Expect  = 1
        Contain = 'ExtraVoiceNoteMigrations.kt declares Migration(2, 3) but no registry row reads it'
    },
    @{
        Name    = 'a missing wear schema directory exits 2 and names wear'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
            OmitWearSchemaDir = $true
        }
        Expect  = 2
        Contain = 'wear-voice-note: registry SchemaDir does not exist'
    },
    @{
        # S2355 step 04.0. Before the fix the version was read with a bare `version = (\d+)` over the
        # whole source, so this decoy constant - sitting above the annotation - was taken as the
        # declared version. The gate would then compare the module against a schema 9.json that does
        # not exist, and the failure it reported would be about the wrong thing entirely.
        Name    = 'a version constant above the annotation does not displace the annotated version'
        Args    = @{ MigrationSql = 'ALTER TABLE `launcher_cells` ADD COLUMN `screenIndex` INTEGER NOT NULL DEFAULT 0'
            SchemaColumnName = 'screenIndex'; SchemaNotNull = $true; SchemaDefault = '0'
            WearDecoyVersionConstant = $true
        }
        Expect  = 0
        Contain = 'wear-voice-note version 1'
    }
)

Write-Host 'assert-migration-schema-conformance regression suite' -ForegroundColor Cyan
foreach ($case in $cases) {
    $sandbox = $null
    try {
        $splat = $case.Args
        $sandbox = New-Sandbox @splat
        $result = Invoke-Gate $sandbox
        $ok = ($result.ExitCode -eq $case.Expect) -and ($result.Text -match [regex]::Escape($case.Contain))
        Assert-That $case.Name $ok ("exit $($result.ExitCode) (expected $($case.Expect)); output: " + ($result.Text -replace '\s+', ' ').Trim())
    }
    finally {
        if ($sandbox -and (Test-Path $sandbox)) { Remove-Item $sandbox -Recurse -Force -ErrorAction SilentlyContinue }
    }
}

Write-Host ("assert-migration-schema-conformance.tests: {0} passed, {1} failed." -f $script:pass, $script:fail) `
    -ForegroundColor ($(if ($script:fail -gt 0) { 'Red' } else { 'Green' }))
exit ($(if ($script:fail -gt 0) { 1 } else { 0 }))
