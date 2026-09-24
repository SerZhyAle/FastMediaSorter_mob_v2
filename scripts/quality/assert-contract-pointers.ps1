<#
.SYNOPSIS
    Contract gate: the contract pointer files, their index, the summary table and the catalog registry
    must name one set of ids and versions.

.DESCRIPTION
    REPO-LAYOUT rule 2 makes docs/contracts/<ID>.md the pointer set, one file per contract. The
    repository also keeps docs/contracts/README.md as the index and docs/CROSS_PROJECT_CONTRACTS.md as
    the one-page summary with the obligation each contract puts on this product - a column the pointer
    files cannot generate. Three lists of the same ids drift apart by hand: on 2026-09-24 the
    LIVE-BROADCAST pointer still read 0.9 while the summary and the catalog registry read 0.11 (S3457).

    In-repo half, always run:
      - every pointer file carries an Id row and a Version row whose versions pair with its ids;
      - the index links every pointer file, and every file it links exists;
      - the summary tables name exactly the pointer ids, each with the pointer's version.
    Catalog half, run when the catalog is reachable: every pointer id has a row in the catalog's
    _meta/REGISTRY.md with the same version.

    The catalog lives OUTSIDE this repository and its location is named in exactly one tracked file,
    CLAUDE.md, so this gate never spells it: it takes -CatalogRoot, falls back to
    $env:FMS_CONTRACTS_ROOT, and returns 3 only when the in-repo half passed and the catalog could not
    be read - the same shape as assert-stream-asset-revisions.ps1.

.NOTES
    Exit codes:
      0  pointers, index, summary and registry agree on every id and version.
      1  a pointer, the index, the summary or the registry disagrees - each finding is printed.
      2  the gate itself cannot run: docs/contracts/, its README.md or the summary file is missing,
         or no pointer file or summary row parsed at all.
      3  could not verify: the in-repo half passed but the shared contracts catalog is not reachable.
#>
param(
    [switch] $Quiet,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    # Never defaulted to a literal path - CLAUDE.md is the one tracked file allowed to name it.
    [string] $CatalogRoot = $env:FMS_CONTRACTS_ROOT
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')

$name = 'assert-contract-pointers'
$pointerDir = Join-Path $RepoRoot 'docs/contracts'
$indexPath = Join-Path $pointerDir 'README.md'
$summaryPath = Join-Path $RepoRoot 'docs/CROSS_PROJECT_CONTRACTS.md'

Write-CheckSubject -Axes ([ordered]@{ module = 'docs'; scope = 'contract-pointers'; files = 'docs/contracts,docs/CROSS_PROJECT_CONTRACTS.md' })

function Deny([string] $message) {
    Write-Error "assert-contract-pointers: CANNOT VERIFY - $message" -ErrorAction Continue
    exit 2
}

function Get-Ids([string] $cell) {
    return @([regex]::Matches($cell, '`(?<id>[A-Z0-9][A-Z0-9-]+)`') | ForEach-Object { $_.Groups['id'].Value })
}

# A version cell may carry prose after the number ("0.11, draft", "2.1 (reads schemas 1-2)",
# "1.0 each"); only the dotted numbers before that prose are versions.
function Get-Versions([string] $cell) {
    $head = ($cell -replace '\(.*?\)', '') -split '[,;]\s*(?=[a-z])' | Select-Object -First 1
    return @([regex]::Matches($head, '\b\d+\.\d+\b') | ForEach-Object { $_.Value })
}

# Pairs ids with versions: one version per id, or one version shared by every id ("0.9 each").
function Join-IdVersion([string[]] $ids, [string[]] $versions, [string] $where, [ref] $findings) {
    $pairs = [ordered]@{}
    if ($versions.Count -eq $ids.Count) {
        for ($i = 0; $i -lt $ids.Count; $i++) { $pairs[$ids[$i]] = $versions[$i] }
    } elseif ($versions.Count -eq 1) {
        foreach ($id in $ids) { $pairs[$id] = $versions[0] }
    } else {
        $findings.Value += "$where names $($ids.Count) id(s) but $($versions.Count) version(s)"
    }
    return $pairs
}

foreach ($required in @($pointerDir, $indexPath, $summaryPath)) {
    if (-not (Test-Path -LiteralPath $required)) { Deny "cannot run - $required missing." }
}

$findings = @()

$pointers = [ordered]@{}
$pointerFiles = @(Get-ChildItem -LiteralPath $pointerDir -Filter '*.md' -File | Where-Object Name -ne 'README.md')
if ($pointerFiles.Count -eq 0) { Deny "no pointer file in $pointerDir." }
foreach ($file in $pointerFiles) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    $idRow = [regex]::Match($text, '(?m)^\|\s*\*\*Id\*\*\s*\|(?<cell>[^|]+)\|')
    $versionRow = [regex]::Match($text, '(?m)^\|\s*\*\*Version\*\*\s*\|(?<cell>[^|]+)\|')
    if (-not $idRow.Success -or -not $versionRow.Success) {
        $findings += "$($file.Name) has no **Id** or **Version** row"
        continue
    }
    $ids = Get-Ids $idRow.Groups['cell'].Value
    if ($ids.Count -eq 0) { $findings += "$($file.Name) names no id in its **Id** row"; continue }
    $pairs = Join-IdVersion $ids (Get-Versions $versionRow.Groups['cell'].Value) $file.Name ([ref] $findings)
    foreach ($id in $pairs.Keys) {
        if ($pointers.Contains($id)) {
            $findings += "$id has two pointer files: $($pointers[$id].File) and $($file.Name)"
        } else {
            $pointers[$id] = [pscustomobject]@{ File = $file.Name; Version = $pairs[$id] }
        }
    }
}

