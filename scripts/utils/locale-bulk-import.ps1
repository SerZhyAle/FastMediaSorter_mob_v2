<#
.SYNOPSIS
    S1420: imports one locale's translated line file back into its string resources.

.DESCRIPTION
    Takes all_texts_<tag>.txt as returned by the external translation service, pairs line N with the
    Nth record of the sidecar written by locale-bulk-export.ps1, rebuilds one translation map per
    source file, and hands each map to seed-locale-tranche.ps1 with -Merge so an earlier tranche in
    the same file survives.

    Line position is the whole contract, so it is verified before anything is written:

      - The line count must equal the sidecar's exactly. A service that drops, splits or merges a line
        shifts every later line by one, and a shifted corpus builds and ships without a single warning
        while showing the wrong caption everywhere after the shift. A mismatch is refused outright.
      - Every line's format-token multiset must equal the English one recorded at export time. A lost
        or retyped %1$s survives the build and crashes at format time in front of the user. Such a
        line is rejected individually and its key is simply left untranslated - Android then falls
        back to English for that one key (ADR-6), which is a shipped state rather than a defect.
      - An empty line is rejected the same way. Position still holds, so a blank answer costs one key.

    Placeholder rejection is repeated by the seeder against the raw source, deliberately: this side
    compares against the exported text, the seeder against the file it writes into. Both must agree.

.PARAMETER TextPath
    The returned file, e.g. temp/S1420/box/all_texts_de.txt.

.PARAMETER Locale
    BCP-47 tag to write. Omit and it is read from the file name: the last dot-separated piece of the
    stem that is a declared locale wins, so all_texts_de.txt and all_texts_en.en.ar.txt both resolve.

.PARAMETER IndexPath
    Sidecar from the export. By default it is looked for beside -TextPath first, then in temp/S1420/box.

.PARAMETER Module
    Module path relative to repo root. Default app_v2.

.PARAMETER SourceSet
    Fallback source set for a sidecar record that does not name one. The sidecar normally does, so the
    export may span main, vr and noLegal in one file and each line still returns to its own set.

.PARAMETER DryRun
    Validate and write the per-file maps, but call no seeder and change no resource file.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/locale-bulk-import.ps1 -TextPath temp/S1420/box/all_texts_de.txt

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/locale-bulk-import.ps1 -TextPath temp/S1420/box/all_texts_zh-Hans.txt -DryRun

.OUTPUTS
    Exit codes:
      0 - every locale file written, nothing rejected.
      1 - unusable input: file or sidecar missing or unreadable, locale not declared or the default
          one, or the line count differs from the sidecar - nothing is written in that case.
      3 - written, but at least one line was rejected (placeholder mismatch, empty, or refused by the
          seeder). The named keys stay untranslated and fall back to English.

    House text style is corrected in place, not rejected (S1544): a run whose only event is
    normalization still exits 0, and each corrected line is named on stdout as 'normalized: ..'.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$TextPath,
    [string]$Locale,
    [string]$IndexPath,
    [string]$Module = 'app_v2',
    [string]$SourceSet = 'main',
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'locale-set.ps1')
. (Join-Path $repoRoot 'scripts/quality/lib/house-text-style.ps1')

if (-not (Test-Path -LiteralPath $TextPath)) {
    Write-Error "locale-bulk-import: translated file not found: $TextPath" -ErrorAction Continue
    exit 1
}
# The translated file normally arrives wherever the browser drops it, so the sidecar is looked for
# beside it first and in the export directory second. Both, rather than only the second, because an
# export written elsewhere with -OutDir must still round-trip from its own directory.
$exportDir = Join-Path $repoRoot 'temp/S1420/box'
$boxDir = Split-Path -Parent (Resolve-Path -LiteralPath $TextPath)
if (-not $IndexPath) {
    $IndexPath = Join-Path $boxDir 'all_texts_index.jsonl'
    if (-not (Test-Path -LiteralPath $IndexPath)) {
        $IndexPath = Join-Path $exportDir 'all_texts_index.jsonl'
        $boxDir = $exportDir
    }
}
if (-not (Test-Path -LiteralPath $IndexPath)) {
    Write-Error "locale-bulk-import: sidecar not found beside $TextPath nor in $exportDir - re-run locale-bulk-export.ps1 to rebuild it." -ErrorAction Continue
    exit 1
}

