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

    What it does NOT do on the two source selections: it reads SOURCE manifests. A risk that only
    appears after manifest merging - a library's own declaration - is invisible there by
    construction, which is why the per-flavor merged-manifest sweep in android-ci.yml exists
    beside it.

    FOREGROUND SERVICE PAIRING (S4267). A merged manifest is also judged for the pair the
    platform enforces from API 34: every value of a service's android:foregroundServiceType
    needs android.permission.FOREGROUND_SERVICE_<TYPE> as a uses-permission of the same manifest,
    or startForeground() throws SecurityException. The pair exists only after merging - a release
    overlay can strip the permission while the typed service stays declared - so it is checked on
    the -Manifests path only. A permission carrying tools:node="remove", or android:maxSdkVersion
    below 34, does not pair; shortService is the one type with no permission.

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

.PARAMETER PairingOnly
    With -Manifests: judge only the foreground service pairs, not the registry rows and not the
    flavor lists. The release sweep uses it so a flavor the registry does not list is still
    paired. Without -Manifests there is no merged manifest to pair, so the run cannot verify.

.PARAMETER RepoRoot
    The tree to scan.

.PARAMETER RegistryPath
    The registry to read. Both exist so the contract suite can run the gate against fixture
    manifests and a fixture registry: a refusal path that is never executed is the same unobserved
    green it was written to prevent.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - pass: every declaration in the selection has a well-formed registry row and every typed
          foreground service of a merged manifest has its paired permission, or a report run.
      1 - fail: a declaration has no row, a row lacks a justification or a scope, or a typed
          foreground service has no paired permission.
      2 - cannot verify: the registry is missing or holds a line that is not JSON, a merged
          manifest is not well-formed XML, or -PairingOnly was given without -Manifests.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [AllowEmptyCollection()][AllowNull()][string[]]$ChangedFiles,
    [switch]$List,
    [AllowEmptyCollection()][AllowNull()][string[]]$Manifests,
    [string]$Flavor,
    [switch]$PairingOnly,
    [string]$RepoRoot,
    [string]$RegistryPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = if ($RepoRoot) { $RepoRoot } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$repoRootSlash = ($repoRoot -replace '\\', '/').TrimEnd('/')
. (Join-Path $PSScriptRoot 'lib/changed-files.ps1')

$mergedMode = [bool]($Manifests -and (@(Expand-ChangedFiles -ChangedFiles $Manifests).Count -gt 0))
if ($PairingOnly -and -not $mergedMode) {
    Write-Error 'assert-manifest-risk-diff: -PairingOnly judges merged manifests, so it needs -Manifests.' -ErrorAction Continue
    exit 2
}

$registryFile = if ($RegistryPath) { $RegistryPath } else { Join-Path $repoRoot 'docs/manifest-risk-registry.jsonl' }
# A pairing-only run never judges a row, so neither a missing nor a broken registry stops it.
if (-not $PairingOnly -and -not (Test-Path -LiteralPath $registryFile)) {
    Write-Error "assert-manifest-risk-diff: registry not found: $registryFile" -ErrorAction Continue
    exit 2
}
$registryLines = if (-not $PairingOnly) { @(Get-Content -LiteralPath $registryFile) } else { @() }

# ---------------------------------------------------------------------------
# Registry
# ---------------------------------------------------------------------------
# Source manifests use relative component names; the merger expands them using the namespace.
$namespaces = @{}
foreach ($module in @('app_v2', 'wear')) {
    $buildFile = Join-Path $repoRoot "$module/build.gradle.kts"
    if (Test-Path -LiteralPath $buildFile) {
        $namespace = [regex]::Match([IO.File]::ReadAllText($buildFile), 'namespace\s*=\s*"([^"]+)"')
        if ($namespace.Success) { $namespaces[$module] = $namespace.Groups[1].Value }
    }
}
$known = @{}
$malformed = [System.Collections.Generic.List[string]]::new()
$lineNo = 0
foreach ($raw in $registryLines) {
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
    $scope = [pscustomobject]@{ Flavors = @($flavors | ForEach-Object { [string]$_ }); SourceSets = @($sourceSets | ForEach-Object { [string]$_ }) }
    $known["$kind|$key"] = $scope
    if ($kind -eq 'exported' -and $key.StartsWith('.') -and $row.PSObject.Properties.Name -contains 'modules') {
        foreach ($module in $row.modules) {
            if ($namespaces.ContainsKey($module)) { $known["$kind|$($namespaces[$module])$key"] = $scope }
        }
    }
}

