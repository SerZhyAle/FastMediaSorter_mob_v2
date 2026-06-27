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
    cc   - Commit without push
    id   - Install Standard Debug APK on device (no launch)
    ind  - Install noLegal Debug APK on device (no launch)
    ivn  - Install noLegal Debug APK on device (no launch)
    dc   - Build Debug Clean
    d    - Fast reusable debug build
    db   - Fast reusable debug build (without zip)
    dav  - Debug build with timestamped app version
    fk   - Fast Kotlin compile check
    fr   - Fast resources/manifest check
    fc   - Fast code + resources check
    fu   - Fast full unit-test suite
    cd   - Clean + Debug + Zip
    cdb  - Clean + Debug (without zip)
    cls  - Clean Gradle caches
    c    - Commit & Push
    ch   - Check Typo/Lint
    s    - Setup Test Media
    bp   - Build and Push All
    ss   - Show unresolved specs (alias: sca-specs)
    bf   - Show last build failure block
    bfd  - Build failure digest (structured JSON + verdict)
    nl   - Build noLegal Release
    nd   - Build noLegal Debug
    adb  - adb swiss-army passthrough (scripts\devtest\adb.ps1); verb + options ride in via $Rest
    adb-devices / adb-shot / adb-log / adb-current / adb-launch / adb-clear - fixed-verb shortcuts
.EXAMPLE
    .\a.ps1 d
    .\a d
.EXAMPLE
    .\a.ps1 adb devices
    .\a.ps1 adb log -Tail 400 -Grep S0035
    .\a.ps1 adb-shot -DeviceId emulator-5554
#>

param(
    [string]$Command = ''
)

# Everything after <Command> is forwarded verbatim to the target script (after its preset
# Args), e.g. `.\a.ps1 adb log -Tail 400 -Grep S0035` or `.\a.ps1 adb shot -DeviceId X`.
# Reading the automatic $args (simple-script mode) rather than a declared
# ValueFromRemainingArguments parameter is deliberate: $args captures -flag tokens too,
# which an advanced-binding param would reject ("a positional parameter cannot be found").
$Rest = $args

$ErrorActionPreference = "Stop"

# Get project root directory
$ProjectRoot = $PSScriptRoot

