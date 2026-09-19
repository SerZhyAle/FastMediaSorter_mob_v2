#requires -Version 7.0
<#
.SYNOPSIS
    S1627: lists the English UI text that does not yet reach all thirteen declared locales.

.DESCRIPTION
    The release rule is that a new interface string ships translated everywhere, not only in the
    three locales the owner authors. This command produces the list that rule is checked against,
    and it produces it in the form the next step consumes rather than as a report to read.

    How the set is computed:

      - The whole corpus is exported once through locale-bulk-export.ps1 -All, which is also the
        only place that decides what counts as translatable text at all. A value marked
        translatable="false", a glyph, a layout literal such as "1/1" or "3D", a value carrying
        escaped markup - none of those reach the export, so none of them can be reported here.
      - A unit is untranslated in a locale on any of four counts: that locale's resource file
        lacks the key; it carries the key but the fingerprint registry has no provenance for it; the
        provenance it does carry names a different English source text than the one in the tree
        today; or every one of those passes and the localized value is still the English text
        itself. The third count is what makes a REWORDED string visible (S1824) - the key is present
        in all ten locales and every value looks filled in, yet each one translates a sentence the
        English no longer says. Judging presence alone would ship that stale wording silently, which
        is what the whole registry exists to prevent; do not re-describe this check as a key check.
        Ten locales are checked rather than one reference locale, because the rule being enforced
        says thirteen: a key that landed in German and nowhere else is still untranslated.
      - The fourth count is S3304's, and it exists because the first three are all satisfied by a
        locale file holding the English source verbatim: the key is there and the stamp names the
        very text that was copied. Measured on physical_flashlight_title before S3294 fixed it, ar,
        fr, hi and zh-Hans all read "Camera flashlight" while six locales carried the identical hash
        4937533679a78582, and this command named none of them. An English copy is worse than an
        absent key rather than equal to it: absence lets Android fall back to the default locale,
        which renders the same glyphs, while the copy additionally reads as translated to every
        counting tool including this one.
      - Legitimate identicals are exempt through scripts/quality/locale-identical-allowlist.json,
        which names brand names, acronyms, units and pure format tokens - GIF, VPN, Google Drive,
        Mbps. An allow-listed key is not reported on ANY of the four counts, absence included,
        because "this text is the same in every language" answers the presence question too. The
        list is checked in rather than derived from a text-shape heuristic: the crude "identical to
        English and carrying a Latin word" probe scored 383 rows in de and 90 in ru, a locale the
        owner authors, so the metric cannot separate a brand from an untranslated leftover.
      - Everything named in the baseline is subtracted. That file holds the identities already
        untranslated when this command was written, so a pre-existing gap cannot be reported as a
        new one; see scripts/quality/locale-untranslated-baseline.txt for what shrinks it.

    The survivors are written as new_lexemes_en.txt - one English phrase per line, the format the
    external translation service takes - plus a renumbered sidecar in the shape
    locale-bulk-import.ps1 already reads, so the existing round trip works on this subset with no
    second format and no manual step.

    An absent baseline is a warning, not a failure: that is how the first baseline is generated,
    and how S1628 points this command at the wear module before one exists for it.

.PARAMETER Module
    Module path relative to repo root. Default app_v2.

.PARAMETER SourceSet
    Gradle source sets holding the resources. Default main,vr,noLegal - the flavors that carry
    strings of their own outside src/main/res.

.PARAMETER BaselinePath
    Identity list to subtract. Default scripts/quality/locale-untranslated-baseline.txt.

.PARAMETER AllowlistPath
    Keys allowed to equal their English source. Default scripts/quality/locale-identical-allowlist.json.
    Its absence is not an error - an empty allow-list exempts nothing, which is the strict reading.
    An entry is a bare key name, entitling every locale, or an object naming the entitled ones
    ({"key": "camera_mode_photo", "locales": ["fr", "it"]}); the entitlement is judged per locale, so
    a key French may legitimately leave in English is still reported for Arabic (S3309).

.PARAMETER IdenticalKeysPath
    Write the keys whose ONLY gap is an English-identical value to this file, one per line, sorted.
    This is how scripts/quality/locale-identical-allowlist.json was seeded and how it is re-derived:
    run with -AllowlistPath pointed at a file that does not exist, and the output is exactly the
    corpus the fourth count newly sees. A key that is also absent or stale somewhere is left out, so
    seeding from this file can never retire a gap the other three counts already report.

