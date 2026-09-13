#requires -Version 7.0
<#
.SYNOPSIS
    Test suite for scripts/utils/archive-temp.ps1 (S3037).
#>
# Subject: scripts/utils/archive-temp.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..' '..' '..')).Path
$archiveScript = Join-Path (Split-Path $scriptDir -Parent) 'archive-temp.ps1'
$fixtureDir = Join-Path $scriptDir 'temp_test_fixture'
$mockSelectCli = Join-Path $scriptDir 'mock-select.ps1'

if (Test-Path -LiteralPath $fixtureDir) {
    Remove-Item -Recurse -Force -LiteralPath $fixtureDir
}
New-Item -ItemType Directory -Path $fixtureDir | Out-Null

# Create mock select CLI returning fixed catalog json
$mockSelectContent = @'
param(
    [string]$Format
)
$specs = @(
    @{ id = "S1000"; status = "In Progress" },
    @{ id = "S2000"; status = "Verified" }
)
$specs | ConvertTo-Json -Compress
'@
[System.IO.File]::WriteAllText($mockSelectCli, $mockSelectContent, [System.Text.UTF8Encoding]::new($false))

try {
    $stamp = "test_run_stamp"
    $oldTime = (Get-Date).AddDays(-10)
    $freshTime = (Get-Date).AddDays(-1)

    # -------------------------------------------------------------------------
    # Test 1: Fixed infrastructure (dirs, files, patterns) never move
    # -------------------------------------------------------------------------
    $scratchDir = Join-Path $fixtureDir 'scratch'
    $leasesDir = Join-Path $fixtureDir 'SPEC-TICKET.LEASES'
    $deviceRegistryDir = Join-Path $fixtureDir 'DEVICE.REGISTRY'
    New-Item -ItemType Directory -Path $scratchDir | Out-Null
    New-Item -ItemType Directory -Path $leasesDir | Out-Null
    New-Item -ItemType Directory -Path $deviceRegistryDir | Out-Null
    (Get-Item -LiteralPath $scratchDir).LastWriteTime = $oldTime
    (Get-Item -LiteralPath $leasesDir).LastWriteTime = $oldTime
    (Get-Item -LiteralPath $deviceRegistryDir).LastWriteTime = $oldTime

    $releaseFreezeFile = Join-Path $fixtureDir 'RELEASE-FREEZE.json'
    $gitignoreFile = Join-Path $fixtureDir '.gitignore'
    $fixedPatternFile = Join-Path $fixtureDir 'fastmediasorter_20260912.log'
    [System.IO.File]::WriteAllText($releaseFreezeFile, '{}')
    [System.IO.File]::WriteAllText($gitignoreFile, '*')
    [System.IO.File]::WriteAllText($fixedPatternFile, 'log line')
    (Get-Item -LiteralPath $releaseFreezeFile).LastWriteTime = $oldTime
    (Get-Item -LiteralPath $gitignoreFile).LastWriteTime = $oldTime
    (Get-Item -LiteralPath $fixedPatternFile).LastWriteTime = $oldTime

    # -------------------------------------------------------------------------
    # Test 2: Ticket scratch liveness check (S1000 live, S2000 verified, S9999 absent)
    # -------------------------------------------------------------------------
    $s1000Dir = Join-Path $fixtureDir 'S1000'
    $s2000Dir = Join-Path $fixtureDir 'S2000'
    $s9999Dir = Join-Path $fixtureDir 'S9999'
    New-Item -ItemType Directory -Path $s1000Dir | Out-Null
    New-Item -ItemType Directory -Path $s2000Dir | Out-Null
    New-Item -ItemType Directory -Path $s9999Dir | Out-Null
    (Get-Item -LiteralPath $s1000Dir).LastWriteTime = $oldTime
    (Get-Item -LiteralPath $s2000Dir).LastWriteTime = $oldTime
    (Get-Item -LiteralPath $s9999Dir).LastWriteTime = $oldTime

    # -------------------------------------------------------------------------
    # Test 3: Age cutoff for loose files & unprotected directories
    # -------------------------------------------------------------------------
    $staleLog = Join-Path $fixtureDir 'old_logcat.log'
    $freshLog = Join-Path $fixtureDir 'recent_logcat.log'
    $staleDir = Join-Path $fixtureDir 'unprotected_stale_dir'
    $freshDir = Join-Path $fixtureDir 'unprotected_fresh_dir'

    [System.IO.File]::WriteAllText($staleLog, 'stale log content')
    [System.IO.File]::WriteAllText($freshLog, 'fresh log content')
    New-Item -ItemType Directory -Path $staleDir | Out-Null
    New-Item -ItemType Directory -Path $freshDir | Out-Null

    (Get-Item -LiteralPath $staleLog).LastWriteTime = $oldTime
    (Get-Item -LiteralPath $freshLog).LastWriteTime = $freshTime
    (Get-Item -LiteralPath $staleDir).LastWriteTime = $oldTime
    (Get-Item -LiteralPath $freshDir).LastWriteTime = $freshTime

    # -------------------------------------------------------------------------
    # Test 4: -IncludeScratch handling
    # -------------------------------------------------------------------------
    $staleScratchFile = Join-Path $scratchDir 'old_scratch.txt'
    $freshScratchFile = Join-Path $scratchDir 'new_scratch.txt'
    [System.IO.File]::WriteAllText($staleScratchFile, 'stale scratch')
    [System.IO.File]::WriteAllText($freshScratchFile, 'fresh scratch')
    (Get-Item -LiteralPath $staleScratchFile).LastWriteTime = $oldTime
    (Get-Item -LiteralPath $freshScratchFile).LastWriteTime = $freshTime

    # -------------------------------------------------------------------------
    # Test 5: DryRun verification
    # -------------------------------------------------------------------------
    $dryRunFile = Join-Path $fixtureDir 'dry_run_stale.ps1'
    [System.IO.File]::WriteAllText($dryRunFile, 'echo test')
    (Get-Item -LiteralPath $dryRunFile).LastWriteTime = $oldTime

    & pwsh -NoProfile -File $archiveScript -RepoRoot $repoRoot -TempDir $fixtureDir -SelectCli $mockSelectCli -Stamp $stamp -DryRun
    if ($LASTEXITCODE -ne 0) {
        throw "DryRun failed with exit code $LASTEXITCODE"
    }
    if (-not (Test-Path -LiteralPath $dryRunFile)) {
        throw "DryRun failed: stale dry run file was moved!"
    }
    $archivePath = Join-Path $fixtureDir "archive/$stamp"
    if (Test-Path -LiteralPath $archivePath) {
        throw "DryRun failed: archive directory $archivePath was created!"
    }

    # -------------------------------------------------------------------------
    # Execution: Run actual sweep with -IncludeScratch
    # -------------------------------------------------------------------------
    & pwsh -NoProfile -File $archiveScript -RepoRoot $repoRoot -TempDir $fixtureDir -SelectCli $mockSelectCli -Stamp $stamp -IncludeScratch
    if ($LASTEXITCODE -ne 0) {
        throw "archive-temp.ps1 execution failed with exit code $LASTEXITCODE"
    }

    # Assertions after real sweep:
    # 1. Protected infrastructure survived in root
    if (-not (Test-Path -LiteralPath $scratchDir)) { throw "ASSERT FAIL: temp/scratch/ was moved or removed!" }
    if (-not (Test-Path -LiteralPath $leasesDir)) { throw "ASSERT FAIL: SPEC-TICKET.LEASES was moved!" }
    if (-not (Test-Path -LiteralPath $deviceRegistryDir)) { throw "ASSERT FAIL: DEVICE.REGISTRY was moved!" }
    if (-not (Test-Path -LiteralPath $releaseFreezeFile)) { throw "ASSERT FAIL: RELEASE-FREEZE.json was moved!" }
    if (-not (Test-Path -LiteralPath $gitignoreFile)) { throw "ASSERT FAIL: .gitignore was moved!" }
    if (-not (Test-Path -LiteralPath $fixedPatternFile)) { throw "ASSERT FAIL: fastmediasorter_*.log was moved!" }

    # 2. Live ticket survived, Verified & absent moved
    if (-not (Test-Path -LiteralPath $s1000Dir)) { throw "ASSERT FAIL: Live ticket S1000 scratch was moved!" }
    if (Test-Path -LiteralPath $s2000Dir) { throw "ASSERT FAIL: Verified ticket S2000 scratch was NOT moved!" }
    if (Test-Path -LiteralPath $s9999Dir) { throw "ASSERT FAIL: Absent ticket S9999 scratch was NOT moved!" }
    if (-not (Test-Path -LiteralPath (Join-Path $archivePath 'tickets/S2000/S2000'))) { throw "ASSERT FAIL: S2000 not found in archive!" }
    if (-not (Test-Path -LiteralPath (Join-Path $archivePath 'tickets/S9999/S9999'))) { throw "ASSERT FAIL: S9999 not found in archive!" }

    # 3. Age cutoff for loose files & dirs
    if (Test-Path -LiteralPath $staleLog) { throw "ASSERT FAIL: Stale log file was NOT moved!" }
    if (-not (Test-Path -LiteralPath $freshLog)) { throw "ASSERT FAIL: Fresh log file was moved!" }
    if (Test-Path -LiteralPath $staleDir) { throw "ASSERT FAIL: Stale dir was NOT moved!" }
    if (-not (Test-Path -LiteralPath $freshDir)) { throw "ASSERT FAIL: Fresh dir was moved!" }

    if (-not (Test-Path -LiteralPath (Join-Path $archivePath 'logs/old_logcat.log'))) { throw "ASSERT FAIL: Stale logcat file not found in archive/logs!" }
    if (-not (Test-Path -LiteralPath (Join-Path $archivePath 'dirs/unprotected_stale_dir'))) { throw "ASSERT FAIL: Stale dir not found in archive/dirs!" }

    # 4. Scratch contents with -IncludeScratch
    if (Test-Path -LiteralPath $staleScratchFile) { throw "ASSERT FAIL: Stale scratch file was NOT moved!" }
    if (-not (Test-Path -LiteralPath $freshScratchFile)) { throw "ASSERT FAIL: Fresh scratch file was moved!" }
    if (-not (Test-Path -LiteralPath (Join-Path $archivePath 'scratch/old_scratch.txt'))) { throw "ASSERT FAIL: Stale scratch file not found in archive/scratch!" }

    Write-Host "ALL archive-temp UNIT TESTS PASSED." -ForegroundColor Green
    exit 0
}
finally {
    if (Test-Path -LiteralPath $fixtureDir) {
        Remove-Item -Recurse -Force -LiteralPath $fixtureDir -ErrorAction SilentlyContinue
    }
    if (Test-Path -LiteralPath $mockSelectCli) {
        Remove-Item -Force -LiteralPath $mockSelectCli -ErrorAction SilentlyContinue
    }
}
