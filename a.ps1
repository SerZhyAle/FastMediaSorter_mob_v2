#!/usr/bin/env pwsh
<#
.SYNOPSIS
    FastMediaSorter project scripts launcher
.DESCRIPTION
    Quick alias launcher for common project scripts
.PARAMETER Command
    Script command to execute:
    r    - Build AAB Release
    vr   - Build VR Release APK
    vrd  - Build VR Debug APK
    ivr  - Install VR Release APK on device (no launch — use Quest Library)
    ivrd - Install VR Debug APK on device (no launch — use Quest Library)
    ivn  - Install noLegal Debug APK on device (no launch)
    dc   - Build Debug Clean
    d    - Build Debug
    db   - Build Debug (without zip)
    cd   - Clean + Debug + Zip
    cdb  - Clean + Debug (without zip)
    cls  - Clean Gradle caches
    c    - Commit & Push
    ch   - Check Typo/Lint
    s    - Setup Test Media
    bp   - Build and Push All
    ss   - Show unresolved specs (alias: sca-specs)
    nl   - Build noLegal Release
    nd   - Build noLegal Debug
.EXAMPLE
    .\a.ps1 d
    .\a d
#>

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Command
)

$ErrorActionPreference = "Stop"

# Get project root directory
$ProjectRoot = $PSScriptRoot

# Script mapping
$scripts = @{
    'r'         = @{ Path = 'scripts\builders\build-aab-release.ps1'; Args = @() }
    'vr'        = @{ Path = 'scripts\builders\build-vr-release.ps1'; Args = @() }
    'vrd'       = @{ Path = 'scripts\builders\build-vr-debug.ps1'; Args = @() }
    'ivr'       = @{ Path = 'scripts\builders\install-vr-release-to-device.ps1'; Args = @() }
    'ivrd'      = @{ Path = 'scripts\builders\install-vr-debug-to-device.ps1'; Args = @() }
    'ivn'       = @{ Path = 'scripts\builders\install-nolegal-debug-to-device.ps1'; Args = @() }
    'dc'        = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @() }
    'd'         = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @() }
    'db'        = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @('-SkipZip') }
    'cd'        = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @() }
    'cdb'       = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @('-SkipZip') }
    'cls'       = @{ Path = 'scripts\builders\clean-gradle-caches.ps1'; Args = @() }
    'c'         = @{ Path = 'scripts\utils\commit-push.ps1'; Args = @() }
    'ch'        = @{ Path = 'scripts\utils\check-typo-lint.ps1'; Args = @() }
    's'         = @{ Path = 'scripts\utils\setup_test_media.ps1'; Args = @() }
    'b'         = @{ Path = 'scripts\builders\build-and-push-all.ps1'; Args = @() }
    'bp'        = @{ Path = 'scripts\builders\build-and-push-all.ps1'; Args = @() }
    'ss'        = @{ Path = 'scripts\spec_catalog\sca-specs.ps1'; Args = @() }
    'sca-specs' = @{ Path = 'scripts\spec_catalog\sca-specs.ps1'; Args = @() }
    'nl'        = @{ Path = 'scripts\builders\build-nolegal-release.ps1'; Args = @() }
    'nd'        = @{ Path = 'scripts\builders\build-nolegal-debug.ps1'; Args = @() }
}

function Set-ChaquopyLocalState {
    param(
        [Parameter(Mandatory = $true)]
        [bool]$Enabled
    )

    $localPropertiesPath = Join-Path $ProjectRoot 'local.properties'
    if (-not (Test-Path $localPropertiesPath)) {
        Write-Host "Warning: local.properties not found, skipping Chaquopy toggle." -ForegroundColor Yellow
        return
    }

    $desiredLine = if ($Enabled) { 'chaquopy.enabled=true' } else { '# chaquopy.enabled=true' }
    $lines = Get-Content -Path $localPropertiesPath
    $updatedLines = New-Object System.Collections.Generic.List[string]
    $foundChaquopyLine = $false

    foreach ($line in $lines) {
        if ($line -match '^[ \t]*#?[ \t]*chaquopy\.enabled\s*=.*$') {
            if (-not $foundChaquopyLine) {
                $updatedLines.Add($desiredLine)
                $foundChaquopyLine = $true
            }
            continue
        }

        $updatedLines.Add($line)
    }

    if (-not $foundChaquopyLine) {
        if (-not $Enabled) {
            return
        }

        if ($updatedLines.Count -gt 0 -and $updatedLines[$updatedLines.Count - 1] -ne '') {
            $updatedLines.Add('')
        }

        $updatedLines.Add('# S0174: Enable Chaquopy Python plugin for noLegal flavor builds.')
        $updatedLines.Add('# Required whenever :app_v2:compileNoLegal* tasks are in scope (IDE sync, assembleNoLegal*).')
        $updatedLines.Add('# Without this, com.chaquo.python.* is not on the classpath and noLegal fails to compile.')
        $updatedLines.Add($desiredLine)
    }

    $currentContent = Get-Content -Path $localPropertiesPath -Raw
    $updatedContent = ($updatedLines -join [Environment]::NewLine)
    if ($updatedContent -ne $currentContent.TrimEnd("`r", "`n")) {
        $writeError = $null
        for ($attempt = 1; $attempt -le 5; $attempt++) {
            try {
                # Android Studio / Gradle can briefly probe local.properties, so tolerate
                # a short transient lock instead of failing the launcher immediately.
                Set-Content -Path $localPropertiesPath -Value $updatedContent -Encoding utf8NoBOM
                $writeError = $null
                break
            }
            catch {
                $writeError = $_
                [System.Threading.Thread]::Sleep(200)
            }
        }

        if ($null -ne $writeError) {
            throw $writeError
        }

        $stateLabel = if ($Enabled) { 'ON' } else { 'OFF' }
        Write-Host "Chaquopy local toggle: $stateLabel" -ForegroundColor DarkGray
    }
}