if ($malformed.Count -gt 0 -and -not $PairingOnly) {
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

$declarationSources = if ($PairingOnly) { @() } else { $selection }
foreach ($rel in $declarationSources) {
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
# Foreground service pairing - merged manifests only
# ---------------------------------------------------------------------------
$androidNs = 'http://schemas.android.com/apk/res/android'
$toolsNs = 'http://schemas.android.com/tools'
# The platform starts enforcing the typed permission at API 34, so a grant capped below it is no pair.
$pairingApi = 34
# The one type the platform lets a service declare with no permission of its own.
$unpairedTypes = @('shortService')

function Get-FgsPermission([string]$Type) {
    'android.permission.FOREGROUND_SERVICE_' + [regex]::Replace($Type, '(?<=[a-z0-9])(?=[A-Z])', '_').ToUpperInvariant()
}

function Get-PairingFindings([string]$Rel, [string]$AbsolutePath) {
    $doc = [System.Xml.XmlDocument]::new()
    try { $doc.Load($AbsolutePath) }
    catch {
        Write-Error "assert-manifest-risk-diff: ${Rel} is not well-formed XML, so its foreground service pairs cannot be judged." -ErrorAction Continue
        exit 2
    }
    $granted = @{}
    foreach ($node in $doc.SelectNodes('//uses-permission | //uses-permission-sdk-23')) {
        $name = $node.GetAttribute('name', $androidNs)
        if (-not $name) { continue }
        if ($node.GetAttribute('node', $toolsNs) -eq 'remove') { continue }
        $cap = 0
        if ([int]::TryParse($node.GetAttribute('maxSdkVersion', $androidNs), [ref]$cap) -and $cap -lt $pairingApi) { continue }
        $granted[$name] = $true
    }
    $missing = [System.Collections.Generic.List[string]]::new()
    foreach ($service in $doc.SelectNodes('//service')) {
        $typeValue = $service.GetAttribute('foregroundServiceType', $androidNs)
        if (-not $typeValue) { continue }
        if ($service.GetAttribute('node', $toolsNs) -eq 'remove') { continue }
        $serviceName = $service.GetAttribute('name', $androidNs)
        foreach ($raw in ($typeValue -split '\|')) {
            $type = $raw.Trim()
            if (-not $type -or $unpairedTypes -contains $type) { continue }
            if ($type -notmatch '^[a-z][A-Za-z]*$') {
                $missing.Add("service '$serviceName' declares foregroundServiceType '$type', which names no permission to pair (${Rel})")
                continue
            }
            $permission = Get-FgsPermission $type
            if (-not $granted.ContainsKey($permission)) {
                $missing.Add("service '$serviceName' declares foregroundServiceType '$type' but the merged manifest has no uses-permission $permission (${Rel})")
            }
        }
    }
    return $missing.ToArray()
}

$unpaired = [System.Collections.Generic.List[string]]::new()
if ($mergedMode) {
    foreach ($rel in $selection) {
        $absolutePath = Join-Path $repoRoot $rel
        # Nothing to pair is not a pass: the sweep would report the same green for a path nobody built.
        if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) {
            Write-Error "assert-manifest-risk-diff: merged manifest not found: ${rel}" -ErrorAction Continue
            exit 2
        }
        foreach ($finding in @(Get-PairingFindings $rel $absolutePath)) { $unpaired.Add($finding) }
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

$scope = if ($mergedMode) { "merged manifests of flavor '$Flavor'" }
    elseif ($ChangedFiles -and $selection.Count -gt 0 -and (@(Expand-ChangedFiles -ChangedFiles $ChangedFiles).Count -gt 0)) { 'changed set' }
    else { 'both modules' }
$pairingNote = if ($mergedMode) { " | unpaired {0}" -f $unpaired.Count } else { '' }
Write-Host ("manifest-risk: {0} manifest(s) in the {1} | {2} declaration(s) | unjustified {3}{4}" -f $selection.Count, $scope, $declarations.Count, $findings.Count, $pairingNote)

if ($findings.Count -gt 0) {
    foreach ($f in $findings) { Write-Host "  $f" }
    if ($Gate) {
        Write-Host 'FAIL: a manifest risk declaration has no row in docs/manifest-risk-registry.jsonl. Add one row per key - kind, key, modules, flavors, sources and a one-sentence justification a reviewer who has never seen the code can read - or drop the declaration.'
    }
}

if ($unpaired.Count -gt 0) {
    foreach ($f in $unpaired) { Write-Host "  $f" }
    if ($Gate) {
        Write-Host 'FAIL: a foreground service type has no paired FOREGROUND_SERVICE_<TYPE> permission in the merged manifest, so startForeground() throws SecurityException on Android 14+. Declare the permission (and remove a release overlay that strips it), or drop the type from the service.'
    }
}

if ($Gate -and ($findings.Count -gt 0 -or $unpaired.Count -gt 0)) { exit 1 }

if ($Gate -and $malformed.Count -gt 0) {
    Write-Error "assert-manifest-risk-diff: $($malformed.Count) registry row(s) are malformed - each is listed above with its line number. Give the row a justification and a scope, or delete it." -ErrorAction Continue
    exit 1
}
exit 0
