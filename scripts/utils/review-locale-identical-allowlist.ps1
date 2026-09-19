#requires -Version 7.0
<#
.SYNOPSIS
    S3305: dumps the locale-identical allow-list with the evidence needed to judge each entry, and
    rewrites it from a reviewed keep-list.

.DESCRIPTION
    scripts/quality/locale-identical-allowlist.json names the keys entitled to equal their English
    source. S3304 created it and seeded it from the whole English-identical corpus in one pass, so
    it does not only hold brand names, acronyms, units and format tokens - it also holds every
    untranslated English sentence that happened to be identical on the day it was written. An
    allow-listed key is exempt from all four untranslated counts, so that seeding silenced the very
    backlog it was meant to expose: list-new-lexemes.ps1 reports english-identical 0 while ten
    locales render English labels mid-screen.

    This command does not guess which is which - no text-shape heuristic can, which is why the list
    is checked in at all. It lays the decision out instead: for every allow-listed unit it prints
    the English text, the locales that carry it verbatim, the locales that translated it, and the
    locales missing it. A key translated by nine locales and copied by one is an untranslated
    leftover; a key copied by all ten is a brand name or a format token. That split is visible in
    the dump and invisible in the key name, which is all the allow-list stores.

    -Keep turns a reviewed decision back into the file. It rewrites the allow-list to exactly the
    keys named in the keep-list, through the same Save-LocaleIdenticalAllowlist the seeder uses, so
    the result stays sorted and diffable. Keys dropped this way become visible to
    list-new-lexemes.ps1 again and flow into the normal bulk translation round trip.

    S3309: a keep-list line may carry a locale scope - "key" entitles every locale as before,
    "key: fr it" or "key: fr,it" entitles only those. The dump writes allowlist-keep-scoped.txt in
    that grammar, with every key already narrowed to the locales carrying the English verbatim today,
    so the split majority can be applied rather than retyped.

.PARAMETER Module
    Module path relative to repo root. Default app_v2.

.PARAMETER SourceSet
    Gradle source sets holding the resources. Default main,vr,noLegal.

.PARAMETER AllowlistPath
    The list under review. Default scripts/quality/locale-identical-allowlist.json.

.PARAMETER CorpusIndexPath
    An all_texts_index.jsonl produced earlier by locale-bulk-export.ps1 -All. Given one, the export
    is skipped; the corpus is the only input this command needs and re-exporting it costs minutes.

.PARAMETER OutDir
    Directory for the produced files. Default temp/S3305/review.

.PARAMETER Keep
    Apply mode: a file of key names, one per line, that must survive in the allow-list. Everything
    else is removed. Blank lines and lines starting with # are ignored.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/review-locale-identical-allowlist.ps1 -CorpusIndexPath temp/S3305/survey/corpus/all_texts_index.jsonl

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/review-locale-identical-allowlist.ps1 -Keep temp/S3305/keep.txt

.OUTPUTS
    Exit codes:
      0 - the dump was written, or -Keep rewrote the allow-list.
      1 - unusable input: the allow-list, the corpus or the keep-list could not be read.
#>
[CmdletBinding()]
param(
    [string]$Module = 'app_v2',
    [string[]]$SourceSet = @('main', 'vr', 'noLegal'),
    [string]$AllowlistPath,
    [string]$CorpusIndexPath,
    [string]$OutDir,
    [string]$Keep
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'locale-set.ps1')
. (Join-Path $repoRoot 'scripts/quality/lib/locale-fingerprints.ps1')

# pwsh -File hands an array parameter one literal string, so -SourceSet main,vr arrives as a single
# element with a comma in it.
$SourceSet = @($SourceSet | ForEach-Object { $_ -split ',' } | Where-Object { $_ } | ForEach-Object { $_.Trim() })

if (-not $OutDir) { $OutDir = Join-Path $repoRoot 'temp/S3305/review' }
$resolvedAllowlist = Get-LocaleIdenticalAllowlistPath -Path $AllowlistPath
$allowlist = Get-LocaleIdenticalAllowlist -Path $AllowlistPath