# Validate command
if (-not $scripts.ContainsKey($Command)) {
    Write-Host "❌ Unknown command: $Command" -ForegroundColor Red
    Write-Host ""
    Write-Host "Available commands:" -ForegroundColor Yellow
    Write-Host "  r    - Build AAB Release" -ForegroundColor Cyan
    Write-Host "  vr   - Build VR Release APK" -ForegroundColor Cyan
    Write-Host "  vrd  - Build VR Debug APK" -ForegroundColor Cyan
    Write-Host "  ivr  - Install VR Release APK on device (NO launch — use Quest Library)" -ForegroundColor Cyan
    Write-Host "  ivrd - Install VR Debug APK on device (NO launch — use Quest Library)" -ForegroundColor Cyan
    Write-Host "  ivn  - Install noLegal Debug APK on device (NO launch)" -ForegroundColor Cyan
    Write-Host "  dc   - Build Debug Clean" -ForegroundColor Cyan
    Write-Host "  d    - Build Debug" -ForegroundColor Cyan
    Write-Host "  db   - Build Debug without zip" -ForegroundColor Cyan
    Write-Host "  cd   - Clean + Debug + zip" -ForegroundColor Cyan
    Write-Host "  cdb  - Clean + Debug without zip" -ForegroundColor Cyan
    Write-Host "  cls  - Clean Gradle caches" -ForegroundColor Cyan
    Write-Host "  c    - Commit & Push" -ForegroundColor Cyan
    Write-Host "  ch   - Check Typo/Lint" -ForegroundColor Cyan
    Write-Host "  s    - Setup Test Media" -ForegroundColor Cyan
    Write-Host "  b    - Build and Push All (same as bp)" -ForegroundColor Cyan
    Write-Host "  bp   - Build and Push All" -ForegroundColor Cyan
    Write-Host "  ss   - Show unresolved specs (alias: sca-specs)" -ForegroundColor Cyan
    Write-Host "  nl   - Build noLegal Release" -ForegroundColor Cyan
    Write-Host "  nd   - Build noLegal Debug" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage: .\a.ps1 <command>" -ForegroundColor Gray
    Write-Host "Example: .\a.ps1 d" -ForegroundColor Gray
    exit 1
}

# Keep local.properties aligned with the launcher command so IDE sync and manual builds
# follow the same noLegal-vs-non-noLegal Chaquopy state between invocations.
$chaquopyLocalState = switch ($Command) {
    'nd' { $true }
    'nl' { $true }
    'd' { $false }
    'db' { $false }
    'dc' { $false }
    'cd' { $false }
    'cdb' { $false }
    'r' { $false }
    'vr' { $false }
    'vrd' { $false }
    default { $null }
}

if ($null -ne $chaquopyLocalState) {
    Set-ChaquopyLocalState -Enabled $chaquopyLocalState
}

# Get script path
$scriptEntry = $scripts[$Command]
$scriptPath = Join-Path $ProjectRoot $scriptEntry.Path
$scriptArgs = $scriptEntry.Args

# Verify script exists
if (-not (Test-Path $scriptPath)) {
    Write-Host "❌ Script not found: $scriptPath" -ForegroundColor Red
    exit 1
}

