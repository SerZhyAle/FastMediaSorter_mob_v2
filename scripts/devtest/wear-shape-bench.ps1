<#
.SYNOPSIS
    S2273 - provision and assert the watch screen shapes Play reviews on.

.DESCRIPTION
    Play's Wear quality criterion WO-V16 ("Watch shapes") is reviewed on two emulators the guideline
    names by size: a 192 dp small round and a 227 dp large round. Until 2026-09-02 this project owned
    a single watch AVD at 240 dp - larger than both - so every shape measurement it ever recorded was
    taken on a screen no reviewer uses, and the app was rejected for a defect that blocks first launch
    at 192 dp and is invisible at 240 dp.

    This script closes that gap from two sides.

      -Ensure  creates the AVD for a declared profile when it does not exist locally, using the
               profile's avdmanager device id and an installed wear system image. Existing AVDs are
               left alone: the verb reports `present` rather than recreating and wiping them.

      -Assert  reads the geometry off an ATTACHED device and compares it against the declared record.
               This is the half that matters. A sweep is only evidence about the screen it actually
               ran on, and the failure this ticket exists to prevent is a fix measured on the wrong
               bench and then declared done. `-Assert` makes that mismatch an exit code instead of an
               assumption.

    The declared shapes live in wear-shape-profiles.json beside this script, never inline here, so the
    pre-release sweep and this script cannot disagree about what "the reviewed shapes" means.

    Roundness is read from the display's rounded-corner radius as reported by `dumpsys window
    displays`, which is the same source `adb.ps1 clip-check` uses - the two must agree, or a screen
    could pass the shape assertion and be judged against a different geometry moments later. It is NOT
    read from the AVD's hw.lcd.circular key: that key is absent on the XL round AVD, which is round
    anyway, because the shape comes from the emulator skin.

.PARAMETER Profile
    Profile id from wear-shape-profiles.json: small-round, large-round, xl-round.

.PARAMETER Ensure
    Create the AVD for -Profile if it is missing. Never touches an existing AVD.

.PARAMETER Assert
    Compare the attached device against -Profile. Requires -DeviceId when more than one device is
    online.

.PARAMETER List
    Print the declared profiles and exit. Ignores -Profile.

.PARAMETER ReviewedOnly
    With -List, print only the profiles Play reviews on.

.PARAMETER DeviceId
    Serial of the device to assert. Omitted: the single online device is used, and an ambiguous choice
    is refused rather than guessed.

.PARAMETER SystemImage
    Wear system image package for -Ensure. Default: the image the existing watch AVDs use.

.PARAMETER Json
    Emit a result object instead of human lines.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/wear-shape-bench.ps1 -List

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/wear-shape-bench.ps1 -Profile small-round -Ensure

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/wear-shape-bench.ps1 -Profile small-round -Assert -DeviceId emulator-5556

.NOTES
    Exit codes:
      0 - the verb succeeded: -List printed, -Ensure left a usable AVD, or -Assert matched.
      1 - -Assert ran and the device does NOT match the declared profile, or the arguments are
          unusable (unknown profile id, no verb given). A real answer, and a negative one.
      2 - could not verify: profile file missing or unparseable, no online device, an ambiguous
          device choice with no -DeviceId, adb or avdmanager not found, or the system image absent.
          Distinct from 1 because "the bench is wrong" and "nothing was measured" call for opposite
          reactions - the second must never be read as a pass.
#>
[CmdletBinding()]
param(
    [string]$Profile,
    [switch]$Ensure,
    [switch]$Assert,
    [switch]$List,
    [switch]$ReviewedOnly,
    [string]$DeviceId,
    [string]$SystemImage = 'system-images;android-37.0;android-wear-signed;x86_64',
    [switch]$Json
)

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
. "$PSScriptRoot/lib/find-adb.ps1"

$script:result = [ordered]@{ ok = $false; verb = $null; profile = $null; exitCode = 2; reason = $null; data = $null }

function Emit-Result {
    param([int]$Code, [string]$Reason)
    $script:result.exitCode = $Code
    $script:result.ok = ($Code -eq 0)
    if ($Reason) { $script:result.reason = $Reason }
    if ($Json) { $script:result | ConvertTo-Json -Compress -Depth 6 }
    exit $Code
}

function Fail {
    param([int]$Code, [string]$Message)
    if (-not $Json) { Write-Host "FAIL ($Code) - $Message" -ForegroundColor Red }
    Emit-Result -Code $Code -Reason $Message
}

# --- load the declared shapes -------------------------------------------------------------------

$profilePath = Join-Path $PSScriptRoot 'wear-shape-profiles.json'
if (-not (Test-Path -Path $profilePath -PathType Leaf)) {
    Fail 2 "declared profiles not found at $profilePath"
}
try {
    $declared = (Get-Content -Path $profilePath -Raw -Encoding UTF8 | ConvertFrom-Json)
} catch {
    Fail 2 "declared profiles at $profilePath are not valid JSON: $($_.Exception.Message)"
}
$profiles = @($declared.profiles)
if ($profiles.Count -eq 0) { Fail 2 "declared profiles list is empty" }

# --- -List --------------------------------------------------------------------------------------

if ($List) {
    $script:result.verb = 'list'
    $shown = if ($ReviewedOnly) { @($profiles | Where-Object { $_.reviewedByPlay }) } else { $profiles }
    $script:result.data = @($shown | ForEach-Object {
        [ordered]@{ id = $_.id; avdName = $_.avdName; dp = $_.dp; contentBoxDp = $_.contentBoxDp; reviewedByPlay = [bool]$_.reviewedByPlay }
    })
    if (-not $Json) {
        Write-Host "Declared watch shapes (criterion $($declared.criterion)):" -ForegroundColor Cyan
        foreach ($p in $shown) {
            $mark = if ($p.reviewedByPlay) { 'reviewed by Play' } else { 'control only' }
            Write-Host ("  {0,-12} {1,-22} {2,3} dp  content box {3,5} dp  - {4}" -f `
                $p.id, $p.avdName, $p.dp, $p.contentBoxDp, $mark) -ForegroundColor White
        }
    }
    Emit-Result -Code 0
}

