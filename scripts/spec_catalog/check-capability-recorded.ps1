#requires -Version 7.0
<#
.SYNOPSIS
    Remind, at closing time, that a spec claiming user-visible impact left no inventory record (S1665).

.DESCRIPTION
    `docs/ALL_FEATURES.jsonl` is the source of truth for shipped capabilities, and a closed ticket that
    never wrote its record makes its capability invisible to everything built on top of that file. S1654
    measured the damage: of 1549 closed specs, 275 declare user-visible impact in their own section 8 and
    have no record at all - so the guide-coverage gate reports full coverage over an inventory that is
    missing a fifth of what shipped.

    The signal is the spec's own section 8, "Влияние на пользователя". Its default text says the change is
    invisible; anything else is the author stating that something user-facing shipped. That is a claim by
    the person who did the work, not a guess made afterwards from a title or a tier - which is why a
    ticket named `bugfix-` is not excluded: fixing visible behaviour changes the inventory too, through a
    FIX rather than an ADD.

    ADVISORY BY CONSTRUCTION. This script never blocks a transition, and its caller in `_lib.ps1` keeps it
    in a list that cannot throw. A hard gate here would be wrong: bugfix specs written in the compact form
    have no section 8 at all, and tooling and documentation tickets legitimately ship no capability - so a
    refusal would make closing them impossible. A reminder that the author can act on is the whole product.

    Exit codes:
      0 - nothing to say, or the reminder was printed. Both are a successful run.
      2 - cannot verify: the spec file or the inventory is unreadable. Distinct from 0 so a caller can tell
          "looked and found nothing" from "could not look", even though neither blocks.

.PARAMETER Id
    Spec id (S####).

.EXAMPLE
    pwsh -NoProfile -File scripts/spec_catalog/check-capability-recorded.ps1 -Id S1654
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '_lib.ps1')

function Stop-CannotVerify([string]$why) {
    Write-Error "check-capability-recorded: cannot verify $Id - $why" -ErrorAction Continue
    exit 2
}

if ($Id -notmatch '^S\d{4}$') { Stop-CannotVerify "invalid id" }

$records = Read-Catalog
$record = $records | Where-Object { $_.id -eq $Id } | Select-Object -First 1
if (-not $record) { Stop-CannotVerify "no catalog record" }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$specPath = Join-Path $repoRoot ($record.file -replace '/', [IO.Path]::DirectorySeparatorChar)
if (-not (Test-Path -LiteralPath $specPath)) { Stop-CannotVerify "spec file not on disk" }

$text = Get-Content -LiteralPath $specPath -Raw

# Section 8 in the strategic form. Compact bugfix specs do not have one, and that absence is an answer:
# their contribution to the inventory is decided at closing, not declared in the template.
if ($text -notmatch '(?s)##\s*8\.[^\r\n]*[\r\n]+(.{0,600})') { exit 0 }
$section = $Matches[1]

# The template's own default. Anything else is the author saying something user-facing shipped.
if ($section -match '(?i)без\s+изменений' -or $section.Trim().Length -lt 20) { exit 0 }

$inventoryPath = Join-Path $repoRoot 'docs/ALL_FEATURES.jsonl'
if (-not (Test-Path -LiteralPath $inventoryPath)) { Stop-CannotVerify "docs/ALL_FEATURES.jsonl missing" }

$hasRecord = $false
foreach ($line in Get-Content -LiteralPath $inventoryPath) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    if ($line -notlike "*$Id*") { continue }
    if (($line | ConvertFrom-Json).spec -eq $Id) { $hasRecord = $true; break }
}
if ($hasRecord) { exit 0 }

Write-Host ""
Write-Host ("  reminder: {0} declares user-visible impact in section 8 and has no ALL_FEATURES record." -f $Id) -ForegroundColor Yellow
Write-Host "  If something user-facing shipped, record it - close-and-log.ps1 -FuncOp ADD|CHANGE|FIX" -ForegroundColor Yellow
Write-Host "  -FuncDesc .. -FeatArea .. -FeatName .. -FeatFlavors .., or scripts/all_features/add.ps1." -ForegroundColor Yellow
Write-Host "  Nothing shipped? Then section 8 is stale - say so there. This never blocks the close (S1665)." -ForegroundColor Yellow
Write-Host ""
exit 0
