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
      Key                  short stable token used in baseline keys and gate output. It identifies a
                           DATABASE, not a module: the watch runs three of them, so a row keyed by
                           its module would make every verdict about the watch ambiguous (S2829).
      MigrationDir         where Migration<N>To<M>.kt and the @Database class live.
      DatabaseClassFile    the @Database class's file name, inside MigrationDir. Named rather than
                           discovered: a directory may hold several databases (S2829 - the watch's
                           `data/db` now holds three), and scanning it for the annotation made the
                           consumer refuse a state this registry already describes exactly.
      MigrationFilePrefix  the file-name prefix that identifies THIS database's migrations inside a
                           directory it may share with others (S2829). A consumer matches
                           `^<prefix><N>To<M>.kt` and nothing else, so the watch's three databases
                           cannot claim each other's hops. The incumbent of a directory keeps the
                           plain `Migration` prefix; a database that moves in afterwards carries its
                           own, because the anchored match makes the two sets disjoint.
      MigrationAggregateFiles
                           file names inside MigrationDir that declare this database's migrations as
                           `Migration(N, M)` objects rather than as one file per hop (S2830). A hop
                           declared there has no address in a file name, so the name-only search
                           found nothing and its consumer reported "no migration yet" - the SAME
                           sentence it prints for a database that genuinely has none. May be empty;
                           a listed file that does not exist is not a registry error, because a row
                           outlives the release that removes its last aggregate declaration.
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
        Module              = 'app_v2'
        Key                 = 'app_v2'
        MigrationDir        = 'app_v2/src/main/java/com/sza/fastmediasorter/data/local/db'
        DatabaseClassFile   = 'AppDatabase.kt'
        MigrationFilePrefix = 'Migration'
        # S2830: the phone carries an aggregate of its own. AppDatabase.kt's companion object declares
        # MIGRATION_1_18 .. MIGRATION_30_31 - 26 hops the name-only search never read. Nothing is lost
        # by that today, because the oldest exported schema is 36.json and every one of those hops
        # would be skipped as "no exported schema"; the loss starts the day a hop is added there
        # instead of in a new Migration<N>To<M>.kt, which is exactly what nothing could notice.
        MigrationAggregateFiles = @('AppDatabase.kt')
        SchemaDir           = 'app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase'
        RegistrationFile    = 'app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt'
        AndroidTestDir      = 'app_v2/src/androidTest/java/com/sza/fastmediasorter/data/local/db'
        TestPackage         = 'com.sza.fastmediasorter.data.local.db'
        ChainTestFile       = 'AppDatabaseMigrationChainTest.kt'
        ChainTestConstant   = 'CURRENT_SCHEMA'
    }
    # S1862 gave the watch its own Room database and its own exported schema; S2355 brought it under
    # the same tooling. Note the path segment is `data/db`, not `data/local/db` - that one difference
    # is why the phone-shaped path fragments in post-change.ps1 never matched it.
    #
    # S2829: the watch runs THREE databases out of that one directory, and all three export a schema.
    # They share MigrationDir because that is where they really live; what keeps their verdicts apart
    # is Key, DatabaseClassFile, SchemaDir and MigrationFilePrefix. The voice-note database was there
    # first and keeps the plain `Migration` prefix.
    #
    # S3078: they no longer share a RegistrationFile. All three rows named WearAppModule.kt, which
    # builds no database at all any more - voice-note is provided by WearVoiceNoteModule.kt, the two
    # health databases by WearHealthHistoryModule.kt. A registry pointed at an empty file makes the
    # conformance gate answer "cannot verify" about a migration that is correctly wired, which is the
    # one answer that looks like a code defect while being a registry defect.
    [pscustomobject]@{
        Module              = 'wear'
        Key                 = 'wear-voice-note'
        MigrationDir        = 'wear/src/main/java/com/sza/fastmediasorter/wear/data/db'
        DatabaseClassFile   = 'WearVoiceNoteDatabase.kt'
        MigrationFilePrefix = 'Migration'
        MigrationAggregateFiles = @('WearVoiceNoteMigrations.kt')
        SchemaDir           = 'wear/schemas/com.sza.fastmediasorter.wear.data.db.WearVoiceNoteDatabase'
        RegistrationFile    = 'wear/src/main/java/com/sza/fastmediasorter/wear/di/WearVoiceNoteModule.kt'
        AndroidTestDir      = 'wear/src/androidTest/java/com/sza/fastmediasorter/wear/data/db'
        TestPackage         = 'com.sza.fastmediasorter.wear.data.db'
        ChainTestFile       = 'WearVoiceNoteDatabaseMigrationChainTest.kt'
        ChainTestConstant   = 'CURRENT_SCHEMA'
    }
    [pscustomobject]@{
        Module              = 'wear'
        Key                 = 'wear-heart-rate'
        MigrationDir        = 'wear/src/main/java/com/sza/fastmediasorter/wear/data/db'
        DatabaseClassFile   = 'WearHeartRateDatabase.kt'
        MigrationFilePrefix = 'HeartRateMigration'
        MigrationAggregateFiles = @()
        SchemaDir           = 'wear/schemas/com.sza.fastmediasorter.wear.data.db.WearHeartRateDatabase'
        RegistrationFile    = 'wear/src/main/java/com/sza/fastmediasorter/wear/di/WearHealthHistoryModule.kt'
        AndroidTestDir      = 'wear/src/androidTest/java/com/sza/fastmediasorter/wear/data/db'
        TestPackage         = 'com.sza.fastmediasorter.wear.data.db'
        ChainTestFile       = 'WearHeartRateDatabaseMigrationChainTest.kt'
        ChainTestConstant   = 'CURRENT_SCHEMA'
    }
    [pscustomobject]@{
        Module              = 'wear'
        Key                 = 'wear-blood-pressure'
        MigrationDir        = 'wear/src/main/java/com/sza/fastmediasorter/wear/data/db'
        DatabaseClassFile   = 'WearBloodPressureDatabase.kt'
        MigrationFilePrefix = 'BloodPressureMigration'
        MigrationAggregateFiles = @('WearBloodPressureMigrations.kt')
        SchemaDir           = 'wear/schemas/com.sza.fastmediasorter.wear.data.db.WearBloodPressureDatabase'
        RegistrationFile    = 'wear/src/main/java/com/sza/fastmediasorter/wear/di/WearHealthHistoryModule.kt'
        AndroidTestDir      = 'wear/src/androidTest/java/com/sza/fastmediasorter/wear/data/db'
        TestPackage         = 'com.sza.fastmediasorter.wear.data.db'
        ChainTestFile       = 'WearBloodPressureDatabaseMigrationChainTest.kt'
        ChainTestConstant   = 'CURRENT_SCHEMA'
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
            DatabaseClassPath = Join-Path $RepoRoot (Join-Path $row.MigrationDir $row.DatabaseClassFile)
            DatabaseClassFile = $row.DatabaseClassFile
            MigrationFilePrefix = $row.MigrationFilePrefix
            MigrationAggregateFiles = @($row.MigrationAggregateFiles)
            SchemaDir         = Join-Path $RepoRoot $row.SchemaDir
            RegistrationFile  = Join-Path $RepoRoot $row.RegistrationFile
            AndroidTestDir    = Join-Path $RepoRoot $row.AndroidTestDir
            TestPackage       = $row.TestPackage
            ChainTestFile     = $row.ChainTestFile
            ChainTestConstant = $row.ChainTestConstant
            RelativePaths     = [pscustomobject]@{
                MigrationDir      = $row.MigrationDir
                DatabaseClassPath = "$($row.MigrationDir)/$($row.DatabaseClassFile)"
                SchemaDir        = $row.SchemaDir
                RegistrationFile = $row.RegistrationFile
                AndroidTestDir   = $row.AndroidTestDir
            }
        }
    }
}

