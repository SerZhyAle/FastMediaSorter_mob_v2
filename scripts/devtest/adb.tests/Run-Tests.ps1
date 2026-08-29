#requires -Version 7.0
<#
.SYNOPSIS
    S2088 contract suite for the machine-readable (-Json) output of `adb.ps1`.

.DESCRIPTION
    Subject: scripts/devtest/adb.ps1

    Hermetic: no device, no network, no real adb. The suite drops a stub named `adb` on PATH,
    blanks ANDROID_HOME and ANDROID_SDK_ROOT so `Find-Adb` picks it up, and runs the real
    scripts/devtest/adb.ps1 as a child process exactly the way an agent runs it. Everything the
    script would learn from a device comes from stub/adb-stub.ps1 and fixtures/. All artifacts land
    in a per-run directory under temp/scratch/ and are removed at the end.

    Why the script is driven as a process rather than dot-sourced or refactored (S2088 ADR-1): the
    thing under test is the whole shipped path - parameter binding, device selection, payload
    serialization and the process exit code. S2079 was a serialization defect that killed only the
    -Json branch while the human branch stayed healthy, so a suite that calls an extracted result
    builder would have missed exactly the failure this suite exists for.

    Three groups of cases:
      contract  every verb with an `if ($Json)` branch runs to it; the object's shape is asserted
      failure   every reachable Fail code; the process exit code AND the JSON exitCode are asserted
                together, because automation reads one and a calling script reads the other
      ratchet   the verbs adb.ps1 actually has are re-derived from its switch and compared with the
                verbs driven above, so a new verb added without a case turns this suite red

    Measured 2026-08-27: 36 verb invocations, 257 assertions, 46.5 s. Almost all of that is process
    startup - one child pwsh per invocation plus one per stub call - not work. Keep it in this range:
    a suite nobody can afford is switched off, and then it is worth nothing. The lever if it ever
    has to shrink is the stub, which spawns pwsh per adb call; the verb count is not negotiable,
    because a verb dropped to save time is a verb back in the state that produced S2088.

    Sensitivity, measured the same day - a case that cannot fail proves nothing:
      - S2079 reintroduced in adb.ps1 -> both clip-check cases go red (exit 1, no JSON at all).
        Both halves of that fix have to be reverted to reproduce it: `New-Object` for the list AND
        `@($findings)` around it. Reverting either alone still passes, because the throw needs the
        PSObject wrapper that only `New-Object` adds. A regression case aimed at the expression
        alone would have been permanently green.
      - one verb case removed -> the ratchet fails and names the verb ("actual: push").

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/adb.tests/Run-Tests.ps1

    Exit codes:
      0 - every case passed
      1 - at least one case failed
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$suiteDir = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $suiteDir '..\..\..')).Path
$adbScript = Join-Path $repoRoot 'scripts/devtest/adb.ps1'

$script:passed = 0
$script:failed = 0
$script:drivenVerbs = [System.Collections.Generic.HashSet[string]]::new()

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
    } else {
        Write-Host "FAIL | $Label -> expected: $Expected | actual: $Actual"
        $script:failed++
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Label, [string]$Actual = '')
    Assert-Equal $true $Condition ($Label + $(if ($Actual) { " (actual: $Actual)" } else { '' }))
}

# ---------- the run directory ----------
# Rule 10.1: scratch under temp/. One directory per run, holding the stub, the fixtures and every
# artifact the verbs produce, so nothing this suite does can reach a real working file.
$runDir = Join-Path $repoRoot ("temp/scratch/adb-json-suite-{0}" -f $PID)
# Sweep what earlier runs left behind. The cleanup at the bottom cannot run when a case throws
# instead of asserting - $ErrorActionPreference is Stop - so a red run leaks its directory by
# construction, and only the NEXT run is in a position to remove it. Observed 2026-08-27: one
# directory survived a run that died on a StrictMode property access.
Get-ChildItem -Path (Join-Path $repoRoot 'temp/scratch') -Filter 'adb-json-suite-*' -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }
if (Test-Path -LiteralPath $runDir) { Remove-Item -LiteralPath $runDir -Recurse -Force }
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
Copy-Item -Path (Join-Path $suiteDir 'stub') -Destination (Join-Path $runDir 'stub') -Recurse -Force
Copy-Item -Path (Join-Path $suiteDir 'fixtures') -Destination (Join-Path $runDir 'fixtures') -Recurse -Force
$outDir = Join-Path $runDir 'out'
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$missFile = Join-Path $runDir 'stub-misses.txt'
# `install` needs a file that exists; its contents never matter, only the Test-Path.
$fakeApk = Join-Path $runDir 'fake.apk'
Set-Content -LiteralPath $fakeApk -Value 'not really an apk' -Encoding UTF8

