#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/release/assert-play-target-api-floor.ps1 (S4668).

.DESCRIPTION
    Every case writes a fixture tree (app_v2, wear and watchface build files) and a fixture floors
    file into a per-PID temp directory and passes -RepoRoot, -FloorsFile and -Today explicitly, so
    no case depends on the real build files or on the day it runs. The last case reads the real
    tree against the real floors file with a -Today one day after readOn.

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

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'assert-play-target-api-floor.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Host "assert-play-target-api-floor.tests: FAIL (run=0 skipped=0) - not found: $script" -ForegroundColor Red
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$root = Join-Path ([System.IO.Path]::GetTempPath()) "play-target-api-tests-$PID"
Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $root -Force | Out-Null

function New-Fixture {
    param([string] $Name, [string] $Text)
    $path = Join-Path $root $Name
    New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
    [System.IO.File]::WriteAllText($path, $Text)
    return $path
}

function New-FloorsText {
    param([string] $ReadOn = '2026-09-21', [int] $Phone = 36, [int] $Wear = 35)
    return @"
{
  "readOn": "$ReadOn",
  "source": "fixture",
  "floors": [
    { "formFactor": "phone", "label": "Phone and tablet", "targetSdk": $Phone, "appliesTo": "new apps and updates", "enforcedFrom": "2026-08-31" },
    { "formFactor": "wear", "label": "Wear OS", "targetSdk": $Wear, "appliesTo": "new apps and updates", "enforcedFrom": "2026-08-31" }
  ],
  "information": [ { "formFactor": "wear", "label": "Wear OS", "requirement": "64-bit native code", "enforcedFrom": "2026-09-15" } ]
}
"@
}

function New-PhoneText {
    param([string] $Default = 'targetSdk = 37', [string] $Standard = '', [string] $Extra = '')
    return @"
// targetSdk = 20 in a comment never counts
fun helper() { val s = "targetSdk = 1 {"; }
android {
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        $Default  // trailing comment
    }
    productFlavors {
        create("standard") {
            dimension = "version"
            $Standard
        }
        create("vr") {
            minSdk = 29
            targetSdk = 34
        }
    }
    $Extra
}
androidComponents { onVariants { } }
"@
}

