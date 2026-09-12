#requires -Version 7.0
<#
.SYNOPSIS
    S2880 - binds the bridge scenario registry to the Data Layer route catalogs it must cover.

.DESCRIPTION
    `scripts/devtest/bridge-scenarios.json` is the durable home of the S2861 scenario registry
    (the prose lived in the spec body and would be archived with it). Nothing used to connect it
    to the catalogs: a route added to `WearDataLayerPaths.kt` obliged nobody to add a scenario,
    and the gap S2861 measured - two of thirty-eight routes unnamed anywhere - was found only by a
    manual sweep. This gate makes that sweep mechanical, in both directions:

      route  - every path-string constant declared in either `WearDataLayerPaths.kt` must appear in
               at least one `scenarios[].channels[]` or in `excluded[]` with a reason.
      mirror - the two catalogs must declare the same route set. They are mirror declarations; a
               route on one side only is the S1697 silent-drop class waiting to happen.
      excluded - an `excluded[]` record names a route some catalog declares, and carries a reason.

    What this gate CANNOT see, stated so nobody reads a PASS as more than it is: a scenario whose
    `channels[]` names the route but whose `expectation` no longer matches the behaviour. That is
    what the S2861 run's per-scenario verdict files are for.

    Ratchet: the baseline file holds the divergence count accepted on the day the gate landed
    (zero - the registry landed with full coverage) and moves DOWN only. Raising it hides a
    regression; a repair lowers it.

.PARAMETER Gate
    Return exit code 1 when the divergence count exceeds the baseline. Without it the script
    reports and returns 0, which is how an advisory caller inspects the tree.

.PARAMETER UpdateBaseline
    Rewrite the baseline to the count measured now. Refuses to RAISE it - a ratchet only tightens.

.PARAMETER ChangedFiles
    CSV of the paths a closure is judging. Ownership is by RECORD (S2723, the walk-contract rule):
    a divergence is owned when its route is declared by a catalog in the set, or when the registry
    record naming it differs from the pre-edit copy (`git show HEAD:<registry>`), or when the
    `excluded[]` record for it is this edit's work. A scoped run handed none of these judges
    nothing - a route another session added and has not classified yet must not refuse this
    closure (S2621). A pre-edit copy that cannot be obtained falls back to owning the whole
    registry - "could not measure the delta" is judged strictly, never leniently.

.PARAMETER CatalogA
.PARAMETER CatalogB
.PARAMETER Registry
.PARAMETER BaselineFile
    Path overrides. Default to the real tree; a negative-proof run points them at scratch copies
    so proving the gate catches an unclassified route does not edit a shipped catalog.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-bridge-scenario-coverage.ps1 -Gate

.NOTES
    Exit codes:
      0  divergences are at or below the baseline (or -Gate was not passed).
      1  divergences exceed the baseline, or -UpdateBaseline was asked to raise it.
      2  could not verify: a catalog or the registry is missing or unreadable. Never conflated
         with 1 - "found a defect" and "did not look" are different answers.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$UpdateBaseline,
    [string]$ChangedFiles,
    [string]$CatalogA,
    [string]$CatalogB,
    [string]$Registry,
    [string]$BaselineFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

$catalogA = if ($CatalogA) { $CatalogA } else { Join-Path $repoRoot 'app_v2/src/main/java/com/sza/fastmediasorter/service/WearDataLayerPaths.kt' }
$catalogB = if ($CatalogB) { $CatalogB } else { Join-Path $repoRoot 'wear/src/main/java/com/sza/fastmediasorter/wear/data/wear/WearDataLayerPaths.kt' }
$registryPath = if ($Registry) { $Registry } else { Join-Path $repoRoot 'scripts/devtest/bridge-scenarios.json' }
$baselineFile = if ($BaselineFile) { $BaselineFile } else { Join-Path $PSScriptRoot 'bridge-scenario-coverage-baseline.txt' }

function Stop-Unverifiable {
    param([string]$Reason)
    Write-Error "assert-bridge-scenario-coverage: could not verify - $Reason" -ErrorAction Continue
    exit 2
}

# S2621: the presented set, normalised so a repo-relative path and an absolute one compare equal.
$scopeEnabled = -not [string]::IsNullOrWhiteSpace($ChangedFiles)
$scopePaths = @()
if ($scopeEnabled) {
    $scopePaths = @($ChangedFiles -split ',' |
        ForEach-Object { ($_.Trim() -replace '\\', '/').ToLowerInvariant() } |
        Where-Object { $_ })
}

function Test-InScope {
    param([string]$Path)
    if (-not $Path) { return $false }
    $normalised = ($Path -replace '\\', '/').ToLowerInvariant()
    foreach ($candidate in $scopePaths) {
        if ($normalised -eq $candidate -or $normalised.EndsWith('/' + $candidate)) { return $true }
    }
    return $false
}