$treeClean    = Join-Path $runDir 'fixtures/tree_watch_clean.xml'
$treeOffGlass = Join-Path $runDir 'fixtures/tree_watch_offglass.xml'

# ---------- the seam ----------
# Find-Adb reads ANDROID_HOME and ANDROID_SDK_ROOT BEFORE PATH, so both must be blanked or the
# child finds the developer machine's real adb.exe and the suite stops being hermetic.
$env:ANDROID_HOME     = ''
$env:ANDROID_SDK_ROOT = ''
$env:PATH             = (Join-Path $runDir 'stub') + [System.IO.Path]::PathSeparator + $env:PATH
$env:FMS_STUB_HOME    = $runDir

$stubDefaults = @{
    FMS_STUB_DEVICES  = 'emulator-5554'
    FMS_STUB_PACKAGES = 'com.sza.fastmediasorter.debug'
    FMS_STUB_TREE     = $treeClean
    FMS_STUB_WM_SIZE  = '1080x2400'
    FMS_STUB_RADIUS   = '0'
    FMS_STUB_SECURE   = '0'
    FMS_STUB_WATCH    = '0'
}

# Run one verb through the real adb.ps1 and bring back the process exit code plus the parsed object.
# Returns .json = $null when stdout was not a single JSON object - which is itself a contract
# violation the caller asserts on, not an error to swallow.
function Invoke-Verb {
    param(
        [string[]]$VerbArgs,
        [hashtable]$Stub = @{},
        [switch]$NoOutDir
    )
    foreach ($k in $stubDefaults.Keys) {
        $v = if ($Stub.ContainsKey($k)) { $Stub[$k] } else { $stubDefaults[$k] }
        Set-Item -Path "Env:$k" -Value $v
    }
    $script:drivenVerbs.Add($VerbArgs[0]) | Out-Null

    $callArgs = @('-NoProfile', '-File', $adbScript) + $VerbArgs + @('-Json')
    if (-not $NoOutDir) { $callArgs += @('-OutDir', $outDir) }
    $stderrFile = Join-Path $runDir 'stderr.txt'
    $stdout = & pwsh @callArgs 2>$stderrFile
    $code = $LASTEXITCODE

    $text = ($stdout | Out-String).Trim()
    $obj = $null
    try { $obj = $text | ConvertFrom-Json } catch { $obj = $null }
    return [pscustomobject]@{
        exit   = $code
        stdout = $text
        json   = $obj
        stderr = (Get-Content -LiteralPath $stderrFile -Raw -ErrorAction SilentlyContinue)
    }
}

# Every case asserts the envelope the same way before looking at its own payload: a verb that
# answers with valid JSON of the wrong shape is the failure mode this suite is for.
function Assert-Envelope {
    param($Result, [string]$Verb, [bool]$Ok, [int]$ExitCode)
    $label = "$Verb -Json"
    if ($null -eq $Result.json) {
        Assert-Equal 'a single JSON object' "exit $($Result.exit), stdout: $($Result.stdout)" "${label}: stdout parses as JSON"
        return $false
    }
    Assert-Equal $ExitCode $Result.exit             "${label}: process exit code"
    Assert-Equal $ExitCode $Result.json.exitCode    "${label}: JSON exitCode matches the process"
    Assert-Equal $Ok       $Result.json.ok          "${label}: ok"
    Assert-Equal $Verb     $Result.json.verb        "${label}: verb echoed back"
    # Report the payload assertions as skipped rather than letting them throw: a verb that failed
    # where the case expected success carries data = null, and dereferencing it under StrictMode
    # ends the whole run at the first such case instead of listing every failure in one pass.
    if ($Ok -and $null -eq $Result.json.data) {
        Assert-Equal 'a data payload' 'null' "${label}: data present for the payload assertions"
        return $false
    }
    return $true
}

function Test-HasProperty {
    param($Object, [string]$Name)
    return $null -ne $Object -and $null -ne $Object.PSObject.Properties[$Name]
}