$indexed = @([regex]::Matches((Get-Content -LiteralPath $indexPath -Raw), '\]\((?<file>[A-Za-z0-9-]+\.md)\)') |
    ForEach-Object { $_.Groups['file'].Value } | Where-Object { $_ -ne 'README.md' } | Sort-Object -Unique)
foreach ($file in $pointerFiles) {
    if ($file.Name -notin $indexed) { $findings += "docs/contracts/README.md does not index $($file.Name)" }
}
foreach ($file in $indexed) {
    if (-not (Test-Path -LiteralPath (Join-Path $pointerDir $file))) {
        $findings += "docs/contracts/README.md indexes $file, which does not exist"
    }
}

$summary = [ordered]@{}
foreach ($line in Get-Content -LiteralPath $summaryPath) {
    if ($line -notmatch '^\|\s*`') { continue }
    $cells = $line.Trim().Trim('|') -split '\|'
    if ($cells.Count -lt 2) { continue }
    $ids = Get-Ids $cells[0]
    $pairs = Join-IdVersion $ids (Get-Versions $cells[1]) "the summary row for $($ids -join ', ')" ([ref] $findings)
    foreach ($id in $pairs.Keys) { $summary[$id] = $pairs[$id] }
}
if ($summary.Count -eq 0) { Deny "no contract row parsed from $summaryPath." }

foreach ($id in $pointers.Keys) {
    if (-not $summary.Contains($id)) {
        $findings += "$id has a pointer file but no row in docs/CROSS_PROJECT_CONTRACTS.md"
    } elseif ($summary[$id] -ne $pointers[$id].Version) {
        $findings += "$id reads $($pointers[$id].Version) in $($pointers[$id].File) but $($summary[$id]) in docs/CROSS_PROJECT_CONTRACTS.md"
    }
}
foreach ($id in $summary.Keys) {
    if (-not $pointers.Contains($id)) { $findings += "$id has a row in docs/CROSS_PROJECT_CONTRACTS.md but no pointer file" }
}

$catalogNote = $null
$registryPath = if ([string]::IsNullOrWhiteSpace($CatalogRoot)) { $null } else { Join-Path $CatalogRoot '_meta/REGISTRY.md' }
if ($null -eq $registryPath) {
    $catalogNote = 'the shared contracts catalog is not configured - set FMS_CONTRACTS_ROOT (CLAUDE.md names its location).'
} elseif (-not (Test-Path -LiteralPath $registryPath)) {
    $catalogNote = "the catalog registry is not readable at '$registryPath'."
} else {
    # A contract row is the first table row whose second cell links the contract's home; later
    # tables (exceptions, adoption) mention the same ids without a version.
    $registry = @{}
    foreach ($line in Get-Content -LiteralPath $registryPath) {
        $row = [regex]::Match($line, '^\|\s*`(?<id>[A-Z0-9][A-Z0-9-]+)`\s*\|\s*\[[^\]]*\]\([^)]*\)\s*\|\s*(?<version>\d+\.\d+)\s*\|')
        if ($row.Success -and -not $registry.ContainsKey($row.Groups['id'].Value)) {
            $registry[$row.Groups['id'].Value] = $row.Groups['version'].Value
        }
    }
    foreach ($id in $pointers.Keys) {
        if (-not $registry.ContainsKey($id)) {
            $findings += "$id has a pointer file but no row in the catalog registry"
        } elseif ($registry[$id] -ne $pointers[$id].Version) {
            $findings += "$id reads $($pointers[$id].Version) in $($pointers[$id].File) but $($registry[$id]) in the catalog registry"
        }
    }
}

if ($findings.Count -gt 0) {
    foreach ($finding in $findings) { Write-Host "  - $finding" -ForegroundColor Red }
    Write-Host ("assert-contract-pointers: FAIL - $($findings.Count) finding(s). The catalog registry decides the version; " +
        'update the pointer file, its index row and its summary row together.') -ForegroundColor Red
    exit 1
}

if ($null -ne $catalogNote) {
    if (-not $Quiet) { Write-Host "${name}: SKIP - $($pointers.Count) id(s) agree in the repository; $catalogNote" }
    exit 3
}

Write-Host "assert-contract-pointers: PASS - $($pointers.Count) contract id(s) in $($pointerFiles.Count) pointer file(s) agree with the index, the summary and the catalog registry." -ForegroundColor Green
exit 0
