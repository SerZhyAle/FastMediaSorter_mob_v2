#requires -Version 7.0
<#
.SYNOPSIS
    Dot-source library: the registry of every Room database in the repository, one row per database.

.DESCRIPTION
    S2355. S2306 built three tools around the Room upgrade contract - a schema-conformance gate, a
    migration/test pairing gate and a connected run target - and each of them spelled out the ONE
    module it knew as a literal path. A second module with its own Room database was already sitting
    beside it, and both gates walked straight past it.

    That is only half of what was measured. post-change.ps1's trigger for those gates carried an arm
    matching any exported schema, `(^|/)schemas/.*\.json$`, which DOES match
    wear/schemas/**/1.json - so editing the watch schema fired two gates that then read app_v2 and
    printed PASS. Not a coverage gap: a confident verdict about the module nobody touched. The other
    arm, `(^|/)data/local/db/.*\.kt$`, matched nothing under the watch's `wear/data/db` segment, so
    editing the watch entity, DAO or database fired nothing at all.

    This file is the fix for both halves and for the next one: the gates, the closure predicate and
    the device-run targets all read the same list, so a third Room database is a row here and not
    another round of the same ticket. Keeping the list in ONE place is deliberate - two checks each
    holding a private idea of which databases exist is the S1621 failure, where the closing gate
    refused what the tree gate passed.

    A row is nine fields, all repo-relative; the caller supplies the repo root, so a synthetic
    sandbox resolves against itself:

      Module               the Gradle module, matching scripts/utils/gradle-modules.ps1.
      Key                  short stable token used in baseline keys and gate output.
      MigrationDir         where Migration<N>To<M>.kt and the @Database class live.
      SchemaDir            the exported schema JSON directory Room validates against.
      RegistrationFile     the file where migrations are registered on the Room builder.
      AndroidTestDir       where the instrumented migration tests live.
      TestPackage          the instrumented-test package a connected run targets.
      ChainTestFile        the whole-chain test's file name, inside AndroidTestDir.
      ChainTestConstant    the constant in that test naming the version it walks to.

    MigrationDir, AndroidTestDir and ChainTestFile may legitimately be absent: a module can carry an
    exported schema and no migration yet, which is exactly the wear row's state at database version 1.
    Test-RoomDatabaseRegistry therefore reports only the three paths that must exist for the row to
    mean anything at all.

.EXAMPLE
    . "$PSScriptRoot\room-databases.ps1"
    Get-RoomDatabaseRegistry -RepoRoot $repoRoot

.EXAMPLE
    . "$PSScriptRoot\room-databases.ps1"
    Test-RoomDatabaseRegistry -RepoRoot $repoRoot

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  dot-sourced successfully (this file has no command-line interface and returns nothing else).
      2  invoked as a script instead of being dot-sourced - see the guard below.
#>

# Same guard as gradle-modules.ps1 (S1505): a library invoked with `pwsh -File` binds nothing,
# defines nothing the caller can see and exits 0, which reads as a working call.
if ($MyInvocation.InvocationName -ne '.') {
    $roomDbGuardMessage = @(
        "room-databases.ps1 is a dot-source library and has no command-line interface.",
        "Running it as a script does nothing at all - it resolves no database.",
        "",
        "From a script, load the functions instead:",
        "    . `"`$PSScriptRoot\lib\room-databases.ps1`"",
        "    Get-RoomDatabaseRegistry -RepoRoot `$repoRoot"
    ) -join [Environment]::NewLine
    Write-Error $roomDbGuardMessage -ErrorAction Continue
    exit 2
}