# The service names its answer after its own pipeline, not after the request - all_texts_en.en.ar.txt
# for Arabic - so the tag is the last dot-separated piece that is actually a declared locale, and the
# rest of the stem is ignored. Guessing from the whole stem would have yielded 'en.en.ar'.
if (-not $Locale) {
    $stem = [System.IO.Path]::GetFileNameWithoutExtension($TextPath)
    if ($stem -match '^all_texts[_.](.+)$') {
        $declared = Get-SupportedLocales
        foreach ($candidate in ($Matches[1] -split '[._]')) {
            $match = $declared | Where-Object { $_ -ieq $candidate } | Select-Object -First 1
            if ($match -and $match -ine 'en') { $Locale = $match }
        }
    }
}
if (-not $Locale) {
    Write-Error "locale-bulk-import: -Locale not given and the file name does not spell it (expected all_texts_<tag>.txt)." -ErrorAction Continue
    exit 1
}
$localeDir = $null
try { $localeDir = Get-LocaleResourceDir -Tag $Locale } catch { $localeDir = $null }
if (-not $localeDir -or $localeDir -eq 'values') {
    Write-Error "locale-bulk-import: '$Locale' is not a declared locale, or is the default one." -ErrorAction Continue
    exit 1
}

$records = @()
foreach ($jsonLine in [System.IO.File]::ReadAllLines($IndexPath, [System.Text.UTF8Encoding]::new($false))) {
    if ([string]::IsNullOrWhiteSpace($jsonLine)) { continue }
    $records += ($jsonLine | ConvertFrom-Json)
}
if ($records.Count -eq 0) {
    Write-Error "locale-bulk-import: sidecar $IndexPath holds no records." -ErrorAction Continue
    exit 1
}

# A trailing newline is normal and yields one empty tail element; anything beyond that is a real
# line-count difference and must not be trimmed away.
$translated = @([System.IO.File]::ReadAllText($TextPath) -split "`r?`n")
if ($translated.Count -gt 0 -and $translated[-1] -eq '') { $translated = $translated[0..($translated.Count - 2)] }
if ($translated.Count -gt 0) { $translated[0] = $translated[0].TrimStart([char]0xFEFF) }

if ($translated.Count -ne $records.Count) {
    Write-Error "locale-bulk-import: line count differs - $TextPath has $($translated.Count), the English export had $($records.Count). Nothing written: every line after the first divergence would land on the wrong key." -ErrorAction Continue
    exit 1
}

function Get-FormatSignature([AllowEmptyString()][string]$Text) {
    return (([regex]::Matches($Text, '%(\d+\$)?[a-zA-Z]') | ForEach-Object { $_.Value } | Sort-Object) -join '|')
}

$maps = [ordered]@{}
$rejected = [System.Collections.Generic.List[string]]::new()
$normalized = [System.Collections.Generic.List[string]]::new()
$accepted = 0

# The yo rule is deliberately excluded: none of the ten bulk locales is Russian, and the rule would
# only ever misfire on a Cyrillic-looking token in another language.
$styleRules = @('ellipsis', 'long-dash')

