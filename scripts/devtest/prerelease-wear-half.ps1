<#
.SYNOPSIS
  S4249 watch pre-release sweep on the two reviewed shapes at once - steps 0 to 3 of
  /spec-prerelease-wear in one call.

.DESCRIPTION
  Runs the watch sweep beside the phone sweep instead of after the phone release (strategic S4249
  goal 4). Takes the two reviewed Wear emulators - 192 dp small-round and 227 dp large-round
  (scripts/devtest/wear-shape-profiles.json) - and runs, in this order:

    1. assert-prerelease-content-gates.ps1 -Modules wear (the phone sweep judges app_v2 itself, so a
       phone lexeme gap does not stop the watch half), then assert-no-release-probes.ps1 -Module wear.
       Either one failing ends the run before a device is touched.
    2. Two concurrent lanes (lib/device-half-lanes.ps1):
       - small: wear-prerelease-prepare.ps1 WITH the build, then the shape tail;
       - large: .\a.ps1 fwm, then wear-prerelease-prepare.ps1 -SkipBuild once the small lane's build
         has passed, then the shape tail. The migration run replaces the app under test, which is
         why the release install on that device follows it (procedure step 1.2).
       Shape tail: wear-shape-bench.ps1 -Profile <shape> -Assert, wear-prerelease-walk.ps1, then
       prerelease-verdict.ps1 -Json into <out>\verdict.json. The small shape is judged while the
       large one is still migrating; a failed prepare skips its lane's tail.

  Each shape writes under its own <OutRoot>\wear-prerelease-<shape> directory, so the two walks never
  share a screenshot, a dump, a log or a walk.json. Stage timings go to
  <OutRoot>\wear-prerelease-stages_<stamp>.json.

  Only sweep emulators take part (^emulator-\d+$): the development watch is the owner's everyday
  watch (docs/DEVICE_FLEET.md) and the watch verdict rests on the reviewed emulators by owner
  decision 2026-10-14. The freeze is not taken here: the procedure takes
  release-freeze.ps1 -Scope wear before calling this script and releases it after the verdict.

  Exit codes:
    0 - both shapes passed: gates, migration, bench, walk and verdict all clean
    1 - content failure: a gate, the migration run, a shape assertion, a walk or a verdict failed,
        or a verdict is BLOCKED (a manual screen or an unchecked shape is not release-ready)
    2 - could not verify: a device id is not a sweep emulator or both ids are the same, a setup
        stage failed, a stage could not run to a verdict, or a shape produced no verdict

.PARAMETER SmallDeviceId
  Serial of the small-round (192 dp) Wear emulator.

.PARAMETER LargeDeviceId
  Serial of the large-round (227 dp) Wear emulator.

.PARAMETER WearFlavor
  Which watch variant is judged: standard (default, the Play build) or noLegal.

.PARAMETER OutRoot
  Parent of the two per-shape output directories (default temp/scratch).

.PARAMETER Stamp
  File-name stamp for the timings file (default yyyyMMdd_HHmmss now).

.PARAMETER Json
  Emit one JSON summary object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/prerelease-wear-half.ps1 -SmallDeviceId emulator-5560 -LargeDeviceId emulator-5562
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$SmallDeviceId,
    [Parameter(Mandatory)][string]$LargeDeviceId,
    [ValidateSet('standard', 'noLegal')][string]$WearFlavor = 'standard',
    [string]$OutRoot = 'temp/scratch',
    [string]$Stamp,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'lib/device-half-plan.ps1')
. (Join-Path $PSScriptRoot 'lib/device-half-lanes.ps1')

if (-not $Stamp) { $Stamp = (Get-Date).ToString('yyyyMMdd_HHmmss') }
$outBase = if ([System.IO.Path]::IsPathRooted($OutRoot)) { $OutRoot } else { Join-Path $RepoRoot $OutRoot }
$ChildDir = Join-Path $outBase "wear-half_$Stamp"
New-Item -ItemType Directory -Path $ChildDir -Force | Out-Null
$timingsPath = Join-Path $outBase "wear-prerelease-stages_$Stamp.json"

$stages = [System.Collections.Generic.List[object]]::new()
$results = [System.Collections.Generic.List[object]]::new()
$verdicts = @{}

$shapes = @(
    [pscustomobject]@{ Profile = 'small-round'; Device = $SmallDeviceId; Out = (Join-Path $outBase 'wear-prerelease-small-round') },
    [pscustomobject]@{ Profile = 'large-round'; Device = $LargeDeviceId; Out = (Join-Path $outBase 'wear-prerelease-large-round') }
)

