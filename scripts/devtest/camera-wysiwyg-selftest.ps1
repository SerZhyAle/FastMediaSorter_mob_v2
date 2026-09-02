<#
.SYNOPSIS
  S1920: check that camera_fov_compare.py can actually FAIL before its PASS is believed.

.DESCRIPTION
  camera-wysiwyg-sweep.ps1 answers "does the saved photo match the viewfinder". A run of it that
  cannot fail says nothing, and the 2026-08-21 sweep returned PASS on every measured cell while the
  owner was still seeing a cropped frame - so the comparator itself needs a control.

  Two cases are put through the comparator:

    positive  - a pair that agrees            -> must report keep 1.0/1.0 and exit 0
    negative  - the same pair, photo centre-cropped to 0.74 on both axes
                                              -> must report FAIL, exit 1, and recover 0.74

  The negative case is the whole point: the injected crop is known exactly, so the comparator has to
  both refuse the pair AND name the crop it was given. A tool that fails for the wrong reason passes
  a bare FAIL check.

  By default the pair is synthesised, so this runs on any checkout with no device and no stored
  artifacts. Point -Screenshot and -Photo at a real captured pair to run the same two cases against
  device data instead; a named pair that is missing is an error, never a silent fall back to
  synthetic.

.PARAMETER Screenshot
  Viewfinder screenshot PNG. Requires -Photo. Omit both to synthesise the pair.

.PARAMETER Photo
  The JPEG the shutter produced. Requires -Screenshot.

.PARAMETER OutDir
  Where the synthesised pair and the cropped negative land. Default temp/S1920/selftest.

.EXITCODES
  0 - both cases behaved: the comparator passed the matching pair and failed the cropped one
  1 - the comparator misbehaved on at least one case (it is the harness that is broken, not the app)
  2 - could not run: no usable Python, comparator missing, or a named pair that does not exist
#>
[CmdletBinding()]
param(
    [string]$Screenshot,
    [string]$Photo,
    [string]$OutDir = 'temp/S1920/selftest'
)

$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

# The crop injected into the negative case. Recovered value must land within Tolerance of it, so the
# comparator is held to naming the crop rather than merely disliking the pair.
$InjectedKeep = 0.74
$Tolerance = 0.03

$comparator = Join-Path $PSScriptRoot 'camera_fov_compare.py'

function Resolve-Python {
    # Same reasoning as camera-wysiwyg-sweep.ps1: `python3` under PowerShell is the Windows Store stub.
    $repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $venv = Join-Path $repoRoot '.venv/Scripts/python.exe'
    if (Test-Path $venv) { return $venv }
    foreach ($name in 'python', 'py') {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd -and $cmd.Source -notmatch 'WindowsApps') { return $cmd.Source }
    }
    return $null
}

