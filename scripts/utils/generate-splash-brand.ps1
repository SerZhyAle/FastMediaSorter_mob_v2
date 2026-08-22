<#
.SYNOPSIS
    Generate the splash brand drawable for every locale that declares `splash_slogan`.

.DESCRIPTION
    S1706. The splash centre content is a picture, not a layout, so the slogan has to be
    baked into contours and a separate drawable produced per locale. This script is the
    only writer of `ic_splash_app_brand.xml` and of every `drawable-<locale>` variant of
    it - hand-editing one of those files is caught by scripts/quality/assert-splash-brand-sync.ps1.

.PARAMETER Module
    Which module's resources to generate into. Defaults to app_v2.

.PARAMETER Check
    Compare only. Report what would change and fail instead of writing.

.OUTPUTS
    Exit 0 - drawables generated, or already in sync under -Check.
    Exit 1 - a slogan does not fit the mask circle, or -Check found a divergence.
    Exit 2 - could not verify: python or fontTools missing, or the wordmark data absent.
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string]$Module = 'app_v2',

    [switch]$Check
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resRoot = Join-Path $repoRoot "$Module/src/main/res"
$generator = Join-Path $PSScriptRoot 'splash_brand_gen.py'
$wordmark = Join-Path $PSScriptRoot 'splash-wordmark-path.txt'

if (-not (Test-Path $resRoot)) {
    Write-Error "generate-splash-brand: resource root not found: $resRoot" -ErrorAction Continue
    exit 2
}
if (-not (Test-Path $wordmark)) {
    Write-Error "generate-splash-brand: wordmark contour data not found: $wordmark" -ErrorAction Continue
    exit 2
}

# Candidate order matters: on this machine `python3` resolves to the Microsoft Store stub in
# WindowsApps, which exits non-zero for every command, while the working interpreter is the
# repo's own virtual environment. So a candidate is accepted only once fontTools imports in it.
$candidates = @(
    (Join-Path $repoRoot '.venv/Scripts/python.exe'),
    (Get-Command python -ErrorAction SilentlyContinue).Source,
    (Get-Command python3 -ErrorAction SilentlyContinue).Source
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique

$pythonExe = $null
foreach ($candidate in $candidates) {
    & $candidate -c 'import fontTools' 2>$null
    if ($LASTEXITCODE -eq 0) { $pythonExe = $candidate; break }
}
if (-not $pythonExe) {
    Write-Error ('generate-splash-brand: no python with fontTools found (tried: {0}) - pip install fonttools' -f ($candidates -join ', ')) -ErrorAction Continue
    exit 2
}

$genArgs = @($generator, '--res-root', $resRoot, '--wordmark', $wordmark)
if ($Check) { $genArgs += '--check' }
# The watch drops the wordmark and the slogan - measured illegible at watch size, S1706 step 05.4 -
# so its composition is the logo alone and carries no per-locale variant.
if ($Module -eq 'wear') { $genArgs += '--arrows-only' }

& $pythonExe @genArgs
$code = $LASTEXITCODE
if ($code -ne 0) {
    Write-Error "generate-splash-brand: the generator reported a problem for module '$Module' (see its output above) - a slogan that does not fit the mask circle must be shortened in strings.xml, and under -Check a divergence is repaired by re-running without -Check" -ErrorAction Continue
    exit 1
}
exit 0