.PARAMETER OutDir
    Directory for the produced files. Default temp/S1627/<module>.

.PARAMETER Quiet
    Print the summary line only, without the per-key breakdown.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/list-new-lexemes.ps1

.EXAMPLE
    # Bootstrapping a baseline: report everything untranslated, subtracting nothing.
    pwsh -NoProfile -File scripts/utils/list-new-lexemes.ps1 -BaselinePath temp/scratch/none.txt

.OUTPUTS
    Exit codes:
      0 - every unit reaches all thirteen locales, or the only gaps are baselined.
      1 - unusable input: the export failed, or its sidecar could not be read.
      3 - new untranslated text exists; the produced files name it.
#>
[CmdletBinding()]
param(
    [string]$Module = 'app_v2',
    [string[]]$SourceSet = @('main', 'vr', 'noLegal'),
    [string]$BaselinePath,
    [string]$FingerprintsPath,
    [string]$AllowlistPath,
    [string]$IdenticalKeysPath,
    [string]$OutDir,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'locale-set.ps1')
. (Join-Path $repoRoot 'scripts/quality/lib/locale-fingerprints.ps1')

# pwsh -File hands an array parameter one literal string, so -SourceSet main,vr arrives as a single
# element with a comma in it.
$SourceSet = @($SourceSet | ForEach-Object { $_ -split ',' } | Where-Object { $_ } | ForEach-Object { $_.Trim() })

if (-not $OutDir) { $OutDir = Join-Path $repoRoot "temp/S1627/$Module" }
if (-not $BaselinePath) { $BaselinePath = Join-Path $repoRoot 'scripts/quality/locale-untranslated-baseline.txt' }
if (-not $FingerprintsPath) { $FingerprintsPath = Join-Path $repoRoot 'scripts/quality/locale-source-fingerprints.json' }
$corpusDir = Join-Path $OutDir 'corpus'

$exporter = Join-Path $PSScriptRoot 'locale-bulk-export.ps1'
& pwsh -NoProfile -File $exporter -Module $Module -SourceSet ($SourceSet -join ',') -All -OutDir $corpusDir | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "list-new-lexemes: the corpus export failed (exit $LASTEXITCODE) - nothing to compare." -ErrorAction Continue
    exit 1
}

$sidecarPath = Join-Path $corpusDir 'all_texts_index.jsonl'
if (-not (Test-Path -LiteralPath $sidecarPath)) {
    Write-Error "list-new-lexemes: the exporter wrote no sidecar at $sidecarPath." -ErrorAction Continue
    exit 1
}

$records = @(Get-Content -LiteralPath $sidecarPath -Encoding UTF8 | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json })
if ($records.Count -eq 0) {
    Write-Error "list-new-lexemes: the sidecar at $sidecarPath is empty." -ErrorAction Continue
    exit 1
}

$bestEffort = @(Get-SupportedLocales -Module $Module | Where-Object { -not (Test-StrictLocale -Tag $_) })
# The English-identical count runs wider than the other three, on every declared locale but the
# default one (S3304). Presence and freshness are a best-effort policy - a machine-translated locale
# is allowed to lag - while "this value IS the English source" is a defect in any locale, and the
# owner-authored ru scored 90 such rows to the crude probe. The default locale is excluded because
# its values are the English source; comparing it against itself would report the whole corpus.
$identicalLocales = @(Get-SupportedLocales -Module $Module | Where-Object { (Get-LocaleResourceDir -Tag $_) -ne 'values' })
$keyRx = [regex]'<(?:string|plurals|string-array)\s+name="([^"]+)"'
$presentCache = @{}

function Get-PresentKeys([string]$Set, [string]$File, [string]$Tag) {
    <# Keys a locale's own copy of one resource file already carries. Absent file = nothing carried. #>
    $cacheKey = "$Set|$File|$Tag"
    # Comma operator, both here and below: PowerShell unrolls an enumerable on return, so a bare
    # `return $keys` hands back $null for an empty set and the lone String for a one-key set - and
    # .Contains() on a String is a substring test, so the caller was silently wrong before it crashed.
    if ($presentCache.ContainsKey($cacheKey)) { return , $presentCache[$cacheKey] }

    $keys = [System.Collections.Generic.HashSet[string]]::new()
    $path = Join-Path $repoRoot "$Module/src/$Set/res/$(Get-LocaleResourceDir -Tag $Tag)/$File"
    if (Test-Path -LiteralPath $path) {
        foreach ($match in $keyRx.Matches((Get-Content -LiteralPath $path -Raw -Encoding UTF8))) {
            [void]$keys.Add($match.Groups[1].Value)
        }
    }
    $presentCache[$cacheKey] = $keys
    return , $keys
}