# Assert that data carries every field the verb's branch promises. Names, not just success: the
# caller reads these keys, so a renamed field is a broken contract even when ok stays true.
function Assert-DataFields {
    param($Result, [string]$Verb, [string[]]$Fields)
    foreach ($f in $Fields) {
        Assert-True (Test-HasProperty $Result.json.data $f) "$Verb -Json: data.$f present"
    }
}

Write-Host "== contract cases ==" -ForegroundColor Cyan

# ---- verbs that need nothing from the device, or only an acknowledgement ----

$r = Invoke-Verb @('help') -NoOutDir
if (Assert-Envelope $r 'help' $true 0) { Assert-DataFields $r 'help' @('verbs') }

$r = Invoke-Verb @('devices') -NoOutDir
if (Assert-Envelope $r 'devices' $true 0) {
    # Emit-Ok @($rows): data is an ARRAY here and nowhere else, which is exactly the wrapping that
    # broke in S2079 - so it is asserted as an array, not merely as present.
    Assert-True ($r.json.data -is [array]) 'devices -Json: data is an array' $r.json.data.GetType().Name
    Assert-Equal 'emulator-5554' $r.json.data[0].id 'devices -Json: data[0].id'
    Assert-Equal 'Pixel 7'       $r.json.data[0].model 'devices -Json: data[0].model'
}

$r = Invoke-Verb @('props')
if (Assert-Envelope $r 'props' $true 0) {
    Assert-DataFields $r 'props' @('id', 'model', 'android', 'sdk', 'density', 'size')
    Assert-Equal '34' $r.json.data.sdk 'props -Json: data.sdk'
}

$r = Invoke-Verb @('current')
if (Assert-Envelope $r 'current' $true 0) {
    Assert-DataFields $r 'current' @('id', 'current')
    Assert-True ($r.json.data.current -match 'ResumedActivity') 'current -Json: data.current carries the resumed activity' $r.json.data.current
}

$r = Invoke-Verb @('launch')
if (Assert-Envelope $r 'launch' $true 0) {
    Assert-DataFields $r 'launch' @('id', 'package', 'component')
    Assert-Equal 'com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity' $r.json.data.component 'launch -Json: data.component'
}

$r = Invoke-Verb @('stop')
if (Assert-Envelope $r 'stop' $true 0) { Assert-DataFields $r 'stop' @('id', 'package') }

$r = Invoke-Verb @('logcat-clear')
if (Assert-Envelope $r 'logcat-clear' $true 0) { Assert-DataFields $r 'logcat-clear' @('id') }

$r = Invoke-Verb @('tap', '-X', '120', '-Y', '340')
if (Assert-Envelope $r 'tap' $true 0) {
    Assert-Equal 120 $r.json.data.x 'tap -Json: data.x'
    Assert-Equal 340 $r.json.data.y 'tap -Json: data.y'
}

$r = Invoke-Verb @('swipe', '-X', '10', '-Y', '20', '-X2', '30', '-Y2', '40', '-Duration', '250')
if (Assert-Envelope $r 'swipe' $true 0) {
    Assert-Equal 250 $r.json.data.durationMs 'swipe -Json: data.durationMs'
    Assert-Equal '10 20' ($r.json.data.from -join ' ') 'swipe -Json: data.from is the start point'
    Assert-Equal '30 40' ($r.json.data.to -join ' ')   'swipe -Json: data.to is the end point'
}

$r = Invoke-Verb @('text', '-Text', 'hello world')
if (Assert-Envelope $r 'text' $true 0) {
    # The device call encodes the space as %s; the reported value must stay the caller's own string.
    Assert-Equal 'hello world' $r.json.data.text 'text -Json: data.text is the unencoded string'
}

$r = Invoke-Verb @('key', '-Key', 'BACK')
if (Assert-Envelope $r 'key' $true 0) { Assert-Equal 'BACK' $r.json.data.key 'key -Json: data.key' }

$r = Invoke-Verb @('shell', '-Cmd', 'echo hi')
if (Assert-Envelope $r 'shell' $true 0) {
    Assert-DataFields $r 'shell' @('id', 'cmd', 'exit', 'out')
    Assert-Equal 'echo hi' $r.json.data.cmd 'shell -Json: data.cmd'
    Assert-Equal 0 $r.json.data.exit 'shell -Json: data.exit'
}

# ---- verbs whose payload is built from device output ----

