<#
.SYNOPSIS
    Generate the splash brand drawable for every locale that declares `splash_slogan`.

.DESCRIPTION
    S1706. The splash centre content is a picture, not a layout, so the slogan has to be
    baked into contours and a separate drawable produced per locale. This script is the
    only writer of `ic_splash_app_brand.xml` and of every `drawable-<locale>` variant of
    it - hand-editing one of those files is caught by scripts/quality/assert-splash-brand-sync.ps1.

    S4784: the mark is the product master assets/identity/master/mark.svg, coloured through the
    generated @color/identity_mark_* resources, and the wordmark is drawn from the family typeface
    assets/identity/font/Manrope-ExtraBold.ttf - all three inputs come from
    scripts/identity/build-identity.ps1, so rerun that first when the family mark changes.

    S2593: phone-only. The watch once took an arrows-only composition here, but S2274 removed
    `windowSplashScreenAnimatedIcon` from the wear theme for Play requirement WO-V15 - the platform
    draws the launcher icon itself - so the generated watch glyph lost its last consumer and both
    it and this branch were retired.

.PARAMETER Module
    Which module's resources to generate into. app_v2 is the only one; see S2593 above.

.PARAMETER Check
    Compare only. Report what would change and fail instead of writing.

.OUTPUTS
    Exit 0 - drawables generated, or already in sync under -Check.
    Exit 1 - a slogan does not fit the mask circle, or -Check found a divergence.
    Exit 2 - could not verify: python or fontTools missing, or the product master, the family tokens or the wordmark font absent.
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2')]
    [string]$Module = 'app_v2',

    [switch]$Check
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resRoot = Join-Path $repoRoot "$Module/src/main/res"
$generator = Join-Path $PSScriptRoot 'splash_brand_gen.py'
$font = Join-Path $repoRoot 'assets/identity/font/Manrope-ExtraBold.ttf'
$master = Join-Path $repoRoot 'assets/identity/master/mark.svg'
$tokens = Join-Path $repoRoot 'assets/identity/family/tokens.json'

if (-not (Test-Path $resRoot)) {
    Write-Error "generate-splash-brand: resource root not found: $resRoot" -ErrorAction Continue
    exit 2
}
foreach ($source in @($font, $master, $tokens)) {
    if (-not (Test-Path $source)) {
        Write-Error "generate-splash-brand: input not found: $source" -ErrorAction Continue
        exit 2
    }
}

# A candidate is accepted only once fontTools imports in it, so the probe RUNS each one - and that
# is why the Store aliases must be filtered out BEFORE the loop rather than be allowed to fail in it
# (S3342). `python3` and `python` under WindowsApps are zero-length execution aliases; running one
# does not exit non-zero as the earlier note here assumed, it opens Windows' "Select an app to open
# 'python3'" picker in front of the owner. Test-StoreAliasPath decides that from a file stat.
. (Join-Path $PSScriptRoot 'lib/python-interpreter.ps1')
$candidates = @(
    (Join-Path $repoRoot '.venv/Scripts/python.exe'),
    (Get-Command python -CommandType Application -All -ErrorAction SilentlyContinue | ForEach-Object { $_.Source }),
    (Get-Command py -CommandType Application -All -ErrorAction SilentlyContinue | ForEach-Object { $_.Source })
) | Where-Object { $_ -and (Test-Path $_) -and -not (Test-StoreAliasPath -Path $_) } | Select-Object -Unique

$pythonExe = $null
foreach ($candidate in $candidates) {
    & $candidate -c 'import fontTools' 2>$null
    if ($LASTEXITCODE -eq 0) { $pythonExe = $candidate; break }
}
if (-not $pythonExe) {
    Write-Error ('generate-splash-brand: no python with fontTools found (tried: {0}) - pip install fonttools' -f ($candidates -join ', ')) -ErrorAction Continue
    exit 2
}

$genArgs = @($generator, '--res-root', $resRoot, '--font', $font, '--master', $master, '--tokens', $tokens)
if ($Check) { $genArgs += '--check' }

& $pythonExe @genArgs
$code = $LASTEXITCODE
if ($code -ne 0) {
    Write-Error "generate-splash-brand: the generator reported a problem for module '$Module' (see its output above) - a slogan that does not fit the mask circle must be shortened in strings.xml, and under -Check a divergence is repaired by re-running without -Check" -ErrorAction Continue
    exit 1
}
exit 0