$valueCache = @{}

function Get-LocaleValues([string]$Set, [string]$File, [string]$Tag) {
    <# Slot key -> plain localized text for one locale's copy of one resource file. #>
    $cacheKey = "$Set|$File|$Tag"
    if ($valueCache.ContainsKey($cacheKey)) { return $valueCache[$cacheKey] }

    $path = Join-Path $repoRoot "$Module/src/$Set/res/$(Get-LocaleResourceDir -Tag $Tag)/$File"
    $values = Get-LocaleFileUnitValues -Path $path
    $valueCache[$cacheKey] = $values
    return $values
}

$fingerprints = Get-LocaleSourceFingerprints -Path $FingerprintsPath
$allowlist = Get-LocaleIdenticalAllowlist -Path $AllowlistPath
$identicalHits = 0

$missingByIdentity = [ordered]@{}
$structuralByIdentity = [ordered]@{}
$identicalByIdentity = [ordered]@{}
$identityKeys = @{}
foreach ($record in $records) {
    $identity = Get-LocaleUnitId -Module $Module -Set $record.set -File $record.file -Key $record.key
    $unitId = Get-LocaleUnitId -Module $Module -Set $record.set -File $record.file -Key $record.key -Slot ([string]$record.slot)
    $enHash = Get-EnglishStringFingerprint -Text ([string]$record.en)

    # An allow-listed key is exempt from every count, absence included - see the header. S3309: the
    # entitlement is asked per locale, inside the loops below, because an entry may name only some of
    # them; asking once per record here is what made a legitimate French identical hide an
    # untranslated Arabic one under the same key.
    $slotKey = Get-LocaleUnitSlotKey -Key ([string]$record.key) -Slot ([string]$record.slot)

    # Kept apart rather than merged on the spot, because the grandfather seed below has to tell a key
    # that is ONLY English-identical from one that is also absent or stale somewhere. Allow-listing
    # the second kind would retire a gap this command already reports.
    $structuralForThisRecord = [System.Collections.Generic.List[string]]::new()
    $identicalForThisRecord = [System.Collections.Generic.List[string]]::new()

    foreach ($tag in $bestEffort) {
        if (Test-LocaleIdenticalAllowlisted -Key ([string]$record.key) -Locale $tag -Allowlist $allowlist) { continue }
        $hasKey = (Get-PresentKeys $record.set $record.file $tag).Contains($record.key)
        if (-not $hasKey) {
            $structuralForThisRecord.Add($tag)
        } elseif (-not ($fingerprints.ContainsKey($tag) -and $fingerprints[$tag].ContainsKey($unitId))) {
            $structuralForThisRecord.Add($tag)
        } elseif ($fingerprints[$tag][$unitId] -ne $enHash) {
            $structuralForThisRecord.Add($tag)
        }
    }

    foreach ($tag in $identicalLocales) {
        if (Test-LocaleIdenticalAllowlisted -Key ([string]$record.key) -Locale $tag -Allowlist $allowlist) { continue }
        if ($structuralForThisRecord.Contains($tag)) { continue }
        # Ordinal comparison of two values that came through the same normalizer, so an escaping
        # difference alone never reads as a translation and a decoded entity never reads as a copy.
        $localized = Get-LocaleValues $record.set $record.file $tag
        if (-not $localized.ContainsKey($slotKey)) { continue }
        if ([string]::Equals([string]$localized[$slotKey], [string]$record.en, [System.StringComparison]::Ordinal)) {
            $identicalForThisRecord.Add($tag)
            $identicalHits++
        }
    }

    foreach ($bucket in @($missingByIdentity, $structuralByIdentity, $identicalByIdentity)) {
        if (-not $bucket.Contains($identity)) {
            $bucket[$identity] = [System.Collections.Generic.HashSet[string]]::new()
        }
    }
    foreach ($m in $structuralForThisRecord) {
        [void]$missingByIdentity[$identity].Add($m)
        [void]$structuralByIdentity[$identity].Add($m)
    }
    foreach ($m in $identicalForThisRecord) {
        [void]$missingByIdentity[$identity].Add($m)
        [void]$identicalByIdentity[$identity].Add($m)
    }
    $identityKeys[$identity] = [string]$record.key
}