# Declaration order is the order every consumer reports in, so a run spanning both databases always
# prints them in the same sequence regardless of which file the caller changed.
$Script:RoomDatabaseTable = @(
    [pscustomobject]@{
        Module            = 'app_v2'
        Key               = 'app_v2'
        MigrationDir      = 'app_v2/src/main/java/com/sza/fastmediasorter/data/local/db'
        SchemaDir         = 'app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase'
        RegistrationFile  = 'app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt'
        AndroidTestDir    = 'app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db'
        TestPackage       = 'com.sza.fastmediasorter.data.local.db'
        ChainTestFile     = 'AppDatabaseMigrationChainTest.kt'
        ChainTestConstant = 'CURRENT_SCHEMA'
    }
    # S1862 gave the watch its own Room database and its own exported schema; S2355 brought it under
    # the same tooling. Note the path segment is `data/db`, not `data/local/db` - that one difference
    # is why the phone-shaped path fragments in post-change.ps1 never matched it.
    [pscustomobject]@{
        Module            = 'wear'
        Key               = 'wear'
        MigrationDir      = 'wear/src/main/java/com/sza/fastmediasorter/wear/data/db'
        SchemaDir         = 'wear/schemas/com.sza.fastmediasorter.wear.data.db.WearVoiceNoteDatabase'
        RegistrationFile  = 'wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt'
        AndroidTestDir    = 'wear/src/androidTest/java/com/sza/fastmediasorter/wear/data/db'
        TestPackage       = 'com.sza.fastmediasorter.wear.data.db'
        ChainTestFile     = 'WearVoiceNoteDatabaseMigrationChainTest.kt'
        ChainTestConstant = 'CURRENT_SCHEMA'
    }
)

function Get-RoomDatabaseRegistry {
    <#
    .SYNOPSIS
        Every Room database in the repository, one row each, with absolute paths resolved against
        the supplied repo root.
    .PARAMETER RepoRoot
        Repository root the relative paths resolve against. A sandbox passes its own temp root.
    .PARAMETER Module
        Optional filter narrowing the result to one module. Absent means every row - a consumer that
        must remember to ask twice is the wiring mistake this registry exists to remove.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [string]$Module
    )

    $rows = $Script:RoomDatabaseTable
    if ($Module) {
        $rows = @($rows | Where-Object { $_.Module -eq $Module })
    }

    foreach ($row in $rows) {
        [pscustomobject]@{
            Module            = $row.Module
            Key               = $row.Key
            MigrationDir      = Join-Path $RepoRoot $row.MigrationDir
            SchemaDir         = Join-Path $RepoRoot $row.SchemaDir
            RegistrationFile  = Join-Path $RepoRoot $row.RegistrationFile
            AndroidTestDir    = Join-Path $RepoRoot $row.AndroidTestDir
            TestPackage       = $row.TestPackage
            ChainTestFile     = $row.ChainTestFile
            ChainTestConstant = $row.ChainTestConstant
            RelativePaths     = [pscustomobject]@{
                MigrationDir     = $row.MigrationDir
                SchemaDir        = $row.SchemaDir
                RegistrationFile = $row.RegistrationFile
                AndroidTestDir   = $row.AndroidTestDir
            }
        }
    }
}

function Test-RoomDatabaseRegistry {
    <#
    .SYNOPSIS
        One finding per registry row whose mandatory paths are missing on disk.
    .DESCRIPTION
        Mandatory means MigrationDir's parent structure is irrelevant - only SchemaDir and
        RegistrationFile must exist, plus MigrationDir itself, because those three are what makes a
        row describe a real database. AndroidTestDir and ChainTestFile are deliberately NOT checked:
        a module with an exported schema, no migration and no instrumented test yet is a legitimate
        state (the wear row at database version 1), and refusing it would make the gate unusable
        until somebody else's ticket writes the first migration - the deferred activation this
        repository has already paid for four times.
    .PARAMETER RepoRoot
        Repository root the relative paths resolve against.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepoRoot
    )

    foreach ($row in (Get-RoomDatabaseRegistry -RepoRoot $RepoRoot)) {
        $checks = @(
            @{ Name = 'MigrationDir'; Path = $row.MigrationDir; Relative = $row.RelativePaths.MigrationDir }
            @{ Name = 'SchemaDir'; Path = $row.SchemaDir; Relative = $row.RelativePaths.SchemaDir }
            @{ Name = 'RegistrationFile'; Path = $row.RegistrationFile; Relative = $row.RelativePaths.RegistrationFile }
        )
        foreach ($check in $checks) {
            if (-not (Test-Path $check.Path)) {
                [pscustomobject]@{
                    Module  = $row.Module
                    Field   = $check.Name
                    Path    = $check.Relative
                    Message = "$($row.Module): registry $($check.Name) does not exist - $($check.Relative)"
                }
            }
        }
    }
}
