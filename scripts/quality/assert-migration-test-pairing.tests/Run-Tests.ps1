# Run-Tests.ps1 (S2355) - regression suite for the migration/test pairing gate.
#
# The gate is a ratchet over a baseline file, and S2355 changed that file's key format to carry the
# module. That is the one change here that can fail silently in the dangerous direction: a suppression
# that swallows a live module's finding looks exactly like a clean run. Cases 3 and 4 exist for that.
#
# The gate had no suite before this ticket - it was added when the gate stopped judging one hardcoded
# module pair and started reading the shared database registry.
#
# Every case runs against a synthetic repository under a temp dir, removed in a finally block. The
# gate resolves its repo root from its own $PSScriptRoot, so each sandbox holds a copy of the gate in
# 'scripts/quality/' and of the registry in 'scripts/quality/lib/'. Nothing here writes into app_v2
# or wear.
#
# Usage:  pwsh -NoProfile -File scripts/quality/assert-migration-test-pairing.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$gateSource = Join-Path $repoRoot 'scripts/quality/assert-migration-test-pairing.ps1'
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

# Builds a sandbox with every registered database. Each database's migration hops and test files are
# named explicitly, so a case says exactly which pairing it is asserting.
function New-Sandbox {
    param(
        [string[]]$PhoneMigrations = @('53To54'),
        [string[]]$PhoneTests = @('AppDatabaseMigration53To54Test'),
        [string[]]$WearMigrations = @(),
        [string[]]$WearTests = @(),
        # S2829: the watch's heart-rate database shares the voice-note database's directory, so its
        # hops are named separately here to prove the two sets stay apart.
        [string[]]$HeartRateMigrations = @(),
        [string[]]$HeartRateTests = @(),
        [string]$BaselineText,
        # S2832: aggregate declarations - hops the registry row names in MigrationAggregateFiles,
        # each written as "from,to". A hop declared only here is invisible to a name-only listing,
        # which is the defect the new cases exist to pin.
        [string[]]$PhoneAggregateHops = @(),
        [string[]]$WearVoiceAggregateHops = @(),
        # S2832: test file bodies - a hop may be paired by the test's TEXT (MIGRATION_N_M) rather
        # than its file name, so a case must be able to say what the test file contains.
        [hashtable]$TestTexts = @{},
        # S2832: the whole-chain test, which mentions every hop by constant name and must therefore
        # stay outside pairing.
        [string]$PhoneChainTest = '',
        [string]$PhoneChainTestText = ''
    )
    $sandbox = Join-Path ([System.IO.Path]::GetTempPath()) ('s2355-pairing-' + [System.IO.Path]::GetRandomFileName())
    $qualityDir = Join-Path $sandbox 'scripts/quality'
    $libDir = Join-Path $qualityDir 'lib'
    New-Item -ItemType Directory -Force -Path $qualityDir, $libDir | Out-Null
    Copy-Item $gateSource (Join-Path $qualityDir 'assert-migration-test-pairing.ps1')
    Copy-Item $registrySource (Join-Path $libDir 'room-databases.ps1')

    # The baseline file is written even when a case passes no text: an absent file and an empty one
    # take different branches in the gate, and every case here means "empty" rather than "absent".
    $baselineValue = if ($PSBoundParameters.ContainsKey('BaselineText')) { $BaselineText } else { '' }
    Set-Content -Path (Join-Path $qualityDir 'migration-test-pairing-baseline.txt') -Value $baselineValue -Encoding utf8NoBOM

    . $registrySource
    foreach ($db in (Get-RoomDatabaseRegistry -RepoRoot $sandbox)) {
        New-Item -ItemType Directory -Force -Path $db.MigrationDir, $db.SchemaDir | Out-Null
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $db.RegistrationFile) | Out-Null
        Set-Content -Path $db.RegistrationFile -Value '// sandbox registration file' -Encoding utf8NoBOM

        # Wrapped in @(): an empty array returned from an `if` collapses to $null, and $null.Count is
        # a terminating error under StrictMode - the same trap the gate itself documents.
        # S2829: selected by Key, not by Module - three of the four rows are the wear module, and a
        # case that wants a hop in one of them must not plant it in all three.
        $migrations = @(switch ($db.Key) {
                'wear-voice-note' { $WearMigrations }
                'wear-heart-rate' { $HeartRateMigrations }
                'app_v2' { $PhoneMigrations }
            })
        $tests = @(switch ($db.Key) {
                'wear-voice-note' { $WearTests }
                'wear-heart-rate' { $HeartRateTests }
                'app_v2' { $PhoneTests }
            })

        foreach ($hop in $migrations) {
            $fileName = "$($db.MigrationFilePrefix)$hop.kt"
            Set-Content -Path (Join-Path $db.MigrationDir $fileName) -Value "// $fileName" -Encoding utf8NoBOM
        }
        # S2832: aggregate files, one hop per line, exactly the Migration(N, M) shape the shared
        # discovery parses.
        if ($db.Key -eq 'app_v2' -and $PhoneAggregateHops.Count -gt 0) {
            $lines = foreach ($pair in $PhoneAggregateHops) {
                $f, $t = $pair -split ','
                "val MIGRATION_${f}_${t} = Migration($f, $t)"
            }
            Set-Content -Path (Join-Path $db.MigrationDir 'AppDatabase.kt') -Value ($lines -join "`n") -Encoding utf8NoBOM
        }
        if ($db.Key -eq 'wear-voice-note' -and $WearVoiceAggregateHops.Count -gt 0) {
            $lines = foreach ($pair in $WearVoiceAggregateHops) {
                $f, $t = $pair -split ','
                "val MIGRATION_${f}_${t} = Migration($f, $t)"
            }
            Set-Content -Path (Join-Path $db.MigrationDir 'WearVoiceNoteMigrations.kt') -Value ($lines -join "`n") -Encoding utf8NoBOM
        }
        if ($tests.Count -gt 0) {
            New-Item -ItemType Directory -Force -Path $db.AndroidTestDir | Out-Null
            foreach ($test in $tests) {
                $body = if ($TestTexts.ContainsKey($test)) { $TestTexts[$test] } else { "// $test" }
                Set-Content -Path (Join-Path $db.AndroidTestDir "$test.kt") -Value $body -Encoding utf8NoBOM
            }
        }
        if ($db.Key -eq 'app_v2' -and $PhoneChainTest) {
            New-Item -ItemType Directory -Force -Path $db.AndroidTestDir | Out-Null
            Set-Content -Path (Join-Path $db.AndroidTestDir "$PhoneChainTest.kt") -Value $PhoneChainTestText -Encoding utf8NoBOM
        }
    }
    return $sandbox
}

