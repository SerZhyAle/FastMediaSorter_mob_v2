#requires -Version 7.0
<#
.SYNOPSIS
  S0918: guards against silently shrinking Google Play device reach through an
  orientation-implied required screen feature.

.DESCRIPTION
  Any <activity> that pins android:screenOrientation to a portrait- or landscape-
  implying value makes Android imply the matching hardware feature as required=true:

    portrait / reversePortrait / sensorPortrait / userPortrait -> android.hardware.screen.portrait
    landscape / reverseLandscape / sensorLandscape / userLandscape -> android.hardware.screen.landscape

  A required screen feature makes Google Play filter out every device without that
  screen capability (landscape-only TVs, some tablets / auto displays). In 2.60.7031
  the portrait-locked CameraCaptureActivity (S0754) cost ~2,958 devices of reach.

  The canonical Google fix is an explicit <uses-feature required="false"> override,
  which cancels the implied requirement while the activity stays orientation-locked
  for UX. This gate enforces that invariant statically: whenever any app_v2 manifest
  locks orientation, app_v2/src/main/AndroidManifest.xml must declare the matching
  screen.* feature not-required.

.NOTES
  Scope: app_v2 only (Play device-reach concern; wear ships separately).
  Run from anywhere; paths resolve relative to the repo root.
  Exit 0 = clean, Exit 1 = missing override.
#>
param([switch]$Gate)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = (Resolve-Path "$PSScriptRoot/../..").Path
$rel = { param($p) $p.Substring($root.Length + 1) -replace '\\', '/' }

$srcRoot = Join-Path $root 'app_v2/src'
$mainManifest = Join-Path $root 'app_v2/src/main/AndroidManifest.xml'

$portraitValues = @('portrait', 'reversePortrait', 'sensorPortrait', 'userPortrait')
$landscapeValues = @('landscape', 'reverseLandscape', 'sensorLandscape', 'userLandscape')

# --- 1. Find every orientation lock across all app_v2 manifests. ---
$portraitLocks = New-Object System.Collections.Generic.List[string]
$landscapeLocks = New-Object System.Collections.Generic.List[string]

$manifests = Get-ChildItem -Path $srcRoot -Recurse -Filter 'AndroidManifest.xml' -File -ErrorAction SilentlyContinue
foreach ($m in $manifests) {
    $i = 0
    foreach ($line in [System.IO.File]::ReadAllLines($m.FullName)) {
        $i++
        foreach ($match in [regex]::Matches($line, 'android:screenOrientation\s*=\s*"([^"]+)"')) {
            $value = $match.Groups[1].Value
            $where = "{0}:{1}" -f (& $rel $m.FullName), $i
            if ($portraitValues -contains $value) { $portraitLocks.Add("$where ($value)") }
            elseif ($landscapeValues -contains $value) { $landscapeLocks.Add("$where ($value)") }
        }
    }
}

# --- 2. Read the not-required overrides declared in src/main. ---
function Test-NotRequiredOverride {
    param([string]$ManifestText, [string]$FeatureName)
    foreach ($element in [regex]::Matches($ManifestText, '(?s)<uses-feature\b[^>]*?/>')) {
        $el = $element.Value
        $nameMatch = [regex]::Match($el, 'android:name\s*=\s*"([^"]+)"')
        if (-not $nameMatch.Success -or $nameMatch.Groups[1].Value -ne $FeatureName) { continue }
        $reqMatch = [regex]::Match($el, 'android:required\s*=\s*"([^"]+)"')
        if ($reqMatch.Success -and $reqMatch.Groups[1].Value -eq 'false') { return $true }
    }
    return $false
}

$mainText = if (Test-Path $mainManifest) { [System.IO.File]::ReadAllText($mainManifest) } else { '' }
$hasPortraitOverride = Test-NotRequiredOverride -ManifestText $mainText -FeatureName 'android.hardware.screen.portrait'
$hasLandscapeOverride = Test-NotRequiredOverride -ManifestText $mainText -FeatureName 'android.hardware.screen.landscape'

# --- 3. A lock without its matching override is a device-reach regression. ---
$violations = New-Object System.Collections.Generic.List[string]
if ($portraitLocks.Count -gt 0 -and -not $hasPortraitOverride) {
    $used = ($portraitLocks | Select-Object -First 3) -join ', '
    $violations.Add("portrait orientation locked ($used) but src/main declares no <uses-feature android:name=`"android.hardware.screen.portrait`" android:required=`"false`" /> -> Play filters out landscape-only devices")
}
if ($landscapeLocks.Count -gt 0 -and -not $hasLandscapeOverride) {
    $used = ($landscapeLocks | Select-Object -First 3) -join ', '
    $violations.Add("landscape orientation locked ($used) but src/main declares no <uses-feature android:name=`"android.hardware.screen.landscape`" android:required=`"false`" /> -> Play filters out portrait-only devices")
}

if ($violations.Count -gt 0) {
    Write-Host "assert-orientation-implied-feature: FAIL ($($violations.Count) issue(s))" -ForegroundColor Red
    $violations | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
    Write-Host 'Fix: add the not-required override next to the other <uses-feature> overrides in app_v2/src/main/AndroidManifest.xml:' -ForegroundColor Cyan
    Write-Host '  <uses-feature android:name="android.hardware.screen.portrait" android:required="false" />' -ForegroundColor Cyan
    Write-Host '  <uses-feature android:name="android.hardware.screen.landscape" android:required="false" />' -ForegroundColor Cyan
    Write-Host 'Keep the activity orientation lock - the override cancels the implied Play filter without changing UX.' -ForegroundColor Cyan
    exit 1
}

$summary = "assert-orientation-implied-feature: PASS"
if ($portraitLocks.Count -gt 0) { $summary += " (portrait lock covered by not-required override)" }
if ($landscapeLocks.Count -gt 0) { $summary += " (landscape lock covered by not-required override)" }
if ($portraitLocks.Count -eq 0 -and $landscapeLocks.Count -eq 0) { $summary += " (no orientation locks)" }
Write-Host $summary -ForegroundColor Green
exit 0