if (-not $Ensure -and -not $Assert) {
    Fail 1 "no verb given - pass -List, -Ensure or -Assert"
}
if (-not $Profile) {
    Fail 1 "-Profile is required for -Ensure and -Assert. Known ids: $(($profiles.id) -join ', ')"
}

$target = $profiles | Where-Object { $_.id -eq $Profile } | Select-Object -First 1
if (-not $target) {
    Fail 1 "unknown profile '$Profile'. Known ids: $(($profiles.id) -join ', ')"
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
        Emit-Result -Code 0
    }

    $avdManager = @(
        "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat",
        "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat",
        "$env:ANDROID_SDK_ROOT\cmdline-tools\latest\bin\avdmanager.bat"
    ) | Where-Object { $_ -and (Test-Path -Path $_ -PathType Leaf) } | Select-Object -First 1
    if (-not $avdManager) { Fail 2 "avdmanager not found - install the Android cmdline-tools" }

    # `echo no` answers avdmanager's custom-hardware-profile prompt: this script is non-interactive,
    # and an unanswered prompt hangs rather than failing.
    $created = & cmd /c "echo no | `"$avdManager`" create avd -n $($target.avdName) -k `"$SystemImage`" -d $($target.deviceProfile)" 2>&1
    if (-not (Test-Path -Path $avdConfig -PathType Leaf)) {
        Fail 2 "avdmanager did not create $($target.avdName): $($created -join ' ')"
    }

    $script:result.data = [ordered]@{ avdName = $target.avdName; state = 'created'; config = $avdConfig }
    if (-not $Json) {
        Write-Host "CREATED $($target.avdName) ($($target.deviceProfile), $($target.dp) dp)" -ForegroundColor Green
        Write-Host "  boot it, then re-run with -Assert to confirm the geometry the device reports" -ForegroundColor Gray
    }
    Emit-Result -Code 0
}

# --- -Assert ------------------------------------------------------------------------------------

$script:result.verb = 'assert'