$r = Invoke-Verb @('uidump')
if (Assert-Envelope $r 'uidump' $true 0) {
    Assert-DataFields $r 'uidump' @('id', 'file', 'count', 'nodes')
    Assert-True ($r.json.data.nodes -is [array]) 'uidump -Json: data.nodes is an array' $r.json.data.nodes.GetType().Name
    Assert-Equal $r.json.data.count $r.json.data.nodes.Count 'uidump -Json: data.count equals the node count'
    Assert-True ($r.json.data.count -gt 0) 'uidump -Json: the fixture tree yields nodes' $r.json.data.count
}

$r = Invoke-Verb @('tap-label', '-Label', 'Music')
if (Assert-Envelope $r 'tap-label' $true 0) {
    Assert-DataFields $r 'tap-label' @('id', 'label', 'source', 'x', 'y', 'matches', 'file')
    Assert-Equal 'Music' $r.json.data.label 'tap-label -Json: data.label is the node that was tapped'
    Assert-Equal 'text'  $r.json.data.source 'tap-label -Json: data.source says which attribute matched'
    Assert-Equal 1 $r.json.data.matches 'tap-label -Json: data.matches counts the hits'
}

$r = Invoke-Verb @('tap-id', '-ResourceId', 'content')
if (Assert-Envelope $r 'tap-id' $true 0) {
    Assert-DataFields $r 'tap-id' @('id', 'resourceId', 'label', 'x', 'y', 'matches', 'file')
    # The full package-qualified value, not the short name the call passed: a caller that pins the
    # short form still learns which node was actually hit.
    Assert-Equal 'android:id/content' $r.json.data.resourceId 'tap-id -Json: data.resourceId is the full value'
}

$r = Invoke-Verb @('shot') -Stub @{ FMS_STUB_SECURE = '1' }
if (Assert-Envelope $r 'shot' $true 0) {
    Assert-DataFields $r 'shot' @('id', 'file', 'secureWindow', 'treeFile', 'treeNodes')
    # S1506/S1520: a FLAG_SECURE capture is black by design, and the node tree beside it is the only
    # layout evidence. A -Json caller learns that from these three fields alone.
    Assert-Equal $true $r.json.data.secureWindow 'shot -Json: data.secureWindow is true on a secure window'
    Assert-True ($r.json.data.treeNodes -gt 0) 'shot -Json: data.treeNodes counts the tree beside the black frame' $r.json.data.treeNodes
}

$r = Invoke-Verb @('shot')
if (Assert-Envelope $r 'shot' $true 0) {
    Assert-Equal $false $r.json.data.secureWindow 'shot -Json: data.secureWindow is false on an ordinary window'
    Assert-Equal 0 $r.json.data.treeNodes 'shot -Json: no tree is taken when the flag is down'
}

$r = Invoke-Verb @('log', '-Tail', '50')
if (Assert-Envelope $r 'log' $true 0) {
    Assert-DataFields $r 'log' @('id', 'package', 'matched', 'file', 'appPids')
    Assert-True ($r.json.data.appPids -is [array]) 'log -Json: data.appPids is an array' $r.json.data.appPids.GetType().Name
    Assert-True ($r.json.data.appPids -contains 4711) 'log -Json: the app pid is discovered' ($r.json.data.appPids -join ',')
    Assert-True ($r.json.data.matched -gt 0) 'log -Json: data.matched counts the kept lines' $r.json.data.matched
}

# ---- S2079 regression: clip-check, both directions ----
# The defect was `@($findings)` around a List[object], which throws and killed ONLY this branch.
# Both directions are needed: the ok path and the exit-9 path build the result differently, so one
# case cannot vouch for the other.

$watchStub = @{ FMS_STUB_WM_SIZE = '480x480'; FMS_STUB_RADIUS = '240'; FMS_STUB_WATCH = '1' }

$r = Invoke-Verb @('clip-check') -Stub ($watchStub + @{ FMS_STUB_TREE = $treeClean })
if (Assert-Envelope $r 'clip-check' $true 0) {
    Assert-DataFields $r 'clip-check' @('id', 'file', 'shape', 'checked', 'findings', 'offGlass')
    Assert-True ($r.json.data.findings -is [array]) 'clip-check -Json: data.findings is an array (S2079)' $r.json.data.findings.GetType().Name
    Assert-Equal 0 $r.json.data.offGlass 'clip-check -Json: a recorded watch dump reports no off-glass node'
    Assert-Equal 480 $r.json.data.shape.width 'clip-check -Json: data.shape is read from the device'
    Assert-Equal $true $r.json.data.shape.round 'clip-check -Json: a 480x480 display with radius 240 is round'
}