# Release commands are always built from the release worktree (main branch).
# The worktree lives at ../FastMediaSorter_release — created via:
#   git worktree add ../FastMediaSorter_release main
$releaseCommands = @('r', 'vr', 'nl')
if ($releaseCommands -contains $Command) {
    $worktreePath = Join-Path (Split-Path $ProjectRoot -Parent) "FastMediaSorter_release"
    if (Test-Path $worktreePath) {
        Write-Host "Release build — delegating to release worktree [main]" -ForegroundColor Cyan
        Write-Host "  $worktreePath" -ForegroundColor DarkGray

        # Pull latest main into worktree
        Write-Host "Pulling latest main..." -ForegroundColor Yellow
        Push-Location $worktreePath
        git pull --ff-only
        $pullExit = $LASTEXITCODE
        Pop-Location
        if ($pullExit -ne 0) {
            Write-Host "Warning: git pull --ff-only failed (exit $pullExit)." -ForegroundColor Yellow
            Write-Host "Worktree may have uncommitted changes or a diverged history." -ForegroundColor Yellow
            Write-Host "Resolve manually in $worktreePath, then re-run." -ForegroundColor Yellow
            exit 1
        }

        # Sync gitignored-but-required files from dev directory to release worktree.
        # Source of truth is always the dev directory; worktree copies are transient.
        $syncManifest = Join-Path $ProjectRoot "scripts\release-worktree-sync.txt"
        if (Test-Path $syncManifest) {
            Write-Host "Syncing local files to release worktree..." -ForegroundColor Yellow
            $syncLines = Get-Content $syncManifest | Where-Object { $_ -notmatch '^\s*#' -and $_.Trim() -ne '' }
            foreach ($relPath in $syncLines) {
                $relPath = $relPath.Trim()
                $srcFile = Join-Path $ProjectRoot $relPath
                $dstFile = Join-Path $worktreePath $relPath
                if (Test-Path $srcFile) {
                    $dstDir = Split-Path $dstFile -Parent
                    if (-not (Test-Path $dstDir)) {
                        New-Item -ItemType Directory -Path $dstDir -Force | Out-Null
                    }
                    Copy-Item -Path $srcFile -Destination $dstFile -Force
                    Write-Host "  synced: $relPath" -ForegroundColor DarkGray
                }
                else {
                    Write-Host "  skipped (not found): $relPath" -ForegroundColor DarkYellow
                }
            }
        }

        # Run the script from inside the worktree so $PSScriptRoot resolves there
        $worktreeScript = Join-Path $worktreePath $scriptEntry.Path
        if (-not (Test-Path $worktreeScript)) {
            Write-Host "Error: script not found in release worktree: $worktreeScript" -ForegroundColor Red
            exit 1
        }
        Write-Host "Executing (worktree): $($scriptEntry.Path) $($scriptArgs -join ' ')" -ForegroundColor Green
        Write-Host ""
        & $worktreeScript @scriptArgs
        $buildExit = $LASTEXITCODE

        # Mirror DOWNLOADS from worktree to local dev directory
        if ($buildExit -eq 0) {
            $worktreeDownloads = Join-Path $worktreePath "DOWNLOADS"
            $localDownloads = Join-Path $ProjectRoot  "DOWNLOADS"
            if (Test-Path $worktreeDownloads) {
                if (-not (Test-Path $localDownloads)) {
                    New-Item -ItemType Directory -Path $localDownloads | Out-Null
                }
                # Copy all build artifacts (overwrite) — skip the journal so we append below
                Get-ChildItem -Path $worktreeDownloads -File |
                Where-Object { $_.Name -ne "builds_versions.lst" } |
                ForEach-Object {
                    Copy-Item -Path $_.FullName -Destination (Join-Path $localDownloads $_.Name) -Force
                }
                # Append only the last journal line (most recent build entry) to local journal
                $worktreeJournal = Join-Path $worktreeDownloads "builds_versions.lst"
                $localJournal = Join-Path $localDownloads    "builds_versions.lst"
                if (Test-Path $worktreeJournal) {
                    $lastEntry = Get-Content $worktreeJournal | Select-Object -Last 1
                    if ($lastEntry) {
                        Add-Content -Path $localJournal -Value $lastEntry -Encoding UTF8
                    }
                }
                Write-Host ""
                Write-Host "DOWNLOADS synced to: $localDownloads" -ForegroundColor Cyan
            }
        }

        exit $buildExit
    }
    else {
        Write-Host "Warning: release worktree not found at $worktreePath" -ForegroundColor Yellow
        Write-Host "Set it up once with:" -ForegroundColor Gray
        Write-Host "  git worktree add ../FastMediaSorter_release main" -ForegroundColor Gray
        Write-Host "Falling back to current directory ($(git branch --show-current 2>$null))." -ForegroundColor Yellow
        Write-Host ""
    }
}

# Execute script (non-release commands, or release fallback when no worktree)
Write-Host "Executing: $($scriptEntry.Path) $($scriptArgs -join ' ')" -ForegroundColor Green
Write-Host ""

& $scriptPath @scriptArgs

# Return exit code from executed script
exit $LASTEXITCODE