foreach ($catalog in @([pscustomobject]@{ Path = $catalogA; Side = 'phone' }, [pscustomobject]@{ Path = $catalogB; Side = 'watch' })) {
    if (-not (Test-Path -LiteralPath $catalog.Path)) { Stop-Unverifiable "$($catalog.Side) catalog not found: $($catalog.Path)" }
}
if (-not (Test-Path -LiteralPath $registryPath)) { Stop-Unverifiable "registry not found: $registryPath" }

# Route constants only: `const val NAME = "<path>"`. A quoted /fms/... mention inside a KDoc is
# documentation, not a declaration, and counting it made the catalog look bigger than it is.
function Get-DeclaredRoutes {
    param([string]$CatalogPath)
    $text = Get-Content -LiteralPath $CatalogPath -Raw
    $routes = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($m in [regex]::Matches($text, 'const\s+val\s+[A-Z][A-Z0-9_]*\s*=\s*"(/[^"]+)"')) {
        [void]$routes.Add($m.Groups[1].Value)
    }
    return $routes
}

$routesA = Get-DeclaredRoutes -CatalogPath $catalogA
$routesB = Get-DeclaredRoutes -CatalogPath $catalogB
if ($routesA.Count -eq 0) { Stop-Unverifiable "phone catalog declares no route constant: $catalogA" }
if ($routesB.Count -eq 0) { Stop-Unverifiable "watch catalog declares no route constant: $catalogB" }

try { $registryData = Get-Content -LiteralPath $registryPath -Raw | ConvertFrom-Json }
catch { Stop-Unverifiable "registry is not readable JSON: $registryPath" }
if (-not $registryData.PSObject.Properties.Name.Contains('scenarios')) { Stop-Unverifiable "registry declares no 'scenarios' array" }
$scenarios = @($registryData.scenarios)
if ($scenarios.Count -eq 0) { Stop-Unverifiable "registry declares no scenario" }

$divergences = [System.Collections.Generic.List[string]]::new()

# The covered set: every path any scenario names. A scenario with empty channels is a precondition
# or a screen-read scenario - it covers nothing, and that is by design (A1-A4, E1-E3, ...).
$scenarioChannels = [System.Collections.Generic.HashSet[string]]::new()
foreach ($scenario in $scenarios) {
    $channels = @()
    if ($scenario.PSObject.Properties.Name -contains 'channels' -and $null -ne $scenario.channels) { $channels = @($scenario.channels) }
    foreach ($channel in $channels) { [void]$scenarioChannels.Add([string]$channel) }
}

$excludedRecords = @()
if ($registryData.PSObject.Properties.Name.Contains('excluded')) { $excludedRecords = @($registryData.excluded) }
$excluded = [System.Collections.Generic.HashSet[string]]::new()
foreach ($record in $excludedRecords) {
    $path = if ($record.PSObject.Properties.Name -contains 'path' -and $record.path) { [string]$record.path } else { $null }
    $reason = if ($record.PSObject.Properties.Name -contains 'reason' -and $record.reason) { [string]$record.reason } else { $null }
    if (-not $path) { $divergences.Add("excluded[] : a record declares no 'path'"); continue }
    if (-not $reason) { $divergences.Add("$path : excluded with no reason - name why no scenario will ever cover it") }
    if (-not $excluded.Add($path)) { $divergences.Add("$path : listed twice in excluded[]") }
    if ($scenarioChannels.Contains($path)) { $divergences.Add("$path : both scenario-named and excluded - it can only be one") }
}

$allRoutes = [System.Collections.Generic.HashSet[string]]::new()
foreach ($route in $routesA) { [void]$allRoutes.Add($route) }
foreach ($route in $routesB) { [void]$allRoutes.Add($route) }

foreach ($route in (Compare-Object -ReferenceObject @($routesA) -DifferenceObject @($routesB) | ForEach-Object { $_.InputObject })) {
    $side = if ($routesA.Contains($route)) { 'watch' } else { 'phone' }
    $divergences.Add("$route : declared in one catalog only - the $side side is missing it")
}
foreach ($route in $allRoutes) {
    if (-not $scenarioChannels.Contains($route) -and -not $excluded.Contains($route)) {
        $divergences.Add("$route : neither scenario-named nor excluded - add its scenario channel or an excluded[] record in bridge-scenarios.json")
    }
}
foreach ($route in $excluded) {
    if (-not $allRoutes.Contains($route)) {
        $divergences.Add("$route : excluded but no catalog declares it")
    }
}

# S2723: the registry's records, keyed the way the registry keys them - scenarios by id, exclusions
# by path. Each key carries the route names it can attribute a divergence to.
function Get-RegistryRecords {
    param($Data)
    $records = @{}
    $names = @{}
    if (-not $Data.PSObject.Properties.Name.Contains('scenarios')) {
        return [pscustomobject]@{ Records = $records; Names = $names }
    }
    foreach ($record in @($Data.scenarios)) {
        $recordId = if ($record.PSObject.Properties.Name -contains 'id' -and $record.id) { [string]$record.id } else { $null }
        if (-not $recordId) { continue }
        $key = "scenarios/$recordId"
        $records[$key] = ($record | ConvertTo-Json -Depth 8 -Compress)
        $channels = @()
        if ($record.PSObject.Properties.Name -contains 'channels' -and $null -ne $record.channels) { $channels = @($record.channels) }
        $names[$key] = @($channels | ForEach-Object { [string]$_ })
    }
    if ($Data.PSObject.Properties.Name.Contains('excluded')) {
        foreach ($record in @($Data.excluded)) {
            $recordPath = if ($record.PSObject.Properties.Name -contains 'path' -and $record.path) { [string]$record.path } else { $null }
            if (-not $recordPath) { continue }
            $key = "excluded/$recordPath"
            $records[$key] = ($record | ConvertTo-Json -Depth 8 -Compress)
            $names[$key] = @($recordPath)
        }
    }
    return [pscustomobject]@{ Records = $records; Names = $names }
}