$summary = [ordered]@{
    exitCode = $null
    stamp    = $Stamp
    flavor   = $WearFlavor
    shapes   = @($shapes | ForEach-Object { [ordered]@{ profile = $_.Profile; device = $_.Device; verdict = (Join-Path $_.Out 'verdict.json') } })
    timings  = $timingsPath
}

function Get-PwshArgs {
    param([string]$Script, [string[]]$Rest)
    return @('-NoProfile', '-File', (Join-Path $RepoRoot $Script)) + $Rest
}

function Invoke-HalfLanes {
    param([object[]]$Lanes)
    return Invoke-Lanes -Lanes $Lanes -ChildDir $ChildDir -WorkingDirectory $RepoRoot -Stages $stages -Quiet:$Json
}

function Add-Result {
    param([string]$Kind, $Exit)
    $results.Add([pscustomobject]@{ kind = $Kind; exit = $Exit })
}

# -Early: the run stopped before the verdict stage on a recorded result, so the missing verdicts are
# not themselves a finding - a gate's content failure stays 1 instead of being read as 2.
function Complete-WearHalf {
    param([string]$Reason, [switch]$Early)
    Write-StageTimings -Stages @($stages) -Path $timingsPath | Out-Null
    $expected = if ($Early) { 0 } else { $shapes.Count }
    $code = Get-WearHalfExitCode -Stages @($results) -Verdicts @($shapes | ForEach-Object { $verdicts[$_.Profile] }) -ExpectedVerdicts $expected
    $summary.exitCode = $code
    if ($Reason) { $summary['reason'] = $Reason }
    if ($Json) { $summary | ConvertTo-Json -Depth 5 -Compress }
    else {
        foreach ($s in $shapes) {
            $v = $verdicts[$s.Profile]
            $word = if ($null -eq $v) { 'NO VERDICT' } elseif ($v.blocked) { 'BLOCKED' } elseif ($v.pass) { 'PASS' } else { 'FAIL' }
            Write-Host ("  {0,-12} {1,-14} {2}" -f $s.Profile, $s.Device, $word)
        }
        Write-Host ("WEAR-HALF exit {0} - timings={1}{2}" -f $code, $timingsPath, $(if ($Reason) { " - $Reason" } else { '' }))
    }
    exit $code
}

# ---------- roles ----------
foreach ($id in @($SmallDeviceId, $LargeDeviceId)) {
    if (-not (Test-SweepEmulatorId $id)) {
        Add-Result -Kind 'setup' -Exit $null
        Complete-WearHalf -Early -Reason "device '$id' is not a sweep emulator (^emulator-\d+$); the watch verdict rests on the reviewed emulators"
    }
}
if ($SmallDeviceId -eq $LargeDeviceId) {
    Add-Result -Kind 'setup' -Exit $null
    Complete-WearHalf -Early -Reason 'the two shapes need two emulators; both ids are the same'
}

# ---------- 1. device-free gates ----------
$gateCodes = Invoke-HalfLanes -Lanes @(, @(
        [pscustomobject]@{ Name = 'content-gates'; Device = 'host'; Arguments = (Get-PwshArgs 'scripts/quality/assert-prerelease-content-gates.ps1' @('-Modules', 'wear')) },
        [pscustomobject]@{ Name = 'release-probes'; Device = 'host'; Arguments = (Get-PwshArgs 'scripts/quality/assert-no-release-probes.ps1' @('-Module', 'wear')) }
    ))
Add-Result -Kind 'gate' -Exit $gateCodes['content-gates']
Add-Result -Kind 'gate' -Exit $gateCodes['release-probes']
if ($gateCodes['content-gates'] -ne 0 -or $gateCodes['release-probes'] -ne 0) {
    Complete-WearHalf -Early -Reason 'a device-free gate did not pass - nothing was installed'
}

# ---------- 2. both shapes as lanes ----------
# small lane: prepare WITH the build -> bench -> walk -> verdict.
# large lane: fwm -> prepare -SkipBuild (after fwm by lane order, because fwm replaces the app under
#             test, and after the small lane's build by Requires) -> bench -> walk -> verdict.
# The small shape starts judging while the large one is still migrating.
$small = $shapes[0]
$large = $shapes[1]