$baselineIdentities = [System.Collections.Generic.HashSet[string]]::new()
$baselineFound = Test-Path -LiteralPath $BaselinePath
if ($baselineFound) {
    foreach ($line in (Get-Content -LiteralPath $BaselinePath -Encoding UTF8)) {
        $trimmed = $line.Trim()
        if ($trimmed -and -not $trimmed.StartsWith('#')) { [void]$baselineIdentities.Add($trimmed) }
    }
} else {
    Write-Host "list-new-lexemes: no baseline at $BaselinePath - subtracting nothing."
}

$survivors = [System.Collections.Generic.List[object]]::new()
$untranslated = [System.Collections.Generic.HashSet[string]]::new()
foreach ($record in $records) {
    $identity = Get-LocaleUnitId -Module $Module -Set $record.set -File $record.file -Key $record.key
    if ($missingByIdentity[$identity].Count -eq 0) { continue }
    [void]$untranslated.Add($identity)
    if ($baselineIdentities.Contains($identity)) { continue }
    $survivors.Add($record)
}

$stale = @($baselineIdentities | Where-Object { -not $untranslated.Contains($_) } | Sort-Object)

if (-not (Test-Path -LiteralPath $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
$textPath = Join-Path $OutDir 'new_lexemes_en.txt'
$indexPath = Join-Path $OutDir 'new_lexemes_index.jsonl'
$utf8 = [System.Text.UTF8Encoding]::new($false)

$lines = [System.Collections.Generic.List[string]]::new()
$index = [System.Collections.Generic.List[string]]::new()
foreach ($record in $survivors) {
    $lines.Add([string]$record.en)
    $renumbered = [ordered]@{
        line    = $lines.Count
        set     = [string]$record.set
        file    = [string]$record.file
        kind    = [string]$record.kind
        key     = [string]$record.key
        slot    = [string]$record.slot
        formats = [string]$record.formats
        en      = [string]$record.en
    }
    $index.Add(($renumbered | ConvertTo-Json -Compress -Depth 3))
}
$body = if ($lines.Count -gt 0) { ($lines -join "`n") + "`n" } else { '' }
$indexBody = if ($index.Count -gt 0) { ($index -join "`n") + "`n" } else { '' }
[System.IO.File]::WriteAllText($textPath, $body, $utf8)
[System.IO.File]::WriteAllText($indexPath, $indexBody, $utf8)

if ($IdenticalKeysPath) {
    $identicalOnly = @(
        $identicalByIdentity.Keys |
            Where-Object { $identicalByIdentity[$_].Count -gt 0 -and $structuralByIdentity[$_].Count -eq 0 } |
            ForEach-Object { $identityKeys[$_] } |
            Sort-Object -Unique
    )
    $identicalDir = Split-Path -Parent $IdenticalKeysPath
    if ($identicalDir -and -not (Test-Path -LiteralPath $identicalDir)) {
        New-Item -ItemType Directory -Path $identicalDir -Force | Out-Null
    }
    $identicalBody = if ($identicalOnly.Count -gt 0) { ($identicalOnly -join "`n") + "`n" } else { '' }
    [System.IO.File]::WriteAllText($IdenticalKeysPath, $identicalBody, $utf8)
    Write-Host "list-new-lexemes: wrote $IdenticalKeysPath - $($identicalOnly.Count) key(s) whose only gap is an English-identical value"
}

$distinctKeys = @($survivors | ForEach-Object { Get-LocaleUnitId -Module $Module -Set $_.set -File $_.file -Key $_.key } | Select-Object -Unique)
Write-Host "list-new-lexemes: new untranslated keys $($distinctKeys.Count) | lines $($lines.Count) | corpus $($records.Count) | baselined $($baselineIdentities.Count) | english-identical $identicalHits | allow-listed $($allowlist.Count) | locales checked $($bestEffort.Count)"

if (-not $Quiet) {
    foreach ($identity in $distinctKeys) {
        Write-Host "  $identity - missing in: $(($missingByIdentity[$identity]) -join ', ')"
    }
    foreach ($entry in $stale) { Write-Host "  stale baseline entry (now translated): $entry" }
}

if ($distinctKeys.Count -gt 0) {
    Write-Host "list-new-lexemes: wrote $textPath - hand this file to the translator, then import it with locale-bulk-import.ps1"
    Write-Host "list-new-lexemes: wrote $indexPath (keep - the service never sees this one)"
    exit 3
}

Write-Host 'list-new-lexemes: every unit reaches all thirteen locales, or is baselined.'
exit 0