$r = Invoke-Verb @('clip-check') -Stub ($watchStub + @{ FMS_STUB_TREE = $treeOffGlass })
if (Assert-Envelope $r 'clip-check' $false 9) {
    Assert-Equal 1 $r.json.data.offGlass 'clip-check -Json: the off-glass node is counted'
    Assert-True ($r.json.data.findings -is [array]) 'clip-check -Json: data.findings is an array on the failing path' $r.json.data.findings.GetType().Name
    Assert-Equal 'OFF-GLASS' $r.json.data.findings[0].kind 'clip-check -Json: the finding carries its class'
    Assert-True ([string]::IsNullOrEmpty($r.json.reason) -eq $false) 'clip-check -Json: reason names the defect' $r.json.reason
}

# ---- verbs that write, install or remove ----

$r = Invoke-Verb @('install', '-Apk', $fakeApk)
if (Assert-Envelope $r 'install' $true 0) { Assert-DataFields $r 'install' @('id', 'apk') }

$r = Invoke-Verb @('wipe-data', '-Yes')
if (Assert-Envelope $r 'wipe-data' $true 0) { Assert-DataFields $r 'wipe-data' @('id', 'package') }

$r = Invoke-Verb @('uninstall', '-Yes')
if (Assert-Envelope $r 'uninstall' $true 0) { Assert-DataFields $r 'uninstall' @('id', 'package') }

$r = Invoke-Verb @('prefs')
if (Assert-Envelope $r 'prefs' $true 0) {
    Assert-DataFields $r 'prefs' @('id', 'package', 'file')
    Assert-True (Test-Path -LiteralPath $r.json.data.file) 'prefs -Json: data.file names a file that exists' $r.json.data.file
    $prefsBytes = [System.IO.File]::ReadAllBytes($r.json.data.file)
    Assert-Equal 'settings-prefs-fixture' ([System.Text.Encoding]::UTF8.GetString($prefsBytes)) 'prefs -Json: data.file preserves decoded DataStore bytes'
}

$r = Invoke-Verb @('pull', '-Remote', '/sdcard/DCIM/Camera/shot.png')
if (Assert-Envelope $r 'pull' $true 0) {
    Assert-DataFields $r 'pull' @('id', 'remote', 'file', 'size')
    Assert-Equal '/sdcard/DCIM/Camera/shot.png' $r.json.data.remote 'pull -Json: data.remote'
    Assert-True ($r.json.data.size -gt 0) 'pull -Json: data.size is the local byte count' $r.json.data.size
}

$r = Invoke-Verb @('push', '-Local', $fakeApk, '-Remote', '/sdcard/Movies/')
if (Assert-Envelope $r 'push' $true 0) { Assert-DataFields $r 'push' @('id', 'local', 'remote') }

Write-Host ""
Write-Host "== failure cases ==" -ForegroundColor Cyan
# Every one asserts the pair: the process exit code AND the exitCode inside the object. A caller
# script reads the first and an agent reads the second, so a divergence between them is invisible
# until both are asserted by one case.

function Assert-Failure {
    param($Result, [string]$Verb, [int]$Code, [string]$Label)
    if ($null -eq $Result.json) {
        Assert-Equal "JSON with exitCode $Code" "exit $($Result.exit), stdout: $($Result.stdout)" "${Label}: stdout parses as JSON"
        return
    }
    Assert-Equal $Code  $Result.exit          "${Label}: process exit $Code"
    Assert-Equal $Code  $Result.json.exitCode "${Label}: JSON exitCode $Code"
    Assert-Equal $false $Result.json.ok       "${Label}: ok is false"
    Assert-True ([string]::IsNullOrEmpty($Result.json.reason) -eq $false) "${Label}: reason is stated" $Result.json.reason
}

