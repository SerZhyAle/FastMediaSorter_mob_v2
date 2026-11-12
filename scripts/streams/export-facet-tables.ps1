#requires -Version 7.0
<#
.SYNOPSIS
    Export the stream facet tables and vectors into the shared contracts catalog (STREAM-BANK item P.1, P.2).

.DESCRIPTION
    The language vocabulary, the assigned-country list, the alias tables and the vectors of the `language`
    and `country` columns exist as text constants inside the two normalizers (the publisher module
    scripts/streams/modules/StreamPublisher.Facets.ps1 and the Kotlin StreamFacetTables). Item P.1 makes
    them catalog artifacts generated from the owner's normalizer, so a second producer ports and cites
    them by the contract's version instead of retyping them. This script writes, inside `stream-catalog/`
    of the contracts root:

      facet-language-vocabulary.tsv   canonical name, ISO 639-1 code, its ISO 639-2 codes (bibliographic and
                                      terminological), the ISO 639-3 extension code where it is one of the
                                      named extensions
      facet-language-aliases.tsv      folded alias, canonical name (a comma means several languages)
      facet-country-assigned.tsv      the officially assigned ISO 3166-1 alpha-2 codes
      facet-country-aliases.tsv       folded alias, assigned code
      facet-country-names.tsv         the platform's own region names, folded, with their code; the header
                                      names the reference runtime they were generated with, because they come
                                      from that platform's regional tables and differ between runtimes
      facet-golden.tsv                the vectors, a copy of app_v2/src/test/resources/streams/facet-golden.tsv

    Every file is generated and never edited by hand: amend the tables through the generators and rerun.
    Output is deterministic (LF, UTF-8 without BOM, fixed order), so a rerun on the same runtime is
    byte-identical. The sealed contract text (README, registry) is not touched.

.PARAMETER CatalogRoot
    The contracts root. Defaults to the FMS_CONTRACTS_ROOT environment variable.

.PARAMETER RepoRoot
    The repository root the facet module and the vector file are read from. Defaults to the root this
    script sits under.

.EXAMPLE
    pwsh -NoProfile -File scripts/streams/export-facet-tables.ps1

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every file was written.
      1  the tables are inconsistent (an alias points outside the vocabulary, a line is malformed); nothing
         is written.
      2  could not verify: no contracts root, no `stream-catalog/` folder in it, the facet module or the
         vector file is absent, or the platform region tables are too small to name countries (invariant
         globalization).
