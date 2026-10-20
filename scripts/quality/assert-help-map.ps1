#requires -Version 7.0
<#
.SYNOPSIS
    S4288 gate for contract CONTEXT-HELP rule 19: every phone surface has a help-map row, and every page row has its pages.

.DESCRIPTION
    Enumerates the surfaces of app_v2 through Get-HelpSurfaces in scripts/docs/help/list-help-surfaces.ps1 (the
    one enumeration, from the manifest and the dialog classes) and judges app_v2/src/main/assets/help/help-map.json
    against it. Finding kinds:
      UNMAPPED       - an enumerated surface with no map row and no row in scripts/quality/help-map-baseline.txt;
      NO-SURFACE     - a map row whose id is no enumerated surface;
      NO-REASON      - an `inherit` or `none` row without `reason`;
      NO-PARENT      - an `inherit` row whose `parent` names no map row;
      BAD-FORM       - a row whose `form` is not page, inherit or none, or a `page` row without `help`;
      MISSING-PAGE   - a help id of a `page` row (or of a page it lists, or the map's index) with no
                       <locale>/<help>.html for en, ru or uk under app_v2/src/main/assets/help/;
      BROKEN-LINK    - a local href or src in a page that resolves to no file, or to a fragment the target lacks;
      SCRIPT         - a `<script` element in a page (rule 12, rung 1);
      REMOTE-URL     - an http(s) URL a page would load: in a src/srcset/poster attribute, in an href of any
                       element other than <a>, or in a CSS url(); an <a href> is a link the user leaves by and is allowed;
      STALE-BASELINE - a baseline row whose surface now has a map row or is no longer enumerated, so the debt
                       only falls (CHECK-BASELINE).

    The watch app (S4291) is judged in the same run when wear/src/main/java exists. Its surfaces are the
    composable(..) routes, enumerated by Get-WearHelpSurfaces, and its map is wear/help-map.json - kept outside
    src/main because the watch has no resolver, no F1 and no help action to read it. Every finding of the watch
    half starts with `wear`, and the kinds are UNMAPPED, NO-SURFACE, NO-REASON and BAD-FORM (any form but `none`).
    There is no watch baseline: every route has its row.

    Fixed-input gate (scripts/quality/lib/fixed-input-scope.ps1): with -ChangedFiles that names none of its inputs -
    the map, the baseline, the enumerator, the manifest, the help sources under docs/content/help/, the pages under
    app_v2/src/main/assets/help/, the watch map or a file that declares a watch route - findings are reported and
    the gate exits 3 instead of 1.

.PARAMETER Root
    Tree to judge. Defaults to the repository root; the contract suite passes a fixture tree.

.PARAMETER ChangedFiles
    The caller's changed set (comma-joined accepted). Absent: every finding is charged.

.PARAMETER Gate
    Accepted for the closure's uniform call shape; the gate is fail-closed either way.

.PARAMETER Quiet
    Print only the subject, the findings and the verdict line.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-help-map.ps1

.NOTES
    Scope class (CLAUDE.md Rule 33): PER-TICKET. The author of a new screen or dialog knows its help row;
    placement row in gate-placement.jsonl.

    Exit codes (CLAUDE.md Rule 7):
      0 - the map covers every surface and every page row is complete.
      1 - at least one finding, charged to this run.
      2 - cannot verify: the map, the baseline, the enumerator or the surface sources are missing or unreadable;
          the watch sources exist and wear/help-map.json does not.
      3 - findings stand in the tree, but no declared input is in -ChangedFiles; reported, not charged.
#>
[CmdletBinding()]
param(
    [string]$Root,
    [string[]]$ChangedFiles,
    [switch]$Gate,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

$gateName = 'assert-help-map'
$locales = @('en', 'ru', 'uk')

function Stop-CannotVerify([string]$reason) {
    Write-Host "assert-help-map: COULD NOT VERIFY - $reason"
    exit 2
}

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
if (-not (Test-Path -LiteralPath $Root -PathType Container)) { Stop-CannotVerify "root not found: $Root" }
$Root = (Resolve-Path -LiteralPath $Root).Path

. (Join-Path $PSScriptRoot 'lib/fixed-input-scope.ps1')

# The enumerator is the gate's own code, not an input of the judged tree, so it is loaded from this repository
# even when -Root points at a fixture.
$enumerator = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'scripts/docs/help/list-help-surfaces.ps1'
if (-not (Test-Path -LiteralPath $enumerator -PathType Leaf)) { Stop-CannotVerify "enumerator not found: $enumerator" }
. $enumerator

$helpRoot = Join-Path $Root 'app_v2/src/main/assets/help'
$mapPath = Join-Path $helpRoot 'help-map.json'
$baselinePath = Join-Path $Root 'scripts/quality/help-map-baseline.txt'
$manifestPath = Join-Path $Root 'app_v2/src/main/AndroidManifest.xml'
if (-not (Test-Path -LiteralPath $mapPath -PathType Leaf)) { Stop-CannotVerify "help map not found: $mapPath" }
if (-not (Test-Path -LiteralPath $baselinePath -PathType Leaf)) { Stop-CannotVerify "baseline not found: $baselinePath" }

try { $map = Get-Content -LiteralPath $mapPath -Raw -Encoding utf8 | ConvertFrom-Json } catch { Stop-CannotVerify "help map is not JSON: $($_.Exception.Message)" }
try { $surfaces = @(Get-HelpSurfaces -Root $Root) } catch { Stop-CannotVerify $_.Exception.Message }
if ($surfaces.Count -eq 0) { Stop-CannotVerify 'the enumerator found no surface' }

$baseline = @(Get-Content -LiteralPath $baselinePath -Encoding utf8 |
        ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith('#') })
$rows = @($map.surfaces)

# A tree with no watch module (a fixture, a trimmed checkout) has no watch half to judge; a tree with the
# watch sources and no watch map cannot be verified, because the map is the half's whole input.
$wearMapPath = Join-Path $Root 'wear/help-map.json'
$judgeWear = Test-Path -LiteralPath (Join-Path $Root 'wear/src/main/java') -PathType Container
$wearSurfaces = @()
$wearRows = @()
if ($judgeWear) {
    if (-not (Test-Path -LiteralPath $wearMapPath -PathType Leaf)) { Stop-CannotVerify "watch help map not found: $wearMapPath" }
    try { $wearMap = Get-Content -LiteralPath $wearMapPath -Raw -Encoding utf8 | ConvertFrom-Json } catch { Stop-CannotVerify "watch help map is not JSON: $($_.Exception.Message)" }
    try { $wearSurfaces = @(Get-WearHelpSurfaces -Root $Root) } catch { Stop-CannotVerify $_.Exception.Message }
    if ($wearSurfaces.Count -eq 0) { Stop-CannotVerify 'the enumerator found no watch route' }
    $wearRows = @($wearMap.surfaces)
}

$subject = "subject: root=$Root surfaces=$($surfaces.Count) rows=$($rows.Count) baseline=$($baseline.Count)"
if ($judgeWear) { $subject += " wearRoutes=$($wearSurfaces.Count) wearRows=$($wearRows.Count)" }
Write-Host $subject

$findings = [System.Collections.Generic.List[string]]::new()
function Add-Finding([string]$kind, [string]$message) {
    $script:findings.Add(('{0,-14} {1}' -f $kind, $message))
}

function Get-Field($object, [string]$name) {
    if ($null -eq $object) { return $null }
    $prop = $object.PSObject.Properties[$name]
    if ($prop) { return $prop.Value }
    return $null
}

$surfaceIds = [System.Collections.Generic.HashSet[string]]::new([string[]]@($surfaces | ForEach-Object { [string]$_.id }))
$rowIds = [System.Collections.Generic.HashSet[string]]::new()
foreach ($row in $rows) { $null = $rowIds.Add([string](Get-Field $row 'id')) }

foreach ($s in $surfaces) {
    if (-not $rowIds.Contains([string]$s.id) -and $baseline -notcontains [string]$s.id) {
        Add-Finding 'UNMAPPED' "$($s.id) ($($s.source)): add a help-map row - page, inherit with parent and reason, or none with reason"
    }
}

$helpIds = [System.Collections.Generic.List[string]]::new()
foreach ($row in $rows) {
    $id = [string](Get-Field $row 'id')
    $form = [string](Get-Field $row 'form')
    if (-not $surfaceIds.Contains($id)) { Add-Finding 'NO-SURFACE' "row '$id' names no surface of the manifest or a dialog class" }
    switch ($form) {
        'page' {
            $rowHelp = [string](Get-Field $row 'help')
            if (-not $rowHelp) { Add-Finding 'BAD-FORM' "row '$id' is a page without help" } elseif (-not $helpIds.Contains($rowHelp)) { $helpIds.Add($rowHelp) }
            foreach ($sub in @(Get-Field $row 'pages')) {
                $subHelp = [string](Get-Field $sub 'help')
                if ($subHelp -and -not $helpIds.Contains($subHelp)) { $helpIds.Add($subHelp) }
            }
        }
        'inherit' {
            if (-not [string](Get-Field $row 'reason')) { Add-Finding 'NO-REASON' "row '$id' inherits without a reason" }
            $parent = [string](Get-Field $row 'parent')
            if (-not $parent -or -not $rowIds.Contains($parent)) { Add-Finding 'NO-PARENT' "row '$id' inherits from '$parent', which is no map row" }
        }
        'none' {
            if (-not [string](Get-Field $row 'reason')) { Add-Finding 'NO-REASON' "row '$id' has no help and no reason" }
        }
        default { Add-Finding 'BAD-FORM' "row '$id' has form '$form'; expected page, inherit or none" }
    }
}
$indexId = [string](Get-Field $map 'index')
if ($indexId -and -not $helpIds.Contains($indexId)) { $helpIds.Add($indexId) }

foreach ($entry in $baseline) {
    if ($rowIds.Contains($entry)) { Add-Finding 'STALE-BASELINE' "$entry has a map row now - delete its baseline line" }
    elseif (-not $surfaceIds.Contains($entry)) { Add-Finding 'STALE-BASELINE' "$entry is no longer a surface - delete its baseline line" }
}

if ($judgeWear) {
    $wearSurfaceIds = [System.Collections.Generic.HashSet[string]]::new([string[]]@($wearSurfaces | ForEach-Object { [string]$_.id }))
    $wearRowIds = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($row in $wearRows) { $null = $wearRowIds.Add([string](Get-Field $row 'id')) }
    foreach ($s in $wearSurfaces) {
        if (-not $wearRowIds.Contains([string]$s.id)) {
            Add-Finding 'UNMAPPED' "wear $($s.id) ($($s.source)): add a row to wear/help-map.json - form none with a reason"
        }
    }
    foreach ($row in $wearRows) {
        $id = [string](Get-Field $row 'id')
        $form = [string](Get-Field $row 'form')
        if (-not $wearSurfaceIds.Contains($id)) { Add-Finding 'NO-SURFACE' "wear row '$id' names no composable route of the watch app" }
        if ($form -ne 'none') {
            Add-Finding 'BAD-FORM' "wear row '$id' has form '$form'; the watch map holds only none rows with a reason (S4291)"
        } elseif (-not [string](Get-Field $row 'reason')) {
            Add-Finding 'NO-REASON' "wear row '$id' has no help and no reason"
        }
    }
}

foreach ($pageHelp in $helpIds) {
    foreach ($locale in $locales) {
        $page = Join-Path $helpRoot "$locale/$pageHelp.html"
        if (-not (Test-Path -LiteralPath $page -PathType Leaf)) { Add-Finding 'MISSING-PAGE' "$locale/$pageHelp.html (run scripts/docs/help/generate-help-pages.ps1)" }
    }
}

$pageFiles = @()
if (Test-Path -LiteralPath $helpRoot -PathType Container) {
    $pageFiles = @(Get-ChildItem -LiteralPath $helpRoot -Recurse -File -Filter '*.html')
}
$idCache = @{}
function Get-PageIds([string]$path) {
    if (-not $idCache.ContainsKey($path)) {
        $text = Get-Content -LiteralPath $path -Raw -Encoding utf8
        $idCache[$path] = [System.Collections.Generic.HashSet[string]]::new([string[]]@([regex]::Matches($text, '\bid="([^"]+)"') | ForEach-Object { $_.Groups[1].Value }))
    }
    return $idCache[$path]
}

foreach ($file in $pageFiles) {
    $rel = [System.IO.Path]::GetRelativePath($Root, $file.FullName).Replace('\', '/')
    $html = Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8
    if ($html -match '(?i)<script\b') { Add-Finding 'SCRIPT' "${rel}: a page runs no script (contract rule 12)" }
    foreach ($m in [regex]::Matches($html, '(?i)<(\w+)\b[^>]*?\b(src|srcset|poster|href)\s*=\s*"(https?://[^"]*)"')) {
        $tag = $m.Groups[1].Value.ToLowerInvariant()
        $attr = $m.Groups[2].Value.ToLowerInvariant()
        if ($tag -eq 'a' -and $attr -eq 'href') { continue }
        Add-Finding 'REMOTE-URL' "${rel}: <$tag $attr> loads $($m.Groups[3].Value); a page loads nothing from the network"
    }
    foreach ($m in [regex]::Matches($html, '(?i)url\(\s*[''"]?(https?://[^)''"]+)')) {
        Add-Finding 'REMOTE-URL' "${rel}: CSS url() loads $($m.Groups[1].Value)"
    }
    foreach ($m in [regex]::Matches($html, '(?i)\b(?:href|src)\s*=\s*"([^"]*)"')) {
        $target = $m.Groups[1].Value
        if (-not $target -or $target -match '^(?i)(https?:|mailto:|tel:|data:|intent:|javascript:)') { continue }
        $pathPart = ($target -split '[?#]', 2)[0]
        $fragment = if ($target.Contains('#')) { $target.Substring($target.IndexOf('#') + 1) } else { '' }
        $resolved = if ($pathPart) { [System.IO.Path]::GetFullPath((Join-Path $file.DirectoryName ([uri]::UnescapeDataString($pathPart)))) } else { $file.FullName }
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            Add-Finding 'BROKEN-LINK' "${rel}: '$target' resolves to no file"
            continue
        }
        if ($fragment -and $resolved.EndsWith('.html') -and -not (Get-PageIds $resolved).Contains($fragment)) {
            Add-Finding 'BROKEN-LINK' "${rel}: '$target' names a fragment the target page lacks"
        }
    }
}

if ($findings.Count -eq 0) {
    $wearVerdict = if ($judgeWear) { ", $($wearSurfaces.Count) watch routes" } else { '' }
    Write-Host "assert-help-map: PASS ($($surfaces.Count) surfaces, $($rows.Count) rows, $($baseline.Count) baseline, $($pageFiles.Count) pages$wearVerdict)"
    exit 0
}

$inputs = [System.Collections.Generic.List[string]]::new()
foreach ($p in @($mapPath, $baselinePath, $enumerator, $manifestPath)) { $inputs.Add($p) }
if ($judgeWear) {
    $inputs.Add($wearMapPath)
    foreach ($s in $wearSurfaces) { $inputs.Add((Join-Path $Root ([string]$s.source))) }
}
foreach ($dir in @($helpRoot, (Join-Path $Root 'docs/content/help'))) {
    if (Test-Path -LiteralPath $dir -PathType Container) {
        Get-ChildItem -LiteralPath $dir -Recurse -File | ForEach-Object { $inputs.Add($_.FullName) }
    }
}
if (-not (Test-FixedInputsChargeable -ChangedFiles $ChangedFiles -InputPaths $inputs.ToArray())) {
    Write-NotChargedVerdict -GateName $gateName -Findings $findings.ToArray()
    Write-Host "assert-help-map: PASS WITH ADVISORIES ($($findings.Count) finding(s) not charged)"
    exit 3
}

foreach ($f in $findings) { Write-Host "  FAIL $f" }
Write-Host "assert-help-map: FAIL ($($findings.Count) finding(s))"
exit 1
