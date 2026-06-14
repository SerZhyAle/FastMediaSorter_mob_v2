#requires -Version 7.0
<#
.SYNOPSIS
    Rank Kotlin classes by a responsibility proxy, not by raw line count.

.DESCRIPTION
    File length is only a smell signal - the real "too many responsibilities"
    indicator is how many distinct things a class juggles. This script scores each
    src/main Kotlin file at or above a size floor by:
      - publicApi     : public functions (the surface other code depends on)
      - collaborators : private val/var members (injected deps + retained state)
      - callbackSites : listener / observer / receiver registrations
      - extractMarkers: comments admitting cosmetic decomposition
                        ("extracted to keep under", "split to keep", "keep under cap")
    responsibilityScore = publicApi + collaborators + callbackSites + 3*extractMarkers.
    LOC is reported for context but never scored.

.PARAMETER Floor
    Minimum LOC for a file to be considered (default 600).

.PARAMETER Top
    How many top-ranked files to print (default 15).

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/measure-hotspots.ps1
#>
[CmdletBinding()]
param(
    [int]$Floor = 600,
    [int]$Top = 15
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$mainRoot = Join-Path $repoRoot 'app_v2/src/main/java'

$publicFunRx = [regex]'^\s*(?:@\w+\s*)*(?:override\s+|open\s+|suspend\s+|inline\s+|operator\s+|tailrec\s+)*fun\s'
$nonPublicRx = [regex]'^\s*(?:@\w+\s*)*(?:private|internal|protected)\b'
$memberRx = [regex]'^\s*(?:private|internal)\s+(?:val|var)\s'
$callbackRx = [regex]'set[A-Za-z]*Listener\(|addListener\(|\.observe\(|\.observeForever\(|registerReceiver\(|registerForActivityResult\(|addCallback\(|addTextChangedListener\('
$extractRx = [regex]'(?i)extracted to keep|split to keep|keep .*under (the )?cap|keep under \d|to stay under'

$rows = [System.Collections.Generic.List[object]]::new()

$files = Get-ChildItem -LiteralPath $mainRoot -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue
foreach ($file in $files) {
    $lines = [System.IO.File]::ReadAllLines($file.FullName)
    if ($lines.Count -lt $Floor) { continue }

    $publicApi = 0; $collaborators = 0; $callbackSites = 0; $extractMarkers = 0
    foreach ($line in $lines) {
        if ($publicFunRx.IsMatch($line) -and -not $nonPublicRx.IsMatch($line)) { $publicApi++ }
        if ($memberRx.IsMatch($line)) { $collaborators++ }
        if ($callbackRx.IsMatch($line)) { $callbackSites++ }
        if ($extractRx.IsMatch($line)) { $extractMarkers++ }
    }
    $score = $publicApi + $collaborators + $callbackSites + (3 * $extractMarkers)
    $rel = ($file.FullName.Substring($repoRoot.Length).TrimStart('\', '/')) -replace '\\', '/'
    $rows.Add([pscustomobject]@{
        Score          = $score
        LOC            = $lines.Count
        PublicApi      = $publicApi
        Collaborators  = $collaborators
        Callbacks      = $callbackSites
        ExtractMarkers = $extractMarkers
        File           = $rel
    })
}

$ranked = $rows | Sort-Object -Property Score -Descending | Select-Object -First $Top
Write-Host ("Responsibility ranking (floor {0} LOC, top {1}):" -f $Floor, $Top)
Write-Host ("{0,5}  {1,5}  {2,4}  {3,4}  {4,3}  {5,3}  {6}" -f 'SCORE', 'LOC', 'API', 'COL', 'CB', 'EXT', 'FILE')
foreach ($r in $ranked) {
    Write-Host ("{0,5}  {1,5}  {2,4}  {3,4}  {4,3}  {5,3}  {6}" -f $r.Score, $r.LOC, $r.PublicApi, $r.Collaborators, $r.Callbacks, $r.ExtractMarkers, $r.File)
}
Write-Host ("measure-hotspots: scanned {0} files >= {1} LOC" -f $rows.Count, $Floor)
