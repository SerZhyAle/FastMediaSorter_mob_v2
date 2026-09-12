# Run-Tests.ps1 (S2355) - regression suite for the Room database registry.
#
# The registry is read by both migration gates and by the closure predicate, so a defect in it is a
# defect in every consumer at once - including the failure mode that opened S2355, where a gate
# looked confidently at the wrong module and printed PASS.
#
# Every case runs against a synthetic repository under a temp dir, removed in a finally block. The
# library resolves nothing from its own location - the caller supplies the repo root - so a sandbox
# is just a directory tree, and nothing here writes into app_v2 or wear.
#
# Usage:  pwsh -NoProfile -File scripts/quality/lib/room-databases.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Four levels: room-databases.tests -> lib -> quality -> scripts -> repo root. The sibling suites
# under scripts/quality/*.tests need three; this one sits one directory deeper.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..' '..')).Path
$librarySource = Join-Path $repoRoot 'scripts/quality/lib/room-databases.ps1'
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

# Build a sandbox holding whichever of the registry's mandatory paths the case wants present.
function New-Sandbox([string[]]$Present) {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("room-db-registry-" + [Guid]::NewGuid().ToString('N'))
    $null = New-Item -ItemType Directory -Path $root -Force
    . $librarySource
    foreach ($row in (Get-RoomDatabaseRegistry -RepoRoot $root)) {
        if ($Present -contains "$($row.Key):MigrationDir") {
            $null = New-Item -ItemType Directory -Path $row.MigrationDir -Force
        }
        if ($Present -contains "$($row.Key):SchemaDir") {
            $null = New-Item -ItemType Directory -Path $row.SchemaDir -Force
        }
        if ($Present -contains "$($row.Key):RegistrationFile") {
            $null = New-Item -ItemType Directory -Path (Split-Path -Parent $row.RegistrationFile) -Force
            Set-Content -Path $row.RegistrationFile -Value '// sandbox' -Encoding utf8NoBOM
        }
        if ($Present -contains "$($row.Key):AndroidTestDir") {
            $null = New-Item -ItemType Directory -Path $row.AndroidTestDir -Force
        }
    }
    return $root
}

function Get-AllMandatory {
    . $librarySource
    $all = @()
    foreach ($row in (Get-RoomDatabaseRegistry -RepoRoot 'X')) {
        $all += "$($row.Key):MigrationDir"
        $all += "$($row.Key):SchemaDir"
        $all += "$($row.Key):RegistrationFile"
    }
    return $all
}

Write-Host 'room-databases registry - regression suite' -ForegroundColor Cyan