function Get-RoomMigrationFileNamePattern {
    <#
    .SYNOPSIS
        The anchored pattern that identifies one database's per-hop migration files.
    .DESCRIPTION
        Written once so the discovery function and the claim test below cannot disagree about which
        file belongs to which database (the S1621 rule).
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Prefix)

    return '^' + [regex]::Escape($Prefix) + '(\d+)To(\d+)$'
}

function Get-RoomMigrationHopBody {
    <#
    .SYNOPSIS
        The brace-balanced body that follows a `Migration(N, M)` match, or '' when it does not close.
    .DESCRIPTION
        S2830. An aggregate file holds several hops, and each hop's SQL must be attributed to the hop
        that executes it: handing the whole file to a caller comparing against version M's schema
        would judge every OTHER hop's statements against it too, inventing findings the runtime does
        not have. Scanning to the matching brace rather than to the first one is what makes that safe
        for real Kotlin - a migrate() body carries nested lambdas, `use { }` blocks and string
        templates, all of which open braces of their own.
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][int]$StartIndex
    )

    $open = $Text.IndexOf('{', $StartIndex)
    if ($open -lt 0) { return '' }

    $depth = 0
    for ($i = $open; $i -lt $Text.Length; $i++) {
        $ch = $Text[$i]
        if ($ch -eq '{') { $depth++ }
        elseif ($ch -eq '}') {
            $depth--
            if ($depth -eq 0) { return $Text.Substring($open, $i - $open + 1) }
        }
    }
    return ''
}

