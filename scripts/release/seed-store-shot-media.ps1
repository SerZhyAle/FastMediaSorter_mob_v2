#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Put the store-safe synthetic media corpus on a device, ready for a Play listing capture.

.DESCRIPTION
    Runs scripts/release/store_shot_media.py, pushes what it generated to a device root of its own,
    and asks MediaStore to index it so the app's virtual resources count the new files.

    The root is deliberately NOT the one scripts/utils/setup_test_media.ps1 writes. That script
    provisions c:\Common\test_media, which is real personal material and must never appear in a
    published frame (S1991); keeping the two corpora in separate roots means a device staged for a
    store capture cannot quietly show the wrong one.

    Every device call goes through scripts/devtest/adb.ps1 rather than a raw adb, because a raw adb
    invoked from a POSIX-style shell rewrites /sdcard paths into Windows ones and fails with
    remote secure_mkdirs().

.PARAMETER DeviceId
    Target device serial. Omit when exactly one device is attached.

.PARAMETER Root
    Device directory to write. Defaults to /sdcard/Download/FastMediaSorter_Store.

.PARAMETER SkipGenerate
    Push the corpus already under temp/store-shot-media/ instead of regenerating it.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/seed-store-shot-media.ps1 -DeviceId emulator-5556

.NOTES
    Exit codes:
      0 - corpus generated (unless skipped), pushed and handed to MediaStore.
      1 - a generation, push or scan step failed.
      2 - could not verify: no device, no python, or an empty local corpus.
#>
param(
    [string]$DeviceId,
    [string]$Root = '/sdcard/Download/FastMediaSorter_Store',
    [switch]$SkipGenerate
)

$ErrorActionPreference = 'Stop'

$repoRoot  = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adb       = Join-Path $repoRoot 'scripts/devtest/adb.ps1'
$generator = Join-Path $repoRoot 'scripts/release/store_shot_media.py'
$localRoot = Join-Path $repoRoot 'temp/store-shot-media'

$devArgs = @(); if ($DeviceId) { $devArgs = @('-DeviceId', $DeviceId) }

function Invoke-Device {
    param([string[]]$AdbArgs)
    & pwsh -NoProfile -File $adb @devArgs @AdbArgs
}

if (-not (Test-Path $generator)) {
    Write-Host "SEED FAIL: generator not found at $generator"
    exit 2
}

$python = Join-Path $repoRoot '.venv/Scripts/python.exe'
if (-not (Test-Path $python)) { $python = 'python' }

if (-not $SkipGenerate) {
    Write-Host "[1/4] Generating the corpus.."
    & $python $generator --clean --out $localRoot
    if ($LASTEXITCODE -ne 0) {
        Write-Host "SEED FAIL: generator exited $LASTEXITCODE"
        exit ($LASTEXITCODE -eq 2 ? 2 : 1)
    }
} else {
    Write-Host "[1/4] Skipping generation (-SkipGenerate)."
}

$folders = @('Photos', 'Videos', 'Music', 'Books')
$localFiles = @(Get-ChildItem -Path $localRoot -Recurse -File -ErrorAction SilentlyContinue)
if ($localFiles.Count -eq 0) {
    Write-Host "SEED FAIL: no files under $localRoot - nothing to push."
    exit 2
}

Write-Host "[2/4] Clearing the device root.."
Invoke-Device @('shell', '-Cmd', "rm -rf '$Root'") | Out-Null
Invoke-Device @('shell', '-Cmd', "mkdir -p '$Root'") | Out-Null

Write-Host "[3/4] Pushing $($localFiles.Count) file(s).."
foreach ($folder in $folders) {
    $source = Join-Path $localRoot $folder
    if (-not (Test-Path $source)) { continue }
    $out = Invoke-Device @('push', '-Local', $source, '-Remote', $Root) 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host ($out | Out-String)
        Write-Host "SEED FAIL: pushing $folder exited $LASTEXITCODE"
        exit 1
    }
    Write-Host "  pushed $folder"
}

$remoteCount = (Invoke-Device @('shell', '-Cmd', "find '$Root' -type f | wc -l") |
                Select-String -Pattern '^\s*\d+\s*$' | Select-Object -First 1)
if ($remoteCount) { Write-Host "  device now holds $($remoteCount.ToString().Trim()) file(s)" }

# One broadcast per file rather than a volume rescan: the corpus is small, and the per-file form is
# the one this repository has already proven across its AVD fleet.
Write-Host "[4/4] Handing the files to MediaStore.."
foreach ($file in $localFiles) {
    $relative = $file.FullName.Substring($localRoot.Length).Replace('\', '/').TrimStart('/')
    $remote = "$Root/$relative"
    Invoke-Device @('shell', '-Cmd',
        "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$remote'") | Out-Null
}

Write-Host "SEED OK: $($localFiles.Count) file(s) under $Root"
exit 0
