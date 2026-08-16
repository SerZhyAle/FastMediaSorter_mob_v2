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
      - A unit is untranslated when at least one best-effort locale's resource file lacks its key.
        Ten locales are checked rather than one reference locale, because the rule being enforced
        says thirteen: a key that landed in German and nowhere else is still untranslated.
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

.PARAMETER OutDir
    Directory for the produced files. Default temp/S1627.

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
    [string]$OutDir,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'locale-set.ps1')

# pwsh -File hands an array parameter one literal string, so -SourceSet main,vr arrives as a single
# element with a comma in it.
$SourceSet = @($SourceSet | ForEach-Object { $_ -split ',' } | Where-Object { $_ } | ForEach-Object { $_.Trim() })

if (-not $OutDir) { $OutDir = Join-Path $repoRoot 'temp/S1627' }
if (-not $BaselinePath) { $BaselinePath = Join-Path $repoRoot 'scripts/quality/locale-untranslated-baseline.txt' }
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

$bestEffort = @(Get-SupportedLocales | Where-Object { -not (Test-StrictLocale -Tag $_) })
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

$missingByIdentity = [ordered]@{}
foreach ($record in $records) {
    $identity = "$($record.set)|$($record.file)|$($record.key)"
    if (-not $missingByIdentity.Contains($identity)) {
        $missing = @($bestEffort | Where-Object { -not (Get-PresentKeys $record.set $record.file $_).Contains($record.key) })
        $missingByIdentity[$identity] = $missing
    }
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
    $identity = "$($record.set)|$($record.file)|$($record.key)"
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

$distinctKeys = @($survivors | ForEach-Object { "$($_.set)|$($_.file)|$($_.key)" } | Select-Object -Unique)
Write-Host "list-new-lexemes: new untranslated keys $($distinctKeys.Count) | lines $($lines.Count) | corpus $($records.Count) | baselined $($baselineIdentities.Count) | locales checked $($bestEffort.Count)"

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
