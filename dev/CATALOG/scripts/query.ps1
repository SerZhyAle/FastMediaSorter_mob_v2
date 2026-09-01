# Queries the catalogue by filters. Combine filters freely (all are AND'd).
# -Module is optional and defaults to app_v2; pass -Module wear for the watch catalogue.
#
# Examples:
#   # Default module (app_v2), no -Module needed
#   pwsh -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*Manager*"
#
#   # All data-layer classes that touch disk
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer data -SideEffect disk
#
#   # Big files that may need decomposition
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -MinLoc 800
#
#   # Records missing role description
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Missing role
#
#   # All classes that inject a specific type
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Injected ResourceDao
#
#   # All classes that take a type as a constructor parameter (superset of -Injected;
#   # also catches non-Hilt / @Inject-free collaborators)
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -DependsOn ResourceDao
#
#   # Search for a term in class name, path, role, injected/constructor dependencies, or functions
#   # (-Query is canonical; -Search / -Text / -Name are aliases)
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Query "welcome"
#
#   # Classes changed since a date
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -TouchedSince 2026-04-01
#
#   # Machine-readable output for piping
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer ui -Json
#
#   # Named product sectors (S1344): list them, then map one
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Sectors
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Sector player
#
#   # A sector composes with every other filter rather than replacing them
#   pwsh -File dev/CATALOG/scripts/query.ps1 -Sector player -Layer ui
#
# Exit codes:
#   0 - success.
#   2 - unknown -Sector name, or dev/CATALOG/sectors.json is missing or malformed.

param(
    # Optional: only two catalogues exist and app_v2 is the overwhelming default, so requiring the
    # module turned every omission into a failed call. ValidateSet still catches a typo.
    [ValidateSet('app_v2','wear')][string]$Module = 'app_v2',
    [string]$Layer,
    [ValidateSet('new','tested','legacy','todo','unknown')][string]$Status,
    [ValidateSet('db','network','disk','prefs')][string]$SideEffect,
    [int]$MinLoc,
    [int]$MaxLoc,
    [string]$ClassMatches,
    [string]$PathMatches,
    # Named product sector (S1344). A sector is an alias over the class/path axes above, not a new
    # data structure - it removes the guess-the-search-word step, it does not replace the filters.
    [string]$Sector,
    [switch]$Sectors,
    # -Query is the canonical free-text parameter shared by the catalog and registry
    # query CLIs; -Search was this script's former spelling and stays as an alias.
    [Alias('Search','Text','Name')]
    [string]$Query,
    [string]$Injected,
    [string]$DependsOn,
    [ValidateSet('role','description')][string]$Missing,
    [switch]$Coroutines,
    [switch]$UserFeedback,
    [switch]$Tests,
    [switch]$NoTests,
    [string]$TouchedSince,
    [string]$TouchedBefore,
    [switch]$Json,
    [switch]$Help
)

if ($Help) {
    & (Join-Path $PSScriptRoot '..\..\..\scripts\utils\help.ps1') -Name 'dev/CATALOG/scripts/query.ps1'
    exit $LASTEXITCODE
}

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
$SectorFile = Join-Path $Root "dev\CATALOG\sectors.json"
$MarkerFile = Join-Path $Root "temp\catalog-touch.marker"

