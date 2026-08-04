<#
.SYNOPSIS
  Shared adb-path auto-discovery, extracted from adb.ps1 (S1341) so every caller
  (adb.ps1, spec-prerelease.md, any future device script) uses one discovery order
  instead of each hand-rolling its own hardcoded fallback.

.DESCRIPTION
  Dot-source this file to get the `Find-Adb` function:
    . "$PSScriptRoot/lib/find-adb.ps1"
    $adb = Find-Adb

  Discovery order: ANDROID_HOME / ANDROID_SDK_ROOT env vars, then PATH, then the
  known default install location. Returns $null if none resolve - the caller decides
  how to fail (adb.ps1 exits 1; a command's inline block can `Fail`/`throw` its own way).
#>

function Find-Adb {
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root) {
            $candidate = Join-Path $root 'platform-tools\adb.exe'
            if (Test-Path -Path $candidate -PathType Leaf) { return $candidate }
        }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $known = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path -Path $known -PathType Leaf) { return $known }

    return $null
}
