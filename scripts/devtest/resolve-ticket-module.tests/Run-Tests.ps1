#requires -Version 7.0
<#
.SYNOPSIS
    S2611 contract suite for scripts/devtest/resolve-ticket-module.ps1.

.DESCRIPTION
    Subject: scripts/devtest/resolve-ticket-module.ps1

    Why it exists. This script picks the DEVICE that `/spec-sweep` and `/spec-test-device` run a
    ticket on. Its failure mode is not a crash - it is a well-formed verdict about the wrong
    device, which is exactly what S2600 recorded when a watch emulator answered a phone-only
    ticket and the answer went into a spec unchallenged. Every case below is one sentence of that
    failure: a wear ticket must not resolve to the phone, a phone ticket must not resolve to the
    watch, and a ticket spanning both must refuse rather than pick.

    Hermetic: a throwaway tree under temp/scratch/ carrying the two probe scan roots and Kotlin
    fixtures, passed to the subject with -RepoRoot. Nothing here reads which tickets happen to be
    parked in the live tree, because a suite that did would pass vacuously the week none are.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/resolve-ticket-module.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0 - every case passed
      1 - at least one case failed
      2 - the suite could not run (the subject script is missing)
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$suiteDir = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $suiteDir '..\..\..')).Path
$subject = Join-Path $repoRoot 'scripts/devtest/resolve-ticket-module.ps1'

if (-not (Test-Path -LiteralPath $subject)) {
    Write-Error "resolve-ticket-module.tests: cannot run - $subject is missing." -ErrorAction Continue
    exit 2
}

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
    }
    else {
        Write-Host "FAIL | $Label -> expected: $Expected | actual: $Actual"
        $script:failed++
    }
}

function Assert-Match {
    param([string]$Pattern, [string]$Actual, [string]$Label)
    if ($Actual -match $Pattern) {
        Write-Host "PASS | $Label"
        $script:passed++
    }
    else {
        Write-Host "FAIL | $Label -> expected to match: $Pattern | actual: $Actual"
        $script:failed++
    }
}

# ---------- the throwaway tree (Rule 10.1: scratch under temp/) ----------
$runDir = Join-Path $repoRoot ("temp/scratch/resolve-ticket-module-suite-{0}" -f $PID)
Get-ChildItem -Path (Join-Path $repoRoot 'temp/scratch') -Filter 'resolve-ticket-module-suite-*' -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }

# The scan roots are the module directories the profile names, so the fake tree mirrors them.
$phoneDir = Join-Path $runDir 'app_v2/src/main/java'
$watchDir = Join-Path $runDir 'wear/src/main/java'
New-Item -ItemType Directory -Path $phoneDir -Force | Out-Null
New-Item -ItemType Directory -Path $watchDir -Force | Out-Null

# S9001 phone-only, S9002 watch-only, S9003 both. S9004 exists nowhere on purpose.
Set-Content -LiteralPath (Join-Path $phoneDir 'PhoneScreen.kt') -Encoding utf8 -Value @'
package fake
class PhoneScreen {
    fun onOpen() {
        Timber.d("S9001: phone flow entered")
        Timber.d("S9003: shared flow entered on the phone")
    }
}
'@
Set-Content -LiteralPath (Join-Path $watchDir 'WatchScreen.kt') -Encoding utf8 -Value @'
package fake
class WatchScreen {
    fun onOpen() {
        Timber.d("S9002: watch flow entered")
        Timber.d("S9003: shared flow entered on the watch")
    }
}
'@

function Invoke-Subject {
    param([string]$Id)
    $out = & pwsh -NoProfile -File $subject -Id $Id -RepoRoot $runDir -Json 2>&1
    return [pscustomobject]@{ Code = $LASTEXITCODE; Text = ($out | Out-String).Trim() }
}

try {
    # 1. A phone ticket resolves to the phone. Without this the watch may answer for it - S2600.
    $r = Invoke-Subject -Id 'S9001'
    Assert-Equal 0 $r.Code 'phone-only ticket exits 0'
    Assert-Equal 'app_v2' (($r.Text | ConvertFrom-Json).module) 'phone-only ticket resolves to app_v2'

    # 2. A watch ticket resolves to the watch. This is the half no caller could reach before S2611:
    # every call site named the phone package, so a wear ticket was tested on a phone or on luck.
    $r = Invoke-Subject -Id 'S9002'
    Assert-Equal 0 $r.Code 'watch-only ticket exits 0'
    Assert-Equal 'wear' (($r.Text | ConvertFrom-Json).module) 'watch-only ticket resolves to wear'

    # 3. Probes on both sides refuse. Answering with either one would silently leave the other
    # module untested while the ticket reads as covered.
    $r = Invoke-Subject -Id 'S9003'
    Assert-Equal 3 $r.Code 'ticket spanning both modules exits 3'
    Assert-Match 'app_v2' $r.Text 'the refusal names the phone module'
    Assert-Match 'wear' $r.Text 'the refusal names the watch module'

    # 4. No probe anywhere defaults to the phone AND says so. The reason is the load-bearing half:
    # a bare 'app_v2' is indistinguishable from a measured one.
    $r = Invoke-Subject -Id 'S9004'
    Assert-Equal 0 $r.Code 'ticket with no probe exits 0'
    $payload = $r.Text | ConvertFrom-Json
    Assert-Equal 'app_v2' $payload.module 'ticket with no probe defaults to app_v2'
    Assert-Match 'no probe' $payload.reason 'the default answer states that nothing was found'
    Assert-Equal 0 @($payload.probeModules).Count 'the default answer claims no probe module'

    # 5. A malformed id is refused by parameter binding rather than scanned for.
    & pwsh -NoProfile -File $subject -Id 'not-a-ticket' -RepoRoot $runDir *> $null
    Assert-Match '^([1-9]\d*)$' "$LASTEXITCODE" 'a malformed ticket id is refused with a non-zero exit'
}
finally {
    Remove-Item -LiteralPath $runDir -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host "resolve-ticket-module.tests: $script:passed passed, $script:failed failed"
if ($script:failed -gt 0) { exit 1 }
exit 0
