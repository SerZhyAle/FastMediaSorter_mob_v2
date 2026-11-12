#requires -Version 7.0
<#
.SYNOPSIS
    Regenerate the ISO 639-2 table of the stream facet fold from the registration authority's code list.

.DESCRIPTION
    STREAM-BANK item P.3: a three-letter language code (ISO 639-2, bibliographic or terminological) folds
    to the language it names, like a two-letter code. The codes are taken from the Library of Congress
    list `ISO-639-2_utf-8.txt` - one row per language, `alpha3-B|alpha3-T|alpha2|English name|French name`
    - and never typed.

    A row is kept when its alpha-2 code is a code of the vocabulary's ISO 639-1 table, so the vocabulary
    stays closed: a language that has no two-letter code in the vocabulary and every collective or
    special code (`mul`, `und`, `zxx`) is left out. Its bibliographic code and, when different, its
    terminological code each become one line `<code3> <code2>`, sorted by code. The canonical name is read
    through the ISO 639-1 table at lookup time, so a name lives in one place.

    The table body and its two provenance comment lines are rewritten, in the same text, in
    scripts/streams/modules/StreamPublisher.Facets.ps1 (`$script:FacetIso6392`) and in
    app_v2/.../domain/streams/facets/StreamFacetTables.kt (`ISO_639_2_TABLE`); nothing else in either file
    is touched. A rerun on the same list changes nothing, the retrieval date included.

.PARAMETER SourceFile
    A local copy of the list. When omitted the list is downloaded from -Url.

.PARAMETER Url
    Where the list is downloaded from. Defaults to the Library of Congress location.

.PARAMETER RepoRoot
    The repository root the two table files are read from. Defaults to the root this script sits under.

.PARAMETER RetiredCodes
    Two-letter codes the vocabulary keeps although the list no longer maps them to a three-letter code
    (`bh`: the list holds only the collective `bih`, which names no language). Every other code of the
    vocabulary must appear in the list.

.PARAMETER Check
    Write nothing; exit 1 when either file differs from what a rewrite would produce.

.EXAMPLE
    pwsh -NoProfile -File scripts/streams/update-iso6392-table.ps1

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  both table files hold what the list produces (written, or unchanged; with -Check, unchanged).
      1  the list is inconsistent with the vocabulary (a vocabulary code absent from it, a duplicate or
         extension-colliding three-letter code), a table block is missing, or -Check found a difference.
      2  could not verify: the list could not be downloaded or read, or a table file is absent.
