<#
.SYNOPSIS
    S1420: exports the English UI corpus as one plain line per phrase, for an external translator.

.DESCRIPTION
    Writes two files. all_texts_en.txt carries nothing but the English text, one phrase per line -
    that is the file handed to the external translation service, which returns all_texts_<tag>.txt
    with the same number of lines. all_texts_index.jsonl is the sidecar that says which resource key
    line N belongs to; it stays here and is never handed over.

    Line position is the only link between a translation and its key, so every rule below exists to
    keep that link intact:

      - A unit is one line: a <string>, one <item> of a <plurals>, one <item> of a <string-array>.
        A container therefore spans as many lines as it has items, in source order.
      - No line is ever empty and no line ever carries a real line break. A value that would produce
        one is skipped rather than emitted, because a service that drops or splits a line shifts
        every later line by one and the shift is invisible until the wrong caption ships.
      - Format tokens are recorded per line in the sidecar, so the import side can refuse a
        translation that lost or retyped a %1$s instead of crashing at format time in front of a user.

    Skipped by default, each recorded in the sidecar with a reason:

      - translatable="false" - not part of the corpus at all.
      - A value with no run of -MinLetterRun letters: glyphs and layout literals such as the play
        symbol, L1, 3D, 1/1, 180 degrees. Translating those buys nothing and breaks the ones that are
        really layout attribute values (S1550), which have to stay byte-identical to English.
      - A value carrying escaped angle brackets (&lt; / &gt;). The seeder turns a bare <b> back into
        real markup, so a value meaning the literal text "<b>" cannot round-trip through this format.

    Scope is the missing set by default, not the whole corpus: -ReferenceLocale names the locale whose
    gaps define the export. The ten best-effort locales share one identical missing set, so one export
    serves all ten. Re-exporting the whole corpus would send already-shipped tranches back through the
    service and overwrite reviewed text with raw machine output - use -All only when that is the intent.

.PARAMETER Module
    Module path relative to repo root. Default app_v2.

.PARAMETER SourceSet
    Gradle source sets holding the resources, in order. Default main. Name several - main,vr,noLegal -
    to carry a flavor's own strings, which live outside src/main/res, in the same export. One file for
    the service is the point: each trip through it is the expensive part, and the sidecar records which
    set a line came from, so the import side puts it back where it belongs. A set is skipped with a
    note if it has no res/values or no match for -SourceFile, rather than failing the whole export.

.PARAMETER ReferenceLocale
    Locale whose missing keys define the export. Default de. Ignored under -All.

.PARAMETER All
    Export every eligible unit rather than only the ones missing from -ReferenceLocale.

.PARAMETER SourceFile
    Optional basename filter, e.g. strings_reader.xml. Omit to cover every values/strings*.xml.

.PARAMETER OutDir
    Directory for the two output files. Default temp/S1420/box.

.PARAMETER MinLetterRun
    Shortest run of letters that makes a value worth translating. Default 3. Use 0 to export symbols
    and layout literals too.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/locale-bulk-export.ps1 -SourceSet main,vr,noLegal

.EXAMPLE
    # The whole corpus of one file, symbols included.
    pwsh -NoProfile -File scripts/utils/locale-bulk-export.ps1 -SourceFile strings_reader.xml -All -MinLetterRun 0

.OUTPUTS
    Exit codes:
      0 - both files written.
      1 - unusable input: no named source set has res/values or a match for -SourceFile, or the
          reference locale is not declared.
      3 - written, but nothing was eligible: the export is empty and there is nothing to translate.
#>
[CmdletBinding()]
param(
    [string]$Module = 'app_v2',
    [string[]]$SourceSet = @('main'),
    [string]$ReferenceLocale = 'de',
    [switch]$All,
    [string]$SourceFile,
    [string]$OutDir,
    [int]$MinLetterRun = 3
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'locale-set.ps1')
# ConvertFrom-ResourceBody comes from here rather than from a local copy: the `en` it produces is
# what every recorded fingerprint is compared against, and seed-locale-tranche.ps1 now writes those
# fingerprints too (S2327). Two copies of this normalizer would mark the corpus stale on any drift.
. (Join-Path $repoRoot 'scripts/quality/lib/locale-fingerprints.ps1')

if (-not $OutDir) { $OutDir = Join-Path $repoRoot 'temp/S1420/box' }

# pwsh -File hands an array parameter one literal string, so -SourceSet main,vr,noLegal arrives as a
# single element with commas in it. Splitting here is what makes the documented invocation work.
$SourceSet = @($SourceSet | ForEach-Object { $_ -split ',' } | Where-Object { $_ } | ForEach-Object { $_.Trim() })

$elementRx = [regex]'(?s)<(string|plurals|string-array)\s+name="([^"]+)"([^>]*)>(.*?)</\1>'
$pluralItemRx = [regex]'(?s)<item\s+quantity="([^"]+)"[^>]*>(.*?)</item>'
$arrayItemRx = [regex]'(?s)<item[^>]*>(.*?)</item>'

function Get-FormatSignature([AllowEmptyString()][string]$Text) {
    return (([regex]::Matches($Text, '%(\d+\$)?[a-zA-Z]') | ForEach-Object { $_.Value } | Sort-Object) -join '|')
}

$letterRunRx = if ($MinLetterRun -gt 0) { [regex]"\p{L}{$MinLetterRun,}" } else { $null }