# A walk that stops early writes no walk.json, and the verdict stage would then read the previous
# run's file and print a fresh PASS over a shape nobody walked (release 47, small-round: the walk
# refused on two installed packages and the verdict judged the 2026-10-21 walk). Without the old
# files a failed walk leaves the verdict at "walk results not found", exit 2.
foreach ($s in $shapes) {
    foreach ($stale in 'walk.json', 'wear_session.log', 'verdict.json') {
        Remove-Item -LiteralPath (Join-Path $s.Out $stale) -Force -ErrorAction SilentlyContinue
    }
}

function New-ShapeTail {
    param($Shape, [string]$Requires)
    $verdictFile = Join-Path $Shape.Out 'verdict.json'
    $verdictCommand = "& '{0}' -LogFile '{1}' -ArtifactManifest '{2}' -WalkResults '{3}' -Json | Set-Content -LiteralPath '{4}' -Encoding utf8NoBOM; exit `$LASTEXITCODE" -f `
        (Join-Path $RepoRoot 'scripts/devtest/prerelease-verdict.ps1'), (Join-Path $Shape.Out 'wear_session.log'),
        (Join-Path $Shape.Out 'artifact.json'), (Join-Path $Shape.Out 'walk.json'), $verdictFile
    return @(
        [pscustomobject]@{ Name = "bench-$($Shape.Profile)"; Device = $Shape.Device; Requires = @($Requires)
            Arguments = (Get-PwshArgs 'scripts/devtest/wear-shape-bench.ps1' @('-Profile', $Shape.Profile, '-Assert', '-DeviceId', $Shape.Device)) },
        # The walk runs even when the bench disagrees: a shape mismatch is a content finding, and the
        # walk's own reading of the same device is evidence for it.
        [pscustomobject]@{ Name = "walk-$($Shape.Profile)"; Device = $Shape.Device; Requires = @($Requires)
            Arguments = (Get-PwshArgs 'scripts/devtest/wear-prerelease-walk.ps1' @('-DeviceId', $Shape.Device, '-OutDir', $Shape.Out)) },
        [pscustomobject]@{ Name = "verdict-$($Shape.Profile)"; Device = $Shape.Device; Requires = @($Requires)
            Arguments = @('-NoProfile', '-Command', $verdictCommand) }
    )
}

$smallLane = @([pscustomobject]@{ Name = 'prepare-build'; Device = $small.Device
        Arguments = (Get-PwshArgs 'scripts/devtest/wear-prerelease-prepare.ps1' @('-DeviceId', $small.Device, '-WearFlavor', $WearFlavor, '-OutDir', $small.Out)) }) +
    @(New-ShapeTail -Shape $small -Requires 'prepare-build')
$largeLane = @(
    [pscustomobject]@{ Name = 'migration'; Device = $large.Device; Env = @{ FMS_LOCK_BLOCK = '1' }
        Arguments = (Get-PwshArgs 'a.ps1' @('fwm', '-DeviceId', $large.Device)) },
    # Requires the build only: a failed migration run is a content finding and the install still
    # goes ahead, so the large shape is judged either way.
    [pscustomobject]@{ Name = 'prepare-skipbuild'; Device = $large.Device; Requires = @('prepare-build')
        Arguments = (Get-PwshArgs 'scripts/devtest/wear-prerelease-prepare.ps1' @('-DeviceId', $large.Device, '-WearFlavor', $WearFlavor, '-OutDir', $large.Out, '-SkipBuild')) }) +
    @(New-ShapeTail -Shape $large -Requires 'prepare-skipbuild')

$codes = Invoke-HalfLanes -Lanes @(, $smallLane, , $largeLane)

Add-Result -Kind 'setup' -Exit $codes['prepare-build']
Add-Result -Kind 'migration' -Exit $codes['migration']
Add-Result -Kind 'setup' -Exit $codes['prepare-skipbuild']
foreach ($s in $shapes) {
    Add-Result -Kind 'bench' -Exit $codes["bench-$($s.Profile)"]
    Add-Result -Kind 'walk' -Exit $codes["walk-$($s.Profile)"]
    Add-Result -Kind 'verdict' -Exit $codes["verdict-$($s.Profile)"]
    $verdictFile = Join-Path $s.Out 'verdict.json'
    $verdicts[$s.Profile] = $null
    if ($null -ne $codes["verdict-$($s.Profile)"] -and (Test-Path -LiteralPath $verdictFile)) {
        try { $verdicts[$s.Profile] = Get-Content -LiteralPath $verdictFile -Raw | ConvertFrom-Json } catch { $verdicts[$s.Profile] = $null }
    }
}

Complete-WearHalf
