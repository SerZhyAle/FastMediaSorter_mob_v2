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
"@

$synth = if ($Screenshot) { 'no' } else { 'yes' }
$prepare | & $python - $shotPath $photoPath $negativePath $synth $InjectedKeep
if ($LASTEXITCODE -ne 0) {
    Write-Host 'camera-wysiwyg-selftest: could not prepare the pair (is PIL/numpy installed in the venv?).'
    exit 2
}

function Invoke-Comparator {
    param([string]$PhotoPath, [string]$Label)
    $comparatorArgs = @(
        $comparator,
        '--screenshot', $shotPath,
        '--photo', $PhotoPath,
        '--content-from-photo',
        '--expect-fx', '1.0',
        '--expect-fy', '1.0',
        '--tolerance', '0.05',
        '--label', $Label,
        '--json'
    )
    if ($region) { $comparatorArgs += @('--region', $region) }
    $out = & $python @comparatorArgs 2>&1 | Out-String
    return @{ code = $LASTEXITCODE; text = $out.Trim() }
}

$failures = @()

$positive = Invoke-Comparator -PhotoPath $photoPath -Label 'selftest-positive'
if ($positive.code -ne 0) {
    $failures += "positive case: expected exit 0 (PASS), got $($positive.code) - $($positive.text)"
    Write-Host "  positive - UNEXPECTED exit $($positive.code): $($positive.text)"
} else {
    Write-Host "  positive - PASS as expected: $($positive.text)"
}

$negative = Invoke-Comparator -PhotoPath $negativePath -Label 'selftest-negative'
if ($negative.code -ne 1) {
    $failures += "negative case: expected exit 1 (FAIL), got $($negative.code) - $($negative.text)"
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

Write-Host ''
if ($failures.Count -gt 0) {
    Write-Host 'camera-wysiwyg-selftest: the comparator did not behave - a sweep verdict cannot be trusted until this passes.'
    $failures | ForEach-Object { Write-Host "  - $_" }
    exit 1
}
Write-Host 'camera-wysiwyg-selftest: comparator passes a matching pair and fails an injected crop - sweep verdicts are meaningful.'
exit 0
