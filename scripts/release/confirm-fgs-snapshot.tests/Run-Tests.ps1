#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/release/confirm-fgs-snapshot.ps1 and the pending snapshot that
    scripts/release/retain-deobfuscation.ps1 writes (S4499).

.DESCRIPTION
    Every case works in a per-PID temp archive and passes -ArchiveRoot explicitly, so no case
    reads or writes the real deobfuscation archive. The round-trip case runs the real retention
    script against a fixture mapping and a fixture merged manifest.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'confirm-fgs-snapshot.ps1'
$retain = Join-Path (Split-Path -Parent $PSScriptRoot) 'retain-deobfuscation.ps1'
foreach ($required in @($script, $retain)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Host "confirm-fgs-snapshot.tests: FAIL (run=0 skipped=0) - not found: $required" -ForegroundColor Red
        exit 1
    }
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$root = Join-Path ([System.IO.Path]::GetTempPath()) "confirm-fgs-tests-$PID"
Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $root -Force | Out-Null

function New-Fixture {
    param([string] $Name, [string] $Text)
    $path = Join-Path $root $Name
    New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
    [System.IO.File]::WriteAllText($path, $Text)
    return $path
}

function Invoke-Script {
    param([string] $File, [string[]] $ScriptArgs)
    $out = & $pwshExe -NoProfile -File $File @ScriptArgs 2>&1 | Out-String
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

function Get-SnapshotJson {
    param([long] $Code, [string[]] $Types)
    return (@{ versionCode = $Code; variant = 'standard'; types = $Types } | ConvertTo-Json -Compress)
}

try {
    $archive = Join-Path $root 'archive'
    New-Item -ItemType Directory -Path $archive -Force | Out-Null

    Assert-Case 'R1 -Help exits 0' {
        $r = Invoke-Script $script @('-Help')
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code)" }
    }

    Assert-Case 'R2 a pending snapshot is promoted to fgs-types.json' {
        New-Fixture 'archive/261030100/fgs-types.pending.json' (Get-SnapshotJson 261030100 @('mediaPlayback')) | Out-Null
        $r = Invoke-Script $script @('-VersionCode', '261030100', '-ArchiveRoot', $archive)
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code): $($r.Out)" }
        if (-not (Test-Path (Join-Path $archive '261030100/fgs-types.json'))) { return 'fgs-types.json was not created' }
        if (Test-Path (Join-Path $archive '261030100/fgs-types.pending.json')) { return 'the pending file is still there' }
        if ($r.Out -notmatch 'mediaPlayback') { return "the type set is not printed: $($r.Out)" }
    }

    Assert-Case 'R3 an already confirmed snapshot wins and the pending file is removed' {
        $confirmed = Get-SnapshotJson 261030200 @('dataSync')
        New-Fixture 'archive/261030200/fgs-types.json' $confirmed | Out-Null
        New-Fixture 'archive/261030200/fgs-types.pending.json' (Get-SnapshotJson 261030200 @('camera')) | Out-Null
        $r = Invoke-Script $script @('-VersionCode', '261030200', '-ArchiveRoot', $archive)
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code): $($r.Out)" }
        if ([System.IO.File]::ReadAllText((Join-Path $archive '261030200/fgs-types.json')) -ne $confirmed) { return 'the confirmed snapshot was overwritten' }
        if (Test-Path (Join-Path $archive '261030200/fgs-types.pending.json')) { return 'the pending file is still there' }
    }

    Assert-Case 'R4 no snapshot of the versionCode exits 1' {
        $r = Invoke-Script $script @('-VersionCode', '261030300', '-ArchiveRoot', $archive)
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
    }

    Assert-Case 'R5 a pending snapshot naming another versionCode exits 2 and stays pending' {
        New-Fixture 'archive/261030400/fgs-types.pending.json' (Get-SnapshotJson 261030999 @('dataSync')) | Out-Null
        $r = Invoke-Script $script @('-VersionCode', '261030400', '-ArchiveRoot', $archive)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if (-not (Test-Path (Join-Path $archive '261030400/fgs-types.pending.json'))) { return 'the pending file was consumed' }
        if (Test-Path (Join-Path $archive '261030400/fgs-types.json')) { return 'a mismatching snapshot became a baseline' }
    }

    Assert-Case 'R6 an unreadable pending snapshot exits 2' {
        New-Fixture 'archive/261030500/fgs-types.pending.json' 'not json' | Out-Null
        $r = Invoke-Script $script @('-VersionCode', '261030500', '-ArchiveRoot', $archive)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if (Test-Path (Join-Path $archive '261030500/fgs-types.json')) { return 'an unreadable snapshot became a baseline' }
    }

    Assert-Case 'R7 an absent archive root exits 2' {
        $r = Invoke-Script $script @('-VersionCode', '261030100', '-ArchiveRoot', (Join-Path $root 'no-such-archive'))
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
    }

    Assert-Case 'R8 retention writes only a pending snapshot; confirm promotes it; a re-retain keeps the confirmed one' {
        $roundArchive = Join-Path $root 'round-archive'
        $mapping = New-Fixture 'round/mapping.txt' "com.example.A -> a:`n"
        $manifest = New-Fixture 'round/AndroidManifest.xml' @'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="x">
  <application>
    <service android:name="svc.a" android:foregroundServiceType="dataSync|mediaPlayback" />
  </application>
</manifest>
'@
        $retainArgs = @('-Variant', 'standard', '-VersionCode', '261030600', '-Mapping', $mapping,
            '-ArchiveRoot', $roundArchive, '-MergedManifest', $manifest)
        $r = Invoke-Script $retain $retainArgs
        if ($r.Code -ne 0) { return "retention expected 0, got $($r.Code): $($r.Out)" }
        $dir = Join-Path $roundArchive '261030600'
        if (-not (Test-Path (Join-Path $dir 'fgs-types.pending.json'))) { return "no pending snapshot written: $($r.Out)" }
        if (Test-Path (Join-Path $dir 'fgs-types.json')) { return 'retention wrote a confirmed snapshot at build time' }
        $pending = [System.IO.File]::ReadAllText((Join-Path $dir 'fgs-types.pending.json')) | ConvertFrom-Json
        if ((@($pending.types) -join ',') -ne 'dataSync,mediaPlayback') { return "pending types wrong: $(@($pending.types) -join ',')" }

        $r = Invoke-Script $script @('-VersionCode', '261030600', '-ArchiveRoot', $roundArchive)
        if ($r.Code -ne 0) { return "confirm expected 0, got $($r.Code): $($r.Out)" }
        if (-not (Test-Path (Join-Path $dir 'fgs-types.json'))) { return 'confirm did not create fgs-types.json' }

        $r = Invoke-Script $retain $retainArgs
        if ($r.Code -ne 0) { return "re-retention expected 0, got $($r.Code): $($r.Out)" }
        if (Test-Path (Join-Path $dir 'fgs-types.pending.json')) { return 'a re-retention wrote a pending file beside the confirmed snapshot' }
    }
}
finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

if ($script:failures -gt 0) {
    Write-Host "confirm-fgs-snapshot.tests: FAIL (run=$script:caseCount skipped=0)" -ForegroundColor Red
    exit 1
}
Write-Host "confirm-fgs-snapshot.tests: PASS (run=$script:caseCount skipped=0)" -ForegroundColor Green
exit 0
