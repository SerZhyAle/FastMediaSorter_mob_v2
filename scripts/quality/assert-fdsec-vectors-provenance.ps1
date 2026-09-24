<#
.SYNOPSIS
    Contract gate: the vendored FDSEC-FORMAT conformance vectors must still be the catalog's vectors.

.DESCRIPTION
    FDSEC-FORMAT conformance is decided by its published vectors, not by its prose, and the unit
    suite reproduces a copy of them vendored under app_v2/src/test/resources/fdsec/. That copy was
    tied to nothing: a regeneration in the catalog would have left FdSecVectorsTest green against
    stale bytes (S3420).

    PROVENANCE.txt beside the vendored files records the contract id, its version, the vendoring
    date and one `sha256 <file> <hex>` row per file. FdSecVectorsTest holds the vendored bytes to
    those rows; this gate holds the rows to the catalog. It fails when a vendored file or a catalog
    file differs from its row, and when the catalog publishes a vector the record does not list.

    The catalog lives OUTSIDE this repository and its location is named in exactly one tracked file,
    CLAUDE.md, so this gate never spells it: it takes -CatalogRoot, falls back to
    $env:FMS_CONTRACTS_ROOT, and returns 3 (could not verify) when neither resolves - the same shape
    as assert-stream-asset-revisions.ps1.

.NOTES
    Exit codes:
      0  every vendored file and every catalog vector matches its PROVENANCE.txt row.
      1  a vendored file or a catalog vector differs from its row, or the catalog publishes a
         vector the record does not list - re-vendor from the catalog and rewrite the rows.
      2  the gate itself cannot run: PROVENANCE.txt is missing, names no contract, or carries no
         sha256 row.
      3  could not verify: the shared contracts catalog is not reachable from this machine.
#>
param(
    [switch] $Quiet,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    # Never defaulted to a literal path - CLAUDE.md is the one tracked file allowed to name it.
    [string] $CatalogRoot = $env:FMS_CONTRACTS_ROOT
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')

$name = 'assert-fdsec-vectors-provenance'
$vendoredDir = Join-Path $RepoRoot 'app_v2/src/test/resources/fdsec'
$recordPath = Join-Path $vendoredDir 'PROVENANCE.txt'

Write-CheckSubject -Axes ([ordered]@{ module = 'app_v2'; scope = 'fdsec-vectors'; files = 'app_v2/src/test/resources/fdsec' })

function Deny([string] $message) {
    Write-Error "assert-fdsec-vectors-provenance: CANNOT VERIFY - $message" -ErrorAction Continue
    exit 2
}

function Skip([string] $message) {
    if (-not $Quiet) { Write-Host "${name}: SKIP - $message" }
    exit 3
}

function Get-Sha256([string] $path) {
    return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
}

if (-not (Test-Path -LiteralPath $recordPath)) { Deny "cannot run - $recordPath missing." }

$record = Get-Content -LiteralPath $recordPath
$contract = ($record | Select-String -Pattern '^contract:\s*(\S+)' | Select-Object -First 1)
if ($null -eq $contract -or $contract.Matches[0].Groups[1].Value -ne 'FDSEC-FORMAT') {
    Deny "$recordPath names no 'contract: FDSEC-FORMAT' line."
}

$rows = [ordered]@{}
foreach ($line in $record) {
    $row = [regex]::Match($line.Trim(), '^sha256\s+(?<file>\S+)\s+(?<hash>[0-9A-Fa-f]{64})$')
    if ($row.Success) { $rows[$row.Groups['file'].Value] = $row.Groups['hash'].Value.ToLowerInvariant() }
}
if ($rows.Count -eq 0) { Deny "$recordPath carries no sha256 row - refusing to treat that as 'nothing vendored'." }

if ([string]::IsNullOrWhiteSpace($CatalogRoot)) {
    Skip 'the shared contracts catalog is not configured - set FMS_CONTRACTS_ROOT (CLAUDE.md names its location).'
}
$catalogDir = Join-Path $CatalogRoot 'secure-container/vectors'
if (-not (Test-Path -LiteralPath $catalogDir)) {
    Skip "the FDSEC-FORMAT vectors are not readable at '$catalogDir'."
}

$findings = @()
foreach ($file in $rows.Keys) {
    $expected = $rows[$file]
    $vendored = Join-Path $vendoredDir $file
    if (-not (Test-Path -LiteralPath $vendored)) {
        $findings += "$file is recorded but not vendored"
    } elseif ((Get-Sha256 $vendored) -ne $expected) {
        $findings += "the vendored $file differs from its PROVENANCE.txt row"
    }
    $published = Join-Path $catalogDir $file
    if (-not (Test-Path -LiteralPath $published)) {
        $findings += "$file is recorded but the catalog no longer publishes it"
    } elseif ((Get-Sha256 $published) -ne $expected) {
        $findings += "the catalog's $file differs from its PROVENANCE.txt row - the catalog regenerated it"
    }
}
foreach ($published in Get-ChildItem -LiteralPath $catalogDir -File) {
    if (-not $rows.Contains($published.Name)) {
        $findings += "the catalog publishes $($published.Name), which PROVENANCE.txt does not list"
    }
}

if ($findings.Count -gt 0) {
    foreach ($finding in $findings) { Write-Host "  - $finding" -ForegroundColor Red }
    Write-Host ("assert-fdsec-vectors-provenance: FAIL - $($findings.Count) finding(s). Re-vendor the vectors from the catalog, " +
        'rewrite the sha256 rows and re-run FdSecVectorsTest.') -ForegroundColor Red
    exit 1
}

Write-Host "assert-fdsec-vectors-provenance: PASS - $($rows.Count) vendored vector file(s) match the catalog." -ForegroundColor Green
exit 0
