<#
.SYNOPSIS
    Contract gate: every pinned stream-catalog asset the publisher still owns must keep being published.

.DESCRIPTION
    External consumers hard-code the names of revisioned release assets and do not roll
    forward on their own, so a raised revision default silently strands whoever is still
    fetching the displaced name. Nothing in the repository deletes a release asset, which
    is exactly the problem: the survival of a pinned revision rests on the absence of an
    action rather than on a rule (S1828).

    The pinned names live in one place, the `STREAM-BANK` consumer registry
    (`CONSUMERS.md` of the stream-catalog domain), between the literal markers
    <!-- pinned-assets:begin --> and <!-- pinned-assets:end -->. This gate reads them there
    rather than holding its own copy: a second independent literal is the failure the same
    registry documents for the 30 MiB atlas ceiling.

    That registry lives in the shared contracts catalog, which is OUTSIDE this repository and whose
    location is named in exactly one tracked file - CLAUDE.md. So this gate never spells it: it takes
    -CatalogRoot, falls back to $env:FMS_CONTRACTS_ROOT, and answers COULD NOT VERIFY (exit 2) when
    neither resolves - a machine that cannot see the catalog has learned nothing about whether the
    code agrees with it (CHECK-VERDICT rule 2.2, which names this very check). A checkout without
    the catalog is the ordinary case for a CI clone, so the caller that expects one passes
    -AllowNoCatalog and gets NOT APPLICABLE (exit 0) instead; a gate that failed there with no way
    out would be permanently red for everyone but the maintainer. Until 2026-09-22 the path was
    hard-coded under the repo root, where the catalog has never been mounted: the gate had been
    returning 2 on every run, and `a.ps1 fg` counted it among its failures without anyone reading
    which one it was.

    A pinned row carries a Coverage token. `default` means the publisher's current revision
    defaults still produce the name on every run, and the gate fails when such a row stops
    being produced. `frozen` means the name is deliberately no longer republished and must
    only survive, so its absence from the produced set is the expected state. Without that
    distinction the gate would be permanently red: the consumer pins both the -v1 and -v3
    preview assets while the publisher has a single revision default.

    The produced set is derived from the publisher itself - the "<base>-{0}.<ext>" -f $Rev
    format strings and the matching parameter defaults - so no asset name is spelled twice.

.PARAMETER AllowNoCatalog
    The caller declaring a checkout without the shared contracts catalog acceptable for this run.
    `assert-fast-gates.ps1` passes it, because the GitHub Static Gates job runs on a clone that
    does not carry the catalog. With it, an unreachable catalog ends
    `assert-stream-asset-revisions: NOT APPLICABLE (..; -AllowNoCatalog)` with exit 0, which every
    aggregator counts as skipped, never as passed (CHECK-VERDICT 0.12 item A.1). Without it the
    same situation is exit 2. The publisher's own call (StreamPublisher.Delivery.ps1) omits it, so a
    publication without the catalog is still refused.

.NOTES
    Exit codes:
      0  every `default` pinned asset is still produced by the publisher; or NOT APPLICABLE
         because -AllowNoCatalog was passed and the catalog is not reachable.
      1  a pinned asset marked `default` would stop being published - the run is refused.
      2  could not verify: the gate cannot run - the shared contracts catalog is not reachable
         from this machine (unless -AllowNoCatalog), the publisher is missing, the markers are
         absent, the pinned block is empty, a Coverage token is unknown, or a revision
         parameter's default could not be read.

    The last stdout line is the verdict: `PASS (..)`, `FAIL (..)`, `COULD NOT VERIFY (..)` or
    `NOT APPLICABLE (..)`.
#>
param(
    [switch] $Gate,
    [switch] $Quiet,
    [switch] $AllowNoCatalog,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    # The shared contracts catalog's root. Never defaulted to a literal path here - CLAUDE.md is the
    # one tracked file allowed to name it, and a clone of this repository does not have it at all.
    [string] $CatalogRoot = $env:FMS_CONTRACTS_ROOT
)

$ErrorActionPreference = 'Stop'

function Deny([string] $message, [int] $code) {
    Write-Error "stream-asset-revisions: $message" -ErrorAction Continue
    # CHECK-VERDICT rules 2.5 and 2.11: the verdict line is last on stdout, on the failure path too.
    if ($code -eq 1) {
        Write-Host 'assert-stream-asset-revisions: FAIL (a pinned asset would stop being published)'
    }
    else {
        Write-Host 'assert-stream-asset-revisions: COULD NOT VERIFY (see the error above)'
    }
    exit $code
}

function Stop-NoCatalog([string] $message) {
    if ($AllowNoCatalog) {
        Write-Host "assert-stream-asset-revisions: NOT APPLICABLE ($message; -AllowNoCatalog)"
        exit 0
    }
    Deny $message 2
}