function Invoke-Gate([string]$Sandbox) {
    $out = & $pwshExe -NoProfile -File (Join-Path $Sandbox 'scripts/quality/assert-migration-test-pairing.ps1') -Gate 2>&1
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Text = ($out | Out-String) }
}

$cases = @(
    @{
        Name    = 'a migration paired with its test passes'
        Args    = @{}
        Expect  = 0
        Contain = 'PASS'
    },
    @{
        Name    = 'wear with no migration at all is clean, not unverifiable'
        Args    = @{}
        Expect  = 0
        Contain = 'wear-voice-note 0 (schema only)'
    },
    @{
        Name    = 'a wear migration with no test is found and names wear'
        Args    = @{ WearMigrations = @('1To2') }
        Expect  = 1
        Contain = '[wear-voice-note] Migration1To2.kt has no matching'
    },
    @{
        Name    = 'a wear migration with its test passes'
        Args    = @{ WearMigrations = @('1To2'); WearTests = @('WearVoiceNoteDatabaseMigration1To2Test') }
        Expect  = 0
        Contain = 'PASS'
    },
    @{
        # S2829: the two databases share one directory, so the only thing keeping their hops apart is
        # the file-name prefix. A heart-rate hop must be reported against the heart-rate database and
        # must leave the voice-note database at zero - the neighbour has no such migration to test.
        Name    = 'a hop in one watch database is not charged to the one sharing its directory'
        Args    = @{ HeartRateMigrations = @('1To2') }
        Expect  = 1
        Contain = '[wear-heart-rate] HeartRateMigration1To2.kt has no matching'
    },
    @{
        # The collision the module prefix exists to prevent: both databases start at version 1, so an
        # unprefixed "1To2" would have suppressed whichever module asked first.
        Name    = 'an app_v2 baseline key does not suppress the same hop in wear'
        Args    = @{ PhoneMigrations = @('1To2'); PhoneTests = @(); WearMigrations = @('1To2')
            BaselineText = 'app_v2:1To2'
        }
        Expect  = 1
        Contain = '[wear-voice-note] Migration1To2.kt has no matching'
    },
    @{
        Name    = 'a module-prefixed baseline key suppresses its own module'
        Args    = @{ PhoneMigrations = @('1To2'); PhoneTests = @(); BaselineText = 'app_v2:1To2' }
        Expect  = 0
        Contain = '1 baselined as untested debt'
    },
    @{
        # A line left in the pre-S2355 format is not silently read as app_v2: it suppresses nothing,
        # so the finding stays visible instead of vanishing into an ambiguous key.
        Name    = 'an unprefixed baseline line suppresses nothing'
        Args    = @{ PhoneMigrations = @('1To2'); PhoneTests = @(); BaselineText = '1To2' }
        Expect  = 1
        Contain = '[app_v2] Migration1To2.kt has no matching'
    },
    @{
        Name    = 'a migration whose module has no androidTest directory at all is found'
        Args    = @{ PhoneMigrations = @('53To54'); PhoneTests = @() }
        Expect  = 1
        Contain = '[app_v2] Migration53To54.kt has no matching'
    },
    @{
        # S2832: the aggregate declaration the old directory listing never saw.
        Name    = 'a hop declared only in an aggregate file with no test is found'
        Args    = @{ PhoneMigrations = @(); PhoneAggregateHops = @('31,32'); PhoneTests = @() }
        Expect  = 1
        Contain = '[app_v2] AppDatabase.kt has no matching Migration31To32'
    },
    @{
        # S2832: WearVoiceNoteMigrationTest proves its hop by invoking the constant - pairing by
        # test TEXT must accept it without demanding a rename.
        Name    = 'an aggregate hop paired by test TEXT passes'
        Args    = @{ PhoneMigrations = @(); PhoneAggregateHops = @('31,32'); PhoneTests = @('AppDatabaseAggregate31To32Test')
            TestTexts = @{ AppDatabaseAggregate31To32Test = 'val carried = MIGRATION_31_32' } }
        Expect  = 0
        Contain = 'PASS'
    },
    @{
        # S2832: the whole-chain test mentions every hop by constant name; counting it would
        # dissolve the frozen baseline in silence.
        Name    = 'a hop mentioned only by the chain test stays untested'
        Args    = @{ PhoneMigrations = @(); PhoneAggregateHops = @('31,32'); PhoneTests = @()
            PhoneChainTest = 'AppDatabaseMigrationChainTest'; PhoneChainTestText = 'val walked = MIGRATION_31_32' }
        Expect  = 1
        Contain = '[app_v2] AppDatabase.kt has no matching Migration31To32'
    },
    @{
        # S2832: the aggregate belongs to the row that NAMES it - the heart-rate database sharing
        # the directory stays at zero.
        Name    = 'an aggregate hop is not charged to the watch database sharing its directory'
        Args    = @{ WearVoiceAggregateHops = @('1,2') }
        Expect  = 1
        Contain = '[wear-voice-note] WearVoiceNoteMigrations.kt has no matching Migration1To2'
    }
)

Write-Host 'assert-migration-test-pairing regression suite' -ForegroundColor Cyan
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

Write-Host ("assert-migration-test-pairing.tests: {0} passed, {1} failed." -f $script:pass, $script:fail) `
    -ForegroundColor ($(if ($script:fail -gt 0) { 'Red' } else { 'Green' }))
exit ($(if ($script:fail -gt 0) { 1 } else { 0 }))
