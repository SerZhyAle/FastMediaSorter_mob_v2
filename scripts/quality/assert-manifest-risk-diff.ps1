#requires -Version 7.0
<#
.SYNOPSIS
    Refuse a manifest risk declaration that carries no justification row (S3371, security profile).

.DESCRIPTION
    A permission, an exported component, a cleartext allowance, a backup-rule change or a
    foreground service type is a risk the reviewer, the store listing and the privacy page all
    have to answer for, and until this gate nothing asked WHY before one landed. The answer lives
    in docs/manifest-risk-registry.jsonl - one row per distinct declaration, carrying its
    justification and the flavors it reaches.

    THE REGISTRY IS THE ALLOWLIST. A declaration with a row passes; one without is refused by its
    own key, so the refusal says exactly what to justify. A row is itself refused when it has no
    justification, or names neither a flavor nor the source set that gates it - an exemption
    nobody explained is the shape this gate exists to prevent.

    Two selections:
      -ChangedFiles   only the manifests in the changed set. A change that touches the registry
                      and no manifest passes trivially: there is no new risk to judge.
      (none)          every manifest of both modules - the project-wide verdict used by the fg
                      batch and by CI.

    What it does NOT do: it reads SOURCE manifests. A risk that only appears after manifest
    merging - a library's own declaration - is invisible here by construction, which is why the
    per-flavor merged-manifest sweep in android-ci.yml exists beside it.

.PARAMETER Gate
    Fail-closed: exit 1 on any unjustified declaration or malformed row.

.PARAMETER ChangedFiles
    The changed set, as a real array or a comma-separated string.

.PARAMETER List
    Print every declaration judged, not only the findings.

.PARAMETER Manifests
    Judge these manifests instead of a changed set or the source tree. This is how the CI sweep
    hands over MERGED manifests, which no source diff can produce.

.PARAMETER Flavor
    The flavor the given manifests belong to. With it, a declaration whose registry row does not
    list that flavor is a finding of its own - the merged manifest is the only place a library's
    own declaration becomes visible, and the row's flavor list is the claim it contradicts.

.PARAMETER RepoRoot
    The tree to scan.

.PARAMETER RegistryPath
    The registry to read. Both exist so the contract suite can run the gate against fixture
    manifests and a fixture registry: a refusal path that is never executed is the same unobserved
    green it was written to prevent.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - pass: every declaration in the selection has a well-formed registry row, or a report run.
      1 - fail: a declaration has no row, or a row lacks a justification or a scope.
      2 - cannot verify: the registry is missing or holds a line that is not JSON.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [AllowEmptyCollection()][AllowNull()][string[]]$ChangedFiles,
    [switch]$List,
    [AllowEmptyCollection()][AllowNull()][string[]]$Manifests,
    [string]$Flavor,
    [string]$RepoRoot,
    [string]$RegistryPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = if ($RepoRoot) { $RepoRoot } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$repoRootSlash = ($repoRoot -replace '\\', '/').TrimEnd('/')
. (Join-Path $PSScriptRoot 'lib/changed-files.ps1')

$registryFile = if ($RegistryPath) { $RegistryPath } else { Join-Path $repoRoot 'docs/manifest-risk-registry.jsonl' }
if (-not (Test-Path -LiteralPath $registryFile)) {
    Write-Error "assert-manifest-risk-diff: registry not found: $registryFile" -ErrorAction Continue
    exit 2
}