#>
[CmdletBinding()]
param(
    [string]$SourceFile,
    [string]$Url = 'https://www.loc.gov/standards/iso639-2/ISO-639-2_utf-8.txt',
    [string]$RepoRoot,
    [string[]]$RetiredCodes = @('bh'),
    [switch]$Check
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$modulePath = Join-Path $RepoRoot 'scripts/streams/modules/StreamPublisher.Facets.ps1'
$kotlinPath = Join-Path $RepoRoot `
    'app_v2/src/main/java/com/sza/fastmediasorter/domain/streams/facets/StreamFacetTables.kt'
$sourceLabel = ($Url -replace '^https?://(www\.)?', '')

foreach ($path in @($modulePath, $kotlinPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        Write-Host "update-iso6392-table: COULD NOT VERIFY - table file not found: $path"
        exit 2
    }
}

$listPath = $SourceFile
$downloaded = $null
if (-not $listPath) {
    $downloaded = Join-Path ([System.IO.Path]::GetTempPath()) ("iso639-2-{0}.txt" -f [guid]::NewGuid().ToString('N'))
    try {
        Invoke-WebRequest -Uri $Url -OutFile $downloaded -TimeoutSec 60 | Out-Null
    } catch {
        Write-Host "update-iso6392-table: COULD NOT VERIFY - download of $Url failed: $($_.Exception.Message)"
        exit 2
    }
    $listPath = $downloaded
}
if (-not (Test-Path -LiteralPath $listPath)) {
    Write-Host "update-iso6392-table: COULD NOT VERIFY - list not found: $listPath"
    exit 2
}

try {
    $hash = (Get-FileHash -LiteralPath $listPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $listText = [System.IO.File]::ReadAllText($listPath, [System.Text.UTF8Encoding]::new($false))
} finally {
    if ($downloaded -and (Test-Path -LiteralPath $downloaded)) { Remove-Item -LiteralPath $downloaded -Force }
}

. $modulePath

$twoLetter = [System.Collections.Generic.HashSet[string]]::new()
foreach ($line in ($script:FacetIso6391 -split "\r?\n")) {
    if ($line.Trim()) { [void]$twoLetter.Add(($line.Trim() -split ' ', 2)[0]) }
}
$extensionCodes = [System.Collections.Generic.HashSet[string]]::new()
foreach ($line in ($script:FacetIso6393Extension -split "\r?\n")) {
    if ($line.Trim()) { [void]$extensionCodes.Add(($line.Trim() -split ' ', 2)[0]) }
}

$entries = [System.Collections.Generic.Dictionary[string, string]]::new()
$covered = [System.Collections.Generic.HashSet[string]]::new()
$problems = [System.Collections.Generic.List[string]]::new()
foreach ($line in ($listText -split "\r?\n")) {
    $fields = $line.TrimStart([char]0xFEFF).Split('|')
    if ($fields.Count -lt 3) { continue }
    $code2 = $fields[2].Trim()
    if (-not $code2 -or -not $twoLetter.Contains($code2)) { continue }
    [void]$covered.Add($code2)
    foreach ($code3 in @($fields[0].Trim(), $fields[1].Trim() | Where-Object { $_ } | Select-Object -Unique)) {
        if ($code3 -notmatch '^[a-z]{3}$') { $problems.Add("malformed code '$code3' for '$code2'"); continue }
        if ($entries.ContainsKey($code3)) { $problems.Add("duplicate code '$code3'"); continue }
        if ($extensionCodes.Contains($code3)) { $problems.Add("code '$code3' collides with an extension code"); continue }
        $entries[$code3] = $code2
    }
}
$missing = @($twoLetter | Where-Object { -not $covered.Contains($_) -and $RetiredCodes -notcontains $_ } | Sort-Object)
if ($missing.Count -gt 0) { $problems.Add("vocabulary codes absent from the list: $($missing -join ' ')") }
if ($problems.Count -gt 0) {
    foreach ($problem in $problems) { Write-Host "  - $problem" }
    Write-Host "update-iso6392-table: FAIL - the list is inconsistent with the vocabulary ($($problems.Count) problem(s))."
    exit 1
}

$bodyLines = @($entries.Keys | Sort-Object | ForEach-Object { "$_ $($entries[$_])" })

function Get-FileState {
    param([string]$Path)
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    $text = [System.Text.UTF8Encoding]::new($false).GetString($bytes, $(if ($hasBom) { 3 } else { 0 }),
        $bytes.Length - $(if ($hasBom) { 3 } else { 0 }))
    [pscustomobject]@{ Text = $text; HasBom = $hasBom; Eol = $(if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }) }
}

$today = (Get-Date).ToString('yyyy-MM-dd')
$targets = @(
    @{ Path = $modulePath; Name = 'StreamPublisher.Facets.ps1'; Comment = '#'
       Pattern = "(?<=\`$script:FacetIso6392 = @'\r?\n)(.*?)(?='@)" },
    @{ Path = $kotlinPath; Name = 'StreamFacetTables.kt'; Comment = '//'
       Pattern = '(?<=internal const val ISO_639_2_TABLE = """\r?\n)(.*?)(?=""")' }
)

$results = @()
foreach ($target in $targets) {
    $state = Get-FileState -Path $target.Path
    $eol = $state.Eol
    $regex = [regex]::new($target.Pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)
    $match = $regex.Match($state.Text)
    if (-not $match.Success) {
        Write-Host "update-iso6392-table: FAIL - the ISO 639-2 table block is missing from $($target.Name)."
        exit 1
    }
    $body = ($bodyLines -join $eol) + $eol
    $withBody = $state.Text.Substring(0, $match.Index) + $body + $state.Text.Substring($match.Index + $match.Length)

    $existingRetrieved = [regex]::Match($withBody,
        "(?m)^[ \t]*$([regex]::Escape($target.Comment)) ISO 639-2 retrieved (?<date>\S+), sha256 $hash\.\s*$")
    $retrievedLine = if ($existingRetrieved.Success) {
        "$($target.Comment) ISO 639-2 retrieved $($existingRetrieved.Groups['date'].Value), sha256 $hash."
    } else {
        "$($target.Comment) ISO 639-2 retrieved $today, sha256 $hash."
    }
    $sourceLine = "$($target.Comment) ISO 639-2 source: $sourceLabel (Library of Congress)."
    $indent = [regex]::Match($withBody, "(?m)^([ \t]*)$([regex]::Escape($target.Comment)) ISO 639-2 (source|retrieved)").Groups[1].Value

    $outLines = [System.Collections.Generic.List[string]]::new()
    $placed = $false
    foreach ($line in ($withBody -split "\r?\n")) {
        if ($line -match "^[ \t]*$([regex]::Escape($target.Comment)) ISO 639-2 (source|retrieved)\b") {
            if (-not $placed) {
                $outLines.Add("$indent$sourceLine")
                $outLines.Add("$indent$retrievedLine")
                $placed = $true
            }
            continue
        }
        $outLines.Add($line)
    }
    if (-not $placed) {
        Write-Host "update-iso6392-table: FAIL - the provenance comment is missing above the table in $($target.Name)."
        exit 1
    }
    $newText = $outLines -join $eol
    $changed = $newText -ne $state.Text
    if ($changed -and -not $Check) {
        [System.IO.File]::WriteAllText($target.Path, $newText, [System.Text.UTF8Encoding]::new($state.HasBom))
    }
    $results += [pscustomobject]@{ Name = $target.Name; Changed = $changed }
}

$languages = @($entries.Values | Select-Object -Unique).Count
$summary = ($results | ForEach-Object { "{0} {1}" -f $_.Name, $(if ($_.Changed) { if ($Check) { 'DIFFERS' } else { 'written' } } else { 'unchanged' }) }) -join '; '
if ($Check -and (@($results | Where-Object Changed).Count -gt 0)) {
    Write-Host "update-iso6392-table: FAIL - $summary."
    exit 1
}
Write-Host ("update-iso6392-table: PASS - {0} code(s) for {1} language(s), list sha256 {2}; {3}." -f $entries.Count, $languages, $hash, $summary)
exit 0