if ([string]::IsNullOrWhiteSpace($CatalogRoot)) {
    Stop-NoCatalog 'the shared contracts catalog is not configured - set FMS_CONTRACTS_ROOT (CLAUDE.md names its location)'
}
$registryPath = Join-Path $CatalogRoot 'stream-catalog/CONSUMERS.md'
if (-not (Test-Path -LiteralPath $registryPath)) {
    Stop-NoCatalog "the STREAM-BANK consumer registry is not readable at '$registryPath'"
}
$publisherPaths = @(
    Join-Path $RepoRoot 'scripts/streams/collect-stream-candidates.ps1'
    Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'scripts/streams/modules') -Filter '*.ps1' -File -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty FullName
)

if (-not (Test-Path $publisherPaths[0])) { Deny "cannot run - $($publisherPaths[0]) missing." 2 }

$registry = Get-Content $registryPath -Raw
$publisher = ($publisherPaths | ForEach-Object { Get-Content $_ -Raw }) -join "`n"

# The markers, not a heading: a renamed heading must not silently empty the pinned set.
$blockMatch = [regex]::Match(
    $registry,
    '<!--\s*pinned-assets:begin\s*-->(?<body>.*?)<!--\s*pinned-assets:end\s*-->',
    [System.Text.RegularExpressions.RegexOptions]::Singleline)
if (-not $blockMatch.Success) {
    Deny "the pinned-assets markers are missing from $registryPath - an unreadable registry must not read as 'nothing is pinned'." 2
}

# | `base` | rev | coverage | consumer | reason |
$rowPattern = '^\|\s*`(?<base>[^`]+)`\s*\|\s*(?<rev>[^|\s]+)\s*\|\s*(?<coverage>[^|\s]+)\s*\|'
$pinned = @()
foreach ($line in ($blockMatch.Groups['body'].Value -split "`r?`n")) {
    $row = [regex]::Match($line, $rowPattern)
    if (-not $row.Success) { continue }
    $coverage = $row.Groups['coverage'].Value
    if ($coverage -notin @('default', 'frozen')) {
        Deny "row '$($row.Groups['base'].Value)' carries Coverage '$coverage'; expected 'default' or 'frozen'." 2
    }
    $pinned += [pscustomobject]@{
        Base     = $row.Groups['base'].Value
        Revision = $row.Groups['rev'].Value
        Coverage = $coverage
    }
}

if ($pinned.Count -eq 0) {
    Deny "the pinned-assets block in $registryPath parsed to zero rows - refusing to treat that as 'nothing is pinned'." 2
}

# Read the produced names off the publisher's own format strings, so the base names and the
# extensions are never spelled a second time here.
$produced = @{}
$formatMatches = [regex]::Matches($publisher, '"(?<base>[A-Za-z0-9-]+)-\{0\}\.(?<ext>[A-Za-z0-9]+)"\s*-f\s*\$(?<param>\w+)')
foreach ($m in $formatMatches) {
    $paramName = $m.Groups['param'].Value
    $defaultMatch = [regex]::Match($publisher, ('\$' + $paramName + "\s*=\s*'(?<value>[^']+)'"))
    if (-not $defaultMatch.Success) {
        Deny "could not read the default of `$$paramName from the publisher sources." 2
    }
    $produced[$m.Groups['base'].Value] = [pscustomobject]@{
        Extension = $m.Groups['ext'].Value
        Parameter = $paramName
        Revision  = $defaultMatch.Groups['value'].Value
    }
}

if ($produced.Count -eq 0) {
    Deny "no revisioned asset format strings found in the publisher sources - the gate would pass vacuously." 2
}

$lost = @()
foreach ($pin in $pinned) {
    if ($pin.Coverage -ne 'default') { continue }
    $rule = $produced[$pin.Base]
    if ($null -eq $rule) {
        $lost += "$($pin.Base)-$($pin.Revision) is pinned as 'default' but the publisher produces no asset named '$($pin.Base)' at all"
        continue
    }
    if ($rule.Revision -ne $pin.Revision) {
        $lost += ("{0}-{1}.{2} is pinned as 'default' but the next run publishes revision '{3}' instead (`${4} = '{3}')" -f `
            $pin.Base, $pin.Revision, $rule.Extension, $rule.Revision, $rule.Parameter)
    }
}

if ($lost.Count -gt 0) {
    $detail = ($lost | ForEach-Object { "  - $_" }) -join "`n"
    Deny ("a pinned asset would stop being published:`n$detail`n" +
        "  Fix by restoring the revision default, or by recording the displaced rows as 'frozen' in " +
        "the STREAM-BANK consumer registry in the same change - a frozen row is never republished but is never deleted either.") 1
}

$defaults = ($pinned | Where-Object { $_.Coverage -eq 'default' }).Count
$frozen = ($pinned | Where-Object { $_.Coverage -eq 'frozen' }).Count
Write-Host ("assert-stream-asset-revisions: PASS - {0} pinned asset(s) still published, {1} frozen and untouched." -f `
    $defaults, $frozen) -ForegroundColor Green

exit 0
