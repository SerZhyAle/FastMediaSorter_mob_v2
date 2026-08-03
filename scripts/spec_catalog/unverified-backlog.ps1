#requires -Version 5.1
<#
.SYNOPSIS
    Report the shipped-but-unproven backlog: BlockNeedUserTest against Verified (S1338).

.DESCRIPTION
    The ship-to-verify ratio measured 6.9:1 - 87 tickets parked in BlockNeedUserTest against
    39 Verified, with 38% of the catalog shipped and never proven on a device. An autonomous
    loop that keeps chaining tickets while that pile grows is manufacturing unverified work,
    so S1339's loop needs a machine-readable stop reason. This script is that number.

    It reads the ACTIVE journal only - an archived ticket is closed, not pending - counts the
    two statuses, computes the ratio, and compares the BlockNeedUserTest count to -Ceiling.

    Live probe tags are reported alongside as a second symptom of the same backlog: every
    ticket in BlockNeedUserTest is supposed to carry `Timber.d("Sxxxx: ..")` lines in source,
    and they are removed on the way out (CLAUDE.md "Debug Verification Tags"). Counting is
    opt-in via -IncludeTags because it walks app_v2/src and wear/src.

.PARAMETER Ceiling
    Maximum acceptable BlockNeedUserTest count. Exceeded -> exit 3. Omitted -> report only.

.PARAMETER IncludeTags
    Also count live `Timber.d("Sxxxx:` probe lines and the files carrying them.

.PARAMETER Json
    Emit one JSON object instead of the human-readable lines. This is the shape S1339 parses.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  read the catalog; BlockNeedUserTest is at or below -Ceiling, or no ceiling was given.
      1  error - the catalog could not be parsed.
      2  cannot verify - the catalog journal does not exist.
      3  -Ceiling exceeded.

    Contract note: S1339's loop consumes these exit codes and the -Json field names. Changing
    either is a breaking change for that ticket.

.EXAMPLE
    pwsh -NoProfile -File scripts/spec_catalog/unverified-backlog.ps1
    pwsh -NoProfile -File scripts/spec_catalog/unverified-backlog.ps1 -Ceiling 60 -Json
#>
[CmdletBinding()]
param(
    [int]$Ceiling = 0,
    [switch]$IncludeTags,
    [switch]$Json,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    & (Join-Path $PSScriptRoot '..\utils\help.ps1') -Name 'scripts/spec_catalog/unverified-backlog.ps1'
    exit 0
}

. (Join-Path $PSScriptRoot '_lib.ps1')

$catalogPath = Get-CatalogPath
if (-not (Test-Path -LiteralPath $catalogPath)) {
    Write-Error "unverified-backlog: catalog journal not found at $catalogPath - cannot verify." -ErrorAction Continue
    exit 2
}

try {
    # No @() here: Read-Catalog returns `,$array` to defend against unrolling, so wrapping the
    # call collects ONE element - the array itself - and every count below reads 1.
    $records = Read-Catalog
}
catch {
    Write-Error "unverified-backlog: cannot parse the catalog - $($_.Exception.Message)" -ErrorAction Continue
    exit 1
}

$blocked = @($records | Where-Object { $_.status -eq 'BlockNeedUserTest' })
$verified = @($records | Where-Object { $_.status -eq 'Verified' })
$blockedCount = $blocked.Count
$verifiedCount = $verified.Count
# A zero denominator is not an infinite ratio worth printing - report the count itself.
$ratio = if ($verifiedCount -gt 0) { [math]::Round($blockedCount / $verifiedCount, 2) } else { $null }

$tagLines = $null
$tagFiles = $null
if ($IncludeTags) {
    $roots = @('app_v2/src', 'wear/src') |
        ForEach-Object { Join-Path $script:RepoRoot ($_ -replace '/', '\') } |
        Where-Object { Test-Path -LiteralPath $_ }
    $hits = @()
    foreach ($root in $roots) {
        $hits += @(Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue |
            Select-String -Pattern 'Timber\.d\("S\d{4}:' -SimpleMatch:$false -ErrorAction SilentlyContinue)
    }
    $tagLines = $hits.Count
    $tagFiles = @($hits | Select-Object -ExpandProperty Path -Unique).Count
}

$exceeded = ($Ceiling -gt 0 -and $blockedCount -gt $Ceiling)

if ($Json) {
    $payload = [ordered]@{
        blockNeedUserTest = $blockedCount
        verified          = $verifiedCount
        ratio             = $ratio
        ceiling           = $Ceiling
        exceeded          = $exceeded
        probeTagLines     = $tagLines
        probeTagFiles     = $tagFiles
    }
    $payload | ConvertTo-Json -Depth 3 -Compress
}
else {
    $ratioText = if ($null -ne $ratio) { "$ratio : 1" } else { 'n/a (no Verified tickets)' }
    Write-Host ("unverified-backlog: BlockNeedUserTest {0} | Verified {1} | ship-to-verify {2}" -f
        $blockedCount, $verifiedCount, $ratioText)
    if ($IncludeTags) {
        Write-Host ("  live probe tags: {0} line(s) across {1} file(s)" -f $tagLines, $tagFiles)
    }
    if ($Ceiling -gt 0) {
        Write-Host ("  ceiling {0}: {1}" -f $Ceiling, $(if ($exceeded) { 'EXCEEDED' } else { 'ok' }))
    }
}

if ($exceeded) {
    Write-Error ("unverified-backlog: {0} ticket(s) in BlockNeedUserTest exceeds the ceiling of {1}. Drain the backlog with /spec-sweep before starting more work." -f $blockedCount, $Ceiling) -ErrorAction Continue
    exit 3
}

exit 0