# Script mapping.
#
# Args MUST be a hashtable, not a string array. Reason: `& $script @arrayArgs` splats
# positionally - strings starting with `-` are treated as values, not flags, and
# leak into the first positional [string] parameter. With `& $script @hashtableArgs`
# PowerShell always binds by name, no matter how the keys order. Empty hashtable
# is fine and means "no args".
#
# Each value is `$true` for a switch, or a string/int for a typed param.
$scripts = @{
    'r'         = @{ Path = 'scripts\builders\build-aab-release.ps1'; Args = @{} }
    'vr'        = @{ Path = 'scripts\builders\build-vr-release.ps1'; Args = @{} }
    'cc'        = @{ Path = 'scripts\utils\commit-push.ps1'; Args = @{ NoPush = $true } }
    'id'        = @{ Path = 'scripts\builders\install-standard-debug-to-device.ps1'; Args = @{} }
    'ind'       = @{ Path = 'scripts\builders\install-nolegal-debug-to-device.ps1'; Args = @{} }
    'ivn'       = @{ Path = 'scripts\builders\install-nolegal-debug-to-device.ps1'; Args = @{} }
    'dc'        = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @{} }
    'd'         = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @{} }
    'db'        = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @{ SkipZip = $true } }
    'dav'       = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @{ AutoVersion = $true } }
    'bd'        = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @{ SkipZip = $true } }  # typo-tolerant alias for 'db'
    'dq'        = @{ Path = 'scripts\builders\build-debug.PS1'; Args = @{ SkipZip = $true; Quiet = $true } }  # quiet debug: filters known noise
    'cd'        = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @{} }
    'cdb'       = @{ Path = 'scripts\builders\build-debug-clean.PS1'; Args = @{ SkipZip = $true } }
    'fk'        = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'Code' } }
    'fr'        = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'Resources' } }
    'fc'        = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'CodeAndResources' } }
    'fu'        = @{ Path = 'scripts\builders\check-standard-fast.ps1'; Args = @{ Mode = 'Unit' } }
    'cls'       = @{ Path = 'scripts\builders\clean-gradle-caches.ps1'; Args = @{} }
    'c'         = @{ Path = 'scripts\utils\commit-push.ps1'; Args = @{} }
    'ch'        = @{ Path = 'scripts\utils\check-typo-lint.ps1'; Args = @{} }
    's'         = @{ Path = 'scripts\utils\setup_test_media.ps1'; Args = @{} }
    'b'         = @{ Path = 'scripts\builders\build-and-push-all.ps1'; Args = @{} }
    'bp'        = @{ Path = 'scripts\builders\build-and-push-all.ps1'; Args = @{} }
    'ss'        = @{ Path = 'scripts\spec_catalog\sca-specs.ps1'; Args = @{} }
    'sca-specs' = @{ Path = 'scripts\spec_catalog\sca-specs.ps1'; Args = @{} }
    'bf'        = @{ Path = 'scripts\builders\get-last-build-failure.ps1'; Args = @{} }
    'bfd'       = @{ Path = 'scripts\builders\build-failure-digest.ps1'; Args = @{} }
    'nl'        = @{ Path = 'scripts\builders\build-nolegal-release.ps1'; Args = @{} }
    'nd'        = @{ Path = 'scripts\builders\build-nolegal-debug.ps1'; Args = @{} }
    # adb swiss-army (scripts/devtest/adb.ps1). `adb` is the full passthrough - the verb
    # and any options ride in via $Rest, e.g. `.\a.ps1 adb log -Tail 400 -Grep S0035`.
    # The rest are fixed-verb shortcuts; extra options still forward through $Rest.
    'adb'       = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{} }
    'adb-devices' = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'devices' } }
    'adb-shot'  = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'shot' } }
    'adb-log'   = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'log' } }
    'adb-current' = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'current' } }
    'adb-launch' = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'launch' } }
    'adb-clear' = @{ Path = 'scripts\devtest\adb.ps1'; Args = @{ Verb = 'clear' } }
}