function Get-SkipReason([AllowEmptyString()][string]$RawBody, [string]$Plain) {
    if ([string]::IsNullOrWhiteSpace($Plain)) { return 'empty' }
    if ($RawBody -match '&lt;|&gt;') { return 'escaped-markup' }
    if ($null -ne $letterRunRx -and -not $letterRunRx.IsMatch($Plain)) { return 'no-word' }
    return ''
}

# Under the default scope the reference locale's own file says what is already translated. A key it
# holds is out of the export even if that translation is stale - refreshing shipped text is -All's job.
$refDir = $null
if (-not $All) {
    try { $refDir = Get-LocaleResourceDir -Tag $ReferenceLocale } catch { $refDir = $null }
    if (-not $refDir -or $refDir -eq 'values') {
        Write-Error "locale-bulk-export: '$ReferenceLocale' is not a declared locale, or is the default one." -ErrorAction Continue
        exit 1
    }
}

$lines = [System.Collections.Generic.List[string]]::new()
$index = [System.Collections.Generic.List[string]]::new()
$skipped = @{}
$perSet = [ordered]@{}
$script:currentSet = ''

function Add-Unit([string]$File, [string]$Kind, [string]$Key, [string]$Slot, [string]$RawBody) {
    $plain = ConvertFrom-ResourceBody $RawBody
    $reason = Get-SkipReason $RawBody $plain
    if ($reason) {
        if (-not $script:skipped.ContainsKey($reason)) { $script:skipped[$reason] = 0 }
        $script:skipped[$reason]++
        return
    }
    $script:lines.Add($plain)
    $record = [ordered]@{
        line    = $script:lines.Count
        set     = $script:currentSet
        file    = $File
        kind    = $Kind
        key     = $Key
        slot    = $Slot
        formats = (Get-FormatSignature $plain)
        en      = $plain
    }
    $script:index.Add(($record | ConvertTo-Json -Compress -Depth 3))
}

foreach ($set in $SourceSet) {
    $script:currentSet = $set
    $resDir = Join-Path $repoRoot "$Module/src/$set/res"
    $valuesDir = Join-Path $resDir 'values'
    if (-not (Test-Path -LiteralPath $valuesDir)) {
        Write-Host "locale-bulk-export: source set '$set' has no res/values - skipped ($valuesDir)"
        continue
    }

    $sourceFiles = @()
    if ($SourceFile) {
        $one = Join-Path $valuesDir $SourceFile
        if (-not (Test-Path -LiteralPath $one)) {
            Write-Host "locale-bulk-export: source set '$set' has no $SourceFile - skipped"
            continue
        }
        $sourceFiles = @(Get-Item -LiteralPath $one)
    } else {
        $sourceFiles = @(Get-ChildItem -LiteralPath $valuesDir -Filter 'strings*.xml' | Sort-Object Name)
    }

    $before = $lines.Count
    foreach ($file in $sourceFiles) {
        $translated = @{}
        if (-not $All) {
            $refPath = Join-Path (Join-Path $resDir $refDir) $file.Name
            if (Test-Path -LiteralPath $refPath) {
                foreach ($have in $elementRx.Matches((Get-Content -LiteralPath $refPath -Raw -Encoding UTF8))) {
                    $translated[$have.Groups[2].Value] = $true
                }
            }
        }

        foreach ($element in $elementRx.Matches((Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8))) {
            $kind = $element.Groups[1].Value
            $key = $element.Groups[2].Value
            if ($element.Groups[3].Value -match 'translatable\s*=\s*"false"') { continue }
            if ($translated.ContainsKey($key)) { continue }
            $body = $element.Groups[4].Value

            switch ($kind) {
                'plurals' {
                    foreach ($item in $pluralItemRx.Matches($body)) {
                        Add-Unit $file.Name $kind $key $item.Groups[1].Value $item.Groups[2].Value
                    }
                }
                'string-array' {
                    $slot = 0
                    foreach ($item in $arrayItemRx.Matches($body)) {
                        Add-Unit $file.Name $kind $key ([string]$slot) $item.Groups[1].Value
                        $slot++
                    }
                }
                default { Add-Unit $file.Name $kind $key '' $body }
            }
        }
    }
    $perSet[$set] = $lines.Count - $before
}

if ($perSet.Count -eq 0) {
    Write-Error "locale-bulk-export: none of the named source sets could be read." -ErrorAction Continue
    exit 1
}

if (-not (Test-Path -LiteralPath $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
$textPath = Join-Path $OutDir 'all_texts_en.txt'
$indexPath = Join-Path $OutDir 'all_texts_index.jsonl'
$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($textPath, (($lines -join "`n") + "`n"), $utf8)
[System.IO.File]::WriteAllText($indexPath, (($index -join "`n") + "`n"), $utf8)

$scope = if ($All) { 'whole corpus' } else { "missing in $ReferenceLocale" }
$setBreakdown = (($perSet.Keys | ForEach-Object { "$_ $($perSet[$_])" }) -join ', ')
Write-Host "locale-bulk-export: scope $scope | lines $($lines.Count) | by set: $setBreakdown"
foreach ($reason in ($skipped.Keys | Sort-Object)) { Write-Host "  skipped ($reason): $($skipped[$reason])" }
Write-Host "locale-bulk-export: wrote $textPath"
Write-Host "locale-bulk-export: wrote $indexPath (keep - the service never sees this one)"

if ($lines.Count -eq 0) {
    Write-Error "locale-bulk-export: nothing eligible - the export is empty." -ErrorAction Continue
    exit 3
}
exit 0
