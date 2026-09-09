#requires -Version 7.0
# Subject: scripts/quality/assert-play-listing-screenshot-geometry.ps1
<#
.SYNOPSIS
    Regression suite for the ALPHA and FRAME dimensions of assert-play-listing-screenshot-geometry.ps1
    (S2764).

.DESCRIPTION
    Both dimensions exist to refuse an upload Play would reject, and on the live corpus both are
    green - so the refusal is the half nothing would otherwise execute. A gate whose red path nobody
    ran carries exactly the unobserved verdict it was written to prevent, which is why the defective
    fixtures are built here rather than described in a spec.

    Asserted:
      * the live wear frames pass, including their RGBA alpha channel carrying no transparency,
      * a device frame fails, is named, and its measured ring width is printed,
      * the same frame passes once -MaxFrameRingWidth is raised past it - the ceiling is a real dial,
        not a hard-coded verdict,
      * a rim narrower than the ceiling passes, so an antialiased round edge is not read as a frame,
      * fully and partially transparent pixels each fail as ALPHA,
      * an absent listing root and a root holding no PNG both exit 2 - "could not look" must never be
        spelled the way "found a defect" is.

    The fixtures live under temp/scratch/ and are removed afterwards; they are derived from the real
    screenshots in play/listing, which this suite only ever reads.

.NOTES
    Exit codes:
      0   all cases pass.
      1   at least one case failed.
      2   the fixtures could not be prepared (the venv python or Pillow absent, or the source
          screenshots missing).
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else { 'pwsh' }

$gatePs1 = Join-Path $repoRoot 'scripts/quality/assert-play-listing-screenshot-geometry.ps1'
$venvPython = Join-Path $repoRoot '.venv/Scripts/python.exe'
$builder = Join-Path $PSScriptRoot 'make-fixture.py'
$sandbox = Join-Path $repoRoot 'temp/scratch/listing-geometry-suite'

$script:pass = 0
$script:fail = 0

function Assert-That([string]$Name, [bool]$Ok, [string]$Detail) {
    if ($Ok) {
        Write-Host "  PASS  $Name" -ForegroundColor Green
        $script:pass++
    }
    else {
        Write-Host "  FAIL  $Name" -ForegroundColor Red
        if ($Detail) { Write-Host "        $Detail" -ForegroundColor DarkGray }
        $script:fail++
    }
}

function Invoke-Gate([string[]]$Arguments) {
    $out = & $pwshExe -NoProfile -File $gatePs1 @Arguments 2>&1
    return [pscustomobject]@{
        Code = [int]$LASTEXITCODE
        Text = (($out | ForEach-Object { [string]$_ }) -join "`n")
    }
}

function New-Fixture([string]$Kind) {
    $root = Join-Path $sandbox $Kind
    if (Test-Path -LiteralPath $root) { Remove-Item -LiteralPath $root -Recurse -Force }
    $out = & $venvPython $builder $Kind $root 2>&1
    if ($LASTEXITCODE -ne 0) { throw "fixture '$Kind' failed: $($out -join '; ')" }
    return $root
}

if (-not (Test-Path -LiteralPath $venvPython)) {
    Write-Host "Fixtures could not be prepared: venv python not found at $venvPython" -ForegroundColor Yellow
    exit 2
}

