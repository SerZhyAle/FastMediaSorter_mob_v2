#!/usr/bin/env pwsh
<#
.SYNOPSIS
    S2380 - provision and assert the phone screen geometries the automated UI sweep runs on.

.DESCRIPTION
    The sweep's run matrix carries a screen dimension, and every frame it stores is labelled with the
    profile it was supposedly taken on. That label is a claim about hardware, and nothing else in the
    pipeline can check it: a frame captured on whatever emulator happened to be attached looks exactly
    like a frame captured on the declared one. So the walker asserts the bench before each combination
    and this script is what answers.

      -Ensure  creates the AVD for a declared profile when it does not exist locally, using the
               profile's avdmanager device id and an installed system image. An existing AVD is left
               alone: the verb reports `present` rather than recreating and wiping it.

      -Assert  reads the geometry off an ATTACHED device and compares it against the declared record.
               A mismatch is exit 1 - a real, negative answer - and never a warning, because a sweep
               is only evidence about the screen it actually ran on.

      -List    prints the declared profiles and exits.

    The declared geometries live in ui-sweep-profiles.json beside this script, never inline here, so
    the walker and this script cannot disagree about what "the small phone" means. The watch profiles
    in wear-shape-profiles.json are a separate file with a separate consumer and are not read here.

    Every device call goes through scripts/devtest/adb.ps1 rather than a raw adb: that wrapper owns
    device selection and its own exit codes, and a raw adb invoked from a POSIX-style shell rewrites
    device paths into Windows ones (S2602).

    The width/height pair is compared UNORDERED. The sweep rotates the device between combinations,
    and although `wm size` reports the natural size, a size override left behind by an earlier run
    reports the rotated pair - which is the same bench, not a different one. Density and the derived
    dp are compared exactly, since neither survives a genuine profile swap.

.PARAMETER Profile
    Profile id from ui-sweep-profiles.json: small-phone, large-phone.

.PARAMETER Ensure
    Create the AVD for -Profile if it is missing. Never touches an existing AVD.

.PARAMETER Assert
    Compare the attached device against -Profile. Requires -DeviceId when more than one device is
    online.

.PARAMETER List
    Print the declared profiles and exit. Ignores -Profile.

.PARAMETER DeviceId
    Serial of the device to assert. Omitted: the single online device is used, and an ambiguous
    choice is refused rather than guessed.

.PARAMETER SystemImage
    System image package for -Ensure. Default is API 36 with Play services: the media-read
    permissions ui-sweep-seed.ps1 grants before first launch do not exist below API 33.

.PARAMETER Json
    Emit a result object instead of human lines.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/ui-sweep-bench.ps1 -List

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/ui-sweep-bench.ps1 -Profile small-phone -Ensure

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/ui-sweep-bench.ps1 -Profile small-phone -Assert -DeviceId emulator-5554

.NOTES
    Exit codes:
      0 - the verb succeeded: -List printed, -Ensure left a usable AVD, or -Assert matched.
      1 - -Assert ran and the attached device does NOT match the declared profile. A real answer, and
          a negative one: frames taken here would carry a false profile label.
      2 - could not verify - nothing was measured. Profile file missing or unparseable, unknown
          profile id, no verb or no -Profile given, adb or avdmanager not found, no online device, an
          ambiguous device choice with no -DeviceId, or the device refused to report its geometry.
          Distinct from 1 on purpose: "this bench is the wrong screen" and "no screen was read" call
          for opposite reactions, and the second must never be read as a pass.
#>
[CmdletBinding()]
param(
    [string]$Profile,
    [switch]$Ensure,
    [switch]$Assert,
    [switch]$List,
    [string]$DeviceId,
    [string]$SystemImage = 'system-images;android-36;google_apis_playstore;x86_64',
    [switch]$Json
)

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adbScript = Join-Path $repoRoot 'scripts/devtest/adb.ps1'

$script:result = [ordered]@{ ok = $false; verb = $null; profile = $null; exitCode = 2; reason = $null; data = $null }

function Write-Result {
    param([int]$Code, [string]$Reason)
    $script:result.exitCode = $Code
    $script:result.ok = ($Code -eq 0)
    if ($Reason) { $script:result.reason = $Reason }
    if ($Json) { $script:result | ConvertTo-Json -Compress -Depth 6 }
    exit $Code
}

function Stop-WithFailure {
    param([int]$Code, [string]$Message)
    if (-not $Json) { Write-Host "FAIL ($Code) - $Message" -ForegroundColor Red }
    Write-Result -Code $Code -Reason $Message
}