try {
    . $librarySource

    # --- case 1: the registry lists every database ------------------------------------------------
    # S2829: four rows across two modules - the watch runs three databases out of one directory, so a
    # count that tracked modules would be wrong the moment a module grew a second one.
    $rows = @(Get-RoomDatabaseRegistry -RepoRoot 'X')
    Assert-That 'registry returns every database' ($rows.Count -eq 4) "got $($rows.Count) row(s)"
    Assert-That 'registry names app_v2 and wear' `
    ((@($rows.Module) -contains 'app_v2') -and (@($rows.Module) -contains 'wear')) `
        "modules: $(@($rows.Module) -join ', ')"

    # --- case 2: every row is fully populated ----------------------------------------------------
    $fields = @('Module', 'Key', 'MigrationDir', 'DatabaseClassFile', 'MigrationFilePrefix', 'SchemaDir', 'RegistrationFile', 'AndroidTestDir', 'TestPackage', 'ChainTestFile', 'ChainTestConstant')
    $emptyField = $null
    foreach ($row in $rows) {
        foreach ($field in $fields) {
            if ([string]::IsNullOrWhiteSpace([string]$row.$field)) { $emptyField = "$($row.Key).$field" }
        }
    }
    Assert-That 'every row populates all eleven fields' ($null -eq $emptyField) "empty: $emptyField"

    # --- case 2b: the key identifies a database, so no two rows may share one ---------------------
    # Every baseline line and every gate message is keyed by it. Two rows answering to one key would
    # let a suppression written for one database silence a finding in another.
    $duplicateKeys = @(@($rows.Key) | Group-Object | Where-Object { $_.Count -gt 1 } | ForEach-Object { $_.Name })
    Assert-That 'every row carries a distinct key' ($duplicateKeys.Count -eq 0) `
        "duplicated: $($duplicateKeys -join ', ')"

    # --- case 3: the module filter narrows to that module's databases ------------------------------
    $wearOnly = @(Get-RoomDatabaseRegistry -RepoRoot 'X' -Module 'wear')
    Assert-That '-Module narrows to the wear databases' `
    ($wearOnly.Count -eq 3 -and @($wearOnly.Module | Sort-Object -Unique) -eq 'wear') `
        "got $($wearOnly.Count) row(s): $(@($wearOnly.Key) -join ', ')"

    # --- case 4: a complete sandbox produces no finding -------------------------------------------
    $sandbox = New-Sandbox -Present (Get-AllMandatory)
    try {
        $findings = @(Test-RoomDatabaseRegistry -RepoRoot $sandbox)
        Assert-That 'complete sandbox is silent' ($findings.Count -eq 0) "findings: $(($findings | ForEach-Object { $_.Message }) -join '; ')"
    }
    finally { Remove-Item -Recurse -Force $sandbox -ErrorAction SilentlyContinue }

    # --- case 5: a missing schema directory names its database ------------------------------------
    # Only the voice-note schema directory is withheld: it is the one path in the row that no sibling
    # shares, so exactly one finding proves the report is per database rather than per module.
    $sandbox = New-Sandbox -Present (@(Get-AllMandatory) | Where-Object { $_ -ne 'wear-voice-note:SchemaDir' })
    try {
        $findings = @(Test-RoomDatabaseRegistry -RepoRoot $sandbox)
        $named = @($findings | Where-Object { $_.Key -eq 'wear-voice-note' -and $_.Field -eq 'SchemaDir' })
        Assert-That 'missing schema dir is reported against its database' `
        ($findings.Count -eq 1 -and $named.Count -eq 1) `
            "findings: $(($findings | ForEach-Object { $_.Message }) -join '; ')"
    }
    finally { Remove-Item -Recurse -Force $sandbox -ErrorAction SilentlyContinue }

    # --- case 6: absent migration dir and androidTest dir are not findings ------------------------
    # A module carrying an exported schema, no migration and no instrumented test is the wear row's
    # real state at database version 1. Reporting it would make the gate unusable until the first
    # migration exists, which is the deferred activation S2355 exists to stop.
    #
    # S2829: the three watch rows share one MigrationDir, so it is withheld from all of them - asking
    # for it in any one row would recreate the directory for the other two and the case would assert
    # nothing. All three then report the same absent path, which is what one shared path means.
    $sandbox = New-Sandbox -Present (@(Get-AllMandatory) | Where-Object { $_ -notlike 'wear-*:MigrationDir' })
    try {
        $findings = @(Test-RoomDatabaseRegistry -RepoRoot $sandbox)
        $wearFindings = @($findings | Where-Object { $_.Module -eq 'wear' })
        $fields = @($wearFindings | ForEach-Object { $_.Field } | Sort-Object -Unique)
        Assert-That 'absent androidTest dir alone is not a finding' `
        ($wearFindings.Count -eq 3 -and $fields.Count -eq 1 -and $fields[0] -eq 'MigrationDir') `
            "wear findings: $(($wearFindings | ForEach-Object { $_.Field }) -join ', ')"
    }
    finally { Remove-Item -Recurse -Force $sandbox -ErrorAction SilentlyContinue }

    # --- case 7: an empty root reports every database -----------------------------------------------
    $sandbox = New-Sandbox -Present @()
    try {
        $findings = @(Test-RoomDatabaseRegistry -RepoRoot $sandbox)
        $keys = @($findings | ForEach-Object { $_.Key } | Sort-Object -Unique)
        Assert-That 'empty root reports every database' `
        ($keys.Count -eq 4) `
            "keys: $($keys -join ', ')"
    }
    finally { Remove-Item -Recurse -Force $sandbox -ErrorAction SilentlyContinue }

    # --- case 8: running the library as a script refuses -------------------------------------------
    & $pwshExe -NoProfile -File $librarySource 2>$null
    Assert-That 'pwsh -File on the library exits 2' ($LASTEXITCODE -eq 2) "exit $LASTEXITCODE"

    # --- case 9: the live tree satisfies the registry ---------------------------------------------
    $liveFindings = @(Test-RoomDatabaseRegistry -RepoRoot $repoRoot)
    Assert-That 'live repository satisfies the registry' ($liveFindings.Count -eq 0) `
        "findings: $(($liveFindings | ForEach-Object { $_.Message }) -join '; ')"
}
finally {
    Write-Host ''
    Write-Host ("room-databases registry: {0} passed, {1} failed." -f $script:pass, $script:fail) `
        -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
}

exit $(if ($script:fail -eq 0) { 0 } else { 1 })