for ($i = 0; $i -lt $records.Count; $i++) {
    $record = $records[$i]
    $value = $translated[$i].Trim()
    if ([string]::IsNullOrWhiteSpace($value)) {
        [void]$rejected.Add("line $($i + 1) $($record.key) (empty translation)")
        continue
    }
    if ((Get-FormatSignature $value) -ne [string]$record.formats) {
        [void]$rejected.Add("line $($i + 1) $($record.key) (placeholder mismatch: expected '$($record.formats)')")
        continue
    }

    # The service re-typographs what it is given - the English source of every key is house-style
    # clean and the answer comes back with an ellipsis character and an en dash (S1544). Correct it
    # rather than reject it: a lost format token crashes the app, a stray dash does not, so the key
    # stays translated and the change is reported instead.
    $styled = Convert-HouseStyleText -Text $value -Area ResourceValue -Rules $styleRules
    if ($styled.Changed) {
        [void]$normalized.Add("line $($i + 1) $($record.key) ($(($styled.RulesFired | Sort-Object) -join ', '))")
        $value = $styled.Text
    }

    # One export may span several source sets, and strings.xml exists in more than one of them, so the
    # set has to be part of the map's identity - keying by file name alone would merge vr into main.
    $set = if (($record.PSObject.Properties.Name -contains 'set') -and $record.set) { [string]$record.set } else { $SourceSet }
    $mapKey = "$set|$($record.file)"
    if (-not $maps.Contains($mapKey)) { $maps[$mapKey] = [ordered]@{} }
    $fileMap = $maps[$mapKey]
    switch ($record.kind) {
        'plurals' {
            if (-not $fileMap.Contains($record.key)) { $fileMap[$record.key] = [ordered]@{} }
            $fileMap[$record.key][[string]$record.slot] = $value
        }
        'string-array' {
            if (-not $fileMap.Contains($record.key)) { $fileMap[$record.key] = [System.Collections.Generic.List[string]]::new() }
            $fileMap[$record.key].Add($value)
        }
        default { $fileMap[$record.key] = $value }
    }
    $accepted++
}

# Maps are build artifacts, so they stay under temp/ even when the translated file came from elsewhere.
$mapDir = Join-Path $exportDir "maps/$Locale"
if (-not (Test-Path -LiteralPath $mapDir)) { New-Item -ItemType Directory -Path $mapDir -Force | Out-Null }

Write-Host "locale-bulk-import: $Locale <- $TextPath | lines $($records.Count) | accepted $accepted | rejected $($rejected.Count) | normalized $($normalized.Count)"
foreach ($reason in $rejected) { Write-Host "  rejected: $reason" }
foreach ($note in $normalized) { Write-Host "  normalized: $note" }

$seeder = Join-Path $PSScriptRoot 'seed-locale-tranche.ps1'
$pwshExe = [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
$seedFailed = 0
$utf8 = [System.Text.UTF8Encoding]::new($false)

foreach ($mapKey in $maps.Keys) {
    $set, $sourceFile = $mapKey -split '\|', 2
    $setDir = Join-Path $mapDir $set
    if (-not (Test-Path -LiteralPath $setDir)) { New-Item -ItemType Directory -Path $setDir -Force | Out-Null }
    $mapPath = Join-Path $setDir ([System.IO.Path]::GetFileNameWithoutExtension($sourceFile) + '.json')
    [System.IO.File]::WriteAllText($mapPath, ($maps[$mapKey] | ConvertTo-Json -Depth 5), $utf8)
    if ($DryRun) {
        Write-Host "  map: $mapPath ($($maps[$mapKey].Count) keys) - -DryRun, not seeded"
        continue
    }
    & $pwshExe -NoProfile -File $seeder -Module $Module -SourceSet $set -SourceFile $sourceFile -Locale $Locale -Merge -MapPath $mapPath
    $seedExit = $LASTEXITCODE
    if ($seedExit -ne 0) { $seedFailed++ }
}

if ($DryRun) {
    Write-Host "locale-bulk-import: -DryRun, no resource file changed."
}

if ($seedFailed -gt 0) {
    Write-Error "locale-bulk-import: $seedFailed source file(s) reported rejected translations - see the seeder output above." -ErrorAction Continue
    exit 3
}
if ($rejected.Count -gt 0) {
    Write-Error "locale-bulk-import: $($rejected.Count) line(s) rejected; their keys stay untranslated." -ErrorAction Continue
    exit 3
}
exit 0