# Returns the command's stdout lines; $script:deviceExit carries adb.ps1's own exit code so a caller
# can tell "the device answered something unexpected" from "there was no device to ask".
function Invoke-DeviceShell {
    param([string]$Command)
    $adbArgs = @()
    if ($DeviceId) { $adbArgs += @('-DeviceId', $DeviceId) }
    $adbArgs += @('shell', '-Cmd', $Command)
    $out = & pwsh -NoProfile -File $adbScript @adbArgs 2>&1
    $script:deviceExit = $LASTEXITCODE
    return ($out | ForEach-Object { $_.ToString() })
}

# --- load the declared geometries ---------------------------------------------------------------

$profilePath = Join-Path $PSScriptRoot 'ui-sweep-profiles.json'
if (-not (Test-Path -Path $profilePath -PathType Leaf)) {
    Stop-WithFailure 2 "declared profiles not found at $profilePath"
}
try {
    $declared = (Get-Content -Path $profilePath -Raw -Encoding UTF8 | ConvertFrom-Json)
} catch {
    Stop-WithFailure 2 "declared profiles at $profilePath are not valid JSON: $($_.Exception.Message)"
}
$profiles = @($declared.profiles)
if ($profiles.Count -eq 0) { Stop-WithFailure 2 "declared profiles list is empty" }

# --- -List --------------------------------------------------------------------------------------