$InvokedWith = (($PSBoundParameters.GetEnumerator() | Sort-Object Key | ForEach-Object {
    if ($_.Value -is [switch]) { "-$($_.Key)" } else { "-$($_.Key) `"$($_.Value)`"" }
}) -join ' ')

# S1344: a PreToolUse hook keeps no state between tool calls, so "has this session consulted the
# catalogue" has nowhere to live but a file - the mechanism temp/BUILD.LOCK and temp/CODE.LOCK
# already use under Rule 23. A marker that cannot be written must never fail the query: a catalogue
# less reliable than the grep it replaces gets abandoned, and the gate with it.
function Write-CatalogTouchMarker {
    try {
        $dir = Split-Path -Parent $MarkerFile
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        $stamp = Get-Date -Format 'yyyy-MM-ddTHH:mm:ssK'
        Set-Content -Path $MarkerFile -Value "$stamp`nquery.ps1 $InvokedWith" -Encoding UTF8
    }
    catch {
        Write-Warning "catalog-touch marker not written ($MarkerFile): $_"
    }
}

function Get-SectorDefinition {
    if (-not (Test-Path $SectorFile)) {
        Write-Error "Sector definitions not found: $SectorFile" -ErrorAction Continue
        exit 2
    }
    try {
        return (Get-Content -Path $SectorFile -Raw -Encoding UTF8 | ConvertFrom-Json).sectors
    }
    catch {
        Write-Error "Sector definitions malformed: $SectorFile - $_" -ErrorAction Continue
        exit 2
    }
}

# Listing sectors must work without a built catalogue - it is the discovery call an agent makes
# before it knows which module it needs.
if ($Sectors) {
    foreach ($s in (Get-SectorDefinition)) {
        Write-Host ("{0,-10} {1}" -f $s.name, $s.description)
    }
    Write-CatalogTouchMarker
    exit 0
}

$InFile = Join-Path $Root "dev\CATALOG\$Module.jsonl"
if (-not (Test-Path $InFile)) { throw "Catalogue not found: $InFile" }

# One parse for the whole journal, not one per line. Measured on app_v2.jsonl (3233 records,
# 3.2 MB): reading the bytes costs 26 ms and a per-line ConvertFrom-Json costs 3034 ms, so the
# parser - not the file - was this query's whole cost. `$result +=` compounded it by reallocating
# the array on every record. Joining the lines into one JSON array parses the same records in
# 384 ms. The format on disk is unchanged; only the number of parser invocations is.
$catalogLines = @([System.IO.File]::ReadAllLines($InFile) | Where-Object { $_ })
$result = if ($catalogLines.Count -eq 0) { @() } else { @(('[' + ($catalogLines -join ',') + ']') | ConvertFrom-Json) }

if ($Layer)         { $result = @($result | Where-Object { $_.layer -eq $Layer }) }
if ($Status)        { $result = @($result | Where-Object { $_.status -eq $Status }) }
if ($SideEffect)    { $result = @($result | Where-Object { $_.sideEffects -contains $SideEffect }) }
if ($MinLoc)        { $result = @($result | Where-Object { $_.loc -ge $MinLoc }) }
if ($MaxLoc)        { $result = @($result | Where-Object { $_.loc -le $MaxLoc }) }
if ($ClassMatches)  { $result = @($result | Where-Object { $_.class -like $ClassMatches }) }
if ($PathMatches)   { $result = @($result | Where-Object { $_.path -like $PathMatches }) }
if ($Sector) {
    $definitions = Get-SectorDefinition
    $definition = $definitions | Where-Object { $_.name -eq $Sector.ToLower() } | Select-Object -First 1
    if (-not $definition) {
        Write-Error "Unknown sector '$Sector'. Known sectors: $(($definitions.name) -join ', ')" -ErrorAction Continue
        exit 2
    }
    # Precedence mirrors catalog_sync's stamp pass: an explicit override wins, then the class-name
    # axis, then the path axis. A class name alone under-claims domain/data (S1344 phase 01 log:
    # *Player* is 84/90 ui), so the path axis is not a fallback - it carries the rest of the sector.
    $result = @($result | Where-Object {
        $cls = if ($_.class) { $_.class } else { '' }
        $pth = if ($_.path) { $_.path } else { '' }
        $hit = ($definition.overrides -contains $cls) -or ($definition.overrides -contains $pth)
        if (-not $hit) {
            foreach ($pattern in $definition.classMatches) { if ($cls -like $pattern) { $hit = $true; break } }
        }
        if (-not $hit) {
            foreach ($pattern in $definition.pathMatches) { if ($pth -like $pattern) { $hit = $true; break } }
        }
        $hit
    })
}
if ($Query) {
    # Tokenise on whitespace and require EVERY token to be present (AND). A single -like over the
    # whole phrase fails for multi-word queries ("screen capture") because those words never appear
    # as one adjacent substring in class/path/function names.
    $terms = @(($Query.ToLower() -split '\s+') | Where-Object { $_ })
    # The fixed root package poisons substring search: every path contains "fastmediasorter", so
    # domain terms like "sort"/"sorter"/"media" would otherwise match all records. Strip the prefix
    # from the match haystack (genuine "Sorter" in a class name or subpackage still matches).
    $pkgPrefix = "com/sza/fastmediasorter/"
    $result = @($result | Where-Object {
        $injectedStr = if ($_.injected) { ($_.injected -join " ") } else { "" }
        $depsStr = if ($_.constructorDeps) { ($_.constructorDeps -join " ") } else { "" }
        $funcsStr = ""
        if ($_.functions) {
            $funcsStr = (($_.functions | ForEach-Object { "$($_.name) $($_.description)" }) -join " ")
        }
        $roleStr = if ($_.role) { $_.role } else { "" }
        $classStr = if ($_.class) { $_.class } else { "" }
        $pathStr = if ($_.path) { $_.path } else { "" }
        if ($pathStr.StartsWith($pkgPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            $pathStr = $pathStr.Substring($pkgPrefix.Length)
        }
        $hay = "$classStr $pathStr $roleStr $injectedStr $depsStr $funcsStr".ToLower()
        $ok = $true
        foreach ($t in $terms) { if ($hay -notlike "*$t*") { $ok = $false; break } }
        $ok
    })
}
if ($Injected)      { $result = @($result | Where-Object { $_.injected -contains $Injected }) }
if ($DependsOn)     { $result = @($result | Where-Object { $_.constructorDeps -contains $DependsOn }) }
if ($Coroutines)    { $result = @($result | Where-Object { $_.coroutines }) }
if ($UserFeedback)  { $result = @($result | Where-Object { $_.userFeedback }) }
if ($Tests)         { $result = @($result | Where-Object { $_.hasTests }) }
if ($NoTests)       { $result = @($result | Where-Object { -not $_.hasTests }) }
if ($TouchedSince)  { $result = @($result | Where-Object { $_.lastTouched -ge $TouchedSince }) }
if ($TouchedBefore) { $result = @($result | Where-Object { $_.lastTouched -and $_.lastTouched -lt $TouchedBefore }) }
if ($Missing -eq 'role') {
    $result = @($result | Where-Object { -not $_.role })
}
if ($Missing -eq 'description') {
    $result = @($result | Where-Object {
        @($_.functions | Where-Object { -not $_.description }).Count -gt 0
    })
}

if ($Json) {
    $result | ForEach-Object { $_ | ConvertTo-Json -Depth 10 -Compress }
    Write-CatalogTouchMarker
    exit 0
}

if (-not $result -or $result.Count -eq 0) {
    Write-Host "No records matched." -ForegroundColor Yellow
    Write-CatalogTouchMarker
    exit 0
}

$result |
    Sort-Object -Property @{Expression={$_.layer}}, @{Expression={$_.path}} |
    Select-Object `
        @{N='path';E={$_.path}}, `
        @{N='class';E={$_.class}}, `
        @{N='layer';E={$_.layer}}, `
        @{N='loc';E={$_.loc}}, `
        @{N='last';E={$_.lastTouched}}, `
        @{N='status';E={$_.status}}, `
        @{N='role';E={if ($_.role) { $_.role } else { '-' }}} |
    Format-Table -AutoSize -Wrap

Write-Host "`n$($result.Count) records matched" -ForegroundColor Cyan

Write-CatalogTouchMarker
exit 0