if ($Keep) {
    if (-not (Test-Path -LiteralPath $Keep)) {
        Write-Error "review-locale-identical-allowlist: keep-list not found at $Keep - nothing to apply."
        exit 1
    }
    # S3309: a keep-list line is "key", "key: fr it" or "key: fr,it" - the key alone keeps the entry
    # entitled in every locale, a scope narrows it to the ones named. That scope is the whole point of
    # the reviewed decision: a key nine locales translated and one copies is an untranslated leftover
    # IN THAT ONE LOCALE, and before this shape the only two moves were keeping it everywhere or
    # dropping it everywhere.
    $kept = [ordered]@{}
    foreach ($line in (Get-Content -LiteralPath $Keep -Encoding UTF8)) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $name = $trimmed
        $scopeText = ''
        $split = $trimmed.IndexOfAny([char[]]@(':', "`t"))
        if ($split -ge 0) {
            $name = $trimmed.Substring(0, $split).Trim()
            $scopeText = $trimmed.Substring($split + 1).Trim()
        }
        if (-not $name) { continue }
        $tags = @($scopeText -split '[\s,]+' | Where-Object { $_ })
        $kept[$name] = New-LocaleIdenticalAllowlistScope -Locales $tags
    }
    $survivors = [ordered]@{}
    foreach ($name in @($kept.Keys | Sort-Object -Unique)) {
        if (-not (Test-LocaleIdenticalAllowlistHasKey -Key $name -Allowlist $allowlist)) { continue }
        $survivors[$name] = $kept[$name]
    }
    # Named in the keep-list but absent from the allow-list: reported rather than added, because
    # this command only ever narrows the list - widening it is the seeder's job and needs evidence
    # this command does not have.
    $unknown = @($kept.Keys | Where-Object { -not (Test-LocaleIdenticalAllowlistHasKey -Key $_ -Allowlist $allowlist) } | Sort-Object)
    Save-LocaleIdenticalAllowlist -Allowlist $survivors -Path $AllowlistPath
    $removed = $allowlist.Count - $survivors.Count
    $scoped = @($survivors.Keys | Where-Object { $null -ne $survivors[$_] }).Count
    Write-Host "review-locale-identical-allowlist: $resolvedAllowlist now holds $($survivors.Count) key(s), $scoped of them scoped to named locales; removed $removed."
    if ($unknown.Count -gt 0) {
        Write-Host "  keep-list named $($unknown.Count) key(s) the allow-list never held - ignored."
    }
    exit 0
}

if (-not $CorpusIndexPath) {
    $corpusDir = Join-Path $OutDir 'corpus'
    $exporter = Join-Path $PSScriptRoot 'locale-bulk-export.ps1'
    & pwsh -NoProfile -File $exporter -Module $Module -SourceSet ($SourceSet -join ',') -All -OutDir $corpusDir | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "review-locale-identical-allowlist: the corpus export failed (exit $LASTEXITCODE) - nothing to compare."
        exit 1
    }
    $CorpusIndexPath = Join-Path $corpusDir 'all_texts_index.jsonl'
}

if (-not (Test-Path -LiteralPath $CorpusIndexPath)) {
    Write-Error "review-locale-identical-allowlist: corpus sidecar not found at $CorpusIndexPath."
    exit 1
}

$locales = @(Get-SupportedLocales -Module $Module | Where-Object { $_ -ne 'en' })

# Locale files are read once per (set, file, locale) and cached: the allow-list addresses hundreds
# of keys spread over a few dozen files, so a per-key read would parse the same XML hundreds of
# times.
$fileCache = @{}
function Get-CachedValues {
    param([string]$Set, [string]$File, [string]$Tag)

    $cacheKey = "$Set|$File|$Tag"
    if (-not $fileCache.ContainsKey($cacheKey)) {
        $dir = Get-LocaleResourceDir -Tag $Tag
        $path = Join-Path $repoRoot "$Module/src/$Set/res/$dir/$File"
        $fileCache[$cacheKey] = Get-LocaleFileUnitValues -Path $path
    }
    return $fileCache[$cacheKey]
}

$rows = [System.Collections.Generic.List[object]]::new()
$seen = [System.Collections.Generic.HashSet[string]]::new()

