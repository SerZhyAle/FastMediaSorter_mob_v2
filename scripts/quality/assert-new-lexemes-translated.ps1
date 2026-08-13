#requires -Version 7.0
<#
.SYNOPSIS
    Gate: no new UI string reaches a release untranslated (S1627).

.DESCRIPTION
    The app declares thirteen interface locales. Three are authored by the owner and must stay
    complete; the other ten are machine-translated in bulk and are allowed to lag - but only until
    the release. This gate is where "allowed to lag" ends: it refuses while any string added since
    the rule was introduced is missing from a declared locale.

    It owns no logic of its own. scripts/utils/list-new-lexemes.ps1 computes the set and writes the
    file the translator takes; this gate turns that command's exit 3 into a release blocker and
    prints what to do about it. Keeping the producer separate is what lets the same set be reported
    without blocking at ticket-closure time (post-change.ps1) and blocked here, from one definition.

    Placed at the pre-release stage rather than at the ticket that adds the string, by the owner's
    decision of 2026-08-14 (strategic ADR-2): nothing ships between releases, so translating each
    key the day it is written buys the user nothing and costs ten translations per ticket, while one
    batch per release costs one round trip.

    The two repair routes it names are both cheap and neither needs a human translator:
      - the bulk round trip, for any number of keys at once;
      - set-android-string.ps1 -Translations, when the values are already known.

.PARAMETER Module
    Module path relative to repo root. Default app_v2.

.PARAMETER SourceSet
    Gradle source sets to check. Default main,vr,noLegal.

.PARAMETER BaselinePath
    Identity list of gaps that predate the rule. Default scripts/quality/locale-untranslated-baseline.txt.
    Its absence is exit 2, not exit 1 - a missing baseline means the gate cannot tell old from new,
    which is "could not verify", never "found a defect".

.PARAMETER OutDir
    Where the producer writes the translator-ready files. Default temp/S1627.

.PARAMETER Quiet
    Print the expected/actual summary line only.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-new-lexemes-translated.ps1

.OUTPUTS
    Exit codes:
      0 - every string reaches all thirteen declared locales, or its gap predates the rule.
      1 - new untranslated strings exist; the release is blocked until they are translated.
      2 - the gate cannot verify: the producer or the baseline is missing.
#>
[CmdletBinding()]
param(
    [string]$Module = 'app_v2',
    [string[]]$SourceSet = @('main', 'vr', 'noLegal'),
    [string]$BaselinePath,
    [string]$OutDir,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$SourceSet = @($SourceSet | ForEach-Object { $_ -split ',' } | Where-Object { $_ } | ForEach-Object { $_.Trim() })

if (-not $OutDir) { $OutDir = Join-Path $repoRoot 'temp/S1627' }
if (-not $BaselinePath) { $BaselinePath = Join-Path $repoRoot 'scripts/quality/locale-untranslated-baseline.txt' }

$producer = Join-Path $repoRoot 'scripts/utils/list-new-lexemes.ps1'
if (-not (Test-Path -LiteralPath $producer)) {
    $msg = "assert-new-lexemes-translated: producer not found at $producer - cannot verify."
    Write-Error $msg -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath $BaselinePath)) {
    $msg = "assert-new-lexemes-translated: baseline not found at $BaselinePath - cannot tell a new gap from one that predates the rule. Regenerate it per its header comment."
    Write-Error $msg -ErrorAction Continue
    exit 2
}

& pwsh -NoProfile -File $producer -Module $Module -SourceSet ($SourceSet -join ',') -BaselinePath $BaselinePath -OutDir $OutDir -Quiet | Out-Null
$producerExit = $LASTEXITCODE

if ($producerExit -eq 1) {
    $msg = 'assert-new-lexemes-translated: list-new-lexemes.ps1 could not read the corpus (exit 1) - cannot verify.'
    Write-Error $msg -ErrorAction Continue
    exit 2
}

if ($producerExit -eq 0) {
    Write-Host 'assert-new-lexemes-translated: expected: 0 | actual: 0 untranslated new string(s).'
    exit 0
}

$indexPath = Join-Path $OutDir 'new_lexemes_index.jsonl'
$textPath = Join-Path $OutDir 'new_lexemes_en.txt'
if (-not (Test-Path -LiteralPath $indexPath)) {
    $msg = "assert-new-lexemes-translated: the producer reported new strings but wrote no sidecar at $indexPath - cannot verify."
    Write-Error $msg -ErrorAction Continue
    exit 2
}

$records = @(Get-Content -LiteralPath $indexPath -Encoding UTF8 | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json })
$keys = @($records | ForEach-Object { "$($_.set)|$($_.file)|$($_.key)" } | Select-Object -Unique)

if (-not $Quiet) {
    Write-Host ''
    Write-Host "assert-new-lexemes-translated: $($keys.Count) new string(s) do not reach every declared locale."
    foreach ($key in ($keys | Select-Object -First 20)) { Write-Host "  $key" }
    if ($keys.Count -gt 20) { Write-Host "  .. and $($keys.Count - 20) more - see $indexPath" }
    Write-Host ''
    Write-Host 'Two ways to clear this, neither needing a human translator:'
    Write-Host "  1. Bulk round trip - hand $textPath to the external translation service, then:"
    Write-Host '     pwsh -NoProfile -File scripts/utils/locale-bulk-import.ps1 -TextPath <returned file>'
    Write-Host '  2. Values already known - pass them per key:'
    Write-Host "     pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action set -Key <key> -Locale <tag> -Value '<text>'"
    Write-Host ''
}

$summary = "assert-new-lexemes-translated: expected: 0 | actual: $($keys.Count) untranslated new string(s) - the release is blocked until they are translated."
Write-Host $summary
exit 1
