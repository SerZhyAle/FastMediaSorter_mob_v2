<#
.SYNOPSIS
    Generate every rendition of the product mark and assets/identity/identity-manifest.json (PRODUCT-IDENTITY 0.2).

.DESCRIPTION
    The one generator command named in the identity manifest. Runs scripts/identity/build_identity.py, which composes
    the product master from the FastMediaSorter family folder copied into assets/identity/family/ (S4784) and cuts
    every rendition - launcher layers, store icon, favicons, social cards, README head, hub card, help head, SVG
    favicon, the badged edition and debug launcher layers and the watch launcher - then writes the manifest.
    A rerun on unchanged inputs changes no byte; -Check proves it without writing and then runs the family check
    (conformance rung 8): the local copy against its recorded hashes, family.sha256 against it, and the catalog's
    family-mark.json against it when FMS_CONTRACTS_ROOT points at the catalog.

    The interpreter is the first real Python (never a Store alias, scripts/utils/lib/python-interpreter.ps1) in which
    Pillow, PyMuPDF and fontTools all import - the lockups are drawn from font outlines.

.PARAMETER Check
    Render in memory and compare with the files on disk, then run the family check; write nothing.

.EXAMPLE
    pwsh -NoProfile -File scripts/identity/build-identity.ps1
    pwsh -NoProfile -File scripts/identity/build-identity.ps1 -Check

.NOTES
    Exit codes:
      0  every rendition written, or with -Check every file equals its rendition and the family matches the catalog.
      1  -Check found a rendition missing or different, or the family copy or family.sha256 disagrees.
      2  COULD NOT VERIFY: no Python with Pillow, PyMuPDF and fontTools, an input missing, or with -Check everything
         matched except the catalog half of the family check, which needs the catalog on this machine.
#>
[CmdletBinding()]
param(
    [switch]$Check
)
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $repoRoot 'scripts/utils/lib/python-interpreter.ps1')
$candidates = @(
    (Join-Path $repoRoot '.venv/Scripts/python.exe'),
    (Get-Command python -CommandType Application -All -ErrorAction SilentlyContinue | ForEach-Object { $_.Source }),
    (Get-Command py -CommandType Application -All -ErrorAction SilentlyContinue | ForEach-Object { $_.Source })
) | Where-Object { $_ -and (Test-Path $_) -and -not (Test-StoreAliasPath -Path $_) } | Select-Object -Unique

$pythonExe = $null
foreach ($candidate in $candidates) {
    & $candidate -c 'import PIL, pymupdf, fontTools' 2>$null
    if ($LASTEXITCODE -eq 0) { $pythonExe = $candidate; break }
}
if (-not $pythonExe) {
    Write-Output ('build-identity: COULD NOT VERIFY (no Python with Pillow, PyMuPDF and fontTools; tried: {0})' -f ($candidates -join ', '))
    exit 2
}
$pyArgs = @((Join-Path $PSScriptRoot 'build_identity.py'))
if ($Check) { $pyArgs += '--check' }
& $pythonExe @pyArgs
$code = $LASTEXITCODE
if ($code -notin 0, 1, 2) { $code = 2 }
exit $code