if (-not (Test-Path $comparator)) {
    Write-Host "camera-wysiwyg-selftest: comparator missing at $comparator"
    exit 2
}
$python = Resolve-Python
if (-not $python) {
    Write-Host 'camera-wysiwyg-selftest: no usable Python found (need the repo .venv, or python/py with PIL + numpy).'
    exit 2
}
if (($Screenshot -and -not $Photo) -or ($Photo -and -not $Screenshot)) {
    Write-Host 'camera-wysiwyg-selftest: -Screenshot and -Photo go together - give both or neither.'
    exit 2
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if ($Screenshot) {
    foreach ($p in @($Screenshot, $Photo)) {
        if (-not (Test-Path $p)) {
            Write-Host "camera-wysiwyg-selftest: named pair member '$p' does not exist."
            exit 2
        }
    }
    $shotPath = (Resolve-Path $Screenshot).Path
    $photoPath = (Resolve-Path $Photo).Path
    # A real screenshot is a whole screen; the preview band inside it is derived from the photo's shape,
    # which is what the sweep does too, so both paths measure the same way.
    $region = $null
} else {
    $shotPath = Join-Path $OutDir 'synthetic_screenshot.png'
    $photoPath = Join-Path $OutDir 'synthetic_photo.jpg'
    $region = '0,0,1080,2400'
}
$negativePath = Join-Path $OutDir 'negative_photo.jpg'

$prepare = @"
import sys
import numpy as np
from PIL import Image

shot_path, photo_path, negative_path, synth, keep = sys.argv[1:6]
keep = float(keep)

if synth == 'yes':
    # A deterministic textured scene: the comparator refuses a flat one, and a random one would make a
    # failure unreproducible. 4:3 content letterboxed into a 1080x2400 view, matching the measured
    # Galaxy S21 geometry the strategic spec records.
    # Structure has to be LOW frequency: the comparator scores on a 128x128 raster, and per-pixel noise
    # averages away to a flat grey there, which it then correctly refuses as a scene it cannot match.
    rng = np.random.default_rng(1920)
    coarse = rng.integers(0, 255, size=(32, 24), dtype=np.uint8)
    scene = np.asarray(Image.fromarray(coarse).resize((1080, 1440), Image.BICUBIC))
    view = np.zeros((2400, 1080), dtype=np.uint8)
    view[480:1920, :] = scene
    Image.fromarray(view).save(shot_path)
    # The sensor writes landscape: the frame is ROTATED into it, not squashed into it. Resizing a
    # portrait scene straight into a landscape box distorts it, and the comparator - which rotates the
    # photo back before matching - then sees a scene that correlates with nothing.
    sensor = Image.fromarray(scene).rotate(90, expand=True)
    sensor.resize((4032, 3024), Image.BILINEAR).save(photo_path, quality=95)

photo = Image.open(photo_path)
w, h = photo.size
cw, ch = int(w * keep), int(h * keep)
photo.crop(((w - cw) // 2, (h - ch) // 2, (w - cw) // 2 + cw, (h - ch) // 2 + ch)).save(negative_path, quality=95)

# S1986: the two rotation negatives. PIL rotates counter-clockwise, so -90 is a clockwise quarter
# turn. 180 is the case no long-axis rule can ever catch - the frame keeps its exact shape.
photo.rotate(-90, expand=True).save(negative_path.replace('negative_photo', 'rot90_photo'), quality=95)
photo.rotate(180, expand=True).save(negative_path.replace('negative_photo', 'rot180_photo'), quality=95)
"@

$synth = if ($Screenshot) { 'no' } else { 'yes' }
$prepare | & $python - $shotPath $photoPath $negativePath $synth $InjectedKeep
if ($LASTEXITCODE -ne 0) {
    Write-Host 'camera-wysiwyg-selftest: could not prepare the pair (is PIL/numpy installed in the venv?).'
    exit 2
}

function Invoke-Comparator {
    param([string]$PhotoPath, [string]$Label, [int]$ExpectRotation = -1)
    $comparatorArgs = @(
        $comparator,
        '--screenshot', $shotPath,
        '--photo', $PhotoPath,
        '--expect-fx', '1.0',
        '--expect-fy', '1.0',
        '--tolerance', '0.05',
        '--label', $Label,
        '--json'
    )
    # The synthetic scene's shape is known here - it is this script that drew it - so the band comes
    # from that shape rather than from the photo. A named real pair carries no declared shape, so it
    # keeps the circular derivation, which is what the sweep used to do for every cell.
    if ($synth -eq 'yes') {
        $comparatorArgs += @('--content-from-aspect', '1.3333')
    } else {
        $comparatorArgs += '--content-from-photo'
    }
    if ($ExpectRotation -ge 0) { $comparatorArgs += @('--expect-rotation', "$ExpectRotation") }
    if ($region) { $comparatorArgs += @('--region', $region) }
    $out = & $python @comparatorArgs 2>&1 | Out-String
    return @{ code = $LASTEXITCODE; text = $out.Trim() }
}

$failures = @()

# The sensor frame is stored landscape while the viewfinder band is portrait, so the matching pair
# genuinely needs one clockwise quarter turn. Stating that expectation here means the positive case
# also proves the rotation channel answers, instead of only the field-of-view one.
$ExpectedTurn = if ($synth -eq 'yes') { 90 } else { -1 }
$positive = Invoke-Comparator -PhotoPath $photoPath -Label 'selftest-positive' -ExpectRotation $ExpectedTurn
if ($positive.code -ne 0) {
    $failures += "positive case: expected PASS (code 0), got $($positive.code) - $($positive.text)"
    Write-Host "  positive - UNEXPECTED exit $($positive.code): $($positive.text)"
} else {
    Write-Host "  positive - PASS as expected: $($positive.text)"
}

$negative = Invoke-Comparator -PhotoPath $negativePath -Label 'selftest-negative'
if ($negative.code -ne 1) {
    # S2075: worded "expected FAIL (code 1)" rather than "expected exit 1" - the exit-contract gate's
    # line-based scan (assert-exit-contract.ps1 Rule C) reads "exit <digit>" anywhere on the line,
    # including inside a string literal, as an unreachable script exit and flagged this message text.
    $failures += "negative case: expected FAIL (code 1), got $($negative.code) - $($negative.text)"
    Write-Host "  negative - UNEXPECTED exit $($negative.code): $($negative.text)"
} else {
    $recovered = $null
    try { $recovered = ($negative.text | ConvertFrom-Json).keep_fx } catch { $recovered = $null }
    if ($null -eq $recovered) {
        $failures += 'negative case: comparator failed but reported no keep_fx to check against the injected crop.'
        Write-Host "  negative - FAIL reported but unreadable: $($negative.text)"
    } elseif ([math]::Abs($recovered - $InjectedKeep) -gt $Tolerance) {
        $failures += "negative case: injected $InjectedKeep but comparator recovered $recovered - it failed for the wrong reason."
        Write-Host "  negative - FAIL reported, but recovered $recovered instead of $InjectedKeep"
    } else {
        Write-Host "  negative - FAIL as expected, and recovered the injected crop: $recovered"
    }
}

# S1986: the two rotation negatives. Without them a PASS says only "same field of view", which is
# exactly how a sweep reported PASS on every cell while the owner was holding a wrongly rotated photo.
if ($synth -eq 'yes') {
    $rotationCases = @(
        @{ file = 'rot90_photo.jpg'; injected = 90; expect = 0 },
        @{ file = 'rot180_photo.jpg'; injected = 180; expect = 270 }
    )
    foreach ($case in $rotationCases) {
        $path = Join-Path $OutDir $case.file
        $label = "selftest-rot$($case.injected)"
        # The pair still shows the same scene, so only the rotation expectation may fail it - which is
        # the point: a tool that fails this case on the field of view would be failing for the wrong reason.
        $res = Invoke-Comparator -PhotoPath $path -Label $label -ExpectRotation $ExpectedTurn
        $measured = $null
        try { $measured = ($res.text | ConvertFrom-Json).rotation_deg } catch { $measured = $null }
        if ($res.code -ne 1) {
            $failures += "rotation $($case.injected): expected FAIL (code 1), got $($res.code) - $($res.text)"
            Write-Host "  rot$($case.injected) - UNEXPECTED exit $($res.code): $($res.text)"
        } elseif ($measured -ne $case.expect) {
            $failures += "rotation $($case.injected): expected the comparator to measure $($case.expect), it measured $measured."
            Write-Host "  rot$($case.injected) - FAIL reported, but measured $measured instead of $($case.expect)"
        } else {
            Write-Host "  rot$($case.injected) - FAIL as expected, and named the turn: $measured"
        }
    }
}

Write-Host ''
if ($failures.Count -gt 0) {
    Write-Host 'camera-wysiwyg-selftest: the comparator did not behave - a sweep verdict cannot be trusted until this passes.'
    $failures | ForEach-Object { Write-Host "  - $_" }
    exit 1
}
Write-Host 'camera-wysiwyg-selftest: comparator passes a matching pair and fails an injected crop - sweep verdicts are meaningful.'
exit 0