foreach ($line in (Get-Content -LiteralPath $CorpusIndexPath -Encoding UTF8)) {
    if (-not $line.Trim()) { continue }
    $unit = $line | ConvertFrom-Json
    # Key-level on purpose: the dump's job is to lay out every entry with its evidence, and an entry
    # already narrowed to two locales still has to be reviewable. Whether a VALUE is entitled is the
    # locale-aware predicate's question, asked by the gates, not here.
    if (-not (Test-LocaleIdenticalAllowlistHasKey -Key $unit.key -Allowlist $allowlist)) { continue }

    $slotKey = Get-LocaleUnitSlotKey -Key $unit.key -Slot $unit.slot
    $identical = [System.Collections.Generic.List[string]]::new()
    $translated = [System.Collections.Generic.List[string]]::new()
    $missing = [System.Collections.Generic.List[string]]::new()

    foreach ($tag in $locales) {
        $values = Get-CachedValues -Set $unit.set -File $unit.file -Tag $tag
        if (-not $values.ContainsKey($slotKey)) { $missing.Add($tag); continue }
        if ($values[$slotKey] -eq $unit.en) { $identical.Add($tag) } else { $translated.Add($tag) }
    }

    [void]$seen.Add($unit.key)
    $verdict = 'split'
    if ($translated.Count -eq 0) { $verdict = 'all-copy' }
    elseif ($identical.Count -eq 0) { $verdict = 'all-translated' }

    $currentScope = $allowlist[[string]$unit.key]
    $rows.Add([pscustomobject]@{
        key        = $unit.key
        slot       = $unit.slot
        set        = $unit.set
        file       = $unit.file
        en         = $unit.en
        scope      = if ($null -eq $currentScope) { 'all' } else { (@($currentScope | Sort-Object) -join ' ') }
        identical  = ($identical -join ' ')
        translated = ($translated -join ' ')
        missing    = ($missing -join ' ')
        verdict    = $verdict
    })
}

New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$tsvPath = Join-Path $OutDir 'allowlist-review.tsv'
$rows | Sort-Object verdict, key | Export-Csv -LiteralPath $tsvPath -Delimiter "`t" -NoTypeInformation -Encoding UTF8

$orphans = @($allowlist.Keys | Where-Object { -not $seen.Contains($_) } | Sort-Object)
$orphanPath = Join-Path $OutDir 'allowlist-orphans.txt'
$orphans | Set-Content -LiteralPath $orphanPath -Encoding UTF8

# S3309: the dump already knows, per key, which locales carry the English verbatim today - that set
# IS the entitlement the entry should hold. Written out in the keep-list grammar so the reviewed
# decision can be applied with -Keep instead of retyped: a key no locale copies any more is left out
# and so drops, a key every locale copies stays unscoped, and the split majority is narrowed to the
# locales that actually need it. An orphan keeps whatever scope it has, because the corpus says
# nothing about a key it does not contain.
$scopeSuggestions = [ordered]@{}
foreach ($row in ($rows | Sort-Object key)) {
    $tags = @($row.identical -split ' ' | Where-Object { $_ })
    if (-not $scopeSuggestions.Contains($row.key)) {
        $scopeSuggestions[$row.key] = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    }
    foreach ($tag in $tags) { [void]$scopeSuggestions[$row.key].Add($tag) }
}
$keepLines = [System.Collections.Generic.List[string]]::new()
$keepLines.Add('# S3309 suggestion: each key scoped to the locales that carry the English verbatim today.')
$keepLines.Add('# Apply with -Keep. A key absent from this file is dropped from the allow-list.')
foreach ($name in @($orphans)) { $keepLines.Add($name) }
foreach ($name in $scopeSuggestions.Keys) {
    $tags = @($scopeSuggestions[$name] | Sort-Object)
    if ($tags.Count -eq 0) { continue }
    if ($tags.Count -eq $locales.Count) { $keepLines.Add($name); continue }
    $keepLines.Add("$name`: $($tags -join ' ')")
}
$keepScopedPath = Join-Path $OutDir 'allowlist-keep-scoped.txt'
[System.IO.File]::WriteAllText($keepScopedPath, ($keepLines -join "`n") + "`n", [System.Text.UTF8Encoding]::new($false))

$byVerdict = $rows | Group-Object verdict | Sort-Object Name
Write-Host "review-locale-identical-allowlist: $($allowlist.Count) allow-listed key(s), $($rows.Count) unit(s) matched in the corpus, $($orphans.Count) not in it."
foreach ($group in $byVerdict) {
    Write-Host "  $($group.Name): $($group.Count)"
}
Write-Host "  wrote $tsvPath"
Write-Host "  wrote $orphanPath"
Write-Host "  wrote $keepScopedPath - apply it with -Keep to narrow every split entry to its own locales"
exit 0
