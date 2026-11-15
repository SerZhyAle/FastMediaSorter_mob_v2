#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/release/assert-required-features-vs-previous.ps1 (S4668).

.DESCRIPTION
    Every case feeds fixture badging dumps, fixture archive folders and fixture tool paths from a
    per-PID temp directory and passes -ArchiveRoot and -WorkDir explicitly, so no case reads the real
    archive or writes under the repository. No case runs bundletool or aapt2: the tool cases stop at
    resolution, before either would be launched.

    Cases run the script in this process (`&`), whose `exit N` ends the script and sets
    $LASTEXITCODE; two cases run it in a child pwsh to prove the process exit code too. A child per
    case would cost more than the 5 s a suite may take.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'assert-required-features-vs-previous.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Host "assert-required-features-vs-previous.tests: FAIL (run=0 skipped=0) - not found: $script" -ForegroundColor Red
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$root = Join-Path ([System.IO.Path]::GetTempPath()) "required-features-tests-$PID"
Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $root -Force | Out-Null
$work = Join-Path $root 'work'

function New-Fixture {
    param([string] $Name, [string] $Text)
    $path = Join-Path $root $Name
    New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
    [System.IO.File]::WriteAllText($path, $Text)
    return $path
}

function New-BadgingText {
    param([string] $VersionCode = '261100300', [string[]] $Lines)
    return (@(
            "package: name='com.sza.fastmediasorter' versionCode='$VersionCode' versionName='2.61.1103.00' platformBuildVersionName='17'"
            "sdkVersion:'26'"
            "targetSdkVersion:'37'"
            "uses-permission: name='android.permission.INTERNET'"
        ) + $Lines + @(
            "application-label:'FastMediaSorter'"
            'supports-screens: ''small'' ''normal'' ''large'' ''xlarge'''
        )) -join "`n"
}

$baseLines = @(
    "uses-feature: name='android.hardware.touchscreen'"
    "uses-feature-not-required: name='android.hardware.camera'"
    "feature-group: label=''"
    "  uses-feature: name='android.hardware.faketouch'"
    "  uses-implied-feature: name='android.hardware.faketouch' reason='default feature for all apps'"
)
$previous = New-Fixture 'previous.txt' (New-BadgingText -VersionCode '261000200' -Lines $baseLines)

function Invoke-Check {
    param([string[]] $CheckArgs)
    # An array splatted in-process binds positionally, so the '-Name value' pairs become a hashtable.
    $named = @{ WorkDir = $work }
    for ($i = 0; $i -lt $CheckArgs.Count; $i += 2) { $named[$CheckArgs[$i].TrimStart('-')] = $CheckArgs[$i + 1] }
    $global:LASTEXITCODE = 0
    $out = & $script @named *>&1 | Out-String
    return [pscustomobject]@{ Code = $LASTEXITCODE; Out = $out }
}