function Get-RoomMigrationSource {
    <#
    .SYNOPSIS
        Every migration hop a database really declares, with the Kotlin text whose SQL belongs to it.
    .DESCRIPTION
        S2830. Discovery used to be a directory listing, so a hop declared inside an aggregate file
        was invisible and its consumer printed the sentence it prints for a database with no
        migration at all. Two origins are read now:

          file       Migration<N>To<M>.kt, matched on the row's own prefix so databases sharing a
                     directory cannot claim each other's hops (S2829). Text is the whole file.
          aggregate  a `Migration(N, M)` object inside a file the row NAMES in MigrationAggregateFiles.
                     Text is that hop's brace-balanced body alone.

        Kotlin string concatenation is joined here rather than in each consumer, so a caller always
        receives text in which a SQL statement split across source lines is already one statement.
    .PARAMETER Database
        One row from Get-RoomDatabaseRegistry, with its paths already resolved.
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][object]$Database)

    $joinConcatenation = { param([string]$t) $t -replace '"\s*\+\s*\r?\n?\s*"', '' }
    $hops = [System.Collections.Generic.List[object]]::new()

    $filePattern = Get-RoomMigrationFileNamePattern -Prefix $Database.MigrationFilePrefix
    if (Test-Path $Database.MigrationDir) {
        foreach ($file in @(Get-ChildItem -Path $Database.MigrationDir -Filter "$($Database.MigrationFilePrefix)*.kt" -File)) {
            if ($file.BaseName -notmatch $filePattern) { continue }
            $hops.Add([pscustomobject]@{
                    From       = [int]$Matches[1]
                    To         = [int]$Matches[2]
                    Token      = "$([int]$Matches[1])To$([int]$Matches[2])"
                    SourceFile = $file.Name
                    Origin     = 'file'
                    Text       = & $joinConcatenation (Get-Content $file.FullName -Raw)
                })
        }
    }

    foreach ($aggregateName in @($Database.MigrationAggregateFiles)) {
        $aggregatePath = Join-Path $Database.MigrationDir $aggregateName
        if (-not (Test-Path $aggregatePath)) { continue }
        $aggregateText = Get-Content $aggregatePath -Raw
        foreach ($m in [regex]::Matches($aggregateText, 'Migration\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)')) {
            $from = [int]$m.Groups[1].Value
            $to = [int]$m.Groups[2].Value
            $body = Get-RoomMigrationHopBody -Text $aggregateText -StartIndex ($m.Index + $m.Length)
            $hops.Add([pscustomobject]@{
                    From       = $from
                    To         = $to
                    Token      = "$($from)To$($to)"
                    SourceFile = $aggregateName
                    Origin     = 'aggregate'
                    Text       = & $joinConcatenation $body
                })
        }
    }

    return @($hops | Sort-Object From, To)
}

function Get-RoomUnclaimedMigrationDeclaration {
    <#
    .SYNOPSIS
        One finding per `Migration(N, M)` declaration no registry row reads.
    .DESCRIPTION
        S2830 goal 2: "this database has no migration" and "this database has migrations nobody
        found" must stop being the same output. Reading the aggregate files the registry names fixes
        the one file that was measured; only a check over declarations the registry did NOT expect
        keeps it fixed, because the next aggregate reproduces the whole ticket in silence otherwise.

        A file is claimed when its name matches some row's `^<prefix><N>To<M>$` pattern or is listed
        in some row's MigrationAggregateFiles. Claim is judged across ALL rows sharing that
        directory, never row by row: the watch's three databases live in one directory, so a file
        belonging to a neighbour is claimed, not unclaimed.
    .PARAMETER RepoRoot
        Repository root the relative paths resolve against.
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$RepoRoot)

    $rows = @(Get-RoomDatabaseRegistry -RepoRoot $RepoRoot)
    foreach ($dir in @($rows | Group-Object MigrationDir)) {
        if (-not (Test-Path $dir.Name)) { continue }
        $claimedNames = @($dir.Group | ForEach-Object { $_.MigrationAggregateFiles })
        $patterns = @($dir.Group | ForEach-Object { Get-RoomMigrationFileNamePattern -Prefix $_.MigrationFilePrefix })

        foreach ($file in @(Get-ChildItem -Path $dir.Name -Filter '*.kt' -File)) {
            if ($claimedNames -contains $file.Name) { continue }
            $claimedByName = $false
            foreach ($pattern in $patterns) {
                if ($file.BaseName -match $pattern) { $claimedByName = $true; break }
            }
            if ($claimedByName) { continue }

            $text = Get-Content $file.FullName -Raw
            foreach ($m in [regex]::Matches($text, 'Migration\s*\(\s*(\d+)\s*,\s*(\d+)\s*\)')) {
                $from = [int]$m.Groups[1].Value
                $to = [int]$m.Groups[2].Value
                [pscustomobject]@{
                    Module     = $dir.Group[0].Module
                    Key        = $dir.Group[0].Key
                    SourceFile = $file.Name
                    From       = $from
                    To         = $to
                    Message    = "$($file.Name) declares Migration($from, $to) but no registry row reads it - its file name matches no database's migration prefix and no row lists it in MigrationAggregateFiles, so its SQL is compared against no exported schema"
                }
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
        row describe a real database. DatabaseClassFile is deliberately NOT among them (S2829): only
        the conformance gate reads the class, the pairing gate never opens it, and requiring it here
        would make a row unusable for the consumer that does not need it. AndroidTestDir and ChainTestFile are deliberately NOT checked:
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
                    Key     = $row.Key
                    Field   = $check.Name
                    Path    = $check.Relative
                    Message = "$($row.Key): registry $($check.Name) does not exist - $($check.Relative)"
                }
            }
        }
    }
}