if ($List) {
    $script:result.verb = 'list'
    $script:result.data = @($profiles | ForEach-Object {
        [ordered]@{ id = $_.id; avdName = $_.avdName; deviceProfile = $_.deviceProfile
                    widthPx = [int]$_.widthPx; heightPx = [int]$_.heightPx
                    densityDpi = [int]$_.densityDpi; widthDp = [double]$_.widthDp }
    })
    if (-not $Json) {
        Write-Host "Declared phone benches (ticket $($declared.ticket)):" -ForegroundColor Cyan
        foreach ($p in $profiles) {
            Write-Host ("  {0,-12} {1,-22} {2,4}x{3,-5} px at {4,3} dpi = {5} x {6} dp" -f `
                $p.id, $p.avdName, $p.widthPx, $p.heightPx, $p.densityDpi, $p.widthDp, $p.heightDp) -ForegroundColor White
        }
    }
    Write-Result -Code 0
}

if (-not $Ensure -and -not $Assert) {
    Stop-WithFailure 2 "no verb given - pass -List, -Ensure or -Assert"
}
if (-not $Profile) {
    Stop-WithFailure 2 "-Profile is required for -Ensure and -Assert. Known ids: $(($profiles.id) -join ', ')"
}

$target = $profiles | Where-Object { $_.id -eq $Profile } | Select-Object -First 1
if (-not $target) {
    # 2 rather than 1: an id this file does not carry measured nothing, so the answer is "could not
    # verify". Reporting it as a mismatch would let a typo in the matrix read as a failed bench.
    Stop-WithFailure 2 "unknown profile '$Profile'. Known ids: $(($profiles.id) -join ', ')"
}
$script:result.profile = $target.id

# --- -Ensure ------------------------------------------------------------------------------------

if ($Ensure) {
    $script:result.verb = 'ensure'
    $avdRoot = Join-Path $env:USERPROFILE '.android\avd'
    $avdConfig = Join-Path $avdRoot "$($target.avdName).avd\config.ini"

    if (Test-Path -Path $avdConfig -PathType Leaf) {
        $script:result.data = [ordered]@{ avdName = $target.avdName; state = 'present'; config = $avdConfig }
        if (-not $Json) { Write-Host "PRESENT $($target.avdName) - left untouched" -ForegroundColor Green }
        Write-Result -Code 0
    }

    $avdManager = @(
        "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat",
        "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat",
        "$env:ANDROID_SDK_ROOT\cmdline-tools\latest\bin\avdmanager.bat"
    ) | Where-Object { $_ -and (Test-Path -Path $_ -PathType Leaf) } | Select-Object -First 1
    if (-not $avdManager) { Stop-WithFailure 2 "avdmanager not found - install the Android cmdline-tools" }

    # `echo no` answers avdmanager's custom-hardware-profile prompt: this script is non-interactive,
    # and an unanswered prompt hangs rather than failing.
    $created = & cmd /c "echo no | `"$avdManager`" create avd -n $($target.avdName) -k `"$SystemImage`" -d $($target.deviceProfile)" 2>&1
    if (-not (Test-Path -Path $avdConfig -PathType Leaf)) {
        Stop-WithFailure 2 "avdmanager did not create $($target.avdName): $($created -join ' ')"
    }

    $script:result.data = [ordered]@{ avdName = $target.avdName; state = 'created'; config = $avdConfig }
    if (-not $Json) {
        Write-Host "CREATED $($target.avdName) ($($target.deviceProfile), $($target.widthPx)x$($target.heightPx) px)" -ForegroundColor Green
        Write-Host "  boot it, then re-run with -Assert to confirm the geometry the device reports" -ForegroundColor Gray
    }
    Write-Result -Code 0
}

# --- -Assert ------------------------------------------------------------------------------------

$script:result.verb = 'assert'

if (-not (Test-Path -Path $adbScript -PathType Leaf)) {
    Stop-WithFailure 2 "device wrapper not found at $adbScript"
}

$sizeLine = (Invoke-DeviceShell 'wm size') -join ' '
if ($script:deviceExit -ne 0) {
    Stop-WithFailure 2 "could not read screen size (adb.ps1 exit $($script:deviceExit)): $sizeLine"
}
$densityLine = (Invoke-DeviceShell 'wm density') -join ' '
if ($script:deviceExit -ne 0) {
    Stop-WithFailure 2 "could not read density (adb.ps1 exit $($script:deviceExit)): $densityLine"
}

# "Override size" wins when present: it is what the app actually renders against.
$sizeMatches = [regex]::Matches($sizeLine, '(\d+)x(\d+)')
if ($sizeMatches.Count -eq 0) { Stop-WithFailure 2 "screen size not present in device output ($sizeLine)" }
$sizeMatch = $sizeMatches[$sizeMatches.Count - 1]
$actualWidth = [int]$sizeMatch.Groups[1].Value
$actualHeight = [int]$sizeMatch.Groups[2].Value

$densityMatches = [regex]::Matches($densityLine, '(\d+)')
if ($densityMatches.Count -eq 0) { Stop-WithFailure 2 "density not present in device output ($densityLine)" }
$actualDensity = [int]$densityMatches[$densityMatches.Count - 1].Groups[1].Value
if ($actualDensity -le 0) { Stop-WithFailure 2 "device reported density $actualDensity - not a usable number" }

$actualShort = [math]::Min($actualWidth, $actualHeight)
$actualLong = [math]::Max($actualWidth, $actualHeight)
$expectedShort = [math]::Min([int]$target.widthPx, [int]$target.heightPx)
$expectedLong = [math]::Max([int]$target.widthPx, [int]$target.heightPx)
$actualShortDp = [math]::Round($actualShort / $actualDensity * 160, 1)

$script:result.data = [ordered]@{
    device = $(if ($DeviceId) { $DeviceId } else { 'single online device' })
    actual = [ordered]@{
        widthPx = $actualWidth; heightPx = $actualHeight; densityDpi = $actualDensity
        shortEdgePx = $actualShort; longEdgePx = $actualLong; shortEdgeDp = $actualShortDp
    }
    expected = [ordered]@{
        widthPx = [int]$target.widthPx; heightPx = [int]$target.heightPx
        densityDpi = [int]$target.densityDpi; shortEdgePx = $expectedShort; longEdgePx = $expectedLong
        shortEdgeDp = [double]$target.widthDp
    }
}

$mismatches = [System.Collections.Generic.List[string]]::new()
if ($actualShort -ne $expectedShort) { $mismatches.Add("short edge $actualShort px, expected $expectedShort px") }
if ($actualLong -ne $expectedLong) { $mismatches.Add("long edge $actualLong px, expected $expectedLong px") }
if ($actualDensity -ne [int]$target.densityDpi) { $mismatches.Add("density $actualDensity dpi, expected $($target.densityDpi) dpi") }
$script:result.data.mismatches = $mismatches.ToArray()

if (-not $Json) {
    Write-Host ("DEVICE  {0}x{1} px at {2} dpi - short edge {3} dp" -f `
        $actualWidth, $actualHeight, $actualDensity, $actualShortDp) -ForegroundColor Gray
    Write-Host ("PROFILE {0} - {1}x{2} px at {3} dpi - short edge {4} dp" -f `
        $target.id, $target.widthPx, $target.heightPx, $target.densityDpi, $target.widthDp) -ForegroundColor Gray
}

if ($mismatches.Count -gt 0) {
    if (-not $Json) {
        foreach ($m in $mismatches) { Write-Host "  MISMATCH $m" -ForegroundColor Red }
    }
    Stop-WithFailure 1 "the attached device is not profile '$($target.id)' - frames captured here would carry a false profile label"
}

if (-not $Json) {
    Write-Host "MATCH the attached device is '$($target.id)' ($($target.avdName))" -ForegroundColor Cyan
}
Write-Result -Code 0