function New-ModuleText {
    param([string] $Default = 'targetSdk = 36', [switch] $Flavors)
    $flavorBlock = if ($Flavors) { "    productFlavors {`n        create(`"standard`") { isDefault = true }`n        create(`"noLegal`") { versionNameSuffix = `"-NoLegal`" }`n    }" } else { '' }
    return @"
/* a block comment with targetSdk = 1 and { an unbalanced brace */
android {
    defaultConfig {
        minSdk = 28
        $Default
    }
$flavorBlock
}
"@
}

function New-Tree {
    param([string] $Name, [string] $Phone = (New-PhoneText), [string] $Wear = (New-ModuleText -Flavors), [string] $Face = (New-ModuleText), [switch] $NoFace)
    New-Fixture "$Name/app_v2/build.gradle.kts" $Phone | Out-Null
    New-Fixture "$Name/wear/build.gradle.kts" $Wear | Out-Null
    if (-not $NoFace) { New-Fixture "$Name/watchface/build.gradle.kts" $Face | Out-Null }
    return (Join-Path $root $Name)
}

$floors = New-Fixture 'floors.json' (New-FloorsText)
$fresh = '2026-09-22'

function Invoke-Check {
    param([string[]] $CheckArgs)
    # An array splatted in-process binds positionally, so the '-Name value' pairs become a hashtable.
    $named = @{}
    for ($i = 0; $i -lt $CheckArgs.Count; $i += 2) { $named[$CheckArgs[$i].TrimStart('-')] = $CheckArgs[$i + 1] }
    $global:LASTEXITCODE = 0
    $out = & $script @named *>&1 | Out-String
    return [pscustomobject]@{ Code = $LASTEXITCODE; Out = $out }
}

function Invoke-ChildCheck {
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
    Assert-Case 'C1 -Help exits 0 (child process)' {
        $r = Invoke-ChildCheck @('-Help')
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code)" }
    }

    Assert-Case 'C2 every artifact above its floor exits 0 and prints the floors with the date read' {
        $tree = New-Tree 'c2'
        $r = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $floors, '-Today', $fresh)
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'floors read 2026-09-21') { return "the date read is not printed: $($r.Out)" }
        if ($r.Out -notmatch 'floor Phone and tablet targetSdk 36' -or $r.Out -notmatch 'floor Wear OS targetSdk 35') { return "a floor is not printed: $($r.Out)" }
        if ($r.Out -notmatch 'info Wear OS 64-bit native code') { return "the 64-bit line is not printed: $($r.Out)" }
        if ($r.Out -notmatch "flavor 'vr' sets targetSdk 34.*not judged") { return "the vr override is not reported as unjudged: $($r.Out)" }
        if ($r.Out -notmatch 'assert-play-target-api-floor: PASS \(') { return "no PASS verdict: $($r.Out)" }
    }

    Assert-Case 'C3 targetSdk equal to the floor exits 0' {
        $tree = New-Tree 'c3' -Phone (New-PhoneText -Default 'targetSdk = 36') -Wear (New-ModuleText -Default 'targetSdk = 35' -Flavors) -Face (New-ModuleText -Default 'targetSdk = 35')
        $r = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $floors, '-Today', $fresh)
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code): $($r.Out)" }
    }

    Assert-Case 'C4 standard flavor override below the phone floor exits 1 and names it (child process)' {
        $tree = New-Tree 'c4' -Phone (New-PhoneText -Standard 'targetSdk = 35')
        $r = Invoke-ChildCheck @('-RepoRoot', $tree, '-FloorsFile', $floors, '-Today', $fresh)
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'phone app targetSdk 35 < phone floor 36') { return "the refusal does not name the phone app: $($r.Out)" }
        if ($r.Out -notmatch 'flavor standard override') { return "the override is not named as the origin: $($r.Out)" }
    }

    Assert-Case 'C5 watch app below the wear floor exits 1' {
        $tree = New-Tree 'c5' -Wear (New-ModuleText -Default 'targetSdk = 34' -Flavors)
        $r = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $floors, '-Today', $fresh)
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'watch app targetSdk 34 < wear floor 35') { return "the refusal does not name the watch app: $($r.Out)" }
    }

    Assert-Case 'C6 a missing build file exits 2' {
        $tree = New-Tree 'c6' -NoFace
        $r = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $floors, '-Today', $fresh)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'COULD NOT VERIFY.*watch face: build file not found') { return "the reason is not named: $($r.Out)" }
    }

    Assert-Case 'C7 a non-literal targetSdk exits 2' {
        $tree = New-Tree 'c7' -Phone (New-PhoneText -Default 'targetSdk = libs.versions.target.get().toInt()')
        $r = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $floors, '-Today', $fresh)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch "not a literal 'targetSdk = <integer>'") { return "the reason is not named: $($r.Out)" }
    }

    Assert-Case 'C8 a targetSdk outside defaultConfig and the flavors exits 2' {
        $tree = New-Tree 'c8' -Phone (New-PhoneText -Extra "buildTypes { create(`"release`") { targetSdk = 30 } }")
        $r = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $floors, '-Today', $fresh)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'outside defaultConfig and every flavor block') { return "the reason is not named: $($r.Out)" }
    }

    Assert-Case 'C9 a module declaring no targetSdk exits 2 as not established' {
        $tree = New-Tree 'c9' -Face (New-ModuleText -Default 'versionCode = 1')
        $r = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $floors, '-Today', $fresh)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'targetSdk not declared.*not established') { return "the reason is not named: $($r.Out)" }
    }

    Assert-Case 'C10 floors read more than 30 days ago exit 3 with the age' {
        $tree = New-Tree 'c10'
        $r = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $floors, '-Today', '2026-10-22')
        if ($r.Code -ne 3) { return "expected 3, got $($r.Code): $($r.Out)" }
        if ($r.Out -notmatch 'PASS WITH ADVISORIES \(floors read 31 day\(s\) ago.*update readOn') { return "the age and the remedy are not printed: $($r.Out)" }
    }

    Assert-Case 'C11 exactly 30 days is not stale' {
        $tree = New-Tree 'c11'
        $r = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $floors, '-Today', '2026-10-21')
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code): $($r.Out)" }
    }

    Assert-Case 'C12 stale floors and a violation exit 1' {
        $tree = New-Tree 'c12' -Face (New-ModuleText -Default 'targetSdk = 33')
        $r = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $floors, '-Today', '2026-12-01')
        if ($r.Code -ne 1) { return "expected 1, got $($r.Code): $($r.Out)" }
    }

    Assert-Case 'C13 an unreadable floors file exits 2' {
        $tree = New-Tree 'c13'
        $bad = New-Fixture 'c13/floors.json' 'not json'
        $r = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $bad, '-Today', $fresh)
        if ($r.Code -ne 2) { return "expected 2, got $($r.Code): $($r.Out)" }
        $noWear = New-Fixture 'c13/no-wear.json' '{"readOn":"2026-09-21","floors":[{"formFactor":"phone","targetSdk":36,"enforcedFrom":"2026-08-31"}]}'
        $r2 = Invoke-Check @('-RepoRoot', $tree, '-FloorsFile', $noWear, '-Today', $fresh)
        if ($r2.Code -ne 2 -or $r2.Out -notmatch "no 'wear' floor") { return "a floors file without a wear floor: expected 2 naming it, got $($r2.Code): $($r2.Out)" }
    }

    Assert-Case 'C14 the live tree meets the recorded floors a day after they were read' {
        $r = Invoke-Check @('-Today', '2026-09-22')
        if ($r.Code -ne 0) { return "expected 0, got $($r.Code): $($r.Out)" }
    }
}
finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

if ($script:failures -gt 0) {
    Write-Host "assert-play-target-api-floor.tests: FAIL (run=$script:caseCount skipped=0)" -ForegroundColor Red
    exit 1
}
Write-Host "assert-play-target-api-floor.tests: PASS (run=$script:caseCount skipped=0)" -ForegroundColor Green
exit 0