$prepared = $false
try {
    New-Item -ItemType Directory -Path $sandbox -Force | Out-Null

    # A. the live corpus. Its wear frames are RGBA, so this also asserts that an alpha CHANNEL with
    # no transparent pixel in it is not a finding - the distinction the whole ALPHA dimension rests
    # on, and the one the pre-S2764 report could not make.
    $cleanRoot = New-Fixture 'clean'
    $prepared = $true
    $a = Invoke-Gate @('-ListingRoot', $cleanRoot)
    Assert-That 'A. the live wear frames pass' ($a.Code -eq 0) "exit $($a.Code): $($a.Text)"
    Assert-That 'A2. and the verdict says transparency was judged' ($a.Text -match 'no transparency') $a.Text
    Assert-That 'A3. and the summary prints the widest frame ring' ($a.Text -match 'widest frame ring') $a.Text

    # B. a device frame: content shrunk into a smaller circle, a flat bezel around it.
    $framedRoot = New-Fixture 'framed'
    $b = Invoke-Gate @('-ListingRoot', $framedRoot)
    Assert-That 'B. a device frame fails' ($b.Code -eq 1) "exit $($b.Code): $($b.Text)"
    Assert-That 'B2. the refusal is named FRAME and carries the file' (
        $b.Text -match 'FRAME\s+\S+01\.png'
    ) $b.Text
    Assert-That 'B3. the refusal prints the measured ring width' ($b.Text -match 'ring 0\.\d+ of the radius') $b.Text
    Assert-That 'B4. the refusal names the repair' ($b.Text -match 'recapture without the watch bezel') $b.Text

    # C. the ceiling is a dial. The bezel of fixture B is BRIGHTER than the dim content it covers,
    # so this case also pins the criterion to ring width: a brightness-step test would have scored
    # this frame the wrong way round in the first place (S2764 3.2).
    $c = Invoke-Gate @('-ListingRoot', $framedRoot, '-MaxFrameRingWidth', '0.30')
    Assert-That 'C. the same frame passes under a raised ceiling' ($c.Code -eq 0) "exit $($c.Code): $($c.Text)"

    # D. a rim narrower than the ceiling. An antialiased round edge leaves a thin dark rim on every
    # honest watch screenshot; reading that as a frame would redden the live listing.
    $narrowRoot = New-Fixture 'framed-narrow'
    $d = Invoke-Gate @('-ListingRoot', $narrowRoot)
    Assert-That 'D. a rim under the ceiling is not a frame' ($d.Code -eq 0) "exit $($d.Code): $($d.Text)"

    # E. transparency, both kinds, on an otherwise clean corpus.
    $transparentRoot = New-Fixture 'transparent'
    $e = Invoke-Gate @('-ListingRoot', $transparentRoot)
    Assert-That 'E. transparent pixels fail' ($e.Code -eq 1) "exit $($e.Code): $($e.Text)"
    Assert-That 'E2. the refusal is named ALPHA and carries the file' (
        $e.Text -match 'ALPHA\s+\S+02\.png'
    ) $e.Text
    Assert-That 'E3. it reports both the full and the partial share' (
        $e.Text -match 'fully transparent' -and $e.Text -match 'partially'
    ) $e.Text
    Assert-That 'E4. the four clean frames beside it are not reported' (
        $e.Text -match 'FAIL \(1 finding\(s\) over 5 image\(s\)\)'
    ) $e.Text

    # F. an absent root and an empty one are both "could not look".
    $f = Invoke-Gate @('-ListingRoot', (Join-Path $sandbox 'no-such-root'))
    Assert-That 'F. an absent listing root exits 2' ($f.Code -eq 2) "exit $($f.Code): $($f.Text)"
    $emptyRoot = Join-Path $sandbox 'empty'
    New-Item -ItemType Directory -Path $emptyRoot -Force | Out-Null
    $g = Invoke-Gate @('-ListingRoot', $emptyRoot)
    Assert-That 'F2. a root holding no PNG exits 2' ($g.Code -eq 2) "exit $($g.Code): $($g.Text)"
    Assert-That 'F3. and says it could not verify' ($g.Text -match 'CANNOT VERIFY') $g.Text
}
catch {
    Write-Host "  fixture error: $($_.Exception.Message)" -ForegroundColor Yellow
}
finally {
    if (Test-Path -LiteralPath $sandbox) {
        Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if (-not $prepared) {
    Write-Host 'Fixtures could not be prepared.' -ForegroundColor Yellow
    exit 2
}

Write-Host ''
Write-Host ("passed: {0}  failed: {1}" -f $script:pass, $script:fail) -ForegroundColor Cyan
if ($script:fail -gt 0) {
    Write-Host 'assert-play-listing-screenshot-geometry suite: FAIL' -ForegroundColor Red
    exit 1
}
exit 0
