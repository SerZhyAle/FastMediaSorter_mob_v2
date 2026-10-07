#requires -Version 7.0
<#
.SYNOPSIS
    Quality gate: the hand-maintained portal hubs carry a current shared shell (S4114).

.DESCRIPTION
    SITE-STRUCTURE rules 5 and 7 want the same chrome in the same place on every portal page. The
    generated pages get their shell from their own generators; the hubs (documentation/index*,
    overview* and design-system/index.html) are hand-kept and get it only from
    scripts/docs/update-docs-shell.ps1. Its -Check ran in no gate, so the hubs drifted unseen.

    Thin wrapper: the comparison lives in update-docs-shell.ps1 -Check; this file gives it a
    placement record (scripts/quality/gate-placement.jsonl) and a closure label.

.PARAMETER Gate
    Accepted for uniform runner invocation; the gate is always fail-closed.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-docs-hub-shell.ps1

.NOTES
    Exit codes:
      0 - every hub shell is current.
      1 - at least one hub is stale; run scripts/docs/update-docs-shell.ps1.
      2 - cannot verify: the shell script is missing or failed to run.
#>
[CmdletBinding()]
param([switch]$Gate)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
Write-CheckSubject -Axes ([ordered]@{ module = 'site'; scope = 'docs-hub-shell'; files = 'documentation/index*.html,documentation/overview*.html,documentation/design-system/index.html' })

$shell = Join-Path $PSScriptRoot '../docs/update-docs-shell.ps1'
if (-not (Test-Path -LiteralPath $shell)) {
    Write-Host "assert-docs-hub-shell: COULD NOT VERIFY - $shell is missing." -ForegroundColor Red
    exit 2
}

& pwsh -NoProfile -File $shell -Check
$code = $LASTEXITCODE
if ($code -eq 0) {
    Write-Host 'assert-docs-hub-shell: PASS' -ForegroundColor Green
    exit 0
}
if ($code -eq 1) {
    Write-Host 'assert-docs-hub-shell: FAIL - run pwsh -NoProfile -File scripts/docs/update-docs-shell.ps1 and keep the result.' -ForegroundColor Red
    exit 1
}
Write-Host "assert-docs-hub-shell: COULD NOT VERIFY - update-docs-shell.ps1 -Check exited $code." -ForegroundColor Red
exit 2
