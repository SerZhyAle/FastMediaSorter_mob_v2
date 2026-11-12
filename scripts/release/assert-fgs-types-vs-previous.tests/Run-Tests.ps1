#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/release/assert-fgs-types-vs-previous.ps1 (S4413).

.DESCRIPTION
    Every case feeds fixture manifests, snapshots and a fixture registry from a per-PID temp
    directory and passes -ArchiveRoot explicitly, so no case reads the real archive or the real
    registry. No case passes -Build: the suite never runs gradle.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'assert-fgs-types-vs-previous.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Host "assert-fgs-types-vs-previous.tests: FAIL (run=0 skipped=0) - not found: $script" -ForegroundColor Red
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$root = Join-Path ([System.IO.Path]::GetTempPath()) "fgs-types-tests-$PID"
Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $root -Force | Out-Null

function New-Fixture {
    param([string] $Name, [string] $Text)
    $path = Join-Path $root $Name
    New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
    [System.IO.File]::WriteAllText($path, $Text)
    return $path
}

function New-ManifestText {
    param([string[]] $ServiceTypes, [string] $RemovedType, [string] $VersionCode)
    $services = foreach ($t in $ServiceTypes) { "    <service android:name=`"svc.$($t -replace '\|', '_')`" android:foregroundServiceType=`"$t`" />" }
    if ($RemovedType) { $services += "    <service android:name=`"svc.removed`" android:foregroundServiceType=`"$RemovedType`" tools:node=`"remove`" />" }
    $codeAttr = if ($VersionCode) { " android:versionCode=`"$VersionCode`"" } else { '' }
    return @"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools" package="x"$codeAttr>
  <application>
$($services -join "`n")
  </application>
</manifest>
"@
}

$registry = New-Fixture 'registry.jsonl' (@(
    '{"kind":"foregroundServiceType","key":"dataSync","modules":["app_v2"],"flavors":["standard","noLegal"],"justification":"j"}'
    '{"kind":"foregroundServiceType","key":"microphone","modules":["app_v2"],"flavors":["standard"],"justification":"j"}'
    '{"kind":"foregroundServiceType","key":"camera","modules":["app_v2"],"flavors":["noLegal"],"justification":"j"}'
    '{"kind":"permission","key":"android.permission.FOREGROUND_SERVICE_CAMERA","modules":["app_v2"],"flavors":["standard"],"justification":"j"}'
) -join "`n")

$emptyArchive = Join-Path $root 'archive-empty'
New-Item -ItemType Directory -Path $emptyArchive -Force | Out-Null
$archive = Join-Path $root 'archive'
New-Fixture 'archive/260900100/fgs-types.json' '{"versionCode":260900100,"variant":"standard","types":["dataSync"]}' | Out-Null
New-Fixture 'archive/261000200/fgs-types.json' '{"versionCode":261000200,"variant":"standard","types":["dataSync","mediaPlayback"]}' | Out-Null
New-Item -ItemType Directory -Path (Join-Path $archive '261100300') -Force | Out-Null
# A bundle built but not carried by Play production (S4499): newer than every confirmed snapshot,
# and carrying the type that made Play refuse it.
New-Fixture 'archive/261050400/fgs-types.pending.json' '{"versionCode":261050400,"variant":"standard","types":["camera","dataSync","mediaPlayback"]}' | Out-Null

function Invoke-Check {
    param([string[]] $CheckArgs)
    $out = & $pwshExe -NoProfile -File $script @CheckArgs 2>&1 | Out-String
    return [pscustomobject]@{ Code = $LASTEXITCODE; Out = $out }
}

$script:failures = 0
$script:caseCount = 0
function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
    $script:caseCount++
    try {
        $message = & $Body
        if ($message) { Write-Host "FAIL  $Name - $message" -ForegroundColor Red; $script:failures++ }
        else { Write-Host "PASS  $Name" -ForegroundColor Green }
    }
    catch {
        Write-Host "FAIL  $Name - threw: $($_.Exception.Message)" -ForegroundColor Red
        $script:failures++
    }
}

try {
    Assert-Case 'C1 -Help exits 0' {
        $r = Invoke-Check @('-Help')
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code)" }
    }

    Assert-Case 'C2 new unregistered type against a snapshot exits 1 and names it' {
        $m = New-Fixture 'c2/AndroidManifest.xml' (New-ManifestText @('dataSync', 'camera'))
        $r = Invoke-Check @('-Manifest', $m, '-ArchiveRoot', $archive, '-RegistryPath', $registry)
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch "'camera'.*strip it from standard") { return "the refusal does not name camera and the strip: $($r.Out)" }
    }

    Assert-Case 'C3 type in the newest snapshot exits 0 (older snapshot and empty folder ignored)' {
        $m = New-Fixture 'c3/AndroidManifest.xml' (New-ManifestText @('dataSync|mediaPlayback'))
        $r = Invoke-Check @('-Manifest', $m, '-ArchiveRoot', $archive, '-RegistryPath', $registry)
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'versionCode 261000200') { return "the newest snapshot was not the baseline: $($r.Out)" }
        if ($r.Out -notmatch 'dataSync, mediaPlayback') { return "the type set is not printed: $($r.Out)" }
    }

    Assert-Case 'C4 new against the snapshot but registered for standard still exits 1' {
        $m = New-Fixture 'c4/AndroidManifest.xml' (New-ManifestText @('dataSync', 'microphone'))
        $r = Invoke-Check @('-Manifest', $m, '-ArchiveRoot', $archive, '-RegistryPath', $registry)
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch "'microphone'") { return "the refusal does not name the registered new type: $($r.Out)" }
    }

    Assert-Case 'C4b the judged build''s own snapshot is not its baseline' {
        $m = New-Fixture 'c4b/AndroidManifest.xml' (New-ManifestText @('dataSync|mediaPlayback') '' '261000200')
        $r = Invoke-Check @('-Manifest', $m, '-ArchiveRoot', $archive, '-RegistryPath', $registry)
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'versionCode 260900100') { return "the older snapshot was not the baseline: $($r.Out)" }
    }

    Assert-Case 'C4c a newer pending snapshot is never the baseline and is named as ignored' {
        $m = New-Fixture 'c4c/AndroidManifest.xml' (New-ManifestText @('camera|dataSync|mediaPlayback'))
        $r = Invoke-Check @('-Manifest', $m, '-ArchiveRoot', $archive, '-RegistryPath', $registry)
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch "'camera'") { return "the refused type passed against the pending snapshot: $($r.Out)" }
        if ($r.Out -notmatch 'baseline snapshot of versionCode 261000200') { return "the confirmed snapshot was not the baseline: $($r.Out)" }
        if ($r.Out -notmatch 'ignored unconfirmed snapshot\(s\) of versionCode 261050400') { return "the pending snapshot is not named as ignored: $($r.Out)" }
    }

    Assert-Case 'C5 no snapshot, registry fallback, registered types exit 0' {
        $m = New-Fixture 'c5/AndroidManifest.xml' (New-ManifestText @('dataSync|microphone'))
        $r = Invoke-Check @('-Manifest', $m, '-ArchiveRoot', $emptyArchive, '-RegistryPath', $registry)
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'registry rows for standard') { return "the registry was not the baseline: $($r.Out)" }
    }

    Assert-Case 'C6 no snapshot, registry fallback, a noLegal-only type in standard exits 1' {
        $m = New-Fixture 'c6/AndroidManifest.xml' (New-ManifestText @('microphone|camera'))
        $r = Invoke-Check @('-Manifest', $m, '-ArchiveRoot', $emptyArchive, '-RegistryPath', $registry)
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
    }

    Assert-Case 'C7 a tools:node=remove service does not count' {
        $m = New-Fixture 'c7/AndroidManifest.xml' (New-ManifestText @('dataSync') 'camera')
        $r = Invoke-Check @('-Manifest', $m, '-ArchiveRoot', $emptyArchive, '-RegistryPath', $registry)
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code): $($r.Out)" }
    }

    Assert-Case 'C8 missing manifest exits 2' {
        $r = Invoke-Check @('-Manifest', (Join-Path $root 'absent/AndroidManifest.xml'), '-ArchiveRoot', $emptyArchive, '-RegistryPath', $registry)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'COULD NOT VERIFY') { return "no COULD NOT VERIFY verdict: $($r.Out)" }
    }

    Assert-Case 'C9 malformed manifest exits 2' {
        $m = New-Fixture 'c9/AndroidManifest.xml' '<manifest><application>'
        $r = Invoke-Check @('-Manifest', $m, '-ArchiveRoot', $emptyArchive, '-RegistryPath', $registry)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
    }

    Assert-Case 'C10 unreadable snapshot exits 2' {
        $m = New-Fixture 'c10/AndroidManifest.xml' (New-ManifestText @('dataSync'))
        $s = New-Fixture 'c10/fgs-types.json' 'not json'
        $r = Invoke-Check @('-Manifest', $m, '-Snapshot', $s, '-RegistryPath', $registry)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
    }

    Assert-Case 'C11 missing registry exits 2' {
        $m = New-Fixture 'c11/AndroidManifest.xml' (New-ManifestText @('dataSync'))
        $r = Invoke-Check @('-Manifest', $m, '-ArchiveRoot', $emptyArchive, '-RegistryPath', (Join-Path $root 'absent.jsonl'))
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
    }
}
finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

if ($script:failures -gt 0) {
    Write-Host "assert-fgs-types-vs-previous.tests: FAIL (run=$script:caseCount skipped=0)" -ForegroundColor Red
    exit 1
}
Write-Host "assert-fgs-types-vs-previous.tests: PASS (run=$script:caseCount skipped=0)" -ForegroundColor Green
exit 0