#>
[CmdletBinding()]
param(
    [string]$CatalogRoot = $env:FMS_CONTRACTS_ROOT,
    [string]$RepoRoot
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$modulePath = Join-Path $RepoRoot 'scripts/streams/modules/StreamPublisher.Facets.ps1'
$goldenPath = Join-Path $RepoRoot 'app_v2/src/test/resources/streams/facet-golden.tsv'
$minPlatformNames = 150

function Stop-Verdict {
    param([int]$Code, [string]$Word, [string[]]$Reasons)
    foreach ($reason in $Reasons) { Write-Host "  - $reason" }
    Write-Host "export-facet-tables: $Word"
    exit $Code
}

if (-not $CatalogRoot) { Stop-Verdict 2 'COULD NOT VERIFY - no contracts root (FMS_CONTRACTS_ROOT is not set).' @() }
$outDir = Join-Path $CatalogRoot 'stream-catalog'
if (-not (Test-Path -LiteralPath $outDir -PathType Container)) {
    Stop-Verdict 2 "COULD NOT VERIFY - no stream-catalog folder under $CatalogRoot." @()
}
foreach ($path in @($modulePath, $goldenPath)) {
    if (-not (Test-Path -LiteralPath $path)) { Stop-Verdict 2 "COULD NOT VERIFY - not found: $path" @() }
}

. $modulePath

function Sort-Ordinal {
    param([string[]]$Values)
    $copy = [string[]]@($Values)
    [array]::Sort($copy, [System.StringComparer]::Ordinal)
    return $copy
}

function Get-TableLines {
    param([string]$Table)
    return @($Table -split "\r?\n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

$languages = Get-FacetLanguageTables
$countries = Get-FacetCountryTables
if ($countries.PlatformNames.Count -lt $minPlatformNames) {
    Stop-Verdict 2 'COULD NOT VERIFY - the platform region tables are too small.' @(
        "$($countries.PlatformNames.Count) region name(s) read, fewer than $minPlatformNames; run on a runtime with full globalization data.")
}

$problems = [System.Collections.Generic.List[string]]::new()
$twoLetterRows = foreach ($line in (Get-TableLines $script:FacetIso6391)) {
    $parts = $line -split ' ', 2
    if ($parts.Count -ne 2 -or $parts[0] -cnotmatch '^[a-z]{2}$') { $problems.Add("malformed ISO 639-1 line '$line'"); continue }
    [pscustomobject]@{ Name = $parts[1]; Code2 = $parts[0] }
}
$codes3ByCode2 = @{}
foreach ($line in (Get-TableLines $script:FacetIso6392)) {
    $parts = $line -split ' ', 2
    if ($parts.Count -ne 2 -or $parts[0] -cnotmatch '^[a-z]{3}$' -or -not $languages.Codes.ContainsKey($parts[1])) {
        $problems.Add("malformed ISO 639-2 line '$line'")
        continue
    }
    if (-not $codes3ByCode2.ContainsKey($parts[1])) { $codes3ByCode2[$parts[1]] = [System.Collections.Generic.List[string]]::new() }
    $codes3ByCode2[$parts[1]].Add($parts[0])
}
$extensionRows = foreach ($line in (Get-TableLines $script:FacetIso6393Extension)) {
    $parts = $line -split ' ', 2
    if ($parts.Count -ne 2 -or $parts[0] -cnotmatch '^[a-z]{3}$') { $problems.Add("malformed ISO 639-3 line '$line'"); continue }
    [pscustomobject]@{ Name = $parts[1]; Code3 = $parts[0] }
}
$languageAliases = foreach ($line in (Get-TableLines $script:FacetLanguageAliases)) {
    $parts = $line -split '\|', 2
    if ($parts.Count -ne 2) { $problems.Add("malformed language alias '$line'"); continue }
    foreach ($target in ($parts[1] -split ',')) {
        if (-not $languages.Canonical.Contains($target)) { $problems.Add("language alias '$($parts[0])' points at '$target', outside the vocabulary") }
    }
    [pscustomobject]@{ Alias = $parts[0]; Target = $parts[1] }
}
$countryAliases = foreach ($line in (Get-TableLines $script:FacetCountryAliases)) {
    $parts = $line -split '\|', 2
    if ($parts.Count -ne 2 -or -not $countries.Assigned.Contains($parts[1])) { $problems.Add("country alias '$line' does not name an assigned code"); continue }
    [pscustomobject]@{ Alias = $parts[0]; Code = $parts[1] }
}
if ($problems.Count -gt 0) { Stop-Verdict 1 "FAIL - the tables are inconsistent ($($problems.Count) problem(s)); nothing written." $problems }

$invariant = $false
[void][AppContext]::TryGetSwitch('System.Globalization.Invariant', [ref]$invariant)
$runtime = 'PowerShell {0}, {1}, {2}, {3}, invariant globalization {4}' -f $PSVersionTable.PSVersion,
    [System.Runtime.InteropServices.RuntimeInformation]::FrameworkDescription,
    [System.Runtime.InteropServices.RuntimeInformation]::OSDescription,
    [System.Runtime.InteropServices.RuntimeInformation]::ProcessArchitecture, $invariant.ToString().ToLowerInvariant()

$banner = @(
    '# Generated by scripts/streams/export-facet-tables.ps1 of FastMediaSorter Android from its normalizer',
    '# (STREAM-BANK item P, rule 14 tables). Never edit by hand: amend the normalizer and re-export.'
)
$utf8 = [System.Text.UTF8Encoding]::new($false)
function Write-Artifact {
    param([string]$Name, [string[]]$Header, [string[]]$Rows)
    $text = (($banner + $Header + $Rows) -join "`n") + "`n"
    [System.IO.File]::WriteAllText((Join-Path $outDir $Name), $text, $utf8)
    Write-Host ("  wrote {0} ({1} row(s))" -f $Name, $Rows.Count)
}

$vocabularyRows = @(
    foreach ($row in $twoLetterRows) {
        $codes3 = if ($codes3ByCode2.ContainsKey($row.Code2)) { (Sort-Ordinal $codes3ByCode2[$row.Code2]) -join ',' } else { '' }
        "{0}`t{1}`t{2}`t" -f $row.Name, $row.Code2, $codes3
    }
    foreach ($row in $extensionRows) { "{0}`t`t`t{1}" -f $row.Name, $row.Code3 }
)
$vocabularyRows = Sort-Ordinal $vocabularyRows

Write-Host "export-facet-tables: writing to $outDir"
Write-Artifact 'facet-language-vocabulary.tsv' @(
    '# Closed language vocabulary of the stream catalog `language` column (item O); one row per canonical name.',
    '# Columns: canonical name, ISO 639-1 code, ISO 639-2 codes (bibliographic and terminological), ISO 639-3 extension code.',
    "name`tiso639_1`tiso639_2`tiso639_3_extension") $vocabularyRows
Write-Artifact 'facet-language-aliases.tsv' @(
    '# Spellings that fold to a language (applied after case, diacritic and dash folding); a comma in the target means several languages.',
    "alias`tcanonical") @($languageAliases | ForEach-Object { "{0}`t{1}" -f $_.Alias, $_.Target })
Write-Artifact 'facet-country-assigned.tsv' @(
    '# Officially assigned ISO 3166-1 alpha-2 codes; a code outside this list is blank in the `country` column.',
    'code') (Sort-Ordinal @($countries.Assigned))
Write-Artifact 'facet-country-aliases.tsv' @(
    '# Country names and aliases the platform region tables do not carry, or carry under another spelling.',
    "alias`tcode") @($countryAliases | ForEach-Object { "{0}`t{1}" -f $_.Alias, $_.Code })
Write-Artifact 'facet-country-names.tsv' @(
    '# Platform region names (English, native and display names), folded, with the assigned code they name.',
    "# Reference runtime: $runtime.",
    '# The names come from that platform''s regional tables and differ between runtimes; the alias table is separate.',
    "name`tcode") (Sort-Ordinal @($countries.PlatformNames.GetEnumerator() |
        ForEach-Object { "{0}`t{1}" -f $_.Key, $_.Value }))

$golden = [System.IO.File]::ReadAllText($goldenPath, $utf8).Replace("`r`n", "`n")
[System.IO.File]::WriteAllText((Join-Path $outDir 'facet-golden.tsv'), $golden, $utf8)
Write-Host ("  wrote facet-golden.tsv ({0} line(s))" -f ($golden -split "`n").Count)
Write-Host 'export-facet-tables: PASS - 6 file(s) written.'
exit 0
