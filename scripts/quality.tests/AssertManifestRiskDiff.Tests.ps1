#requires -Version 7.0
<#
.SYNOPSIS
    S3371: contract suite for scripts/quality/assert-manifest-risk-diff.ps1.

.DESCRIPTION
    This is the gate most likely to be argued with - it refuses a manifest line somebody has
    already decided to write - so its refusals are demonstrated here rather than asserted. Fixture
    manifests and a fixture registry, so every case runs the real verdict path against a tree this
    suite owns.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every test passed.
      1  at least one test failed.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:pass = 0
$script:fail = 0

function Test-Case([string]$Name, [scriptblock]$Body) {
    try {
        & $Body
        $script:pass++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    }
    catch {
        $script:fail++
        Write-Host "  FAIL  $Name - $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Assert-Equal($Expected, $Actual, [string]$What) {
    if ($Expected -ne $Actual) { throw "$What - expected: $Expected | actual: $Actual" }
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gate = Join-Path $repoRoot 'scripts/quality/assert-manifest-risk-diff.ps1'
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("manifest-risk-fixture-{0}" -f $PID)
$mainDir = Join-Path $fixtureRoot 'app_v2/src/main'
$registry = Join-Path $fixtureRoot 'registry.jsonl'
$manifestRel = 'app_v2/src/main/AndroidManifest.xml'

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $mainDir -Force | Out-Null
}

function Set-Manifest([string[]]$Body) {
    $lines = @('<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">') + $Body + @('</manifest>')
    Set-Content -LiteralPath (Join-Path $mainDir 'AndroidManifest.xml') -Value $lines -Encoding UTF8
}

function Set-Registry([string[]]$Rows) {
    Set-Content -LiteralPath $registry -Value $Rows -Encoding UTF8
}

function Invoke-Gate([string]$Changed) {
    $argv = @('-NoProfile', '-NonInteractive', '-File', $gate, '-Gate', '-RepoRoot', $fixtureRoot, '-RegistryPath', $registry)
    if ($Changed) { $argv += @('-ChangedFiles', $Changed) }
    $output = & $pwshExe @argv 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

# The merged-manifest sweep is a THIRD selection path, and the only one that judges the flavor
# list as a claim rather than as documentation. It is exercised here because CI is the only place
# it otherwise runs, and a sweep that silently judged nothing would report the same green.
function Invoke-Merged([string]$ManifestPath, [string]$FlavorName) {
    $argv = @('-NoProfile', '-NonInteractive', '-File', $gate, '-Gate', '-RepoRoot', $fixtureRoot, '-RegistryPath', $registry, '-Manifests', $ManifestPath, '-Flavor', $FlavorName)
    $output = & $pwshExe @argv 2>&1 | Out-String
    return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Output = $output }
}

$cameraRow = '{"kind":"permission","key":"android.permission.CAMERA","modules":["app_v2"],"flavors":["standard"],"sources":["app_v2/src/main/AndroidManifest.xml"],"justification":"The in-app capture screen photographs a document straight into the sorted folder."}'

try {
    Test-Case 'a permission with a registry row passes' {
        Reset-Fixture
        Set-Registry @($cameraRow)
        Set-Manifest @('    <uses-permission android:name="android.permission.CAMERA" />')
        $r = Invoke-Gate $manifestRel
        Assert-Equal 0 $r.ExitCode "justified permission verdict - output: $($r.Output)"
    }

    Test-Case 'a new permission absent from the registry is refused by its key' {
        Reset-Fixture
        Set-Registry @($cameraRow)
        Set-Manifest @(
            '    <uses-permission android:name="android.permission.CAMERA" />',
            '    <uses-permission android:name="android.permission.READ_SMS" />'
        )
        $r = Invoke-Gate $manifestRel
        Assert-Equal 1 $r.ExitCode 'unjustified permission verdict'
        if ($r.Output -notmatch 'READ_SMS') { throw "the refusal did not name the key - output: $($r.Output)" }
    }

    Test-Case 'an exported component added without a row is caught' {
        Reset-Fixture
        Set-Registry @($cameraRow)
        Set-Manifest @(
            '    <application>',
            '        <activity android:name=".ShareTargetActivity" android:exported="true" />',
            '    </application>'
        )
        $r = Invoke-Gate $manifestRel
        Assert-Equal 1 $r.ExitCode 'exported component verdict'
        if ($r.Output -notmatch 'ShareTargetActivity') { throw "the refusal did not name the component - output: $($r.Output)" }
    }

    Test-Case 'a component that is NOT exported is not a risk declaration' {
        Reset-Fixture
        Set-Registry @($cameraRow)
        Set-Manifest @(
            '    <application>',
            '        <activity android:name=".InternalActivity" android:exported="false" />',
            '    </application>'
        )
        $r = Invoke-Gate $manifestRel
        Assert-Equal 0 $r.ExitCode "non-exported component verdict - output: $($r.Output)"
    }

    Test-Case 'a registry row without a justification is refused' {
        Reset-Fixture
        Set-Registry @('{"kind":"permission","key":"android.permission.CAMERA","modules":["app_v2"],"flavors":["standard"],"justification":""}')
        Set-Manifest @('    <uses-permission android:name="android.permission.CAMERA" />')
        $r = Invoke-Gate $manifestRel
        Assert-Equal 1 $r.ExitCode 'reasonless row verdict'
        if ($r.Output -notmatch 'no justification') { throw "the refusal did not say why - output: $($r.Output)" }
    }

    Test-Case 'a registry row naming neither a flavor nor a source set is refused' {
        Reset-Fixture
        Set-Registry @('{"kind":"permission","key":"android.permission.CAMERA","modules":["app_v2"],"flavors":[],"justification":"Capture screen."}')
        Set-Manifest @('    <uses-permission android:name="android.permission.CAMERA" />')
        $r = Invoke-Gate $manifestRel
        Assert-Equal 1 $r.ExitCode 'scopeless row verdict'
        if ($r.Output -notmatch 'neither a flavor nor') { throw "the refusal did not say why - output: $($r.Output)" }
    }

    Test-Case 'a source set named instead of a flavor is accepted' {
        Reset-Fixture
        Set-Registry @('{"kind":"permission","key":"android.permission.CAMERA","modules":["app_v2"],"flavors":[],"sourceSets":["standardEdgeTile"],"justification":"Gated by a build property, so it reaches no flavor in the current tree."}')
        Set-Manifest @('    <uses-permission android:name="android.permission.CAMERA" />')
        $r = Invoke-Gate $manifestRel
        Assert-Equal 0 $r.ExitCode "source-set scope verdict - output: $($r.Output)"
    }

    Test-Case 'a foreground service type is judged per type, not per attribute' {
        Reset-Fixture
        Set-Registry @('{"kind":"foregroundServiceType","key":"mediaPlayback","modules":["app_v2"],"flavors":["standard"],"justification":"Audio keeps playing while the screen is off."}')
        Set-Manifest @(
            '    <application>',
            '        <service android:name=".PlayerService" android:foregroundServiceType="mediaPlayback|camera" />',
            '    </application>'
        )
        $r = Invoke-Gate $manifestRel
        Assert-Equal 1 $r.ExitCode 'split service type verdict'
        if ($r.Output -notmatch 'camera') { throw "the refusal did not name the unjustified type - output: $($r.Output)" }
    }

    Test-Case 'a declaration a flavor overlay removes is not a risk it carries' {
        Reset-Fixture
        Set-Registry @($cameraRow)
        Set-Manifest @(
            '    <uses-permission android:name="android.permission.CAMERA" />',
            '    <uses-permission android:name="android.permission.READ_SMS" tools:node="remove" />'
        )
        $r = Invoke-Gate $manifestRel
        Assert-Equal 0 $r.ExitCode "removed declaration verdict - output: $($r.Output)"
    }

    Test-Case 'a change that touches no manifest passes trivially' {
        Reset-Fixture
        Set-Registry @($cameraRow)
        Set-Manifest @('    <uses-permission android:name="android.permission.READ_SMS" />')
        $r = Invoke-Gate 'docs/manifest-risk-registry.jsonl'
        Assert-Equal 0 $r.ExitCode "registry-only change verdict - output: $($r.Output)"
    }

    Test-Case 'a merged manifest is judged against the flavor it belongs to' {
        Reset-Fixture
        Set-Registry @($cameraRow)
        Set-Manifest @('    <uses-permission android:name="android.permission.CAMERA" />')
        $r = Invoke-Merged $manifestRel 'standard'
        Assert-Equal 0 $r.ExitCode "merged-manifest verdict - output: $($r.Output)"
        if ($r.Output -notmatch "merged manifests of flavor 'standard'") { throw "the run did not say what it judged - output: $($r.Output)" }
    }

    Test-Case 'a declaration reaching a flavor its registry row does not list is refused' {
        Reset-Fixture
        Set-Registry @($cameraRow)
        Set-Manifest @('    <uses-permission android:name="android.permission.CAMERA" />')
        $r = Invoke-Merged $manifestRel 'lite'
        Assert-Equal 1 $r.ExitCode 'flavor-coverage verdict'
        if ($r.Output -notmatch 'reaches flavor') { throw "the refusal did not name the mismatch - output: $($r.Output)" }
    }

    Test-Case 'a missing registry cannot verify rather than passing' {
        Reset-Fixture
        Set-Manifest @('    <uses-permission android:name="android.permission.READ_SMS" />')
        $r = Invoke-Gate $manifestRel
        Assert-Equal 2 $r.ExitCode 'missing registry verdict'
    }

    Test-Case 'with no changed set the whole tree is judged' {
        Reset-Fixture
        Set-Registry @($cameraRow)
        Set-Manifest @('    <uses-permission android:name="android.permission.READ_SMS" />')
        $r = Invoke-Gate $null
        Assert-Equal 1 $r.ExitCode 'project-wide verdict'
        if ($r.Output -notmatch 'both modules') { throw "the run did not say what it scanned - output: $($r.Output)" }
    }
}
finally {
    Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ("AssertManifestRiskDiff.Tests: {0} passed, {1} failed." -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
