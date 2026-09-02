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
        if ($Present -contains "$($row.Module):MigrationDir") {
            $null = New-Item -ItemType Directory -Path $row.MigrationDir -Force
        }
        if ($Present -contains "$($row.Module):SchemaDir") {
            $null = New-Item -ItemType Directory -Path $row.SchemaDir -Force
        }
        if ($Present -contains "$($row.Module):RegistrationFile") {
            $null = New-Item -ItemType Directory -Path (Split-Path -Parent $row.RegistrationFile) -Force
            Set-Content -Path $row.RegistrationFile -Value '// sandbox' -Encoding utf8NoBOM
        }
        if ($Present -contains "$($row.Module):AndroidTestDir") {
            $null = New-Item -ItemType Directory -Path $row.AndroidTestDir -Force
        }
    }
    return $root
}

function Get-AllMandatory {
    . $librarySource
    $all = @()
    foreach ($row in (Get-RoomDatabaseRegistry -RepoRoot 'X')) {
        $all += "$($row.Module):MigrationDir"
        $all += "$($row.Module):SchemaDir"
        $all += "$($row.Module):RegistrationFile"
    }
    return $all
}

Write-Host 'room-databases registry - regression suite' -ForegroundColor Cyan

try {
    . $librarySource

    # --- case 1: the registry lists both databases -----------------------------------------------
    $rows = @(Get-RoomDatabaseRegistry -RepoRoot 'X')
    Assert-That 'registry returns both databases' ($rows.Count -eq 2) "got $($rows.Count) row(s)"
    Assert-That 'registry names app_v2 and wear' `
    ((@($rows.Module) -contains 'app_v2') -and (@($rows.Module) -contains 'wear')) `
        "modules: $(@($rows.Module) -join ', ')"

    # --- case 2: every row is fully populated ----------------------------------------------------
    $fields = @('Module', 'Key', 'MigrationDir', 'SchemaDir', 'RegistrationFile', 'AndroidTestDir', 'TestPackage', 'ChainTestFile', 'ChainTestConstant')
    $emptyField = $null
    foreach ($row in $rows) {
        foreach ($field in $fields) {
            if ([string]::IsNullOrWhiteSpace([string]$row.$field)) { $emptyField = "$($row.Module).$field" }
        }
    }
    Assert-That 'every row populates all nine fields' ($null -eq $emptyField) "empty: $emptyField"

    # --- case 3: the module filter narrows to one row --------------------------------------------
    $wearOnly = @(Get-RoomDatabaseRegistry -RepoRoot 'X' -Module 'wear')
    Assert-That '-Module narrows to one row' `
    ($wearOnly.Count -eq 1 -and $wearOnly[0].Module -eq 'wear') `
        "got $($wearOnly.Count) row(s)"

    # --- case 4: a complete sandbox produces no finding -------------------------------------------
    $sandbox = New-Sandbox -Present (Get-AllMandatory)
    try {
        $findings = @(Test-RoomDatabaseRegistry -RepoRoot $sandbox)
        Assert-That 'complete sandbox is silent' ($findings.Count -eq 0) "findings: $(($findings | ForEach-Object { $_.Message }) -join '; ')"
    }
    finally { Remove-Item -Recurse -Force $sandbox -ErrorAction SilentlyContinue }

    # --- case 5: a missing schema directory names its module --------------------------------------
    $sandbox = New-Sandbox -Present (@(Get-AllMandatory) | Where-Object { $_ -ne 'wear:SchemaDir' })
    try {
        $findings = @(Test-RoomDatabaseRegistry -RepoRoot $sandbox)
        $named = @($findings | Where-Object { $_.Module -eq 'wear' -and $_.Field -eq 'SchemaDir' })
        Assert-That 'missing schema dir is reported against its module' `
        ($findings.Count -eq 1 -and $named.Count -eq 1) `
            "findings: $(($findings | ForEach-Object { $_.Message }) -join '; ')"
    }
    finally { Remove-Item -Recurse -Force $sandbox -ErrorAction SilentlyContinue }

    # --- case 6: absent migration dir and androidTest dir are not findings ------------------------
    # A module carrying an exported schema, no migration and no instrumented test is the wear row's
    # real state at database version 1. Reporting it would make the gate unusable until the first
    # migration exists, which is the deferred activation S2355 exists to stop.
    $sandbox = New-Sandbox -Present (@(Get-AllMandatory) | Where-Object { $_ -ne 'wear:MigrationDir' })
    try {
        $findings = @(Test-RoomDatabaseRegistry -RepoRoot $sandbox)
        $wearFindings = @($findings | Where-Object { $_.Module -eq 'wear' })
        Assert-That 'absent androidTest dir alone is not a finding' `
        ($wearFindings.Count -eq 1 -and $wearFindings[0].Field -eq 'MigrationDir') `
            "wear findings: $(($wearFindings | ForEach-Object { $_.Field }) -join ', ')"
    }
    finally { Remove-Item -Recurse -Force $sandbox -ErrorAction SilentlyContinue }

    # --- case 7: an empty root reports every module ------------------------------------------------
    $sandbox = New-Sandbox -Present @()
    try {
        $findings = @(Test-RoomDatabaseRegistry -RepoRoot $sandbox)
        $modules = @($findings | ForEach-Object { $_.Module } | Sort-Object -Unique)
        Assert-That 'empty root reports both modules' `
        ($modules.Count -eq 2 -and $modules -contains 'app_v2' -and $modules -contains 'wear') `
            "modules: $($modules -join ', ')"
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
