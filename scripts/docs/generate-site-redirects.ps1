<#
.SYNOPSIS
    Writes the forwarder page of every moved or retired address listed in docs/site-redirects.jsonl (S4097).

.DESCRIPTION
    A published address never changes meaning (SITE-STRUCTURE 0.1 rule 8): a page that moves leaves a
    page at the old address that names the new one in a link, a canonical reference and a refresh; a page
    retired without a successor leaves a page that says so and links the documentation home and the
    landing page. The manifest holds one record per old address:
    {"from_path","to_path" (null when retired),"reason","ticket","date"}.

    The page text comes from Get-ForwarderHtml in scripts/docs/lib/site-addresses.ps1, the same function
    scripts/quality/assert-site-addresses.ps1 compares against, so the writer and the gate cannot drift.

.PARAMETER Check
    Write nothing; exit 1 when a forwarder is missing or differs from what the manifest produces.

.PARAMETER ManifestPath
    Redirect manifest; defaults to docs/site-redirects.jsonl. Overridable for tests.

.PARAMETER SiteRoot
    Directory the from_path values are relative to; defaults to the repository root. Overridable for tests.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/generate-site-redirects.ps1 -Check

.NOTES
    Exit codes:
      0  every forwarder was written, or -Check found them all current
      1  -Check found a missing or outdated forwarder
      2  could not verify: a record is malformed, or two records share a from_path
#>
[CmdletBinding()]
param(
    [switch] $Check,
    [string] $ManifestPath,
    [string] $SiteRoot
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/site-addresses.ps1')
. (Join-Path $PSScriptRoot 'lib/doc-stamp.ps1')

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $ManifestPath) { $ManifestPath = Join-Path $repoRoot 'docs/site-redirects.jsonl' }
if (-not $SiteRoot) { $SiteRoot = $repoRoot }

$records = Read-JsonlRecords -Path $ManifestPath
$seen = @{}
$bad = 0
foreach ($record in $records) {
    $problems = Test-RedirectRecord -Record $record
    if ($problems.Count -gt 0) {
        $bad++
        Write-Host "generate-site-redirects: record '$($record.from_path)': $($problems -join '; ')" -ForegroundColor Red
        continue
    }
    if ($seen.ContainsKey($record.from_path)) {
        $bad++
        Write-Host "generate-site-redirects: from_path '$($record.from_path)' is listed twice" -ForegroundColor Red
    }
    $seen[$record.from_path] = $true
}
if ($bad -gt 0) { exit 2 }

$stale = 0
$written = 0
# Derived from the site root so a test site carries its own registry and the real one is never touched.
$registryPath = Join-Path $SiteRoot 'docs/DOCUMENT_REGISTRY.jsonl'
$registryText = if (Test-Path -LiteralPath $registryPath) { [IO.File]::ReadAllText($registryPath) } else { $null }
$registrySynced = if ($null -ne $registryText) { Get-RegistryWithForwarderExclusions -RegistryText $registryText -Records $records } else { $null }
foreach ($record in $records) {
    $expected = Get-ForwarderHtml -Record $record
    $target = Join-Path $SiteRoot $record.from_path
    if ($Check) {
        if (-not (Test-Path -LiteralPath $target)) {
            Write-Host "  [MISSING]  $($record.from_path)" -ForegroundColor Red
            $stale++
            continue
        }
        # The closure may add a last-edited stamp to an html page; it is not part of the forwarder text.
        $onDisk = Remove-DocStamp -Text ([IO.File]::ReadAllText($target))
        if ($onDisk -cne $expected) {
            Write-Host "  [OUTDATED] $($record.from_path)" -ForegroundColor Yellow
            $stale++
        }
        continue
    }
    $dir = Split-Path -Parent $target
    if (-not (Test-Path -LiteralPath $dir)) { $null = New-Item -ItemType Directory -Force $dir }
    [IO.File]::WriteAllText($target, $expected, [Text.UTF8Encoding]::new($false))
    $written++
}

if ($Check -and $null -ne $registryText -and $registrySynced -cne $registryText) {
    Write-Host '  [OUTDATED] docs/DOCUMENT_REGISTRY.jsonl: forwarder sitemap_exclude rows differ from the manifest' -ForegroundColor Yellow
    $stale++
}
if (-not $Check -and $null -ne $registryText -and $registrySynced -cne $registryText) {
    [IO.File]::WriteAllText($registryPath, $registrySynced, [Text.UTF8Encoding]::new($false))
    Write-Host '  [SYNCED]   docs/DOCUMENT_REGISTRY.jsonl forwarder sitemap_exclude rows'
}

if ($Check) {
    if ($stale -gt 0) {
        Write-Host "generate-site-redirects: $stale forwarder(s) missing or outdated; run without -Check." -ForegroundColor Red
        exit 1
    }
    Write-Host "generate-site-redirects: PASS ($($records.Count) forwarder(s) current)" -ForegroundColor Green
    exit 0
}
Write-Host "generate-site-redirects: $written forwarder(s) written" -ForegroundColor Green
exit 0