function Get-TouchedRoutes {
    param($Current, $Baseline)
    $touched = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($key in $Current.Records.Keys) {
        if ((-not $Baseline.Records.ContainsKey($key)) -or ($Baseline.Records[$key] -ne $Current.Records[$key])) {
            foreach ($name in $Current.Names[$key]) { if ($name) { [void]$touched.Add($name) } }
        }
    }
    foreach ($key in $Baseline.Records.Keys) {
        if (-not $Current.Records.ContainsKey($key)) {
            foreach ($name in $Baseline.Names[$key]) { if ($name) { [void]$touched.Add($name) } }
        }
    }
    return $touched
}

$registryPresented = $scopeEnabled -and (Test-InScope $registryPath)
$catalogInScope = $scopeEnabled -and ((Test-InScope $catalogA) -or (Test-InScope $catalogB))
$touchedRoutes = $null
if ($registryPresented) {
    $baselineText = $null
    $relative = ([System.IO.Path]::GetRelativePath($repoRoot, (Resolve-Path -LiteralPath $registryPath).Path)) -replace '\\', '/'
    $gitOutput = & git -C $repoRoot show "HEAD:$relative" 2>$null
    if ($LASTEXITCODE -eq 0) { $baselineText = ($gitOutput -join "`n") }
    if ($baselineText) {
        try { $touchedRoutes = Get-TouchedRoutes (Get-RegistryRecords $registryData) (Get-RegistryRecords ($baselineText | ConvertFrom-Json)) }
        catch { $touchedRoutes = $null }
    }
}

# S2621 + S2723: keep only what the presented set is answerable for. A route is owned when a
# catalog declaring it is in the set (the editor of the catalog classifies the route), when a
# touched registry record names it, or when the registry delta could not be measured while the
# registry itself is presented - the strict fallback.
if ($scopeEnabled) {
    $isOwned = {
        param([string]$Route)
        if ($catalogInScope) { return $true }
        if ($registryPresented) {
            if ($null -eq $touchedRoutes) { return $true }
            if ($touchedRoutes.Contains($Route)) { return $true }
        }
        return $false
    }
    $owned = [System.Collections.Generic.List[string]]::new()
    foreach ($divergence in $divergences) {
        $routeMatch = [regex]::Match($divergence, '^(/[^ ]+)')
        if ($routeMatch.Success -and (& $isOwned $routeMatch.Groups[1].Value)) { $owned.Add($divergence); continue }
        if (-not $routeMatch.Success) { $owned.Add($divergence) }
    }
    $divergences = $owned
}

$baseline = 0
if (Test-Path -LiteralPath $baselineFile) {
    $firstLine = (Get-Content -LiteralPath $baselineFile | Where-Object { $_ -match '^\s*\d+\s*$' } | Select-Object -First 1)
    if ($null -ne $firstLine) { $baseline = [int]$firstLine.Trim() }
}

$count = $divergences.Count

if ($UpdateBaseline) {
    . (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')
    if ($count -gt $baseline) {
        Write-Error "assert-bridge-scenario-coverage: refusing to raise the baseline from $baseline to $count - the ratchet only tightens. Repair the registry instead." -ErrorAction Continue
        exit 1
    }
    $scope = $null
    try {
        $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-bridge-scenario-coverage.ps1 -UpdateBaseline'
        Set-Content -LiteralPath $baselineFile -Value $count -Encoding UTF8
    }
    finally { Exit-CodeLockScope -Scope $scope }
    Write-Host "assert-bridge-scenario-coverage: baseline lowered to $count." -ForegroundColor Green
    exit 0
}

foreach ($d in $divergences) { Write-Host "  $d" -ForegroundColor Yellow }

Write-Host ("assert-bridge-scenario-coverage: coverage {0} scenario-named + {1} excluded of {2} declared routes ({3} scenarios); expected: <= {4} divergence(s) | actual: {5}" -f `
        $scenarioChannels.Count, $excluded.Count, $allRoutes.Count, $scenarios.Count, $baseline, $count)

if ($count -gt $baseline) {
    Write-Error "assert-bridge-scenario-coverage: FAIL - $count divergence(s) above the baseline of $baseline." -ErrorAction Continue
    if ($Gate) { exit 1 }
    exit 0
}

Write-Host 'assert-bridge-scenario-coverage: PASS' -ForegroundColor Green
exit 0