# Validate command
if (-not $scripts.ContainsKey($Command)) {
    Write-Host "❌ Unknown command: $Command" -ForegroundColor Red
    Write-Host ""
    Write-Host "Available commands:" -ForegroundColor Yellow
    Write-Host "  r    - Build AAB Release" -ForegroundColor Cyan
    Write-Host "  vr   - Build VR Release APK" -ForegroundColor Cyan
    Write-Host "  cc   - Commit without push" -ForegroundColor Cyan
    Write-Host "  id   - Install Standard Debug APK on device (NO launch)" -ForegroundColor Cyan
    Write-Host "  ind  - Install noLegal Debug APK on device (NO launch)" -ForegroundColor Cyan
    Write-Host "  ivn  - Install noLegal Debug APK on device (NO launch)" -ForegroundColor Cyan
    Write-Host "  dc   - Build Debug Clean" -ForegroundColor Cyan
    Write-Host "  d    - Fast reusable debug build" -ForegroundColor Cyan
    Write-Host "  db   - Fast reusable debug build without zip" -ForegroundColor Cyan
    Write-Host "  dav  - Debug build with timestamped app version" -ForegroundColor Cyan
    Write-Host "  bd   - Build Debug without zip (typo-tolerant alias for db)" -ForegroundColor Cyan
    Write-Host "  dq   - Build Debug quiet (no zip, suppresses known-noise lines)" -ForegroundColor Cyan
    Write-Host "  cd   - Clean + Debug + zip" -ForegroundColor Cyan
    Write-Host "  cdb  - Clean + Debug without zip" -ForegroundColor Cyan
    Write-Host "  fk   - Fast Kotlin compile check" -ForegroundColor Cyan
    Write-Host "  fr   - Fast resources/manifest check" -ForegroundColor Cyan
    Write-Host "  fc   - Fast code + resources check" -ForegroundColor Cyan
    Write-Host "  fu   - Fast full unit-test suite" -ForegroundColor Cyan
    Write-Host "  cls  - Clean Gradle caches" -ForegroundColor Cyan
    Write-Host "  c    - Commit & Push" -ForegroundColor Cyan
    Write-Host "  ch   - Check Typo/Lint" -ForegroundColor Cyan
    Write-Host "  s    - Setup Test Media" -ForegroundColor Cyan
    Write-Host "  b    - Build and Push All (same as bp)" -ForegroundColor Cyan
    Write-Host "  bp   - Build and Push All" -ForegroundColor Cyan
    Write-Host "  ss   - Show unresolved specs (alias: sca-specs)" -ForegroundColor Cyan
    Write-Host "  bf   - Show last build failure block" -ForegroundColor Cyan
    Write-Host "  bfd  - Build failure digest (structured JSON + verdict)" -ForegroundColor Cyan
    Write-Host "  nl   - Build noLegal Release" -ForegroundColor Cyan
    Write-Host "  nd   - Build noLegal Debug" -ForegroundColor Cyan
    Write-Host "  adb  - adb swiss-army passthrough, e.g. 'adb log -Tail 400 -Grep S0035'" -ForegroundColor Cyan
    Write-Host "  adb-devices / adb-shot / adb-log / adb-current / adb-launch / adb-clear" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage: .\a.ps1 <command>" -ForegroundColor Gray
    Write-Host "Example: .\a.ps1 d" -ForegroundColor Gray
    exit 1
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
# The worktree lives at ../FastMediaSorter_release - created via:
#   git worktree add ../FastMediaSorter_release main
$releaseCommands = @('r', 'nl', 'vr')
if ($releaseCommands -contains $Command) {
    $worktreePath = Join-Path (Split-Path $ProjectRoot -Parent) "FastMediaSorter_release"
    if (Test-Path $worktreePath) {
        Write-Host "Release build - delegating to release worktree [main]" -ForegroundColor Cyan
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
        $argsDisplay = if ($scriptArgs -is [hashtable]) {
            ($scriptArgs.GetEnumerator() | ForEach-Object {
                if ($_.Value -is [bool]) { "-$($_.Key)" } else { "-$($_.Key) $($_.Value)" }
            }) -join ' '
        } else { $scriptArgs -join ' ' }
        Write-Host "Executing (worktree): $($scriptEntry.Path) $argsDisplay $($Rest -join ' ')" -ForegroundColor Green
        Write-Host ""
        # CRITICAL: change CWD to worktree before invoking the build script.
        # Gradle resolves the project directory from CWD (not from gradlew.bat location),
        # so without this Push-Location Gradle would build the DEV project even though
        # the script writes versionCode/Name into the worktree's build.gradle.kts -
        # producing artifacts with stale versions and silently mirroring dev outputs.
        Push-Location $worktreePath
        try {
            & $worktreeScript @scriptArgs @Rest
            $buildExit = $LASTEXITCODE
        }
        finally {
            Pop-Location
        }

        # Mirror DOWNLOADS from worktree to local dev directory
        if ($buildExit -eq 0) {
            $worktreeDownloads = Join-Path $worktreePath "DOWNLOADS"
            $localDownloads = Join-Path $ProjectRoot  "DOWNLOADS"
            if (Test-Path $worktreeDownloads) {
                if (-not (Test-Path $localDownloads)) {
                    New-Item -ItemType Directory -Path $localDownloads | Out-Null
                }
                # Copy all build artifacts (overwrite) - skip the journal so we append below
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
$argsDisplay = if ($scriptArgs -is [hashtable]) {
    ($scriptArgs.GetEnumerator() | ForEach-Object {
        if ($_.Value -is [bool]) { "-$($_.Key)" } else { "-$($_.Key) $($_.Value)" }
    }) -join ' '
} else { $scriptArgs -join ' ' }
Write-Host "Executing: $($scriptEntry.Path) $argsDisplay $($Rest -join ' ')" -ForegroundColor Green
Write-Host ""

& $scriptPath @scriptArgs @Rest

# Return exit code from executed script
exit $LASTEXITCODE