$adb = Find-Adb
if (-not $adb) { Fail 2 "adb not found - set ANDROID_HOME or put adb on PATH" }

if (-not $DeviceId) {
    $online = @(& $adb devices 2>&1 | Select-Object -Skip 1 |
        Where-Object { $_ -match '^\S+\s+device$' } |
        ForEach-Object { ($_ -split '\s+')[0] })
    if ($online.Count -eq 0) { Fail 2 "no online device" }
    if ($online.Count -gt 1) { Fail 2 "$($online.Count) devices online - pass -DeviceId ($($online -join ', '))" }
    $DeviceId = $online[0]
}
$script:result.data = [ordered]@{ device = $DeviceId }

$sizeLine = (& $adb -s $DeviceId shell wm size 2>&1) -join ' '
$densityLine = (& $adb -s $DeviceId shell wm density 2>&1) -join ' '
if ($sizeLine -notmatch '(\d+)x(\d+)') { Fail 2 "could not read screen size from $DeviceId ($sizeLine)" }
$actualWidth = [int]$Matches[1]
$actualHeight = [int]$Matches[2]
if ($densityLine -notmatch '(\d+)') { Fail 2 "could not read density from $DeviceId ($densityLine)" }
$actualDensity = [int]$Matches[1]

$displays = (& $adb -s $DeviceId shell dumpsys window displays 2>&1) -join "`n"
$actualRadius = 0
if ($displays -match 'RoundedCorner\{position=TopLeft,\s*radius=(\d+)') { $actualRadius = [int]$Matches[1] }
$actualRound = ($actualRadius -gt 0)
$actualDp = [math]::Round($actualWidth / $actualDensity * 160, 1)

$script:result.data.actual = [ordered]@{
    widthPx = $actualWidth; heightPx = $actualHeight; densityDpi = $actualDensity
    dp = $actualDp; round = $actualRound; cornerRadiusPx = $actualRadius
}
$script:result.data.expected = [ordered]@{
    widthPx = [int]$target.widthPx; heightPx = [int]$target.heightPx; densityDpi = [int]$target.densityDpi
    dp = [double]$target.dp; round = [bool]$target.round; contentBoxDp = [double]$target.contentBoxDp
}

$mismatches = [System.Collections.Generic.List[string]]::new()
if ($actualWidth -ne [int]$target.widthPx) { $mismatches.Add("width $actualWidth px, expected $($target.widthPx) px") }
if ($actualHeight -ne [int]$target.heightPx) { $mismatches.Add("height $actualHeight px, expected $($target.heightPx) px") }
if ($actualDensity -ne [int]$target.densityDpi) { $mismatches.Add("density $actualDensity dpi, expected $($target.densityDpi) dpi") }
if ($actualRound -ne [bool]$target.round) { $mismatches.Add("round=$actualRound, expected round=$($target.round)") }
$script:result.data.mismatches = $mismatches.ToArray()

if (-not $Json) {
    Write-Host ("DEVICE {0} - {1}x{2} px at {3} dpi = {4} dp{5}" -f `
        $DeviceId, $actualWidth, $actualHeight, $actualDensity, $actualDp, $(if ($actualRound) { ' round' } else { ' not round' })) -ForegroundColor Gray
    Write-Host ("PROFILE {0} - {1}x{2} px at {3} dpi = {4} dp, content box {5} dp" -f `
        $target.id, $target.widthPx, $target.heightPx, $target.densityDpi, $target.dp, $target.contentBoxDp) -ForegroundColor Gray
}

if ($mismatches.Count -gt 0) {
    if (-not $Json) {
        foreach ($m in $mismatches) { Write-Host "  MISMATCH $m" -ForegroundColor Red }
    }
    Fail 1 "$DeviceId is not profile '$($target.id)' - measurements taken here are evidence about a different screen"
}

if (-not $Json) {
    $mark = if ($target.reviewedByPlay) { "reviewed by Play ($($declared.criterion))" } else { 'control shape, not reviewed by Play' }
    Write-Host "MATCH $DeviceId is '$($target.id)' - $mark" -ForegroundColor Cyan
}
Emit-Result -Code 0