# ---------------------------------------------------------------------------
# Registry
# ---------------------------------------------------------------------------
$known = @{}
$malformed = [System.Collections.Generic.List[string]]::new()
$lineNo = 0
foreach ($raw in (Get-Content -LiteralPath $registryFile)) {
    $lineNo++
    $line = ([string]$raw).Trim()
    if (-not $line) { continue }
    try { $row = $line | ConvertFrom-Json }
    catch {
        Write-Error "assert-manifest-risk-diff: line ${lineNo} of the registry is not JSON." -ErrorAction Continue
        exit 2
    }
    $kind = if ($row.PSObject.Properties.Name -contains 'kind') { [string]$row.kind } else { '' }
    $key = if ($row.PSObject.Properties.Name -contains 'key') { [string]$row.key } else { '' }
    if (-not $kind -or -not $key) {
        $malformed.Add("line ${lineNo}: a row needs both kind and key")
        continue
    }
    $justification = if ($row.PSObject.Properties.Name -contains 'justification') { ([string]$row.justification).Trim() } else { '' }
    if (-not $justification) {
        $malformed.Add("line ${lineNo}: ${kind} '${key}' has no justification")
    }
    $flavors = @(if ($row.PSObject.Properties.Name -contains 'flavors') { $row.flavors } else { @() })
    $sourceSets = @(if ($row.PSObject.Properties.Name -contains 'sourceSets') { $row.sourceSets } else { @() })
    if ($flavors.Count -eq 0 -and $sourceSets.Count -eq 0) {
        $malformed.Add("line ${lineNo}: ${kind} '${key}' names neither a flavor nor the source set that gates it")
    }
    $known["$kind|$key"] = [pscustomobject]@{ Flavors = @($flavors | ForEach-Object { [string]$_ }); SourceSets = @($sourceSets | ForEach-Object { [string]$_ }) }
}

if ($malformed.Count -gt 0) {
    Write-Host 'manifest-risk: the registry itself is malformed -'
    foreach ($m in $malformed) { Write-Host "  $m" }
    if ($Gate) { exit 1 }
}

# ---------------------------------------------------------------------------
# Selection
# ---------------------------------------------------------------------------
$rxManifest = [regex]'(?i)(^|/)AndroidManifest[^/]*\.xml$'
$selection = [System.Collections.Generic.List[string]]::new()