Assert-Failure (Invoke-Verb @('tap')) 'tap' 1 'tap without coordinates'
Assert-Failure (Invoke-Verb @('nonsense-verb')) 'nonsense-verb' 1 'an unknown verb'
Assert-Failure (Invoke-Verb @('devices') -Stub @{ FMS_STUB_DEVICES = '' } -NoOutDir) 'devices' 2 'no online device'
Assert-Failure (Invoke-Verb @('props') -Stub @{ FMS_STUB_DEVICES = 'emulator-5554,emulator-5556' }) 'props' 3 'two devices and no -DeviceId'
Assert-Failure (Invoke-Verb @('stop') -Stub @{ FMS_STUB_PACKAGES = '' }) 'stop' 4 'neither package variant installed'
Assert-Failure (Invoke-Verb @('wipe-data')) 'wipe-data' 5 'wipe-data without -Yes'
Assert-Failure (Invoke-Verb @('uninstall')) 'uninstall' 5 'uninstall without -Yes'
# The removed verb: it must refuse and name its two replacements rather than forward (S1572).
$r = Invoke-Verb @('clear')
Assert-Failure $r 'clear' 5 'the removed clear verb'
if ($null -ne $r.json) {
    Assert-True ($r.json.reason -match 'logcat-clear' -and $r.json.reason -match 'wipe-data') 'clear: the refusal names both replacements' $r.json.reason
}
Assert-Failure (Invoke-Verb @('tap-id', '-ResourceId', 'nothingMatchesThis', '-Exact')) 'tap-id' 8 'tap-id with no matching node'
Assert-Failure (Invoke-Verb @('tap-label', '-Label', 'nothingMatchesThis', '-Exact')) 'tap-label' 8 'tap-label with no matching node'

Write-Host ""
Write-Host "== coverage ratchet ==" -ForegroundColor Cyan
# Re-derive the verbs adb.ps1 has from its own switch and compare with what ran above. Without this
# a new verb ships its -Json branch untested exactly the way clip-check did, which is the whole
# reason for this suite.

$verbsWithJson = [System.Collections.Generic.List[object]]::new()
$currentLabel = $null
$currentBody = [System.Text.StringBuilder]::new()
foreach ($line in (Get-Content -LiteralPath $adbScript -Encoding UTF8)) {
    # A top-level case label sits at exactly four spaces of indent and ends the line with `{`.
    # Two shapes exist: one or more quoted names, and the `{ $_ -in 'a', 'b' }` alias form.
    if ($line -match "^    (?<label>'[^']+'(\s*,\s*'[^']+')*|\{\s*\`$_ -in\s+'[^{]+)\s*\{\s*$") {
        # Read the capture FIRST. The body test below is itself a -match and overwrites $Matches,
        # which silently emptied every second label and made the ratchet report 12 verbs instead
        # of 24 - a parse failure that reads as broad coverage, the exact shape this ratchet guards.
        $names = @([regex]::Matches($Matches['label'], "'([^']+)'") | ForEach-Object { $_.Groups[1].Value })
        if ($currentLabel -and $currentBody.ToString() -match '\$Json') {
            $verbsWithJson.Add($currentLabel) | Out-Null
        }
        $currentLabel = $names
        $currentBody.Clear() | Out-Null
        continue
    }
    if ($currentLabel) { $currentBody.AppendLine($line) | Out-Null }
}
if ($currentLabel -and $currentBody.ToString() -match '\$Json') { $verbsWithJson.Add($currentLabel) | Out-Null }

# A parse that silently stopped finding verbs would report perfect coverage, so its own result is
# asserted against the count known when this suite was written (24 on 2026-08-27).
Assert-True ($verbsWithJson.Count -ge 20) "the switch parse found the verbs with a -Json branch" "$($verbsWithJson.Count) found"

$uncovered = @()
foreach ($names in $verbsWithJson) {
    $hit = $false
    foreach ($n in $names) { if ($script:drivenVerbs.Contains($n)) { $hit = $true } }
    if (-not $hit) { $uncovered += ($names -join '/') }
}
Assert-Equal '' ($uncovered -join ', ') 'every verb with a -Json branch is driven by a case above'

Write-Host ""
Write-Host "== hermetic check ==" -ForegroundColor Cyan
# A stub call the table does not know answers with a miss, never with silence (ADR-3): silence
# would turn a change in adb.ps1 into a green run that proved nothing.
if (Test-Path -LiteralPath $missFile) {
    $misses = @(Get-Content -LiteralPath $missFile | Sort-Object -Unique)
    Assert-Equal '' ($misses -join ' ;; ') 'the stub answered every adb call the verbs made'
} else {
    Assert-Equal 'no misses' 'no misses' 'the stub answered every adb call the verbs made'
}

Remove-Item -LiteralPath $runDir -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "passed: $script:passed  failed: $script:failed"
if ($script:failed -gt 0) { exit 1 }
exit 0