function Invoke-ChildCheck {
    param([string[]] $CheckArgs)
    $out = & $pwshExe -NoProfile -File $script @CheckArgs -WorkDir $work 2>&1 | Out-String
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
    Assert-Case 'C1 -Help exits 0 (child process)' {
        & $pwshExe -NoProfile -File $script -Help 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { return "expected 0, got $LASTEXITCODE" }
    }

    Assert-Case 'C2 the same required set exits 0 and prints both sets' {
        $n = New-Fixture 'c2/new.txt' (New-BadgingText -Lines $baseLines)
        $r = Invoke-Check @('-BadgingFile', $n, '-PreviousBadgingFile', $previous)
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'new required set: android\.hardware\.faketouch, android\.hardware\.touchscreen') { return "the new set is not printed: $($r.Out)" }
        if ($r.Out -notmatch 'previous .*versionCode 261000200') { return "the previous versionCode is not printed: $($r.Out)" }
    }

    Assert-Case 'C3 a newly required implied feature exits 1 and names its trigger (child process)' {
        $lines = $baseLines + @(
            "uses-feature: name='android.hardware.screen.portrait'"
            "uses-implied-feature: name='android.hardware.screen.portrait' reason='one or more activities have specified a portrait orientation'"
        )
        $n = New-Fixture 'c3/new.txt' (New-BadgingText -Lines $lines)
        $r = Invoke-ChildCheck @('-BadgingFile', $n, '-PreviousBadgingFile', $previous)
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch "'android\.hardware\.screen\.portrait' is newly required \(implied: one or more activities have specified a portrait orientation\)") { return "the trigger is not named: $($r.Out)" }
        if ($r.Out -notmatch 'assert-required-features-vs-previous: FAIL \(1 newly required') { return "no FAIL verdict: $($r.Out)" }
    }

    Assert-Case 'C4 a newly required explicit feature in the older bare form exits 1' {
        $n = New-Fixture 'c4/new.txt' (New-BadgingText -Lines ($baseLines + @("uses-feature:'android.hardware.nfc'")))
        $r = Invoke-Check @('-BadgingFile', $n, '-PreviousBadgingFile', $previous)
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch "'android\.hardware\.nfc' is newly required \(declared in the manifest") { return "the explicit feature is not named: $($r.Out)" }
    }

    Assert-Case 'C5 a feature moved from not-required to required exits 1' {
        $lines = @($baseLines | Where-Object { $_ -notmatch 'camera' }) + @("uses-feature: name='android.hardware.camera'")
        $n = New-Fixture 'c5/new.txt' (New-BadgingText -Lines $lines)
        $r = Invoke-Check @('-BadgingFile', $n, '-PreviousBadgingFile', $previous)
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
    }

    Assert-Case 'C6 a feature no longer required exits 0 and is reported as information' {
        $lines = @($baseLines | Where-Object { $_ -notmatch 'touchscreen' }) + @("uses-feature: name='android.hardware.touchscreen' required='false'")
        $n = New-Fixture 'c6/new.txt' (New-BadgingText -Lines $lines)
        $r = Invoke-Check @('-BadgingFile', $n, '-PreviousBadgingFile', $previous)
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch "info 'android\.hardware\.touchscreen' is no longer required") { return "the removal is not reported: $($r.Out)" }
    }

    Assert-Case 'C7 a dump that is not badging output exits 2' {
        $n = New-Fixture 'c7/new.txt' "ERROR: dump failed`nuses-feature: name='android.hardware.wifi'"
        $r = Invoke-Check @('-BadgingFile', $n, '-PreviousBadgingFile', $previous)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'COULD NOT VERIFY \(no package: line') { return "the reason is not named: $($r.Out)" }
    }

    Assert-Case 'C8 a missing badging file exits 2' {
        $r = Invoke-Check @('-BadgingFile', (Join-Path $root 'absent.txt'), '-PreviousBadgingFile', $previous)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
    }

    Assert-Case 'C9 the newest retained folder below the judged code holds no bundle: exit 2 naming it' {
        $archive = Join-Path $root 'c9/archive'
        New-Fixture 'c9/archive/260900100/manifest.json' '{}' | Out-Null
        New-Fixture 'c9/archive/260900100/app_v2-standard-release.aab' 'older bundle' | Out-Null
        New-Fixture 'c9/archive/261000200/manifest.json' '{}' | Out-Null
        New-Fixture 'c9/archive/261000200/standard-deobfuscation.zip' 'payload' | Out-Null
        # The judged build's own folder, retained before the Play commit, is never its baseline.
        New-Fixture 'c9/archive/261100300/app_v2-standard-release.aab' 'this build' | Out-Null
        $n = New-Fixture 'c9/new.txt' (New-BadgingText -Lines $baseLines)
        $r = Invoke-Check @('-BadgingFile', $n, '-ArchiveRoot', $archive)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch '261000200 holds 0 usable bundle\(s\)') { return "the previous release folder and its missing bundle are not named: $($r.Out)" }
    }

    Assert-Case 'C10 no retained folder below the judged code exits 2' {
        $archive = Join-Path $root 'c10/archive'
        New-Item -ItemType Directory -Path (Join-Path $archive '261100300') -Force | Out-Null
        $n = New-Fixture 'c10/new.txt' (New-BadgingText -Lines $baseLines)
        $r = Invoke-Check @('-BadgingFile', $n, '-ArchiveRoot', $archive)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'no retained release folder below versionCode 261100300') { return "the reason is not named: $($r.Out)" }
    }

    Assert-Case 'C11 a previous bundle that does not exist exits 2' {
        $n = New-Fixture 'c11/new.txt' (New-BadgingText -Lines $baseLines)
        $r = Invoke-Check @('-BadgingFile', $n, '-Previous', (Join-Path $root 'absent.aab'))
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'previous bundle not found') { return "the reason is not named: $($r.Out)" }
    }

    Assert-Case 'C12 a bundle with an absent aapt2 exits 2 naming aapt2 before any tool runs' {
        $aab = New-Fixture 'c12/new.aab' 'not a bundle'
        $jar = New-Fixture 'c12/bundletool.jar' 'not a jar'
        $r = Invoke-Check @('-Bundle', $aab, '-PreviousBadgingFile', $previous, '-Bundletool', $jar, '-Aapt2', (Join-Path $root 'absent/aapt2.exe'))
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'COULD NOT VERIFY \(aapt2 not found') { return "aapt2 is not named: $($r.Out)" }
    }

    Assert-Case 'C13 a bundle with an absent bundletool exits 2 naming bundletool' {
        $aab = New-Fixture 'c13/new.aab' 'not a bundle'
        $aapt = New-Fixture 'c13/aapt2.exe' 'not an exe'
        $r = Invoke-Check @('-Bundle', $aab, '-PreviousBadgingFile', $previous, '-Aapt2', $aapt, '-Bundletool', (Join-Path $root 'absent/bundletool.jar'))
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'COULD NOT VERIFY \(bundletool not found') { return "bundletool is not named: $($r.Out)" }
    }
}
finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

if ($script:failures -gt 0) {
    Write-Host "assert-required-features-vs-previous.tests: FAIL (run=$script:caseCount skipped=0)" -ForegroundColor Red
    exit 1
}
Write-Host "assert-required-features-vs-previous.tests: PASS (run=$script:caseCount skipped=0)" -ForegroundColor Green
exit 0