if ($Manifests -and (@(Expand-ChangedFiles -ChangedFiles $Manifests).Count -gt 0)) {
    foreach ($path in (Expand-ChangedFiles -ChangedFiles $Manifests)) {
        $rel = ($path -replace '\\', '/').Trim()
        if ($rel.StartsWith($repoRootSlash, [System.StringComparison]::OrdinalIgnoreCase)) {
            $rel = $rel.Substring($repoRootSlash.Length).TrimStart('/')
        }
        if ($rel) { $selection.Add($rel) }
    }
}
elseif ($ChangedFiles -and (@(Expand-ChangedFiles -ChangedFiles $ChangedFiles).Count -gt 0)) {
    foreach ($path in (Expand-ChangedFiles -ChangedFiles $ChangedFiles)) {
        $rel = ($path -replace '\\', '/').Trim()
        if ($rel.StartsWith($repoRootSlash, [System.StringComparison]::OrdinalIgnoreCase)) {
            $rel = $rel.Substring($repoRootSlash.Length).TrimStart('/')
        }
        if ($rel -and $rxManifest.IsMatch($rel)) { $selection.Add($rel) }
    }
}
else {
    foreach ($module in @('app_v2', 'wear')) {
        $root = Join-Path $repoRoot "$module/src"
        if (-not (Test-Path -LiteralPath $root)) { continue }
        foreach ($file in (Get-ChildItem -LiteralPath $root -Recurse -File -Filter 'AndroidManifest*.xml' -ErrorAction SilentlyContinue)) {
            $selection.Add(($file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/'))
        }
    }
}

# ---------------------------------------------------------------------------
# Extraction
# ---------------------------------------------------------------------------
$rxPermission = [regex]'<uses-permission(?:-sdk-23)?\b[^>]*android:name\s*=\s*"([^"]+)"'
$rxComponent = [regex]'(?s)<(activity|activity-alias|service|receiver|provider)\b([^>]*)>'
$rxName = [regex]'android:name\s*=\s*"([^"]+)"'
$rxExported = [regex]'android:exported\s*=\s*"true"'
$rxCleartext = [regex]'android:usesCleartextTraffic\s*=\s*"([^"]+)"'
$rxNetworkConfig = [regex]'android:networkSecurityConfig\s*=\s*"[^"]+"'
$rxBackup = [regex]'android:(allowBackup|fullBackupContent|dataExtractionRules)\s*=\s*"[^"]+"'
$rxForegroundType = [regex]'android:foregroundServiceType\s*=\s*"([^"]+)"'
# A declaration the flavor overlay REMOVES is not a risk that flavor carries, so it is not a
# declaration this gate asks to justify.
$rxRemoved = [regex]'tools:node\s*=\s*"remove"'

$declarations = [System.Collections.Generic.List[object]]::new()

function Add-Declaration([string]$Kind, [string]$Key, [string]$File) {
    if (-not $Key) { return }
    $declarations.Add([pscustomobject]@{ Kind = $Kind; Key = $Key; File = $File })
}

foreach ($rel in $selection) {
    $absolutePath = Join-Path $repoRoot $rel
    if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) { continue }
    $text = Get-Content -LiteralPath $absolutePath -Raw
    if ([string]::IsNullOrEmpty($text)) { continue }

    foreach ($m in $rxPermission.Matches($text)) {
        $element = $text.Substring($m.Index, [Math]::Min(400, $text.Length - $m.Index))
        $end = $element.IndexOf('>')
        if ($end -ge 0) { $element = $element.Substring(0, $end + 1) }
        if ($rxRemoved.IsMatch($element)) { continue }
        Add-Declaration 'permission' $m.Groups[1].Value $rel
    }

    foreach ($m in $rxComponent.Matches($text)) {
        $attributes = $m.Groups[2].Value
        if (-not $rxExported.IsMatch($attributes)) { continue }
        if ($rxRemoved.IsMatch($attributes)) { continue }
        $nameMatch = $rxName.Match($attributes)
        if ($nameMatch.Success) { Add-Declaration 'exported' $nameMatch.Groups[1].Value $rel }
    }

    foreach ($m in $rxCleartext.Matches($text)) { Add-Declaration 'cleartext' 'android:usesCleartextTraffic' $rel }
    foreach ($m in $rxNetworkConfig.Matches($text)) { Add-Declaration 'networkSecurityConfig' 'android:networkSecurityConfig' $rel }
    foreach ($m in $rxBackup.Matches($text)) { Add-Declaration 'backup' ('android:' + $m.Groups[1].Value) $rel }
    foreach ($m in $rxForegroundType.Matches($text)) {
        foreach ($type in ($m.Groups[1].Value -split '\|')) { Add-Declaration 'foregroundServiceType' $type.Trim() $rel }
    }
}

# ---------------------------------------------------------------------------
# Verdict
# ---------------------------------------------------------------------------
$findings = [System.Collections.Generic.List[string]]::new()
$seen = @{}
foreach ($d in $declarations) {
    $id = "$($d.Kind)|$($d.Key)"
    if ($known.ContainsKey($id)) {
        if ($Flavor -and -not $seen.ContainsKey("flavor:$id")) {
            $seen["flavor:$id"] = $true
            $row = $known[$id]
            if ($row.Flavors.Count -gt 0 -and $row.Flavors -notcontains $Flavor) {
                $findings.Add(("{0} '{1}' reaches flavor '{2}' after manifest merging, but its registry row lists only: {3}" -f $d.Kind, $d.Key, $Flavor, ($row.Flavors -join ', ')))
            }
        }
        if ($List -and -not $seen.ContainsKey($id)) { Write-Host ("  ok   {0,-22} {1}" -f $d.Kind, $d.Key) }
        $seen[$id] = $true
        continue
    }
    if ($seen.ContainsKey("miss:$id")) { continue }
    $seen["miss:$id"] = $true
    $findings.Add(("{0} '{1}' in {2}" -f $d.Kind, $d.Key, $d.File))
}

$scope = if ($Manifests -and $selection.Count -gt 0) { "merged manifests of flavor '$Flavor'" }
    elseif ($ChangedFiles -and $selection.Count -gt 0 -and (@(Expand-ChangedFiles -ChangedFiles $ChangedFiles).Count -gt 0)) { 'changed set' }
    else { 'both modules' }
Write-Host ("manifest-risk: {0} manifest(s) in the {1} | {2} declaration(s) | unjustified {3}" -f $selection.Count, $scope, $declarations.Count, $findings.Count)

if ($findings.Count -gt 0) {
    foreach ($f in $findings) { Write-Host "  $f" }
    if ($Gate) {
        Write-Host 'FAIL: a manifest risk declaration has no row in docs/manifest-risk-registry.jsonl. Add one row per key - kind, key, modules, flavors, sources and a one-sentence justification a reviewer who has never seen the code can read - or drop the declaration.'
        exit 1
    }
}

if ($Gate -and $malformed.Count -gt 0) {
    Write-Error "assert-manifest-risk-diff: $($malformed.Count) registry row(s) are malformed - each is listed above with its line number. Give the row a justification and a scope, or delete it." -ErrorAction Continue
    exit 1
}
exit 0
